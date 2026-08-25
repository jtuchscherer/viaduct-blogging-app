package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.decodeGlobalId
import org.tuchscherer.analytics.port.PostStatusLookupPort
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.resolver.Resolver

/**
 * Resolver for Mutation.recordPostView.
 *
 * Increments the view count for the given post (any type — BlogPost or CheckedListPost).
 * No authentication required — view tracking is a public, unauthenticated operation.
 *
 * The [postId] argument is a raw GraphQL `ID!` containing the Viaduct-encoded global ID
 * (base64 `TypeName:uuid`). The UUID is extracted via [decodeGlobalId]; the type prefix is
 * discarded because the PostViews table is type-agnostic.
 */
@Resolver
class RecordPostViewMutationResolver : MutationResolvers.RecordPostView() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)
    private val postStatusLookup: PostStatusLookupPort by inject(PostStatusLookupPort::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = decodeGlobalId(ctx.arguments.postId)

        // A draft has no audience, so it must not accumulate views — otherwise it could climb
        // into trending the moment it is published, or leak its existence through a view count.
        if (postId !in postStatusLookup.publishedIds(listOf(postId))) return false

        postViewRepository.incrementViewCount(postId)
        return true
    }
}
