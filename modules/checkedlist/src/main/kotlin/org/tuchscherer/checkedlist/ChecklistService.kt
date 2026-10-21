package org.tuchscherer.checkedlist

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.checkedlist.port.ChecklistPostRepository
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.shared.TransactionRunner
import java.util.UUID

/** Owns checklist validation and write authorization independently of GraphQL. */
class ChecklistService(
    private val posts: ChecklistPostRepository,
    private val items: CheckedListItemRepository,
    private val transactions: TransactionRunner,
) {
    fun create(
        actorId: UUID,
        title: String,
        description: String,
        texts: List<String>,
        status: String,
    ): PostData = transactions.run {
        validateTitle(title)
        validateDescription(description)
        require(texts.size <= MAX_ITEMS) { "A checklist may have at most 100 items" }
        texts.forEach(::validateItemText)
        val post = posts.createCheckedListPost(title, actorId, description, status)
        texts.forEach { items.addItem(post.id, it) }
        post
    }

    fun update(actorId: UUID, postId: UUID, title: String?, description: String?): PostData = transactions.run {
        title?.let(::validateTitle)
        description?.let(::validateDescription)
        requireOwnedPost(actorId, postId)
        posts.updateCheckedListPost(postId, title, description)
            ?: throw NotFoundException("CheckedListPost not found: $postId")
    }

    fun delete(actorId: UUID, postId: UUID): Boolean = transactions.run {
        val post = posts.getPostData(postId) ?: return@run false
        requireOwner(actorId, post)
        items.deleteItemsForPost(postId)
        posts.deleteCheckedListPost(postId)
    }

    fun addItem(actorId: UUID, postId: UUID, text: String): CheckedListItemData = transactions.run {
        validateItemText(text)
        requireOwnedPost(actorId, postId)
        items.addItem(postId, text)
    }

    fun updateItem(actorId: UUID, itemId: UUID, text: String): CheckedListItemData = transactions.run {
        validateItemText(text)
        requireOwnedItem(actorId, itemId)
        items.updateItem(itemId, text) ?: throw NotFoundException("CheckedListItem not found: $itemId")
    }

    fun toggleItem(actorId: UUID, itemId: UUID): CheckedListItemData = transactions.run {
        requireOwnedItem(actorId, itemId)
        items.toggleItem(itemId) ?: throw NotFoundException("CheckedListItem not found: $itemId")
    }

    fun deleteItem(actorId: UUID, itemId: UUID): Boolean = transactions.run {
        val postId = items.getPostIdForItem(itemId) ?: return@run false
        requireOwnedPost(actorId, postId)
        items.deleteItem(itemId)
    }

    private fun requireOwnedItem(actorId: UUID, itemId: UUID) {
        val postId = items.getPostIdForItem(itemId) ?: throw NotFoundException("CheckedListItem not found: $itemId")
        requireOwnedPost(actorId, postId)
    }

    private fun requireOwnedPost(actorId: UUID, postId: UUID) {
        val post = posts.getPostData(postId) ?: throw NotFoundException("CheckedListPost not found: $postId")
        requireOwner(actorId, post)
    }

    private fun requireOwner(actorId: UUID, post: PostData) {
        if (post.authorId != actorId) throw AuthorizationException("Only the post author can modify a checklist")
    }
}

private fun validateTitle(title: String) {
    require(title.isNotBlank()) { "Title must not be blank" }
    require(title.length <= MAX_TITLE_LENGTH) { "Title must be 500 characters or fewer" }
}

private fun validateDescription(description: String) {
    require(description.length <= MAX_DESCRIPTION_LENGTH) { "Description must be 10,000 characters or fewer" }
}

private fun validateItemText(text: String) {
    require(text.isNotBlank()) { "Item text must not be blank" }
    require(text.length <= MAX_ITEM_TEXT_LENGTH) { "Item text must be 1000 characters or fewer" }
}

private const val MAX_ITEMS = 100
private const val MAX_DESCRIPTION_LENGTH = 10_000
private const val MAX_TITLE_LENGTH = 500
private const val MAX_ITEM_TEXT_LENGTH = 1000
