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
     * Find all comments for a specific post by EntityID.
     */
    fun findByPostId(postId: EntityID<UUID>): List<Comment>

    /**
     * Find all comments for a specific post by UUID.
     */
    fun findByPostId(postId: UUID): List<Comment>

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

    /**
     * Get the author (User) for a comment by comment ID.
     * Returns null if comment not found.
     */
    fun getAuthorForComment(commentId: UUID): com.example.database.User?

    /**
     * Get the post for a comment by comment ID.
     * Returns null if comment not found.
     */
    fun getPostForComment(commentId: UUID): com.example.database.Post?
}
