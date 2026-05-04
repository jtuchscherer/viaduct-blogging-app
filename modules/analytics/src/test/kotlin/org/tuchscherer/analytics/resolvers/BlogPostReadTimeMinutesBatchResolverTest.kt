package org.tuchscherer.analytics.resolvers

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.estimateReadTime
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase

class BlogPostReadTimeMinutesBatchResolverTest : DefaultAbstractResolverTestBase() {

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun mockContext(content: String): BlogPostResolvers.ReadTimeMinutes.Context {
        val ctx = mockk<BlogPostResolvers.ReadTimeMinutes.Context>(relaxed = true)
        coEvery { ctx.objectValue.getContent() } returns content
        return ctx
    }

    @Test
    fun `empty content returns minimum read time 0_5`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext("")))
        assertFalse(results[0].isError)
        assertEquals(0.5, results[0].get())
    }

    @Test
    fun `blank content returns minimum read time 0_5`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext("   ")))
        assertFalse(results[0].isError)
        assertEquals(0.5, results[0].get())
    }

    @Test
    fun `very short content returns minimum read time 0_5`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext("one two three"))) // 3 words = 0.015 min
        assertFalse(results[0].isError)
        assertEquals(0.5, results[0].get())
    }

    @Test
    fun `400-word content returns 2_0 minutes`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val content = List(400) { "word" }.joinToString(" ")
        val results = resolver.batchResolve(listOf(mockContext(content)))
        assertFalse(results[0].isError)
        assertEquals(2.0, results[0].get())
    }

    @Test
    fun `exact 200-word content returns 1_0 minute`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val content = List(200) { "word" }.joinToString(" ")
        val results = resolver.batchResolve(listOf(mockContext(content)))
        assertFalse(results[0].isError)
        assertEquals(1.0, results[0].get())
    }

    @Test
    fun `batch resolves multiple posts independently`() = runBlocking {
        val resolver = BlogPostReadTimeMinutesBatchResolver()
        val shortContent = "hello" // below minimum
        val longContent = List(200) { "word" }.joinToString(" ")

        val results = resolver.batchResolve(
            listOf(mockContext(shortContent), mockContext(longContent))
        )

        assertEquals(2, results.size)
        assertEquals(0.5, results[0].get())
        assertEquals(1.0, results[1].get())
    }

    // ── Unit tests for the shared estimateReadTime helper ────────────────────

    @Test
    fun `estimateReadTime returns minimum for empty string`() {
        assertEquals(0.5, estimateReadTime(""))
    }

    @Test
    fun `estimateReadTime computes correctly for 400 words`() {
        val content = List(400) { "word" }.joinToString(" ")
        assertEquals(2.0, estimateReadTime(content))
    }
}
