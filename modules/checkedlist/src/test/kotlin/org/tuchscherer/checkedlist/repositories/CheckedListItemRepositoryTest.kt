package org.tuchscherer.checkedlist.repositories

import org.tuchscherer.checkedlist.CheckedListDatabaseTestHelper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckedListItemRepositoryTest {

    private lateinit var repository: ExposedCheckedListItemRepository
    private val postId = UUID.randomUUID()

    @BeforeAll
    fun setupDatabase() {
        CheckedListDatabaseTestHelper.setupDatabase()
        repository = ExposedCheckedListItemRepository()
    }

    @AfterEach
    fun cleanDatabase() {
        CheckedListDatabaseTestHelper.cleanDatabase()
    }

    @AfterAll
    fun tearDownDatabase() {
        CheckedListDatabaseTestHelper.tearDownDatabase()
    }

    // ── addItem ────────────────────────────────────────────────────────────────

    @Test
    fun `addItem stores the item and assigns position 0 for first item`() {
        val item = repository.addItem(postId, "Buy milk")

        assertEquals("Buy milk", item.text)
        assertFalse(item.checked)
        assertEquals(0, item.position)
        assertEquals(postId, item.postId)
        assertNotNull(item.id)
    }

    @Test
    fun `addItem assigns sequential positions for subsequent items`() {
        val item1 = repository.addItem(postId, "First")
        val item2 = repository.addItem(postId, "Second")
        val item3 = repository.addItem(postId, "Third")

        assertEquals(0, item1.position)
        assertEquals(1, item2.position)
        assertEquals(2, item3.position)
    }

    @Test
    fun `addItem positions are independent per post`() {
        val otherPost = UUID.randomUUID()
        val item1InPost1 = repository.addItem(postId, "P1 Item 1")
        val item1InPost2 = repository.addItem(otherPost, "P2 Item 1")
        val item2InPost1 = repository.addItem(postId, "P1 Item 2")

        assertEquals(0, item1InPost1.position)
        assertEquals(0, item1InPost2.position)
        assertEquals(1, item2InPost1.position)
    }

    // ── getItem ────────────────────────────────────────────────────────────────

    @Test
    fun `getItem returns stored item`() {
        val added = repository.addItem(postId, "Task A")

        val fetched = repository.getItem(added.id)

        assertNotNull(fetched)
        assertEquals("Task A", fetched!!.text)
        assertFalse(fetched.checked)
    }

    @Test
    fun `getItem returns null for non-existent item`() {
        assertNull(repository.getItem(UUID.randomUUID()))
    }

    // ── getItemsForPost ────────────────────────────────────────────────────────

    @Test
    fun `getItemsForPost returns items ordered by position`() {
        repository.addItem(postId, "C")
        repository.addItem(postId, "A")
        repository.addItem(postId, "B")

        val items = repository.getItemsForPost(postId)

        assertEquals(3, items.size)
        assertEquals(listOf(0, 1, 2), items.map { it.position })
    }

    @Test
    fun `getItemsForPost returns empty list when post has no items`() {
        assertTrue(repository.getItemsForPost(UUID.randomUUID()).isEmpty())
    }

    // ── getItemsForPosts ───────────────────────────────────────────────────────

    @Test
    fun `getItemsForPosts batch-fetches items keyed by postId`() {
        val post2 = UUID.randomUUID()
        repository.addItem(postId, "P1-A")
        repository.addItem(postId, "P1-B")
        repository.addItem(post2, "P2-A")

        val result = repository.getItemsForPosts(listOf(postId, post2))

        assertEquals(2, result[postId]?.size)
        assertEquals(1, result[post2]?.size)
    }

    @Test
    fun `getItemsForPosts returns empty map for empty input`() {
        assertTrue(repository.getItemsForPosts(emptyList()).isEmpty())
    }

    // ── toggleItem ────────────────────────────────────────────────────────────

    @Test
    fun `toggleItem flips checked from false to true`() {
        val item = repository.addItem(postId, "Do something")

        val toggled = repository.toggleItem(item.id)

        assertNotNull(toggled)
        assertTrue(toggled!!.checked)
        assertEquals("Do something", toggled.text)
    }

    @Test
    fun `toggleItem flips checked back to false on second call`() {
        val item = repository.addItem(postId, "Toggle me")
        repository.toggleItem(item.id)   // now true
        val result = repository.toggleItem(item.id)  // back to false

        assertNotNull(result)
        assertFalse(result!!.checked)
    }

    @Test
    fun `toggleItem returns null for non-existent item`() {
        assertNull(repository.toggleItem(UUID.randomUUID()))
    }

    // ── deleteItem ────────────────────────────────────────────────────────────

    @Test
    fun `deleteItem removes an existing item and returns true`() {
        val item = repository.addItem(postId, "Delete me")

        assertTrue(repository.deleteItem(item.id))
        assertNull(repository.getItem(item.id))
    }

    @Test
    fun `deleteItem returns false for non-existent item`() {
        assertFalse(repository.deleteItem(UUID.randomUUID()))
    }

    // ── deleteItemsForPost ────────────────────────────────────────────────────

    @Test
    fun `deleteItemsForPost removes all items for the post`() {
        repository.addItem(postId, "Item 1")
        repository.addItem(postId, "Item 2")
        repository.addItem(postId, "Item 3")

        val deleted = repository.deleteItemsForPost(postId)

        assertEquals(3, deleted)
        assertTrue(repository.getItemsForPost(postId).isEmpty())
    }

    @Test
    fun `deleteItemsForPost does not affect items for other posts`() {
        val otherPost = UUID.randomUUID()
        repository.addItem(postId, "Mine")
        repository.addItem(otherPost, "Not mine")

        repository.deleteItemsForPost(postId)

        assertEquals(1, repository.getItemsForPost(otherPost).size)
    }

    // ── getPostIdForItem ──────────────────────────────────────────────────────

    @Test
    fun `getPostIdForItem returns the correct post ID`() {
        val item = repository.addItem(postId, "My item")

        assertEquals(postId, repository.getPostIdForItem(item.id))
    }

    @Test
    fun `getPostIdForItem returns null for non-existent item`() {
        assertNull(repository.getPostIdForItem(UUID.randomUUID()))
    }
}
