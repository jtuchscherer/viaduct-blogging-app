package com.example.database.repositories

import com.example.database.User
import com.example.database.Users
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of UserRepository.
 * All methods execute within a database transaction.
 */
class ExposedUserRepository : UserRepository {

    override fun findById(id: UUID): User? = transaction {
        User.findById(id)
    }

    override fun findByUsername(username: String): User? = transaction {
        User.find { Users.username eq username }.firstOrNull()
    }

    override fun findByEmail(email: String): User? = transaction {
        User.find { Users.email eq email }.firstOrNull()
    }

    override fun existsByUsername(username: String): Boolean = transaction {
        !User.find { Users.username eq username }.empty()
    }

    override fun create(
        username: String,
        email: String,
        name: String,
        passwordHash: String,
        salt: String,
        createdAt: LocalDateTime
    ): User = transaction {
        User.new {
            this.username = username
            this.email = email
            this.name = name
            this.passwordHash = passwordHash
            this.salt = salt
            this.createdAt = createdAt
        }
    }

    override fun update(user: User): User = transaction {
        user.also {
            // Exposed automatically tracks changes, just flush
            it.flush()
        }
    }

    override fun delete(id: UUID): Boolean = transaction {
        val user = User.findById(id)
        if (user != null) {
            user.delete()
            true
        } else {
            false
        }
    }

    override fun findAll(): List<User> = transaction {
        User.all().toList()
    }
}
