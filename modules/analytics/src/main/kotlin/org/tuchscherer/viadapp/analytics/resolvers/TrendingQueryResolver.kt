package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.QueryResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost

/**
 * Resolver for Query.trending.
 *
 * Returns the most-viewed posts in descending view-count order.
 * Each post is returned as a node reference so Viaduct resolves its fields lazily
 * via the existing BlogPostNodeResolver — the analytics module needs no compile-time
 * dependency on the root project's PostRepository.
 */
@Resolver
class TrendingQueryResolver : QueryResolvers.Trending() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductBlogPost> {
        val limit = ctx.arguments.limit ?: 10
        val postIds = postViewRepository.getMostViewed(limit)
        return postIds.map {
            ctx.nodeRef(ctx.globalIDFor(ViaductBlogPost.Reflection, it.toString()))
        }
    }
}
