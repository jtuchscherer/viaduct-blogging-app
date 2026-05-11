package org.tuchscherer.checkedlist.resolvers

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostSocialPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostCommentCountResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostIsLikedByMeResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostItemsResolver
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostLikeCountResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.CheckedListPostResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.util.UUID
import viaduct.api.context.FieldExecutionContext
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.Query as ViaductQuery
import viaduct.api.internal.InternalContext
import viaduct.api.types.Arguments.NoArguments
import viaduct.api.types.CompositeOutput

class CheckedListPostFieldResolversTest {

    private lateinit var socialPort: PostSocialPort
    private lateinit var currentUserProvider: CheckedListCurrentUserProvider
    private lateinit var itemRepository: CheckedListItemRepository

    private val postId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        socialPort = mockk()
        currentUserProvider = mockk()
        itemRepository = mockk()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostSocialPort> { socialPort }
                single<CheckedListCurrentUserProvider> { currentUserProvider }
                single<CheckedListItemRepository> { itemRepository }
            })
        }
    }

    private fun mockCommentCountContext(): CheckedListPostResolvers.CommentCount.Context {
        val post = mockk<ViaductCheckedListPost>()
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns postId.toString()
        every { post.getId() } returns globalId

        val inner = mockk<FieldExecutionContext<ViaductCheckedListPost, ViaductQuery, NoArguments, CompositeOutput.NotComposite>>(
            relaxed = true,
            moreInterfaces = arrayOf(InternalContext::class),
        )
        coEvery { inner.getObjectValue() } returns post
        return CheckedListPostResolvers.CommentCount.Context(inner)
    }

    private fun mockLikeCountContext(): CheckedListPostResolvers.LikeCount.Context {
        val post = mockk<ViaductCheckedListPost>()
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns postId.toString()
        every { post.getId() } returns globalId

        val inner = mockk<FieldExecutionContext<ViaductCheckedListPost, ViaductQuery, NoArguments, CompositeOutput.NotComposite>>(
            relaxed = true,
            moreInterfaces = arrayOf(InternalContext::class),
        )
        coEvery { inner.getObjectValue() } returns post
        return CheckedListPostResolvers.LikeCount.Context(inner)
    }

    private fun mockIsLikedByMeContext(): CheckedListPostResolvers.IsLikedByMe.Context {
        val post = mockk<ViaductCheckedListPost>()
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns postId.toString()
        every { post.getId() } returns globalId

        val inner = mockk<FieldExecutionContext<ViaductCheckedListPost, ViaductQuery, NoArguments, CompositeOutput.NotComposite>>(
            relaxed = true,
            moreInterfaces = arrayOf(InternalContext::class),
        )
        coEvery { inner.getObjectValue() } returns post
        return CheckedListPostResolvers.IsLikedByMe.Context(inner)
    }

    private fun mockItemsContext(): CheckedListPostResolvers.Items.Context {
        val post = mockk<ViaductCheckedListPost>()
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns postId.toString()
        every { post.getId() } returns globalId

        val inner = mockk<FieldExecutionContext<ViaductCheckedListPost, ViaductQuery, NoArguments, viaduct.api.grts.CheckedListItem>>(
            relaxed = true,
            moreInterfaces = arrayOf(InternalContext::class),
        )
        coEvery { inner.getObjectValue() } returns post
        return CheckedListPostResolvers.Items.Context(inner)
    }

    // ── items ─────────────────────────────────────────────────────────────────

    @Test
    fun `items returns empty list when post has no items`() = runBlocking {
        every { itemRepository.getItemsForPost(postId) } returns emptyList()
        assertTrue(CheckedListPostItemsResolver().resolve(mockItemsContext()).isEmpty())
    }

    // ── commentCount ──────────────────────────────────────────────────────────

    @Test
    fun `commentCount returns correct count`() = runBlocking {
        every { socialPort.getCommentCountForPost(postId) } returns 5L
        assertEquals(5, CheckedListPostCommentCountResolver().resolve(mockCommentCountContext()))
    }

    @Test
    fun `commentCount returns 0 when there are no comments`() = runBlocking {
        every { socialPort.getCommentCountForPost(postId) } returns 0L
        assertEquals(0, CheckedListPostCommentCountResolver().resolve(mockCommentCountContext()))
    }

    // ── likeCount ─────────────────────────────────────────────────────────────

    @Test
    fun `likeCount returns correct count`() = runBlocking {
        every { socialPort.getLikeCountForPost(postId) } returns 3L
        assertEquals(3, CheckedListPostLikeCountResolver().resolve(mockLikeCountContext()))
    }

    // ── isLikedByMe ───────────────────────────────────────────────────────────

    @Test
    fun `isLikedByMe returns true when the user has liked the post`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } returns userId
        every { socialPort.isLikedByUser(postId, userId) } returns true
        assertTrue(CheckedListPostIsLikedByMeResolver().resolve(mockIsLikedByMeContext()))
    }

    @Test
    fun `isLikedByMe returns false for unauthenticated user`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } throws RuntimeException("Not authed")
        assertFalse(CheckedListPostIsLikedByMeResolver().resolve(mockIsLikedByMeContext()))
    }

    @Test
    fun `isLikedByMe returns false when user has not liked the post`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } returns userId
        every { socialPort.isLikedByUser(postId, userId) } returns false
        assertFalse(CheckedListPostIsLikedByMeResolver().resolve(mockIsLikedByMeContext()))
    }
}
