package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.estimateReadTime
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver

/**
 * Batch resolver for CheckedListPost.readTimeMinutes.
 *
 * Concatenates all item texts, then estimates reading time using the shared [estimateReadTime]
 * heuristic (200 WPM, 0.5 min minimum). Pure computation — no database access needed.
 *
 * Note: [getItems] and [getText] are suspend functions; [map] is inline so [getText] can be
 * called inside its lambda. [joinToString] is non-inline and cannot call suspend functions.
 */
@Resolver(objectValueFragment = "fragment _ on CheckedListPost { items { text } }")
class CheckedListPostReadTimeMinutesBatchResolver : CheckedListPostResolvers.ReadTimeMinutes() {

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Double>> =
        contexts.map { ctx ->
            val combinedText = ctx.getObjectValue().getItems().map { it.getText() }.joinToString(" ")
            FieldValue.ofValue(estimateReadTime(combinedText))
        }
}
