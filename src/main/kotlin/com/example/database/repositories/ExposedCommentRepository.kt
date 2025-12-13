package com.example.database.repositories

import com.example.database.Comment
import com.example.database.Comments
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of CommentRepository.
 */
class ExposedCommentRepository : CommentRepository {

    override fun findById(id: UUID): Comment? = transaction {
        Comment.findById(id)
    }

    override fun findByPostId(postId: EntityID<UUID>): List<Comment> = transaction {
        Comment.find { Comments.postId eq postId }.toList()
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

    override fun countByPostId(postId: EntityID<UUID>): Long = transaction {
        Comment.find { Comments.postId eq postId }.count()
    }

    override fun getAuthorForComment(commentId: UUID): com.example.database.User? = transaction {
        Comment.findById(commentId)?.author
    }

    override fun getPostForComment(commentId: UUID): com.example.database.Post? = transaction {
        Comment.findById(commentId)?.post
    }
}
