package org.tuchscherer.database.repositories

import org.tuchscherer.database.Like
import java.time.LocalDateTime
import java.util.*

/**
 * Repository interface for Like database operations.
 */
interface LikeRepository {
    /**
     * Find a like by its unique ID.
     */
    fun findById(id: UUID): Like?

    /**
     * Find multiple likes by IDs in a single query. Missing IDs are absent from the map.
     */
    fun findByIds(ids: List<UUID>): Map<UUID, Like>

    /**
     * Find all likes for a specific post.
     */
    fun findByPostId(postId: UUID): List<Like>

    /**
     * Find all likes by a specific user.
     */
    fun findByUserId(userId: UUID): List<Like>

    /**
     * Find a like for a specific post and user combination.
     */
    fun findByPostAndUser(postId: UUID, userId: UUID): Like?

    /**
     * Create a new like.
     */
    fun create(
        postId: UUID,
        userId: UUID,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Like

    /**
     * Delete a like by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Delete a like by post and user.
     */
    fun deleteByPostAndUser(postId: UUID, userId: UUID): Boolean

    /**
     * Check if a user has liked a specific post.
     */
    fun existsByPostAndUser(postId: UUID, userId: UUID): Boolean

    /**
     * Count likes for a post.
     */
    fun countByPostId(postId: UUID): Long

    /**
     * Get the user who created a like by like ID.
     * Returns null if like not found.
     */
    fun getUserForLike(likeId: UUID): org.tuchscherer.database.User?

    /**
     * Get the post for a like by like ID.
     * Returns null if like not found.
     */
    fun getPostForLike(likeId: UUID): org.tuchscherer.database.Post?

    /**
     * Count total likes.
     */
    fun count(): Long

    /**
     * Count likes by a specific user.
     */
    fun countByUserId(userId: UUID): Long

    /**
     * Delete all likes by a user. Returns count of deleted likes.
     */
    fun deleteByUserId(userId: UUID): Int
}
