package org.tuchscherer.database.repositories

import org.tuchscherer.database.Comment
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
     * Find multiple comments by IDs in a single query. Missing IDs are absent from the map.
     */
    fun findByIds(ids: List<UUID>): Map<UUID, Comment>

    /**
     * Find all comments for a specific post.
     */
    fun findByPostId(postId: UUID): List<Comment>

    /**
     * Find all comments by a specific author.
     */
    fun findByAuthorId(authorId: UUID): List<Comment>

    /**
     * Create a new comment.
     */
    fun create(
        content: String,
        postId: UUID,
        authorId: UUID,
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
    fun countByPostId(postId: UUID): Long

    /**
     * Get the author (User) for a comment by comment ID.
     * Returns null if comment not found.
     */
    fun getAuthorForComment(commentId: UUID): org.tuchscherer.database.User?

    /**
     * Get the post for a comment by comment ID.
     * Returns null if comment not found.
     */
    fun getPostForComment(commentId: UUID): org.tuchscherer.database.Post?

    /**
     * Get all comments.
     */
    fun findAll(): List<Comment>

    /**
     * Get a page of comments ordered by createdAt descending.
     */
    fun findPage(limit: Int, offset: Int): List<Comment>

    /**
     * Count total comments.
     */
    fun count(): Long

    /**
     * Count comments by a specific user.
     */
    fun countByUserId(userId: UUID): Long

    /**
     * Delete all comments by a user. Returns count of deleted comments.
     */
    fun deleteByUserId(userId: UUID): Int
}
