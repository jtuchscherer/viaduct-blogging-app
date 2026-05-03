package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import java.util.UUID

/**
 * Creates a new [CheckedListPost] with the supplied initial items.
 * Requires the caller to be authenticated.
 *
 * Validation:
 * - title: non-blank, max 500 chars
 * - items: up to 100 items, each text non-blank, max 1000 chars
 */
@Resolver
class CreateCheckedListPostMutationResolver : MutationResolvers.CreateCheckedListPost() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        val userId = currentUserProvider.getCurrentUserId(ctx.requestContext)
        val input = ctx.arguments.input

        validateTitle(input.title)
        val itemTexts = input.items
        require(itemTexts.size <= 100) { "A checklist may have at most 100 items" }
        itemTexts.forEach { validateItemText(it) }

        val postData = postCreationPort.createCheckedListPost(input.title, userId)
        itemTexts.forEach { text -> itemRepository.addItem(postData.id, text) }

        return postData.toViaductPost(ctx)
    }
}

/**
 * Adds a single item to an existing [CheckedListPost].
 * Requires authentication.
 */
@Resolver
class AddCheckedListItemMutationResolver : MutationResolvers.AddCheckedListItem() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val input = ctx.arguments.input
        val postId = UUID.fromString(input.postId.internalID)
        validateItemText(input.text)

        postCreationPort.getPostData(postId)
            ?: error("CheckedListPost not found: $postId")

        return itemRepository.addItem(postId, input.text).toViaductItem(ctx)
    }
}

/**
 * Toggles the checked state of a [CheckedListItem].
 * Requires authentication.
 */
@Resolver
class ToggleCheckedListItemMutationResolver : MutationResolvers.ToggleCheckedListItem() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val itemId = UUID.fromString(ctx.arguments.id.internalID)
        val item = itemRepository.toggleItem(itemId)
            ?: error("CheckedListItem not found: $itemId")

        return item.toViaductItem(ctx)
    }
}

/**
 * Deletes a [CheckedListItem].
 * Requires authentication.
 */
@Resolver
class DeleteCheckedListItemMutationResolver : MutationResolvers.DeleteCheckedListItem() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val itemId = UUID.fromString(ctx.arguments.id.internalID)
        return itemRepository.deleteItem(itemId)
    }
}

// ── Validation helpers ────────────────────────────────────────────────────────

internal fun validateTitle(title: String) {
    require(title.isNotBlank()) { "Title must not be blank" }
    require(title.length <= 500) { "Title must be 500 characters or fewer" }
}

internal fun validateItemText(text: String) {
    require(text.isNotBlank()) { "Item text must not be blank" }
    require(text.length <= 1000) { "Item text must be 1000 characters or fewer" }
}
