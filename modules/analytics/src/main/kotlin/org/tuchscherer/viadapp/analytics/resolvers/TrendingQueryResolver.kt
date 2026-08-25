package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.port.PostStatusLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.QueryResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.resolver.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.Post as ViaductPost

/**
 * Resolver for Query.trending.
 *
 * Returns the most-viewed posts in descending view-count order. Posts of any type
 * (BlogPost or CheckedListPost) are eligible. Each post is returned as a node reference
 * so Viaduct resolves its fields lazily via the registered node resolver — the analytics
 * module needs no compile-time dependency on the root project's PostRepository.
 *
 * [PostTypeLookupPort] is used to determine the concrete type for each post ID so the
 * correct [ViaductBlogPost] or [ViaductCheckedListPost] node-ref is produced.
 */
@Resolver
class TrendingQueryResolver : QueryResolvers.Trending() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)
    private val postTypeLookupPort: PostTypeLookupPort by inject(PostTypeLookupPort::class.java)
    private val postStatusLookupPort: PostStatusLookupPort by inject(PostStatusLookupPort::class.java)

    private companion object {
        /**
         * How many extra candidates to rank before dropping unpublished ones.
         *
         * Views are ranked first and filtered second, so filtering alone would return fewer than
         * the requested limit. Over-fetching keeps the list full in the common case without
         * paging through the whole table.
         */
        const val OVER_FETCH_FACTOR = 3
    }

    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        val limit = ctx.arguments.limit ?: 10

        // A post keeps the views it earned while published, so unpublishing it would otherwise
        // leave it sitting in trending. Filter by current status, not by view history.
        val candidates = postViewRepository.getMostViewed(limit * OVER_FETCH_FACTOR)
        if (candidates.isEmpty()) return emptyList()

        val published = postStatusLookupPort.publishedIds(candidates)
        val postIds = candidates.filter { it in published }.take(limit)
        if (postIds.isEmpty()) return emptyList()

        val typeByPostId = postTypeLookupPort.getPostTypes(postIds)

        return postIds.mapNotNull { postId ->
            when (typeByPostId[postId]) {
                PostKind.CHECKLIST_POST ->
                    ctx.nodeRef(ctx.globalIDFor(ViaductCheckedListPost.Reflection, postId.toString()))
                PostKind.BLOG_POST ->
                    ctx.nodeRef(ctx.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
                null -> null // post was deleted; view records are orphaned — skip it
            }
        }
    }
}
