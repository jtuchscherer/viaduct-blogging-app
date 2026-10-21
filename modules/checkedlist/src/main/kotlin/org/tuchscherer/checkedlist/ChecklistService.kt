package org.tuchscherer.checkedlist

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import java.util.UUID

/** Owns checklist validation and write authorization independently of GraphQL. */
class ChecklistService(
    private val posts: PostCreationPort,
    private val items: CheckedListItemRepository,
) {
    fun create(actorId: UUID, title: String, description: String, texts: List<String>, status: String): PostData {
        validateTitle(title)
        validateDescription(description)
        require(texts.size <= MAX_ITEMS) { "A checklist may have at most 100 items" }
        texts.forEach(::validateItemText)
        val post = posts.createCheckedListPost(title, actorId, description, status)
        texts.forEach { items.addItem(post.id, it) }
        return post
    }

    fun update(actorId: UUID, postId: UUID, title: String?, description: String?): PostData {
        title?.let(::validateTitle)
        description?.let(::validateDescription)
        requireOwnedPost(actorId, postId)
        return posts.updateCheckedListPost(postId, title, description)
            ?: throw NotFoundException("CheckedListPost not found: $postId")
    }

    fun delete(actorId: UUID, postId: UUID): Boolean {
        if (posts.getPostData(postId) == null) return false
        requireOwnedPost(actorId, postId)
        items.deleteItemsForPost(postId)
        return posts.deleteCheckedListPost(postId)
    }

    fun addItem(actorId: UUID, postId: UUID, text: String): CheckedListItemData {
        validateItemText(text)
        requireOwnedPost(actorId, postId)
        return items.addItem(postId, text)
    }

    fun updateItem(actorId: UUID, itemId: UUID, text: String): CheckedListItemData {
        validateItemText(text)
        requireOwnedItem(actorId, itemId)
        return items.updateItem(itemId, text) ?: throw NotFoundException("CheckedListItem not found: $itemId")
    }

    fun toggleItem(actorId: UUID, itemId: UUID): CheckedListItemData {
        requireOwnedItem(actorId, itemId)
        return items.toggleItem(itemId) ?: throw NotFoundException("CheckedListItem not found: $itemId")
    }

    fun deleteItem(actorId: UUID, itemId: UUID): Boolean {
        val postId = items.getPostIdForItem(itemId) ?: return false
        requireOwnedPost(actorId, postId)
        return items.deleteItem(itemId)
    }

    private fun requireOwnedItem(actorId: UUID, itemId: UUID) {
        val postId = items.getPostIdForItem(itemId) ?: throw NotFoundException("CheckedListItem not found: $itemId")
        requireOwnedPost(actorId, postId)
    }

    private fun requireOwnedPost(actorId: UUID, postId: UUID) {
        val post = posts.getPostData(postId) ?: throw NotFoundException("CheckedListPost not found: $postId")
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
