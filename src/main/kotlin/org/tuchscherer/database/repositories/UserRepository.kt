package org.tuchscherer.database.repositories

import org.tuchscherer.database.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime
import java.util.UUID

/**
 * Repository interface for User database operations.
 * Abstracts database access to enable testing with mocks.
 */
interface UserRepository {
    /**
     * Find a user by their unique ID.
     */
    fun findById(id: UUID): User?

    /**
     * Find multiple users by their IDs. Returns a map of ID to User for efficient batch lookups.
     */
    fun findByIds(ids: List<UUID>): Map<UUID, User>

    /**
     * Find a user by their unique username.
     */
    fun findByUsername(username: String): User?

    /**
     * Find a user by their email address.
     */
    fun findByEmail(email: String): User?

    /**
     * Check if a user exists with the given username.
     */
    fun existsByUsername(username: String): Boolean

    /**
     * Create a new user.
     */
    fun create(
        username: String,
        email: String,
        name: String,
        passwordHash: String,
        salt: String,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): User

    /**
     * Update an existing user.
     */
    fun update(user: User): User

    /**
     * Partially update mutable fields on an existing user.
     * Each nullable argument is applied only when non-null.
     * Returns the updated user, or null if no user with [id] exists.
     */
    fun updateFields(id: UUID, name: String? = null, email: String? = null, isAdmin: Boolean? = null): User?

    /**
     * Delete a user by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Get all users (for admin purposes).
     */
    fun findAll(): List<User>

    /**
     * Get a page of users ordered by createdAt descending.
     */
    fun findPage(limit: Int, offset: Int): List<User>

    /**
     * Count total users.
     */
    fun count(): Long
}
