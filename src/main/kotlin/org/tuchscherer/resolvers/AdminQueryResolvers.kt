package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAdmin
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.AdminStats
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import viaduct.api.grts.UserContentCounts
import java.util.*

/**
 * Resolver for adminStats query - returns system-wide statistics.
 */
@Resolver
class AdminStatsResolver(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository
) : QueryResolvers.AdminStats() {
    override suspend fun resolve(ctx: Context): AdminStats {
        requireAdmin(ctx.requestContext)

        return AdminStats.of(ctx) {
            userCount(userRepository.count().toInt())
            postCount(postRepository.count().toInt())
            commentCount(commentRepository.count().toInt())
            likeCount(likeRepository.count().toInt())
        }
    }
}

/**
 * Resolver for adminUsers query - returns all users.
 */
@Resolver
class AdminUsersResolver(
    private val userRepository: UserRepository
) : QueryResolvers.AdminUsers() {
    override suspend fun resolve(ctx: Context): List<ViaductUser> {
        requireAdmin(ctx.requestContext)

        return userRepository.findAll().map { user ->
            ViaductUser.of(ctx) {
                id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
                username(user.username)
                email(user.email)
                name(user.name)
                createdAt(user.createdAt.toString())
            }
        }
    }
}

/**
 * Resolver for adminUser query - returns a single user by ID.
 */
@Resolver
class AdminUserResolver(
    private val userRepository: UserRepository
) : QueryResolvers.AdminUser() {
    override suspend fun resolve(ctx: Context): ViaductUser? {
        requireAdmin(ctx.requestContext)

        val userId = UUID.fromString(ctx.arguments.id.internalID)
        return userRepository.findById(userId)?.let { user ->
            ViaductUser.of(ctx) {
                id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
                username(user.username)
                email(user.email)
                name(user.name)
                createdAt(user.createdAt.toString())
            }
        }
    }
}

/**
 * Resolver for adminUserContentCounts query - returns content counts for a user.
 */
@Resolver
class AdminUserContentCountsResolver(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository
) : QueryResolvers.AdminUserContentCounts() {
    override suspend fun resolve(ctx: Context): UserContentCounts {
        requireAdmin(ctx.requestContext)

        val userId = UUID.fromString(ctx.arguments.userId.internalID)

        return UserContentCounts.of(ctx) {
            postCount(postRepository.countByAuthorId(userId).toInt())
            commentCount(commentRepository.countByUserId(userId).toInt())
            likeCount(likeRepository.countByUserId(userId).toInt())
        }
    }
}

/**
 * Resolver for adminPosts query - returns all posts.
 */
@Resolver
class AdminPostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.AdminPosts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        requireAdmin(ctx.requestContext)

        return postRepository.findAll().map { post ->
            ViaductPost.of(ctx) {
                id(ctx.globalIDFor(ViaductPost.Reflection, post.id.value.toString()))
                title(post.title)
                content(post.content)
                createdAt(post.createdAt.toString())
                updatedAt(post.updatedAt.toString())
            }
        }
    }
}

/**
 * Resolver for adminPost query - returns a single post by ID.
 */
@Resolver
class AdminPostResolver(
    private val postRepository: PostRepository
) : QueryResolvers.AdminPost() {
    override suspend fun resolve(ctx: Context): ViaductPost? {
        requireAdmin(ctx.requestContext)

        val postId = UUID.fromString(ctx.arguments.id.internalID)
        return postRepository.findById(postId)?.let { post ->
            ViaductPost.of(ctx) {
                id(ctx.globalIDFor(ViaductPost.Reflection, post.id.value.toString()))
                title(post.title)
                content(post.content)
                createdAt(post.createdAt.toString())
                updatedAt(post.updatedAt.toString())
            }
        }
    }
}

/**
 * Resolver for adminComments query - returns all comments.
 */
@Resolver
class AdminCommentsResolver(
    private val commentRepository: CommentRepository
) : QueryResolvers.AdminComments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        requireAdmin(ctx.requestContext)

        return commentRepository.findAll().map { comment ->
            ViaductComment.of(ctx) {
                id(ctx.globalIDFor(ViaductComment.Reflection, comment.id.value.toString()))
                content(comment.content)
                createdAt(comment.createdAt.toString())
            }
        }
    }
}
