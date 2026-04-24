package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAdmin
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.AdminQueriesResolvers
import viaduct.api.Resolver
import viaduct.api.grts.AdminCommentsPage
import viaduct.api.grts.AdminPostsPage
import viaduct.api.grts.AdminStats
import viaduct.api.grts.AdminUsersPage
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import viaduct.api.grts.UserContentCounts
import java.util.*

/**
 * Resolver for admin.stats query - returns system-wide statistics.
 */
@Resolver
class AdminStatsResolver(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository
) : AdminQueriesResolvers.Stats() {
    override suspend fun resolve(ctx: Context): AdminStats {
        requireAdmin(ctx.requestContext)

        return AdminStats.of(ctx) {
            userCount(userRepository.count().toCountInt())
            postCount(postRepository.count().toCountInt())
            commentCount(commentRepository.count().toCountInt())
            likeCount(likeRepository.count().toCountInt())
        }
    }
}

/**
 * Resolver for admin.users query - returns a paginated page of users.
 */
@Resolver
class AdminUsersResolver(
    private val userRepository: UserRepository
) : AdminQueriesResolvers.Users() {
    override suspend fun resolve(ctx: Context): AdminUsersPage {
        requireAdmin(ctx.requestContext)

        val limit = ctx.arguments.limit ?: 10
        val offset = ctx.arguments.offset ?: 0
        val users = userRepository.findPage(limit, offset).map { user ->
            ViaductUser.of(ctx) {
                id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
                username(user.username)
                email(user.email)
                name(user.name)
                createdAt(user.createdAt.toString())
            }
        }
        return AdminUsersPage.of(ctx) {
            users(users)
            totalCount(userRepository.count().toCountInt())
        }
    }
}

/**
 * Resolver for admin.user query - returns a single user by ID.
 */
@Resolver
class AdminUserResolver(
    private val userRepository: UserRepository
) : AdminQueriesResolvers.User() {
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
 * Resolver for admin.userContentCounts query - returns content counts for a user.
 */
@Resolver
class AdminUserContentCountsResolver(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository
) : AdminQueriesResolvers.UserContentCounts() {
    override suspend fun resolve(ctx: Context): UserContentCounts {
        requireAdmin(ctx.requestContext)

        val userId = UUID.fromString(ctx.arguments.userId.internalID)

        return UserContentCounts.of(ctx) {
            postCount(postRepository.countByAuthorId(userId).toCountInt())
            commentCount(commentRepository.countByUserId(userId).toCountInt())
            likeCount(likeRepository.countByUserId(userId).toCountInt())
        }
    }
}

/**
 * Resolver for admin.posts query - returns a paginated page of posts.
 */
@Resolver
class AdminPostsResolver(
    private val postRepository: PostRepository
) : AdminQueriesResolvers.Posts() {
    override suspend fun resolve(ctx: Context): AdminPostsPage {
        requireAdmin(ctx.requestContext)

        val limit = ctx.arguments.limit ?: 10
        val offset = ctx.arguments.offset ?: 0
        val posts = postRepository.findPage(limit, offset).map { post ->
            ViaductPost.of(ctx) {
                id(ctx.globalIDFor(ViaductPost.Reflection, post.id.value.toString()))
                title(post.title)
                content(post.content)
                createdAt(post.createdAt.toString())
                updatedAt(post.updatedAt.toString())
            }
        }
        return AdminPostsPage.of(ctx) {
            posts(posts)
            totalCount(postRepository.count().toCountInt())
        }
    }
}

/**
 * Resolver for admin.post query - returns a single post by ID.
 */
@Resolver
class AdminPostResolver(
    private val postRepository: PostRepository
) : AdminQueriesResolvers.Post() {
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
 * Resolver for admin.comments query - returns a paginated page of comments.
 */
@Resolver
class AdminCommentsResolver(
    private val commentRepository: CommentRepository
) : AdminQueriesResolvers.Comments() {
    override suspend fun resolve(ctx: Context): AdminCommentsPage {
        requireAdmin(ctx.requestContext)

        val limit = ctx.arguments.limit ?: 10
        val offset = ctx.arguments.offset ?: 0
        val comments = commentRepository.findPage(limit, offset).map { comment ->
            ViaductComment.of(ctx) {
                id(ctx.globalIDFor(ViaductComment.Reflection, comment.id.value.toString()))
                content(comment.content)
                createdAt(comment.createdAt.toString())
            }
        }
        return AdminCommentsPage.of(ctx) {
            comments(comments)
            totalCount(commentRepository.count().toCountInt())
        }
    }
}
