package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
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

    private fun mockContext(id: UUID): NodeResolvers.CheckedListItem.Context {
        val ctx = mockk<NodeResolvers.CheckedListItem.Context>(relaxed = true)
        every { ctx.id.internalID } returns id.toString()
        return ctx
    }

    @Test
    fun `returns error FieldValue when item not found`() = runBlocking {
        every { itemRepository.findByIds(any()) } returns emptyMap()

        val results = CheckedListItemBatchResolver().batchResolve(listOf(mockContext(itemId)))

        assertEquals(1, results.size)
        assertTrue(results.values.single().isError)
    }

    @Test
    fun `returns error for each missing ID in a batch`() = runBlocking {
        every { itemRepository.findByIds(any()) } returns emptyMap()

        val ids = List(3) { UUID.randomUUID() }
        val results = CheckedListItemBatchResolver().batchResolve(ids.map(::mockContext))

        assertEquals(3, results.size)
        assertTrue(results.values.all { it.isError })
    }

    @Test
    fun `calls findByIds once for the whole batch`() = runBlocking {
        val ids = List(3) { UUID.randomUUID() }
        every { itemRepository.findByIds(any()) } returns emptyMap()

        CheckedListItemBatchResolver().batchResolve(ids.map(::mockContext))

        verify(exactly = 1) { itemRepository.findByIds(ids) }
    }
}
