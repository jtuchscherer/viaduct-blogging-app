package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.PageInfo
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.PostEdge
import viaduct.api.grts.PostsConnection
import java.util.*
import java.util.Base64

@Resolver
class PostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Posts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
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

@Resolver
class PostResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost? {
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

@Resolver
class PostsConnectionResolver(
    private val postRepository: PostRepository
) : QueryResolvers.PostsConnection() {

    companion object {
        const val DEFAULT_PAGE_SIZE = 10
        private const val CURSOR_PREFIX = "__viaduct:idx:"

        fun encodeCursor(index: Int): String =
            Base64.getEncoder().encodeToString("$CURSOR_PREFIX$index".toByteArray())
    }

    @Suppress("UnstableApiUsage")
    override suspend fun resolve(ctx: Context): PostsConnection? {
        val offsetLimit = ctx.arguments.toOffsetLimit(DEFAULT_PAGE_SIZE)
        val posts = postRepository.findPage(offsetLimit.limit, offsetLimit.offset)
        val totalCount = postRepository.count().toInt()

        val edges = posts.mapIndexed { i, post ->
            val cursor = encodeCursor(offsetLimit.offset + i)
            val node = ViaductPost.of(ctx) {
                id(ctx.globalIDFor(ViaductPost.Reflection, post.id.value.toString()))
                title(post.title)
                content(post.content)
                createdAt(post.createdAt.toString())
                updatedAt(post.updatedAt.toString())
            }
            PostEdge.of(ctx) {
                node(node)
                cursor(cursor)
            }
        }

        val hasNextPage = (offsetLimit.offset + posts.size) < totalCount
        val startCursor = if (edges.isNotEmpty()) encodeCursor(offsetLimit.offset) else null
        val endCursor = if (edges.isNotEmpty()) encodeCursor(offsetLimit.offset + edges.size - 1) else null

        val pageInfo = PageInfo.of(ctx) {
            hasNextPage(hasNextPage)
            hasPreviousPage(offsetLimit.offset > 0)
            startCursor(startCursor)
            endCursor(endCursor)
        }

        return PostsConnection.of(ctx) {
            totalCount(totalCount)
            edges(edges)
            pageInfo(pageInfo)
        }
    }
}

@Resolver
class MyPostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.MyPosts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        val user = requireAuth(ctx.requestContext)

        return postRepository.findByAuthorId(user.id).map { post ->
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
