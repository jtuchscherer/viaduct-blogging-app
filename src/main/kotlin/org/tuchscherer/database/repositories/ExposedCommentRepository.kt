package org.tuchscherer.database.repositories

import org.tuchscherer.database.Comment
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/**
 * Exposed ORM implementation of CommentRepository.
 */
class ExposedCommentRepository : CommentRepository {

    override fun findById(id: UUID): Comment? = transaction {
        CommentEntity.findById(id)?.toRecord()
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Comment> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        CommentEntity.find { Comments.id inList ids.map { EntityID(it, Comments) } }
            .map { it.toRecord() }.associateBy { it.id }
    }

    override fun findByPostId(postId: UUID): List<Comment> = transaction {
        CommentEntity.find { Comments.postId eq postId }.map { it.toRecord() }
    }

    override fun findByAuthorId(authorId: UUID): List<Comment> = transaction {
        CommentEntity.find { Comments.authorId eq authorId }.map { it.toRecord() }
    }

    override fun create(
        content: String,
        postId: UUID,
        authorId: UUID,
        createdAt: LocalDateTime
    ): Comment = transaction {
        CommentEntity.new {
            this.content = content
            this.postId = EntityID(postId, Posts)
            this.authorId = EntityID(authorId, Users)
            this.createdAt = createdAt
        }.toRecord()
    }

    override fun updateContent(id: UUID, content: String): Comment? = transaction {
        val comment = CommentEntity.findById(id) ?: return@transaction null
        comment.content = content
        comment.flush()
        comment.toRecord()
    }

    override fun delete(id: UUID): Boolean = transaction {
        val comment = CommentEntity.findById(id)
        if (comment != null) {
            comment.delete()
            true
        } else {
            false
        }
    }

    override fun countByPostId(postId: UUID): Long = transaction {
        CommentEntity.find { Comments.postId eq postId }.count()
    }

    override fun getAuthorForComment(commentId: UUID): org.tuchscherer.database.User? = transaction {
        CommentEntity.findById(commentId)?.author?.toRecord()
    }

    override fun getPostForComment(commentId: UUID): org.tuchscherer.database.Post? = transaction {
        CommentEntity.findById(commentId)?.post?.toRecord()
    }

    override fun findAll(): List<Comment> = transaction {
        CommentEntity.all().map { it.toRecord() }
    }

    override fun findPage(limit: Int, offset: Int): List<Comment> = transaction {
        CommentEntity.all()
            .orderBy(Comments.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toRecord() }
    }

    override fun count(): Long = transaction {
        CommentEntity.all().count()
    }

    override fun countByUserId(userId: UUID): Long = transaction {
        CommentEntity.find { Comments.authorId eq userId }.count()
    }

    override fun deleteByUserId(userId: UUID): Int = transaction {
        val comments = CommentEntity.find { Comments.authorId eq userId }.toList()
        val count = comments.size
        comments.forEach { it.delete() }
        count
    }
}
