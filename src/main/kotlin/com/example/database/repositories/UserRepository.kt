package com.example.database.repositories

import com.example.database.User
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDateTime
import java.util.*

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
     * Delete a user by ID.
     */
    fun delete(id: UUID): Boolean

    /**
     * Get all users (for admin purposes).
     */
    fun findAll(): List<User>
}
