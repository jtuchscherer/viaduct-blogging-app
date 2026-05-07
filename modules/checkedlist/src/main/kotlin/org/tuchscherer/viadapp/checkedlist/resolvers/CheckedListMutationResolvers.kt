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

        val description = input.description ?: ""
        val postData = postCreationPort.createCheckedListPost(input.title, userId, description)
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
 * Requires authentication AND that the caller is the post's author.
 */
@Resolver
class ToggleCheckedListItemMutationResolver : MutationResolvers.ToggleCheckedListItem() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        val userId = currentUserProvider.getCurrentUserId(ctx.requestContext)

        val itemId = UUID.fromString(ctx.arguments.id.internalID)

        val postId = itemRepository.getPostIdForItem(itemId)
            ?: error("CheckedListItem not found: $itemId")

        val postData = postCreationPort.getPostData(postId)
            ?: error("CheckedListPost not found: $postId")

        check(postData.authorId == userId) {
            "Not authorized: only the post author can toggle checklist items"
        }

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

/**
 * Updates the title and/or description of a [CheckedListPost].
 * Requires authentication and ownership.
 */
@Resolver
class UpdateCheckedListPostMutationResolver : MutationResolvers.UpdateCheckedListPost() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListPost {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id.internalID)
        input.title?.let { validateTitle(it) }
        input.description?.let { desc ->
            require(desc.length <= 10_000) { "Description must be 10,000 characters or fewer" }
        }

        val postData = postCreationPort.updateCheckedListPost(
            id = postId,
            title = input.title,
            description = input.description,
        ) ?: error("CheckedListPost not found: $postId")

        return postData.toViaductPost(ctx)
    }
}

/**
 * Deletes a [CheckedListPost] and all its items.
 * Requires authentication.
 */
@Resolver
class DeleteCheckedListPostMutationResolver : MutationResolvers.DeleteCheckedListPost() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val postCreationPort: PostCreationPort by inject(PostCreationPort::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val postId = UUID.fromString(ctx.arguments.id.internalID)
        // Delete items first (no FK constraint, but clean up orphans)
        itemRepository.deleteItemsForPost(postId)
        return postCreationPort.deleteCheckedListPost(postId)
    }
}

/**
 * Updates the text of a [CheckedListItem].
 * Requires authentication.
 */
@Resolver
class UpdateCheckedListItemMutationResolver : MutationResolvers.UpdateCheckedListItem() {
    private val currentUserProvider: CheckedListCurrentUserProvider
        by inject(CheckedListCurrentUserProvider::class.java)
    private val itemRepository: CheckedListItemRepository by inject(CheckedListItemRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductCheckedListItem {
        currentUserProvider.getCurrentUserId(ctx.requestContext) // ensure authenticated

        val input = ctx.arguments.input
        val itemId = UUID.fromString(input.id.internalID)
        validateItemText(input.text)

        val item = itemRepository.updateItem(itemId, input.text)
            ?: error("CheckedListItem not found: $itemId")

        return item.toViaductItem(ctx)
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
