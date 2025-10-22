package com.example.auth

import com.example.database.User
import com.example.database.repositories.UserRepository

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
