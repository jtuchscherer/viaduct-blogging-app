package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.decodeGlobalId
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver

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

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = decodeGlobalId(ctx.arguments.postId)
        postViewRepository.incrementViewCount(postId)
        return true
    }
}
