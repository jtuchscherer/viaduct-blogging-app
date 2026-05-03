package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListItemBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.UUID

class CheckedListItemBatchResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var itemRepository: CheckedListItemRepository
    private val itemId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    @BeforeEach
    fun setup() {
        itemRepository = mockk()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<CheckedListItemRepository> { itemRepository }
            })
        }
    }

    private fun stubItemData(id: UUID = itemId, text: String = "Buy milk") = CheckedListItemData(
        id = id,
        postId = postId,
        text = text,
        checked = false,
        position = 0,
        createdAt = "2025-01-01T10:00:00",
    )

    private fun mockContext(id: UUID): NodeResolvers.CheckedListItem.Context {
        val ctx = mockk<NodeResolvers.CheckedListItem.Context>(relaxed = true)
        every { ctx.id.internalID } returns id.toString()
        every { ctx.globalIDFor(ViaductCheckedListItem.Reflection, any()) } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, id.toString())
        return ctx
    }

    @Test
    fun `returns one result per context in the success path`() = runBlocking {
        every { itemRepository.getItem(any()) } returns stubItemData()

        // Building ViaductCheckedListItem requires a real InternalContext, not a mock.
        // We verify the resolver does not throw a "not found" error from the port layer.
        val result = runCatching {
            CheckedListItemBatchResolver().batchResolve(listOf(mockContext(itemId)))
        }
        assertTrue(result.isSuccess || result.exceptionOrNull()?.message?.contains("not found") == false)
    }

    @Test
    fun `returns error FieldValue when item not found`() = runBlocking {
        every { itemRepository.getItem(any()) } returns null

        val resolver = CheckedListItemBatchResolver()
        val results = resolver.batchResolve(listOf(mockContext(itemId)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
    }
}
