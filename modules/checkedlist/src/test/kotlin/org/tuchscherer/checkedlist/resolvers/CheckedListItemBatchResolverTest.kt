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
import java.util.UUID

class CheckedListItemBatchResolverTest {

    private lateinit var itemRepository: CheckedListItemRepository
    private val itemId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

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

    private fun stubItemData(id: UUID = itemId) = CheckedListItemData(
        id = id,
        postId = postId,
        text = "Buy milk",
        checked = false,
        position = 0,
        createdAt = "2025-01-01T10:00:00",
    )

    private fun mockContext(id: UUID): NodeResolvers.CheckedListItem.Context {
        val ctx = mockk<NodeResolvers.CheckedListItem.Context>(relaxed = true)
        every { ctx.id.internalID } returns id.toString()
        return ctx
    }

    @Test
    fun `returns error FieldValue when item not found`() = runBlocking {
        every { itemRepository.getItem(any()) } returns null

        val results = CheckedListItemBatchResolver().batchResolve(listOf(mockContext(itemId)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
    }

    @Test
    fun `returns error for each missing ID in a batch`() = runBlocking {
        every { itemRepository.getItem(any()) } returns null

        val ids = List(3) { UUID.randomUUID() }
        val results = CheckedListItemBatchResolver().batchResolve(ids.map(::mockContext))

        assertEquals(3, results.size)
        assertTrue(results.all { it.isError })
    }
}
