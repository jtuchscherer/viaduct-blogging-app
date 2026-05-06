package org.tuchscherer.analytics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [estimateReadTime] and [decodeGlobalId] utility functions.
 *
 * These helpers are shared across all analytics resolvers, so a change to
 * the read-time heuristic or the global-ID decoding logic would affect every
 * viewCount / readTime field in the API.  Tests are pure — no DB or Koin.
 */
class AnalyticsUtilsTest {

    // ── estimateReadTime ──────────────────────────────────────────────────────

    @Test
    fun `estimateReadTime returns the minimum 0_5 for an empty string`() {
        assertEquals(0.5, estimateReadTime(""), 0.001)
    }

    @Test
    fun `estimateReadTime returns the minimum 0_5 for whitespace-only content`() {
        assertEquals(0.5, estimateReadTime("   \n  \t  "), 0.001)
    }

    @Test
    fun `estimateReadTime returns the minimum 0_5 for a single word`() {
        // 1 word / 200 wpm = 0.005 → clamped to 0.5
        assertEquals(0.5, estimateReadTime("hello"), 0.001)
    }

    @Test
    fun `estimateReadTime returns 1_0 minute for exactly 200 words`() {
        val twoHundredWords = List(200) { "word" }.joinToString(" ")
        assertEquals(1.0, estimateReadTime(twoHundredWords), 0.01)
    }

    @Test
    fun `estimateReadTime returns 2_0 minutes for 400 words`() {
        val fourHundredWords = List(400) { "word" }.joinToString(" ")
        assertEquals(2.0, estimateReadTime(fourHundredWords), 0.01)
    }

    @Test
    fun `estimateReadTime respects newlines and tabs as word separators`() {
        // 4 tokens separated by mixed whitespace
        val content = "word1\nword2\tword3  word4"
        val result = estimateReadTime(content)
        // 4 words / 200 wpm = 0.02 → clamped to 0.5
        assertEquals(0.5, result, 0.001)
    }

    @Test
    fun `estimateReadTime returns strictly greater than 0_5 for more than 100 words`() {
        val manyWords = List(300) { "word" }.joinToString(" ")
        val result = estimateReadTime(manyWords)
        assertTrue(result > 0.5)
    }

    // ── decodeGlobalId ────────────────────────────────────────────────────────

    @Test
    fun `decodeGlobalId extracts the UUID from a valid BlogPost global ID`() {
        val uuid = UUID.randomUUID()
        val encoded = Base64.getEncoder().encodeToString("BlogPost:$uuid".toByteArray())

        assertEquals(uuid, decodeGlobalId(encoded))
    }

    @Test
    fun `decodeGlobalId works for CheckedListPost type prefix`() {
        val uuid = UUID.randomUUID()
        val encoded = Base64.getEncoder().encodeToString("CheckedListPost:$uuid".toByteArray())

        assertEquals(uuid, decodeGlobalId(encoded))
    }

    @Test
    fun `decodeGlobalId works for arbitrary type prefixes`() {
        val uuid = UUID.randomUUID()
        val encoded = Base64.getEncoder().encodeToString("User:$uuid".toByteArray())

        assertEquals(uuid, decodeGlobalId(encoded))
    }

    @Test
    fun `decodeGlobalId throws IllegalArgumentException for non-base64 input`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId("not-valid-base64!!!") }
    }

    @Test
    fun `decodeGlobalId throws IllegalArgumentException when there is no colon separator`() {
        // Valid base64 but no ':' in the decoded string
        val encoded = Base64.getEncoder().encodeToString("nocolonhere".toByteArray())
        assertThrows<IllegalArgumentException> { decodeGlobalId(encoded) }
    }

    @Test
    fun `decodeGlobalId throws IllegalArgumentException when UUID part is not a valid UUID`() {
        val encoded = Base64.getEncoder().encodeToString("BlogPost:not-a-real-uuid".toByteArray())
        assertThrows<IllegalArgumentException> { decodeGlobalId(encoded) }
    }

    @Test
    fun `decodeGlobalId throws IllegalArgumentException for an empty string`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId("") }
    }
}
