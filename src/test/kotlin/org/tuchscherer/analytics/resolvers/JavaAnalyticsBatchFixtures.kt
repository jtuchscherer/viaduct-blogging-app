package org.tuchscherer.analytics.resolvers

import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostViewCountBatchResolver
import viaduct.java.api.resolvers.FieldValue
import java.util.concurrent.CompletableFuture

// Java batches return identity-keyed maps. Read results in original context order so the
// assertions remain about the value returned for each parent, independent of map iteration.
private fun <C, V> orderedResults(
    contexts: List<C>,
    resolve: (List<C>) -> CompletableFuture<Map<C, FieldValue<V>>>,
): List<FieldValue<V>> {
    val results = resolve(contexts).join()
    return contexts.map { checkNotNull(results[it]) }
}

internal fun BlogPostViewCountBatchResolver.resolveBatch(contexts: List<BlogPostResolvers.ViewCount.Context>) =
    orderedResults(contexts, ::batchResolveWithErrors)

internal fun CheckedListPostViewCountBatchResolver.resolveBatch(contexts: List<CheckedListPostResolvers.ViewCount.Context>) =
    orderedResults(contexts, ::batchResolveWithErrors)

internal fun BlogPostReadTimeMinutesBatchResolver.resolveBatch(contexts: List<BlogPostResolvers.ReadTimeMinutes.Context>) =
    orderedResults(contexts, ::batchResolveWithErrors)

internal fun CheckedListPostReadTimeMinutesBatchResolver.resolveBatch(contexts: List<CheckedListPostResolvers.ReadTimeMinutes.Context>) =
    orderedResults(contexts, ::batchResolveWithErrors)
