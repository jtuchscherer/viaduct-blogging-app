package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAdmin
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.AdminMutationsResolvers
import viaduct.api.Resolver
import viaduct.api.grts.AdminDeleteUserResult
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

/**
 * Resolver for adminUpdateUser mutation - update any user's details.
 */
@Resolver
class AdminUpdateUserResolver(
    private val userRepository: UserRepository
) : AdminMutationsResolvers.UpdateUser() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        requireAdmin(ctx.requestContext)

        val input = ctx.arguments.input
        val userId = UUID.fromString(input.id.internalID)

        input.name?.let { require(it.length <= 255) { "Name cannot exceed 255 characters" } }
        input.email?.let { require(it.length <= 255) { "Email cannot exceed 255 characters" } }

        val user = userRepository.updateFields(
            id = userId,
            name = input.name,
            email = input.email,
            isAdmin = input.isAdmin,
        ) ?: throw NotFoundException("User not found")

        return user.toViaductUser(ctx)
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
) : AdminMutationsResolvers.DeleteUser() {
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
) : AdminMutationsResolvers.UpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductBlogPost {
        requireAdmin(ctx.requestContext)

        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id.internalID)

        input.title?.let(PostValidation::validateTitle)
        input.content?.let(PostValidation::validateContent)

        val updatedPost = postRepository.updateById(
            id = postId,
            title = input.title,
            content = input.content
        ) ?: throw NotFoundException("Post not found")

        return updatedPost.toViaductBlogPost(ctx)
    }
}

/**
 * Resolver for adminDeletePost mutation - delete any post.
 */
@Resolver
class AdminDeletePostResolver(
    private val postRepository: PostRepository
) : AdminMutationsResolvers.DeletePost() {
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
) : AdminMutationsResolvers.DeleteComment() {
    override suspend fun resolve(ctx: Context): Boolean {
        requireAdmin(ctx.requestContext)

        val commentId = UUID.fromString(ctx.arguments.id.internalID)

        if (commentRepository.findById(commentId) == null) {
            throw NotFoundException("Comment not found")
        }

        return commentRepository.delete(commentId)
    }
}
