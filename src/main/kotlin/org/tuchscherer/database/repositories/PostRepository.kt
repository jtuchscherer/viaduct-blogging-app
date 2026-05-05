package org.tuchscherer.database.repositories

import org.tuchscherer.database.Post
import java.time.LocalDateTime
import java.util.*

/**
 * Repository interface for Post database operations.
 */
interface PostRepository {
    /**
     * Find a post by its unique ID.
     */
    fun findById(id: UUID): Post?

    /**
     * Find multiple posts by IDs in a single query. Missing IDs are absent from the map.
     */
    fun findByIds(ids: List<UUID>): Map<UUID, Post>

    /**
     * Find all posts by a specific author.
     */
    fun findByAuthorId(authorId: UUID): List<Post>

    /**
     * Get all posts.
     */
    fun findAll(): List<Post>

    /**
     * Create a new post.
     */
    fun create(
        title: String,
        content: String,
        authorId: UUID,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): Post

    /**
     * Update an existing post.
     */
    fun update(post: Post): Post

    /**
     * Update a post by ID with new values.
     * Returns the updated post or null if not found.
     */
    fun updateById(
        id: UUID,
        title: String? = null,
        content: String? = null
    ): Post?

    /**
     * Delete a post by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Find a page of posts ordered by createdAt descending.
     */
    fun findPage(limit: Int, offset: Int): List<Post>

    /**
     * Count total posts.
     */
    fun count(): Long

    /**
     * Get author IDs for multiple posts in a single query.
     * Returns a map from post ID to author ID; missing entries mean the post was not found.
     */
    fun getAuthorIdsByPostIds(postIds: List<UUID>): Map<UUID, UUID>

    /**
     * Count posts by author using UUID.
     */
    fun countByAuthorId(authorId: UUID): Long

    /**
     * Delete all posts by an author. Returns count of deleted posts.
     */
    fun deleteByAuthorId(authorId: UUID): Int
}
