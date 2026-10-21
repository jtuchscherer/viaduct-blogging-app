package org.tuchscherer.database.repositories

import org.tuchscherer.database.Post
import org.tuchscherer.database.PostStatus
import java.time.LocalDateTime
import java.util.UUID

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
     * Find blog posts by a specific author, including drafts.
     */
    fun findByAuthorId(authorId: UUID): List<Post>

    /**
     * Get published blog posts.
     */
    fun findAll(): List<Post>

    /** Published blog feed, loading only requested scalar columns in stable publication order. */
    fun findPublishedProjection(fields: Set<PostField>, limit: Int? = null, offset: Int = 0): List<PostProjection>

    /** Batched scalar projection with author/status metadata for node visibility checks. */
    fun findProjectedByIds(ids: List<UUID>, fields: Set<PostField>): Map<UUID, PostProjection>

    /** Published IDs across every post type. */
    fun publishedIds(ids: List<UUID>): Set<UUID>

    /** Sets publication state and timestamp; returns null if the post is missing. */
    fun updateStatus(id: UUID, status: String, publishedAt: LocalDateTime?): Post?

    /** Creates a blog post and returns a materialized snapshot. */
    fun create(
        title: String,
        content: String,
        authorId: UUID,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now(),
        status: String = PostStatus.PUBLISHED
    ): Post

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
     * Find published blog posts ordered by publication time descending.
     */
    fun findPage(limit: Int, offset: Int): List<Post>

    /**
     * Count published blog posts.
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
