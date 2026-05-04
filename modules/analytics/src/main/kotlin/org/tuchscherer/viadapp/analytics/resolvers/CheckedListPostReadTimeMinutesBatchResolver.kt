package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import viaduct.api.FieldValue
import viaduct.api.Resolver

/**
 * Batch resolver for CheckedListPost.readTimeMinutes.
 *
 * Estimates reading time from the concatenated text of all checklist items using the
 * standard 200 words-per-minute heuristic (shared with [BlogPostReadTimeMinutesBatchResolver]),
 * with a minimum of 0.5 minutes (30 seconds) for very short or empty checklists.
 *
 * Pure computation — no database access needed.
 */
@Resolver(objectValueFragment = "fragment _ on CheckedListPost { items { text } }")
class CheckedListPostReadTimeMinutesBatchResolver : CheckedListPostResolvers.ReadTimeMinutes() {

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Double>> {
        return contexts.map { ctx ->
            // getItems() and getText() are suspend; map is inline so getText() can be called
            // inside its lambda. joinToString is non-inline, so getText() cannot be used there.
            val items = ctx.objectValue.getItems()
            val combinedText = items.map { it.getText() }.joinToString(" ")
            FieldValue.ofValue(BlogPostReadTimeMinutesBatchResolver.estimateReadTime(combinedText))
        }
    }
}
