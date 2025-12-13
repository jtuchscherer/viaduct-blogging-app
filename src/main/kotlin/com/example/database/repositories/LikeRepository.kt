package com.example.database.repositories

import com.example.database.Like
import org.jetbrains.exposed.dao.id.EntityID
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
     * Find all likes for a specific post by EntityID.
     */
    fun findByPostId(postId: EntityID<UUID>): List<Like>

    /**
     * Find all likes for a specific post by UUID.
     */
    fun findByPostId(postId: UUID): List<Like>

    /**
     * Find all likes by a specific user.
     */
    fun findByUserId(userId: EntityID<UUID>): List<Like>

    /**
     * Find a like for a specific post and user combination.
     */
    fun findByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Like?

    /**
     * Check if a user has liked a specific post.
     */
    fun existsByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Boolean

    /**
     * Create a new like.
     */
    fun create(
        postId: EntityID<UUID>,
        userId: EntityID<UUID>,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Like

    /**
     * Delete a like by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Delete a like by post and user.
     */
    fun deleteByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Boolean

    /**
     * Count likes for a post by EntityID.
     */
    fun countByPostId(postId: EntityID<UUID>): Long

    /**
     * Count likes for a post by UUID.
     */
    fun countByPostId(postId: UUID): Long

    /**
     * Check if a user has liked a specific post by UUIDs.
     */
    fun existsByPostAndUser(postId: UUID, userId: UUID): Boolean

    /**
     * Get the user who created a like by like ID.
     * Returns null if like not found.
     */
    fun getUserForLike(likeId: UUID): com.example.database.User?

    /**
     * Get the post for a like by like ID.
     * Returns null if like not found.
     */
    fun getPostForLike(likeId: UUID): com.example.database.Post?
}
