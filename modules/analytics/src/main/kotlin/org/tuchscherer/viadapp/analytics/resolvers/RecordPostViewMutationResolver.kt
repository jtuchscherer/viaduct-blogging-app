package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import java.util.UUID

/**
 * Resolver for Mutation.recordPostView.
 *
 * Increments the view count for the given post. No authentication required —
 * view tracking is a public, unauthenticated operation.
 */
@Resolver
class RecordPostViewMutationResolver : MutationResolvers.RecordPostView() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.postId.internalID)
        postViewRepository.incrementViewCount(postId)
        return true
    }
}
