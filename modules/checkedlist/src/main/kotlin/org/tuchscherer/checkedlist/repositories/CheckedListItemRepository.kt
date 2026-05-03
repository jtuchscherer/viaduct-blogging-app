package org.tuchscherer.checkedlist.repositories

import java.util.UUID

/**
 * Data view returned by [CheckedListItemRepository]. Uses plain types only — no
 * Exposed DAO entities — so the interface stays portable across modules.
 */
data class CheckedListItemData(
    val id: UUID,
    val postId: UUID,
    val text: String,
    val checked: Boolean,
    val position: Int,
    val createdAt: String,
)

/**
 * Repository for [org.tuchscherer.checkedlist.database.CheckedListItems] table operations.
 * All implementations must run inside Exposed transactions.
 */
interface CheckedListItemRepository {
    /**
     * Adds a new item to the given post. The item is appended at the end
     * (position = current max + 1).
     */
    fun addItem(postId: UUID, text: String): CheckedListItemData

    /**
     * Fetches a single item by ID, or null if not found.
     */
    fun getItem(id: UUID): CheckedListItemData?

    /**
     * Fetches all items for a post ordered by position ascending.
     */
    fun getItemsForPost(postId: UUID): List<CheckedListItemData>

    /**
     * Batch-fetches items for multiple posts. Returns a map from postId to item list.
     */
    fun getItemsForPosts(postIds: List<UUID>): Map<UUID, List<CheckedListItemData>>

    /**
     * Flips the checked state of the item. Returns the updated item, or null if not found.
     */
    fun toggleItem(id: UUID): CheckedListItemData?

    /**
     * Deletes an item. Returns true if it existed and was removed.
     */
    fun deleteItem(id: UUID): Boolean

    /**
     * Deletes all items for a post (cascade on post deletion). Returns count deleted.
     */
    fun deleteItemsForPost(postId: UUID): Int

    /**
     * Returns the postId for a given item ID, or null if the item does not exist.
     */
    fun getPostIdForItem(id: UUID): UUID?
}
