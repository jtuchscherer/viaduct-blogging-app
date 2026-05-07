package org.tuchscherer.checkedlist

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Like
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * Unit tests for [ViaductPostSocialAccess].
 *
 * This bridge adapts the root-project [CommentRepository] and [LikeRepository]
 * to the checkedlist module's [PostSocialPort].  Each method must map DAO entities
 * to the correct port view type without losing any fields.
 */
class ViaductPostSocialAccessTest {

    private val commentRepo = mockk<CommentRepository>()
    private val likeRepo = mockk<LikeRepository>()
    private val social = ViaductPostSocialAccess(commentRepo, likeRepo)

    // ── getCommentsForPost ────────────────────────────────────────────────────

    @Test
    fun `getCommentsForPost maps Comment entities to CommentView objects`() {
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val createdAt = LocalDateTime.of(2024, 1, 15, 10, 30)

        val comment = mockk<Comment>(relaxed = true)
        every { comment.id.value } returns commentId
        every { comment.content } returns "Nice post!"
        every { comment.createdAt } returns createdAt

        every { commentRepo.findByPostId(postId) } returns listOf(comment)

        val result = social.getCommentsForPost(postId)

        assertEquals(1, result.size)
        assertEquals(commentId, result[0].id)
        assertEquals("Nice post!", result[0].content)
        assertEquals(createdAt.toString(), result[0].createdAt)
    }

    @Test
    fun `getCommentsForPost returns empty list when post has no comments`() {
        val postId = UUID.randomUUID()
        every { commentRepo.findByPostId(postId) } returns emptyList()

        val result = social.getCommentsForPost(postId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCommentsForPost passes the correct post ID to the repository`() {
        val postId = UUID.randomUUID()
        every { commentRepo.findByPostId(postId) } returns emptyList()

        social.getCommentsForPost(postId)

        verify(exactly = 1) { commentRepo.findByPostId(postId) }
    }

    // ── getCommentCountForPost ────────────────────────────────────────────────

    @Test
    fun `getCommentCountForPost delegates to the repository`() {
        val postId = UUID.randomUUID()
        every { commentRepo.countByPostId(postId) } returns 7L

        assertEquals(7L, social.getCommentCountForPost(postId))
    }

    @Test
    fun `getCommentCountForPost returns 0 for a post with no comments`() {
        val postId = UUID.randomUUID()
        every { commentRepo.countByPostId(postId) } returns 0L

        assertEquals(0L, social.getCommentCountForPost(postId))
    }

    // ── getLikesForPost ───────────────────────────────────────────────────────

    @Test
    fun `getLikesForPost maps Like entities to LikeView objects`() {
        val postId = UUID.randomUUID()
        val likeId = UUID.randomUUID()
        val createdAt = LocalDateTime.of(2024, 2, 1, 8, 0)

        val like = mockk<Like>(relaxed = true)
        every { like.id.value } returns likeId
        every { like.createdAt } returns createdAt

        every { likeRepo.findByPostId(postId) } returns listOf(like)

        val result = social.getLikesForPost(postId)

        assertEquals(1, result.size)
        assertEquals(likeId, result[0].id)
        assertEquals(createdAt.toString(), result[0].createdAt)
    }

    @Test
    fun `getLikesForPost returns empty list when post has no likes`() {
        val postId = UUID.randomUUID()
        every { likeRepo.findByPostId(postId) } returns emptyList()

        assertTrue(social.getLikesForPost(postId).isEmpty())
    }

    // ── getLikeCountForPost ───────────────────────────────────────────────────

    @Test
    fun `getLikeCountForPost delegates to the repository`() {
        val postId = UUID.randomUUID()
        every { likeRepo.countByPostId(postId) } returns 3L

        assertEquals(3L, social.getLikeCountForPost(postId))
    }

    // ── isLikedByUser ─────────────────────────────────────────────────────────

    @Test
    fun `isLikedByUser returns true when the user has liked the post`() {
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { likeRepo.existsByPostAndUser(postId, userId) } returns true

        assertTrue(social.isLikedByUser(postId, userId))
    }

    @Test
    fun `isLikedByUser returns false when the user has not liked the post`() {
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { likeRepo.existsByPostAndUser(postId, userId) } returns false

        assertFalse(social.isLikedByUser(postId, userId))
    }

    @Test
    fun `isLikedByUser passes the correct IDs to the repository`() {
        val postId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { likeRepo.existsByPostAndUser(postId, userId) } returns false

        social.isLikedByUser(postId, userId)

        verify(exactly = 1) { likeRepo.existsByPostAndUser(postId, userId) }
    }
}
