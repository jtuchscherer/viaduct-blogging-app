package com.example.database.repositories

import com.example.database.Like
import com.example.database.Likes
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of LikeRepository.
 */
class ExposedLikeRepository : LikeRepository {

    override fun findById(id: UUID): Like? = transaction {
        Like.findById(id)
    }

    override fun findByPostId(postId: EntityID<UUID>): List<Like> = transaction {
        Like.find { Likes.postId eq postId }.toList()
    }

    override fun findByPostId(postId: UUID): List<Like> = transaction {
        Like.find { Likes.postId eq postId }.toList()
    }

    override fun findByUserId(userId: EntityID<UUID>): List<Like> = transaction {
        Like.find { Likes.userId eq userId }.toList()
    }

    override fun findByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Like? = transaction {
        Like.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.firstOrNull()
    }

    override fun existsByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Boolean = transaction {
        !Like.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.empty()
    }

    override fun create(
        postId: EntityID<UUID>,
        userId: EntityID<UUID>,
        createdAt: LocalDateTime
    ): Like = transaction {
        Like.new {
            this.postId = postId
            this.userId = userId
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

    override fun deleteByPostAndUser(postId: EntityID<UUID>, userId: EntityID<UUID>): Boolean = transaction {
        val like = findByPostAndUser(postId, userId)
        if (like != null) {
            like.delete()
            true
        } else {
            false
        }
    }

    override fun countByPostId(postId: EntityID<UUID>): Long = transaction {
        Like.find { Likes.postId eq postId }.count()
    }

    override fun countByPostId(postId: UUID): Long = transaction {
        Like.find { Likes.postId eq postId }.count()
    }

    override fun existsByPostAndUser(postId: UUID, userId: UUID): Boolean = transaction {
        !Like.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.empty()
    }

    override fun getUserForLike(likeId: UUID): com.example.database.User? = transaction {
        Like.findById(likeId)?.user
    }

    override fun getPostForLike(likeId: UUID): com.example.database.Post? = transaction {
        Like.findById(likeId)?.post
    }
}
