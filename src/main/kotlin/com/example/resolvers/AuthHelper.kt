package com.example.viadapp.resolvers

import com.example.database.User
import com.example.database.Users
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.internal.InternalContext
import java.time.LocalDateTime

/**
 * Temporary helper functions that return a test user for development.
 * Authentication will be properly implemented after upgrading to Viaduct 0.5.0
 */

private val testUser: User by lazy {
    transaction {
        // Find or create a test user
        User.find { Users.username eq "testuser" }.firstOrNull()
            ?: User.new {
                username = "testuser"
                email = "test@example.com"
                name = "Test User"
                passwordHash = "dummy"
                salt = "dummy"
                createdAt = LocalDateTime.now()
            }
    }
}

fun InternalContext.getAuthenticatedUser(): User? {
    return testUser
}

fun InternalContext.requireAuthenticatedUser(): User {
    return testUser
}