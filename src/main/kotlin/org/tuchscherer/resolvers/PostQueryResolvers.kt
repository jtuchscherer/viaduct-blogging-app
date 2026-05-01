package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.PostsConnection
import java.util.*

@Resolver
class PostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Posts() {
    override suspend fun resolve(ctx: Context): List<ViaductBlogPost> {
        return postRepository.findAll().map { it.toViaductBlogPost(ctx) }
    }
}

@Resolver
class PostResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductBlogPost? {
        val postId = UUID.fromString(ctx.arguments.id.internalID)
        return postRepository.findById(postId)?.toViaductBlogPost(ctx)
    }
}

@Resolver
class PostsConnectionResolver(
    private val postRepository: PostRepository
) : QueryResolvers.PostsConnection() {

    companion object {
        const val DEFAULT_PAGE_SIZE = 10
    }

    override suspend fun resolve(ctx: Context): PostsConnection? {
        val offsetLimit = ctx.arguments.toOffsetLimit(DEFAULT_PAGE_SIZE)
        val posts = postRepository.findPage(offsetLimit.limit, offsetLimit.offset)
        val totalCount = postRepository.count().toCountInt()
        val hasMore = (offsetLimit.offset + posts.size) < totalCount

        // ConnectionBuilder.fromSlice handles cursor encoding (OffsetCursor), edge
        // construction, and pageInfo (hasNextPage / hasPreviousPage / startCursor /
        // endCursor) internally — added in Viaduct 0.30.0. fromSlice returns the
        // parent ConnectionBuilder type, so totalCount() (which only exists on the
        // generated PostsConnection.Builder subclass) is set up front.
        val builder = PostsConnection.Builder(ctx).totalCount(totalCount)
        builder.fromSlice(posts, hasMore) { it.toViaductBlogPost(ctx) }
        return builder.build()
    }
}

@Resolver
class MyPostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.MyPosts() {
    override suspend fun resolve(ctx: Context): List<ViaductBlogPost> {
        val user = requireAuth(ctx.requestContext)
        return postRepository.findByAuthorId(user.id).map { it.toViaductBlogPost(ctx) }
    }
}
