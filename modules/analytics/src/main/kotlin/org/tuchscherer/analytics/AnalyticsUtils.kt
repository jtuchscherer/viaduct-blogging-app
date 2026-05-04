package org.tuchscherer.analytics

import org.tuchscherer.analytics.repositories.PostViewRepository
import viaduct.api.FieldValue
import java.util.Base64
import java.util.UUID

// ── Read time estimation ──────────────────────────────────────────────────────

private const val WORDS_PER_MINUTE = 200.0
private const val MINIMUM_READ_TIME = 0.5

/**
 * Estimates reading time for a body of text using the standard 200 WPM heuristic.
 * Returns at least 0.5 minutes (30 seconds) for very short or empty content.
 *
 * Shared by [org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver]
 * and [org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostReadTimeMinutesBatchResolver].
 */
fun estimateReadTime(content: String): Double {
    val wordCount = content.trim()
        .split("\\s+".toRegex())
        .count { it.isNotEmpty() }
    return maxOf(MINIMUM_READ_TIME, wordCount / WORDS_PER_MINUTE)
}

// ── View count resolution ─────────────────────────────────────────────────────

/**
 * Fetches view counts for a batch of post IDs in one repository round-trip and returns
 * one [FieldValue<Int>] per ID. Posts that have never been viewed return 0.
 *
 * Shared by [org.tuchscherer.viadapp.analytics.resolvers.BlogPostViewCountBatchResolver]
 * and [org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostViewCountBatchResolver].
 */
fun resolveViewCounts(postIds: List<UUID>, repository: PostViewRepository): List<FieldValue<Int>> {
    val viewCounts = repository.bulkGetViewCounts(postIds)
    return postIds.map { postId -> FieldValue.ofValue((viewCounts[postId] ?: 0L).toInt()) }
}

// ── Global ID decoding ────────────────────────────────────────────────────────

/**
 * Decodes a Viaduct global ID string (base64 `TypeName:uuid`) into its UUID component.
 * The type name prefix is discarded — the caller is responsible for knowing the type.
 *
 * Throws [IllegalArgumentException] if [encodedId] is not a valid Viaduct global ID.
 */
fun decodeGlobalId(encodedId: String): UUID {
    val decoded = runCatching { String(Base64.getDecoder().decode(encodedId)) }
        .getOrElse { throw IllegalArgumentException("Invalid post ID: $encodedId") }
    val colonIdx = decoded.indexOf(':')
    require(colonIdx > 0) { "Invalid post ID format: $encodedId" }
    val internalId = decoded.substring(colonIdx + 1)
    return runCatching { UUID.fromString(internalId) }
        .getOrElse { throw IllegalArgumentException("Invalid UUID in post ID: $internalId") }
}
