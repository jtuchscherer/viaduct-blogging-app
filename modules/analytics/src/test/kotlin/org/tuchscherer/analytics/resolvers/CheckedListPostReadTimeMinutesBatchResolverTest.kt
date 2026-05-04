package org.tuchscherer.analytics.resolvers

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.estimateReadTime
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostReadTimeMinutesBatchResolver
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase

class CheckedListPostReadTimeMinutesBatchResolverTest : DefaultAbstractResolverTestBase() {

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun makeItem(text: String): ViaductCheckedListItem {
        val item = mockk<ViaductCheckedListItem>(relaxed = true)
        coEvery { item.getText() } returns text
        return item
    }

    private fun mockContext(itemTexts: List<String>): CheckedListPostResolvers.ReadTimeMinutes.Context {
        val ctx = mockk<CheckedListPostResolvers.ReadTimeMinutes.Context>(relaxed = true)
        val items = itemTexts.map { makeItem(it) }
        coEvery { ctx.objectValue.getItems() } returns items
        return ctx
    }

    @Test
    fun `empty checklist returns minimum read time 0_5`() = runBlocking {
        val resolver = CheckedListPostReadTimeMinutesBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext(emptyList())))

        assertFalse(results[0].isError)
        assertEquals(0.5, results[0].get())
    }

    @Test
    fun `single short item returns minimum read time 0_5`() = runBlocking {
        val resolver = CheckedListPostReadTimeMinutesBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext(listOf("Milk"))))

        assertFalse(results[0].isError)
        assertEquals(0.5, results[0].get())
    }

    @Test
    fun `200-word items combine to 1_0 minute`() = runBlocking {
        val resolver = CheckedListPostReadTimeMinutesBatchResolver()
        // 10 items × 20 words each = 200 words → 1.0 minute at 200 WPM
        val items = List(10) { List(20) { "word" }.joinToString(" ") }
        val results = resolver.batchResolve(listOf(mockContext(items)))

        assertFalse(results[0].isError)
        assertEquals(1.0, results[0].get())
    }

    @Test
    fun `batch resolves multiple checklists independently`() = runBlocking {
        val resolver = CheckedListPostReadTimeMinutesBatchResolver()
        // 100 single-word items = 100 words total = 0.5 min (below 200 WPM minimum)
        val shortCtx = mockContext(List(100) { "word" })
        // 200 single-word items = 200 words total → 200/200 WPM = 1.0 min
        val longCtx = mockContext(List(200) { "word" })

        val results = resolver.batchResolve(listOf(shortCtx, longCtx))

        assertEquals(2, results.size)
        assertEquals(0.5, results[0].get())
        assertEquals(1.0, results[1].get())
    }

    @Test
    fun `read time is computed from combined item text using shared estimator`() = runBlocking {
        val resolver = CheckedListPostReadTimeMinutesBatchResolver()
        val fourHundredWordItems = List(20) { List(20) { "word" }.joinToString(" ") }
        val results = resolver.batchResolve(listOf(mockContext(fourHundredWordItems)))

        // 400 words → same result regardless of post type
        assertEquals(estimateReadTime("word ".repeat(400).trim()), results[0].get())
    }
}
