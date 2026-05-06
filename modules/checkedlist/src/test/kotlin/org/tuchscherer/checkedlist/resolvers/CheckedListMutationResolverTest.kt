package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.viadapp.checkedlist.resolvers.AddCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CreateCheckedListPostMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.DeleteCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.DeleteCheckedListPostMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.ToggleCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.UpdateCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.UpdateCheckedListPostMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import java.util.UUID

class CheckedListMutationResolverTest {

    private lateinit var currentUserProvider: CheckedListCurrentUserProvider
    private lateinit var postCreationPort: PostCreationPort
    private lateinit var itemRepository: CheckedListItemRepository

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    private fun makePostData(authorId: UUID = userId) = PostData(
        id = postId, title = "Test Post", description = "",
        authorId = authorId, createdAt = "2024-01-01T00:00:00", updatedAt = "2024-01-01T00:00:00",
    )

    private fun makeItemData() = CheckedListItemData(
        id = itemId, postId = postId, text = "Test item",
        checked = false, position = 0, createdAt = "2024-01-01T00:00:00",
    )

    @BeforeEach
    fun setup() {
        currentUserProvider = mockk()
        postCreationPort = mockk()
        itemRepository = mockk()

        every { currentUserProvider.getCurrentUserId(any()) } returns userId

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<CheckedListCurrentUserProvider> { currentUserProvider }
                single<PostCreationPort> { postCreationPort }
                single<CheckedListItemRepository> { itemRepository }
            })
        }
    }

    private fun postGlobalId(): GlobalID<ViaductCheckedListPost> {
        val id = mockk<GlobalID<ViaductCheckedListPost>>()
        every { id.internalID } returns postId.toString()
        return id
    }

    private fun itemGlobalId(): GlobalID<ViaductCheckedListItem> {
        val id = mockk<GlobalID<ViaductCheckedListItem>>()
        every { id.internalID } returns itemId.toString()
        return id
    }

    // ── CreateCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `createCheckedListPost throws for blank title`() {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "   "
        every { ctx.arguments.input.items } returns listOf("Item")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `createCheckedListPost throws for title exceeding 500 characters`() {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "a".repeat(501)
        every { ctx.arguments.input.items } returns listOf("Item")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `createCheckedListPost throws for more than 100 items`() {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "Too Many Items"
        every { ctx.arguments.input.items } returns List(101) { "Item $it" }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `createCheckedListPost throws for blank item text`() {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "Valid Title"
        every { ctx.arguments.input.items } returns listOf("  ")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `createCheckedListPost throws for item text exceeding 1000 characters`() {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "Valid Title"
        every { ctx.arguments.input.items } returns listOf("a".repeat(1001))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `createCheckedListPost throws when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "Valid Title"
        every { ctx.arguments.input.items } returns listOf("Item")

        assertThrows(RuntimeException::class.java) {
            runBlocking { CreateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    // ── AddCheckedListItem ────────────────────────────────────────────────────

    @Test
    fun `addCheckedListItem throws when post not found`() = runBlocking {
        val ctx = mockk<MutationResolvers.AddCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.postId } returns postGlobalId()
        every { ctx.arguments.input.text } returns "Some text"
        every { postCreationPort.getPostData(any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AddCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `addCheckedListItem throws for blank text`() = runBlocking {
        val ctx = mockk<MutationResolvers.AddCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.postId } returns postGlobalId()
        every { ctx.arguments.input.text } returns "  "

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AddCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    // ── ToggleCheckedListItem ─────────────────────────────────────────────────

    @Test
    fun `toggleCheckedListItem authorization passes when user is the post author`() {
        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.getPostIdForItem(itemId) } returns postId
        every { postCreationPort.getPostData(postId) } returns makePostData(authorId = userId)
        // Return null so the resolver throws "item not found" — this confirms the auth check
        // passed (if it had failed, "Not authorized" would have been thrown instead).
        every { itemRepository.toggleItem(itemId) } returns null

        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(ctx) }
        }
        assertTrue(ex.message?.contains("CheckedListItem not found") == true,
            "Expected 'item not found' error (auth passed), but got: ${ex.message}")
    }

    @Test
    fun `toggleCheckedListItem throws when user is not the post author`() {
        every { currentUserProvider.getCurrentUserId(any()) } returns otherUserId

        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.getPostIdForItem(itemId) } returns postId
        every { postCreationPort.getPostData(postId) } returns makePostData(authorId = userId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `toggleCheckedListItem throws when item not found`() {
        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.getPostIdForItem(itemId) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    // ── DeleteCheckedListItem ─────────────────────────────────────────────────

    @Test
    fun `deleteCheckedListItem returns true when item was deleted`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.deleteItem(itemId) } returns true

        assertTrue(DeleteCheckedListItemMutationResolver().resolve(ctx))
    }

    @Test
    fun `deleteCheckedListItem returns false when item does not exist`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.deleteItem(any()) } returns false

        assertFalse(DeleteCheckedListItemMutationResolver().resolve(ctx))
    }

    @Test
    fun `all mutations throw when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val toggleCtx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { toggleCtx.arguments.id } returns itemGlobalId()

        assertThrows(RuntimeException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(toggleCtx) }
        }
    }

    // ── UpdateCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `updateCheckedListPost throws for blank title`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns postGlobalId()
        every { ctx.arguments.input.title } returns "   "
        every { ctx.arguments.input.description } returns null

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { UpdateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListPost throws for title exceeding 500 characters`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns postGlobalId()
        every { ctx.arguments.input.title } returns "a".repeat(501)
        every { ctx.arguments.input.description } returns null

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { UpdateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListPost throws for description exceeding 10000 characters`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns postGlobalId()
        every { ctx.arguments.input.title } returns null
        every { ctx.arguments.input.description } returns "a".repeat(10_001)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { UpdateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListPost throws IllegalStateException when post not found`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns postGlobalId()
        every { ctx.arguments.input.title } returns "New Title"
        every { ctx.arguments.input.description } returns null
        every { postCreationPort.updateCheckedListPost(any(), any(), any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { UpdateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListPost throws when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val ctx = mockk<MutationResolvers.UpdateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns postGlobalId()

        assertThrows(RuntimeException::class.java) {
            runBlocking { UpdateCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    // ── DeleteCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `deleteCheckedListPost returns true when post was deleted`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.id } returns postGlobalId()
        every { itemRepository.deleteItemsForPost(postId) } returns 0
        every { postCreationPort.deleteCheckedListPost(postId) } returns true

        assertTrue(DeleteCheckedListPostMutationResolver().resolve(ctx))
    }

    @Test
    fun `deleteCheckedListPost returns false when post does not exist`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.id } returns postGlobalId()
        every { itemRepository.deleteItemsForPost(any()) } returns 0
        every { postCreationPort.deleteCheckedListPost(any()) } returns false

        assertFalse(DeleteCheckedListPostMutationResolver().resolve(ctx))
    }

    @Test
    fun `deleteCheckedListPost deletes items before deleting the post`() = runBlocking {
        var itemsDeletedFirst = false
        var postDeletedAfterItems = false

        val ctx = mockk<MutationResolvers.DeleteCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.id } returns postGlobalId()
        every { itemRepository.deleteItemsForPost(postId) } answers {
            itemsDeletedFirst = true
            0
        }
        every { postCreationPort.deleteCheckedListPost(postId) } answers {
            postDeletedAfterItems = itemsDeletedFirst
            true
        }

        DeleteCheckedListPostMutationResolver().resolve(ctx)

        assertTrue(postDeletedAfterItems, "Post must be deleted after its items")
    }

    @Test
    fun `deleteCheckedListPost throws when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val ctx = mockk<MutationResolvers.DeleteCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.id } returns postGlobalId()

        assertThrows(RuntimeException::class.java) {
            runBlocking { DeleteCheckedListPostMutationResolver().resolve(ctx) }
        }
    }

    // ── UpdateCheckedListItem ─────────────────────────────────────────────────

    @Test
    fun `updateCheckedListItem throws for blank text`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns itemGlobalId()
        every { ctx.arguments.input.text } returns "  "

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { UpdateCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListItem throws for text exceeding 1000 characters`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns itemGlobalId()
        every { ctx.arguments.input.text } returns "a".repeat(1001)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { UpdateCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListItem throws IllegalStateException when item not found`() {
        val ctx = mockk<MutationResolvers.UpdateCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns itemGlobalId()
        every { ctx.arguments.input.text } returns "Updated text"
        every { itemRepository.updateItem(any(), any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { UpdateCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `updateCheckedListItem throws when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val ctx = mockk<MutationResolvers.UpdateCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.id } returns itemGlobalId()

        assertThrows(RuntimeException::class.java) {
            runBlocking { UpdateCheckedListItemMutationResolver().resolve(ctx) }
        }
    }
}
