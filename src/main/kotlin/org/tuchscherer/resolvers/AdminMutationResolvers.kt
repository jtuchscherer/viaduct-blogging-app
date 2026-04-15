package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAdmin
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.AdminDeleteUserResult
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

/**
 * Resolver for adminUpdateUser mutation - update any user's details.
 */
@Resolver
class AdminUpdateUserResolver(
    private val userRepository: UserRepository
) : MutationResolvers.AdminUpdateUser() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        requireAdmin(ctx.requestContext)

        val input = ctx.arguments.input
        val userId = UUID.fromString(input.id.internalID)

        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User not found")

        input.name?.let { require(it.length <= 255) { "Name cannot exceed 255 characters" } }
        input.email?.let { require(it.length <= 255) { "Email cannot exceed 255 characters" } }

        // Update fields if provided
        transaction {
            input.name?.let { user.name = it }
            input.email?.let { user.email = it }
            input.isAdmin?.let { user.isAdmin = it }
            user.flush()
        }

        return ViaductUser.of(ctx) {
            id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
            username(user.username)
            email(user.email)
            name(user.name)
            createdAt(user.createdAt.toString())
        }
    }
}

/**
 * Resolver for adminDeleteUser mutation - delete a user and all their content.
 */
@Resolver
class AdminDeleteUserResolver(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository
) : MutationResolvers.AdminDeleteUser() {
    override suspend fun resolve(ctx: Context): AdminDeleteUserResult {
        requireAdmin(ctx.requestContext)

        val userId = UUID.fromString(ctx.arguments.id.internalID)

        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User not found")

        // Delete in order: likes, comments, posts, then user
        val likesDeleted = likeRepository.deleteByUserId(userId)
        val commentsDeleted = commentRepository.deleteByUserId(userId)
        val postsDeleted = postRepository.deleteByAuthorId(userId)
        val userDeleted = userRepository.delete(userId)

        return AdminDeleteUserResult.of(ctx) {
            success(userDeleted)
            postsDeleted(postsDeleted)
            commentsDeleted(commentsDeleted)
            likesDeleted(likesDeleted)
        }
    }
}

/**
 * Resolver for adminUpdatePost mutation - update any post.
 */
@Resolver
class AdminUpdatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.AdminUpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        requireAdmin(ctx.requestContext)

        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id.internalID)

        input.title?.let {
            require(it.isNotBlank()) { "Title cannot be blank" }
            require(it.length <= 500) { "Title cannot exceed 500 characters" }
        }
        input.content?.let { require(it.length <= 100_000) { "Content cannot exceed 100,000 characters" } }

        val updatedPost = postRepository.updateById(
            id = postId,
            title = input.title,
            content = input.content
        ) ?: throw NotFoundException("Post not found")

        return ViaductPost.of(ctx) {
            id(ctx.globalIDFor(ViaductPost.Reflection, updatedPost.id.value.toString()))
            title(updatedPost.title)
            content(updatedPost.content)
            createdAt(updatedPost.createdAt.toString())
            updatedAt(updatedPost.updatedAt.toString())
        }
    }
}

/**
 * Resolver for adminDeletePost mutation - delete any post.
 */
@Resolver
class AdminDeletePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.AdminDeletePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        requireAdmin(ctx.requestContext)

        val postId = UUID.fromString(ctx.arguments.id.internalID)

        if (postRepository.findById(postId) == null) {
            throw NotFoundException("Post not found")
        }

        return postRepository.delete(postId)
    }
}

/**
 * Resolver for adminDeleteComment mutation - delete any comment.
 */
@Resolver
class AdminDeleteCommentResolver(
    private val commentRepository: CommentRepository
) : MutationResolvers.AdminDeleteComment() {
    override suspend fun resolve(ctx: Context): Boolean {
        requireAdmin(ctx.requestContext)

        val commentId = UUID.fromString(ctx.arguments.id.internalID)

        if (commentRepository.findById(commentId) == null) {
            throw NotFoundException("Comment not found")
        }

        return commentRepository.delete(commentId)
    }
}
