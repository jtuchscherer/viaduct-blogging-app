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
import java.util.UUID

/**
 * Exposed ORM implementation of LikeRepository.
 */
class ExposedLikeRepository : LikeRepository {

    override fun findById(id: UUID): Like? = transaction {
        LikeEntity.findById(id)?.toRecord()
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Like> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        LikeEntity.find { Likes.id inList ids.map { EntityID(it, Likes) } }
            .map { it.toRecord() }.associateBy { it.id }
    }

    override fun findByPostId(postId: UUID): List<Like> = transaction {
        LikeEntity.find { Likes.postId eq postId }.map { it.toRecord() }
    }

    override fun findByUserId(userId: UUID): List<Like> = transaction {
        LikeEntity.find { Likes.userId eq userId }.map { it.toRecord() }
    }

    override fun findByPostAndUser(postId: UUID, userId: UUID): Like? = transaction {
        LikeEntity.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.firstOrNull()?.toRecord()
    }

    override fun create(
        postId: UUID,
        userId: UUID,
        createdAt: LocalDateTime
    ): Like = transaction {
        LikeEntity.new {
            this.postId = EntityID(postId, Posts)
            this.userId = EntityID(userId, Users)
            this.createdAt = createdAt
        }.toRecord()
    }

    override fun delete(id: UUID): Boolean = transaction {
        val like = LikeEntity.findById(id)
        if (like != null) {
            like.delete()
            true
        } else {
            false
        }
    }

    override fun deleteByPostAndUser(postId: UUID, userId: UUID): Boolean = transaction {
        val like = LikeEntity.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.firstOrNull()
        if (like != null) {
            like.delete()
            true
        } else {
            false
        }
    }

    override fun countByPostId(postId: UUID): Long = transaction {
        LikeEntity.find { Likes.postId eq postId }.count()
    }

    override fun existsByPostAndUser(postId: UUID, userId: UUID): Boolean = transaction {
        !LikeEntity.find { (Likes.postId eq postId) and (Likes.userId eq userId) }.empty()
    }

    override fun getUserForLike(likeId: UUID): org.tuchscherer.database.User? = transaction {
        LikeEntity.findById(likeId)?.user?.toRecord()
    }

    override fun getPostForLike(likeId: UUID): org.tuchscherer.database.Post? = transaction {
        LikeEntity.findById(likeId)?.post?.toRecord()
    }

    override fun count(): Long = transaction {
        LikeEntity.all().count()
    }

    override fun countByUserId(userId: UUID): Long = transaction {
        LikeEntity.find { Likes.userId eq userId }.count()
    }

    override fun deleteByUserId(userId: UUID): Int = transaction {
        val likes = LikeEntity.find { Likes.userId eq userId }.toList()
        val count = likes.size
        likes.forEach { it.delete() }
        count
    }
}
