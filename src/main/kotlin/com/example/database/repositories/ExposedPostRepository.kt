package com.example.database.repositories

import com.example.database.Post
import com.example.database.Posts
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of PostRepository.
 */
class ExposedPostRepository : PostRepository {

    override fun findById(id: UUID): Post? = transaction {
        Post.findById(id)
    }

    override fun findByAuthorId(authorId: EntityID<UUID>): List<Post> = transaction {
        Post.find { Posts.authorId eq authorId }.toList()
    }

    override fun findAll(): List<Post> = transaction {
        Post.all().toList()
    }

    override fun create(
        title: String,
        content: String,
        authorId: EntityID<UUID>,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ): Post = transaction {
        Post.new {
            this.title = title
            this.content = content
            this.authorId = authorId
            this.createdAt = createdAt
            this.updatedAt = updatedAt
        }
    }

    override fun update(post: Post): Post = transaction {
        post.also {
            it.updatedAt = LocalDateTime.now()
            it.flush()
        }
    }

    override fun delete(id: UUID): Boolean = transaction {
        val post = Post.findById(id)
        if (post != null) {
            post.delete()
            true
        } else {
            false
        }
    }

    override fun count(): Long = transaction {
        Post.all().count()
    }

    override fun countByAuthor(authorId: EntityID<UUID>): Long = transaction {
        Post.find { Posts.authorId eq authorId }.count()
    }
}
