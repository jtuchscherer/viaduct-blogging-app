package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.ChecklistPostRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.MyCheckedListPostsQueryResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.QueryResolvers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class MyCheckedListPostsQueryResolverTest {

    private lateinit var currentUserProvider: CheckedListCurrentUserProvider
    private lateinit var checklistPosts: ChecklistPostRepository

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        currentUserProvider = mockk()
        checklistPosts = mockk()

        every { currentUserProvider.getCurrentUserId(any()) } returns userId

    }

    @Test
    fun `returns empty list when user has no checklist posts`() = runBlocking {
        val ctx = mockk<QueryResolvers.MyCheckedListPosts.Context>(relaxed = true)
        every { ctx.requestContext } returns mockk()
        every { checklistPosts.getCheckedListPostsByAuthorId(userId) } returns emptyList()

        val result = MyCheckedListPostsQueryResolver(currentUserProvider, checklistPosts).resolve(ctx)

        assertEquals(0, result.size)
    }

    @Test
    fun `throws when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws RuntimeException("Not authenticated")

        val ctx = mockk<QueryResolvers.MyCheckedListPosts.Context>(relaxed = true)
        every { ctx.requestContext } returns null

        assertThrows<RuntimeException> {
            runBlocking { MyCheckedListPostsQueryResolver(currentUserProvider, checklistPosts).resolve(ctx) }
        }
    }
}
