package com.example.auth

import java.security.MessageDigest
import java.security.SecureRandom

class PasswordService {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + salt
        val hashedBytes = digest.digest(saltedPassword.toByteArray())
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String, hashedPassword: String): Boolean {
        return hashPassword(password, salt) == hashedPassword
    }
}

class AuthenticationService {
    private val passwordService = PasswordService()

    fun createUser(username: String, email: String, name: String, password: String): com.example.database.User {
        val salt = passwordService.generateSalt()
        val passwordHash = passwordService.hashPassword(password, salt)

        return com.example.database.User.new {
            this.username = username
            this.email = email
            this.name = name
            this.passwordHash = passwordHash
            this.salt = salt
            this.createdAt = java.time.LocalDateTime.now()
        }
    }

    fun authenticateUser(username: String, password: String): com.example.database.User? {
        val user = com.example.database.User.find {
            com.example.database.Users.username eq username
        }.firstOrNull()

        return if (user != null && passwordService.verifyPassword(password, user.salt, user.passwordHash)) {
            user
        } else {
            null
        }
    }
}