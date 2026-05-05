package org.tuchscherer.database.repositories

import org.tuchscherer.database.Like
import org.tuchscherer.database.Likes
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of LikeRepository.
 */
class ExposedLikeRepository : LikeRepository {

    override fun findById(id: UUID): Like? = transaction {
        Like.findById(id)
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Like> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        Like.find { Likes.id inList ids.map { EntityID(it, Likes) } }
            .associateBy { it.id.value }
    }

    override fun findByPostId(postId: UUID): List<Like> = transaction {
        Like.find { Likes.postId eq postId }.toList()
    }

    override fun findByUserId(userId: UUID): List<Like> = transaction {
        Like.find { Likes.userId eq userId }.toList()
    }

    override fun findByPostAndUser(postId: UUID, userId: UUID): Like? = transaction {
        Like.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.firstOrNull()
    }

    override fun create(
        postId: UUID,
        userId: UUID,
        createdAt: LocalDateTime
    ): Like = transaction {
        Like.new {
            this.postId = EntityID(postId, Posts)
            this.userId = EntityID(userId, Users)
            this.createdAt = createdAt
        }
    }

    override fun delete(id: UUID): Boolean = transaction {
        val like = Like.findById(id)
        if (like != null) {
            like.delete()
            true
        } else {
            false
        }
    }

    override fun deleteByPostAndUser(postId: UUID, userId: UUID): Boolean = transaction {
        val like = findByPostAndUser(postId, userId)
        if (like != null) {
            like.delete()
            true
        } else {
            false
        }
    }

    override fun countByPostId(postId: UUID): Long = transaction {
        Like.find { Likes.postId eq postId }.count()
    }

    override fun existsByPostAndUser(postId: UUID, userId: UUID): Boolean = transaction {
        !Like.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.empty()
    }

    override fun getUserForLike(likeId: UUID): org.tuchscherer.database.User? = transaction {
        Like.findById(likeId)?.user
    }

    override fun getPostForLike(likeId: UUID): org.tuchscherer.database.Post? = transaction {
        Like.findById(likeId)?.post
    }

    override fun count(): Long = transaction {
        Like.all().count()
    }

    override fun countByUserId(userId: UUID): Long = transaction {
        Like.find { Likes.userId eq userId }.count()
    }

    override fun deleteByUserId(userId: UUID): Int = transaction {
        val likes = Like.find { Likes.userId eq userId }.toList()
        val count = likes.size
        likes.forEach { it.delete() }
        count
    }
}
