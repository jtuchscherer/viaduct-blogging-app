package org.tuchscherer.checkedlist

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tuchscherer.DirectTransactionRunner
import org.tuchscherer.checkedlist.port.ChecklistPostRepository
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import java.util.UUID

class ChecklistServiceTest {
    private val posts = mockk<ChecklistPostRepository>()
    private val items = mockk<CheckedListItemRepository>()
    private val service = ChecklistService(posts, items, DirectTransactionRunner)
    private val actor = UUID.randomUUID()

    @Test
    fun `create rejects an oversized description with a descriptive error`() {
        val error = assertThrows<IllegalArgumentException> {
            service.create(actor, "Title", "x".repeat(10_001), emptyList(), "DRAFT")
        }
        assertEquals("Description must be 10,000 characters or fewer", error.message)
    }

    @Test
    fun `create accepts a description at the maximum length`() {
        val description = "x".repeat(10_000)
        val saved = PostData(
            UUID.randomUUID(), "Title", description, actor, "DRAFT", null,
            "2026-10-21T12:00:00", "2026-10-21T12:00:00",
        )
        every { posts.createCheckedListPost("Title", actor, description, "DRAFT") } returns saved

        val result = service.create(actor, "Title", description, emptyList(), "DRAFT")

        assertEquals(description, result.description)
        assertEquals("DRAFT", result.status)
    }
}
