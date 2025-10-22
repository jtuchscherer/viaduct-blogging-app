package com.example.database.repositories

import com.example.database.Post
import org.jetbrains.exposed.dao.id.EntityID
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
     * Find all posts by a specific author.
     */
    fun findByAuthorId(authorId: EntityID<UUID>): List<Post>

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
        authorId: EntityID<UUID>,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): Post

    /**
     * Update an existing post.
     */
    fun update(post: Post): Post

    /**
     * Delete a post by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Count total posts.
     */
    fun count(): Long

    /**
     * Count posts by author.
     */
    fun countByAuthor(authorId: EntityID<UUID>): Long
}
