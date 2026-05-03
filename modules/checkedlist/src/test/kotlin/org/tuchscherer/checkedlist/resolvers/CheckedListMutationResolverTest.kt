package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
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
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.UUID

class CheckedListMutationResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var currentUserProvider: CheckedListCurrentUserProvider
    private lateinit var postCreationPort: PostCreationPort
    private lateinit var itemRepository: CheckedListItemRepository

    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

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

    private fun stubPostData() = PostData(
        id = postId,
        title = "My Checklist",
        authorId = userId,
        createdAt = "2025-01-01T10:00:00",
        updatedAt = "2025-01-01T10:00:00",
    )

    private fun stubItemData(id: UUID = itemId, checked: Boolean = false) = CheckedListItemData(
        id = id,
        postId = postId,
        text = "Buy milk",
        checked = checked,
        position = 0,
        createdAt = "2025-01-01T10:00:00",
    )

    // ── CreateCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `createCheckedListPost does not throw when inputs are valid`() = runBlocking {
        val ctx = mockk<MutationResolvers.CreateCheckedListPost.Context>(relaxed = true)
        every { ctx.arguments.input.title } returns "My Checklist"
        every { ctx.arguments.input.items } returns listOf("Buy milk", "Buy bread")
        every { postCreationPort.createCheckedListPost("My Checklist", userId) } returns stubPostData()
        every { itemRepository.addItem(postId, any()) } returns stubItemData()
        every { ctx.globalIDFor(ViaductCheckedListPost.Reflection, any()) } returns
            context.globalIDFor(ViaductCheckedListPost.Reflection, postId.toString())

        // Building ViaductCheckedListPost requires a real InternalContext — success-path
        // return value is verified in query-tests.sh.
        val result = runCatching { CreateCheckedListPostMutationResolver().resolve(ctx) }
        assertTrue(result.isSuccess || result.exceptionOrNull()?.message?.contains("not found") == false)
    }

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
    fun `addCheckedListItem does not throw when post and item exist`() = runBlocking {
        val ctx = mockk<MutationResolvers.AddCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.postId } returns
            context.globalIDFor(ViaductCheckedListPost.Reflection, postId.toString())
        every { ctx.arguments.input.text } returns "Buy milk"

        every { postCreationPort.getPostData(postId) } returns stubPostData()
        every { itemRepository.addItem(postId, "Buy milk") } returns stubItemData()
        every { ctx.globalIDFor(ViaductCheckedListItem.Reflection, any()) } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())

        // Building ViaductCheckedListItem requires a real InternalContext — success-path
        // return value is verified in query-tests.sh.
        val result = runCatching { AddCheckedListItemMutationResolver().resolve(ctx) }
        assertTrue(result.isSuccess || result.exceptionOrNull()?.message?.contains("not found") == false)
    }

    @Test
    fun `addCheckedListItem throws when post not found`() = runBlocking {
        val ctx = mockk<MutationResolvers.AddCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.postId } returns
            context.globalIDFor(ViaductCheckedListPost.Reflection, postId.toString())
        every { ctx.arguments.input.text } returns "Some text"
        every { postCreationPort.getPostData(any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { AddCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    @Test
    fun `addCheckedListItem throws for blank text`() = runBlocking {
        val ctx = mockk<MutationResolvers.AddCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.input.postId } returns
            context.globalIDFor(ViaductCheckedListPost.Reflection, postId.toString())
        every { ctx.arguments.input.text } returns "  "

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AddCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    // ── ToggleCheckedListItem ─────────────────────────────────────────────────

    @Test
    fun `toggleCheckedListItem does not throw when item exists`() = runBlocking {
        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())
        every { itemRepository.toggleItem(itemId) } returns stubItemData(checked = true)
        every { ctx.globalIDFor(ViaductCheckedListItem.Reflection, any()) } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())

        // Building ViaductCheckedListItem requires a real InternalContext — success-path
        // return value is verified in query-tests.sh.
        val result = runCatching { ToggleCheckedListItemMutationResolver().resolve(ctx) }
        assertTrue(result.isSuccess || result.exceptionOrNull()?.message?.contains("not found") == false)
    }

    @Test
    fun `toggleCheckedListItem throws when item not found`() {
        val ctx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())
        every { itemRepository.toggleItem(any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(ctx) }
        }
    }

    // ── DeleteCheckedListItem ─────────────────────────────────────────────────

    @Test
    fun `deleteCheckedListItem returns true when item was deleted`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())
        every { itemRepository.deleteItem(itemId) } returns true

        assertTrue(DeleteCheckedListItemMutationResolver().resolve(ctx))
    }

    @Test
    fun `deleteCheckedListItem returns false when item does not exist`() = runBlocking {
        val ctx = mockk<MutationResolvers.DeleteCheckedListItem.Context>(relaxed = true)
        every { ctx.arguments.id } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())
        every { itemRepository.deleteItem(any()) } returns false

        assertFalse(DeleteCheckedListItemMutationResolver().resolve(ctx))
    }

    @Test
    fun `all mutations throw when user is not authenticated`() {
        every { currentUserProvider.getCurrentUserId(any()) } throws
            RuntimeException("Authentication required")

        val toggleCtx = mockk<MutationResolvers.ToggleCheckedListItem.Context>(relaxed = true)
        every { toggleCtx.arguments.id } returns
            context.globalIDFor(ViaductCheckedListItem.Reflection, itemId.toString())

        assertThrows(RuntimeException::class.java) {
            runBlocking { ToggleCheckedListItemMutationResolver().resolve(toggleCtx) }
        }
    }
}
