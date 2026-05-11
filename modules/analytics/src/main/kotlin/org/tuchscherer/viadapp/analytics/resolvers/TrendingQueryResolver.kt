package org.tuchscherer.viadapp.analytics.resolvers

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

    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        val limit = ctx.arguments.limit ?: 10
        val postIds = postViewRepository.getMostViewed(limit)
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
