package org.tuchscherer.auth

import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.UserRepository

/**
 * Service for user authentication operations.
 * Uses dependency injection for PasswordService and UserRepository.
 */
class AuthenticationService(
    private val passwordService: PasswordService,
    private val userRepository: UserRepository
) {
    /**
     * Create a new user with hashed password.
     * Throws exception if username already exists.
     */
    fun createUser(username: String, email: String, name: String, password: String): User {
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(username.length <= 100) { "Username cannot exceed 100 characters" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(email.length <= 255) { "Email cannot exceed 255 characters" }
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(name.length <= 255) { "Name cannot exceed 255 characters" }

        if (userRepository.existsByUsername(username)) {
            throw UserAlreadyExistsException("Username '$username' already exists")
        }

        val salt = passwordService.generateSalt()
        val passwordHash = passwordService.hashPassword(password, salt)

        return userRepository.create(
            username = username,
            email = email,
            name = name,
            passwordHash = passwordHash,
            salt = salt
        )
    }

    /**
     * Authenticate a user with username and password.
     * Returns the user if authentication succeeds, null otherwise.
     */
    fun authenticateUser(username: String, password: String): User? {
        val user = userRepository.findByUsername(username) ?: return null

        return if (passwordService.verifyPassword(password, user.salt, user.passwordHash)) {
            user
        } else {
            null
        }
    }
}

/**
 * Exception thrown when attempting to create a user with an existing username.
 */
class UserAlreadyExistsException(message: String) : RuntimeException(message)
