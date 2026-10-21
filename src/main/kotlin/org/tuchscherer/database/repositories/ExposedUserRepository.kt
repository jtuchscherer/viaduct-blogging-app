package org.tuchscherer.database.repositories

import org.tuchscherer.database.User
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/**
 * Exposed ORM implementation of UserRepository.
 * All methods execute within a database transaction.
 */
class ExposedUserRepository : UserRepository {

    override fun findById(id: UUID): User? = transaction {
        UserEntity.findById(id)?.toRecord()
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, User> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        UserEntity.find { Users.id inList ids.map { EntityID(it, Users) } }
            .map { it.toRecord() }.associateBy { it.id }
    }

    override fun findByUsername(username: String): User? = transaction {
        UserEntity.find { Users.username eq username }.firstOrNull()?.toRecord()
    }

    override fun findByEmail(email: String): User? = transaction {
        UserEntity.find { Users.email eq email }.firstOrNull()?.toRecord()
    }

    override fun existsByUsername(username: String): Boolean = transaction {
        !UserEntity.find { Users.username eq username }.empty()
    }

    override fun create(
        username: String,
        email: String,
        name: String,
        passwordHash: String,
        salt: String,
        createdAt: LocalDateTime
    ): User = transaction {
        UserEntity.new {
            this.username = username
            this.email = email
            this.name = name
            this.passwordHash = passwordHash
            this.salt = salt
            this.createdAt = createdAt
        }.toRecord()
    }

    override fun updateFields(id: UUID, name: String?, email: String?, isAdmin: Boolean?): User? = transaction {
        val user = UserEntity.findById(id) ?: return@transaction null
        name?.let { user.name = it }
        email?.let { user.email = it }
        isAdmin?.let { user.isAdmin = it }
        user.flush()
        user.toRecord()
    }

    override fun delete(id: UUID): Boolean = transaction {
        val user = UserEntity.findById(id)
        if (user != null) {
            user.delete()
            true
        } else {
            false
        }
    }

    override fun findAll(): List<User> = transaction {
        UserEntity.all().map { it.toRecord() }
    }

    override fun findPage(limit: Int, offset: Int): List<User> = transaction {
        UserEntity.all()
            .orderBy(Users.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override fun count(): Long = transaction {
        UserEntity.all().count()
    }
}
