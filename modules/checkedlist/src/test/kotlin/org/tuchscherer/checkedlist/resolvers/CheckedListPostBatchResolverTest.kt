package org.tuchscherer.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.ChecklistPostRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for [CheckedListPostBatchResolver].
 *
 * Success-path return value verification (building ViaductCheckedListPost requires a real
 * InternalContext) is covered end-to-end by query-tests.sh. Here we pin:
 *   - error FieldValue is returned for missing IDs
 *   - results list length matches batch input length
 */
class CheckedListPostBatchResolverTest {
    private val users = mockk<CheckedListCurrentUserProvider>()

    private lateinit var checklistPosts: ChecklistPostRepository

    @BeforeEach
    fun setup() {
        checklistPosts = mockk()

    }

    private fun mockContext(id: UUID): NodeResolvers.CheckedListPost.Context {
        val ctx = mockk<NodeResolvers.CheckedListPost.Context>(relaxed = true)
        every { ctx.id.internalID } returns id.toString()
        return ctx
    }

    @Test
    fun `returns error FieldValue when post not found`() = runBlocking {
        every { checklistPosts.getPostsData(any()) } returns emptyMap()

        val id = UUID.randomUUID()
        val results = CheckedListPostBatchResolver(checklistPosts, users).batchResolve(listOf(mockContext(id)))

        assertEquals(1, results.size)
        assertTrue(results.values.single().isError)
    }

    @Test
    fun `returns error for each missing ID in a batch`() = runBlocking {
        every { checklistPosts.getPostsData(any()) } returns emptyMap()

        val ids = List(3) { UUID.randomUUID() }
        val results = CheckedListPostBatchResolver(checklistPosts, users).batchResolve(ids.map(::mockContext))

        assertEquals(3, results.size)
        assertTrue(results.values.all { it.isError })
    }

    @Test
    fun `calls getPostsData once for the whole batch`() = runBlocking {
        val ids = List(3) { UUID.randomUUID() }
        every { checklistPosts.getPostsData(any()) } returns emptyMap()

        CheckedListPostBatchResolver(checklistPosts, users).batchResolve(ids.map(::mockContext))

        verify(exactly = 1) { checklistPosts.getPostsData(ids) }
    }
}
