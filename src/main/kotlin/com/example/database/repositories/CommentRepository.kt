package com.example.database.repositories

import com.example.database.Comment
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDateTime
import java.util.*

/**
 * Repository interface for Comment database operations.
 */
interface CommentRepository {
    /**
     * Find a comment by its unique ID.
     */
    fun findById(id: UUID): Comment?

    /**
     * Find all comments for a specific post.
     */
    fun findByPostId(postId: EntityID<UUID>): List<Comment>

    /**
     * Find all comments by a specific author.
     */
    fun findByAuthorId(authorId: EntityID<UUID>): List<Comment>

    /**
     * Create a new comment.
     */
    fun create(
        content: String,
        postId: EntityID<UUID>,
        authorId: EntityID<UUID>,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Comment

    /**
     * Update an existing comment.
     */
    fun update(comment: Comment): Comment

    /**
     * Delete a comment by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Count comments for a post.
     */
    fun countByPostId(postId: EntityID<UUID>): Long
}
