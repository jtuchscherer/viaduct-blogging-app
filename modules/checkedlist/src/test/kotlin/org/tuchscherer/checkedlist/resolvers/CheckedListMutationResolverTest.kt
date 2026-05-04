package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.AddCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CreateCheckedListPostMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.DeleteCheckedListItemMutationResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.ToggleCheckedListItemMutationResolver
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
    private val postId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

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
    fun `toggleCheckedListItem throws when item not found`() {
        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns itemGlobalId()
        every { itemRepository.toggleItem(any()) } returns null

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
}
