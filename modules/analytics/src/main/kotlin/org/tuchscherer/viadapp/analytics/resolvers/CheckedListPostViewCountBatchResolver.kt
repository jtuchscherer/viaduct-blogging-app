package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.FieldValue
import viaduct.api.Resolver
import java.util.UUID

/**
 * Batch resolver for CheckedListPost.viewCount.
 * Fetches view counts for all requested posts in a single DB round-trip.
 * Posts that have never been viewed return 0.
 */
@Resolver(objectValueFragment = "fragment _ on CheckedListPost { id }")
class CheckedListPostViewCountBatchResolver : CheckedListPostResolvers.ViewCount() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Int>> {
        val postIds = contexts.map { UUID.fromString(it.objectValue.getId().internalID) }
        val viewCounts = postViewRepository.bulkGetViewCounts(postIds)
        return postIds.map { postId ->
            FieldValue.ofValue((viewCounts[postId] ?: 0L).toInt())
        }
    }
}
