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
import java.util.UUID

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
    }

    private fun mockCommentCountContext(): CheckedListPostResolvers.CommentCount.Context {
        val ctx = mockk<CheckedListPostResolvers.CommentCount.Context>(relaxed = true)
        coEvery { ctx.getObjectValue().getIdOrThrow().internalID } returns postId.toString()
        return ctx
    }

    private fun mockLikeCountContext(): CheckedListPostResolvers.LikeCount.Context {
        val ctx = mockk<CheckedListPostResolvers.LikeCount.Context>(relaxed = true)
        coEvery { ctx.getObjectValue().getIdOrThrow().internalID } returns postId.toString()
        return ctx
    }

    private fun mockIsLikedByMeContext(): CheckedListPostResolvers.IsLikedByMe.Context {
        val ctx = mockk<CheckedListPostResolvers.IsLikedByMe.Context>(relaxed = true)
        coEvery { ctx.getObjectValue().getIdOrThrow().internalID } returns postId.toString()
        return ctx
    }

    private fun mockItemsContext(): CheckedListPostResolvers.Items.Context {
        val ctx = mockk<CheckedListPostResolvers.Items.Context>(relaxed = true)
        coEvery { ctx.getObjectValue().getIdOrThrow().internalID } returns postId.toString()
        return ctx
    }

    // ── items ─────────────────────────────────────────────────────────────────

    @Test
    fun `items returns empty list when post has no items`() = runBlocking {
        every { itemRepository.getItemsForPost(postId) } returns emptyList()
        assertTrue(CheckedListPostItemsResolver(itemRepository).resolve(mockItemsContext()).isEmpty())
    }

    // ── commentCount ──────────────────────────────────────────────────────────

    @Test
    fun `commentCount returns correct count`() = runBlocking {
        every { socialPort.getCommentCountForPost(postId) } returns 5L
        assertEquals(5, CheckedListPostCommentCountResolver(socialPort).resolve(mockCommentCountContext()))
    }

    @Test
    fun `commentCount returns 0 when there are no comments`() = runBlocking {
        every { socialPort.getCommentCountForPost(postId) } returns 0L
        assertEquals(0, CheckedListPostCommentCountResolver(socialPort).resolve(mockCommentCountContext()))
    }

    // ── likeCount ─────────────────────────────────────────────────────────────

    @Test
    fun `likeCount returns correct count`() = runBlocking {
        every { socialPort.getLikeCountForPost(postId) } returns 3L
        assertEquals(3, CheckedListPostLikeCountResolver(socialPort).resolve(mockLikeCountContext()))
    }

    // ── isLikedByMe ───────────────────────────────────────────────────────────

    @Test
    fun `isLikedByMe returns true when the user has liked the post`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } returns userId
        every { socialPort.isLikedByUser(postId, userId) } returns true
        assertTrue(CheckedListPostIsLikedByMeResolver(socialPort, currentUserProvider).resolve(mockIsLikedByMeContext()))
    }

    @Test
    fun `isLikedByMe returns false for unauthenticated user`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } throws RuntimeException("Not authed")
        assertFalse(CheckedListPostIsLikedByMeResolver(socialPort, currentUserProvider).resolve(mockIsLikedByMeContext()))
    }

    @Test
    fun `isLikedByMe returns false when user has not liked the post`() = runBlocking {
        every { currentUserProvider.getCurrentUserId(any()) } returns userId
        every { socialPort.isLikedByUser(postId, userId) } returns false
        assertFalse(CheckedListPostIsLikedByMeResolver(socialPort, currentUserProvider).resolve(mockIsLikedByMeContext()))
    }
}
