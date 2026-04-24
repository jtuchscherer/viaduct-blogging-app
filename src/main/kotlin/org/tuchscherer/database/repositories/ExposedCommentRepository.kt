package org.tuchscherer.database.repositories

import org.tuchscherer.database.Comment
import org.tuchscherer.database.Comments
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of CommentRepository.
 */
class ExposedCommentRepository : CommentRepository {

    override fun findById(id: UUID): Comment? = transaction {
        Comment.findById(id)
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Comment> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        Comment.find { Comments.id inList ids.map { EntityID(it, Comments) } }
            .associateBy { it.id.value }
    }

    override fun findByPostId(postId: UUID): List<Comment> = transaction {
        Comment.find { Comments.postId eq postId }.toList()
    }

    override fun findByAuthorId(authorId: EntityID<UUID>): List<Comment> = transaction {
        Comment.find { Comments.authorId eq authorId }.toList()
    }

    override fun create(
        content: String,
        postId: EntityID<UUID>,
        authorId: EntityID<UUID>,
        createdAt: LocalDateTime
    ): Comment = transaction {
        Comment.new {
            this.content = content
            this.postId = postId
            this.authorId = authorId
            this.createdAt = createdAt
        }
    }

    override fun update(comment: Comment): Comment = transaction {
        comment.also {
            it.flush()
        }
    }

    override fun delete(id: UUID): Boolean = transaction {
        val comment = Comment.findById(id)
        if (comment != null) {
            comment.delete()
            true
        } else {
            false
        }
    }

    override fun countByPostId(postId: UUID): Long = transaction {
        Comment.find { Comments.postId eq postId }.count()
    }

    override fun getAuthorForComment(commentId: UUID): org.tuchscherer.database.User? = transaction {
        Comment.findById(commentId)?.author
    }

    override fun getPostForComment(commentId: UUID): org.tuchscherer.database.Post? = transaction {
        Comment.findById(commentId)?.post
    }

    override fun findAll(): List<Comment> = transaction {
        Comment.all().toList()
    }

    override fun findPage(limit: Int, offset: Int): List<Comment> = transaction {
        Comment.all()
            .orderBy(Comments.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .toList()
    }

    override fun count(): Long = transaction {
        Comment.all().count()
    }

    override fun countByUserId(userId: UUID): Long = transaction {
        Comment.find { Comments.authorId eq userId }.count()
    }

    override fun deleteByUserId(userId: UUID): Int = transaction {
        val comments = Comment.find { Comments.authorId eq userId }.toList()
        val count = comments.size
        comments.forEach { it.delete() }
        count
    }
}
