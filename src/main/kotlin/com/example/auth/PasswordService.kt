package com.example.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Service for password hashing and verification.
 * Uses SHA-256 for hashing with random salts.
 */
class PasswordService {
    private val random = SecureRandom()

    /**
     * Generate a random salt for password hashing.
     * Returns a 32-character hex string (16 bytes).
     */
    fun generateSalt(): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hash a password with a salt using SHA-256.
     * Returns a 64-character hex string (32 bytes).
     */
    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + salt
        val hashedBytes = digest.digest(saltedPassword.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a password against a stored hash and salt.
     */
    fun verifyPassword(password: String, salt: String, hashedPassword: String): Boolean {
        return hashPassword(password, salt) == hashedPassword
    }
}