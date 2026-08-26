package org.tuchscherer.analytics

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [estimateReadTime].
 *
 * The heuristic is shared across all analytics resolvers, so a change to it would affect every
 * readTime field in the API. Tests are pure — no DB or Koin.
 *
 * Global-ID decoding used to be tested here too; it now lives in :modules:resolverkit, which is
 * where the function moved when its duplicate in the root project was removed.
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
}
