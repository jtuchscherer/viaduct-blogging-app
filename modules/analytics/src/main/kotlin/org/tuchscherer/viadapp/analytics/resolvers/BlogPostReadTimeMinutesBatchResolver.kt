package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers
import viaduct.api.FieldValue
import viaduct.api.Resolver

/**
 * Batch resolver for BlogPost.readTimeMinutes.
 *
 * Estimates reading time from the post's raw content using the standard 200 words-per-minute
 * heuristic, with a minimum of 0.5 minutes (30 seconds) for very short posts.
 *
 * Pure computation — no database access needed.
 */
@Resolver(objectValueFragment = "fragment _ on BlogPost { content }")
class BlogPostReadTimeMinutesBatchResolver : BlogPostResolvers.ReadTimeMinutes() {

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<Double>> {
        return contexts.map { ctx ->
            val content = ctx.objectValue.getContent()
            FieldValue.ofValue(estimateReadTime(content))
        }
    }

    companion object {
        private const val WORDS_PER_MINUTE = 200.0
        private const val MINIMUM_READ_TIME = 0.5

        fun estimateReadTime(content: String): Double {
            val wordCount = content.trim()
                .split("\\s+".toRegex())
                .count { it.isNotEmpty() }
            return maxOf(MINIMUM_READ_TIME, wordCount / WORDS_PER_MINUTE)
        }
    }
}
