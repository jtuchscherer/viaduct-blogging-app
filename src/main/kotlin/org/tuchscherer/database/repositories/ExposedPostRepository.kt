package org.tuchscherer.database.repositories

import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
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

    override fun updateById(
        id: UUID,
        title: String?,
        content: String?
    ): Post? = transaction {
        val post = Post.findById(id) ?: return@transaction null
        title?.let { post.title = it }
        content?.let { post.content = it }
        post.updatedAt = LocalDateTime.now()
        post.flush()
        post
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

    override fun findPage(limit: Int, offset: Int): List<Post> = transaction {
        Post.all()
            .orderBy(Posts.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .toList()
    }

    override fun count(): Long = transaction {
        Post.all().count()
    }

    override fun countByAuthor(authorId: EntityID<UUID>): Long = transaction {
        Post.find { Posts.authorId eq authorId }.count()
    }

    override fun getAuthorForPost(postId: UUID): org.tuchscherer.database.User? = transaction {
        Post.findById(postId)?.author
    }

    override fun getAuthorsByPostIds(postIds: List<UUID>): Map<UUID, org.tuchscherer.database.User> = transaction {
        Post.find { Posts.id inList postIds }
            .associate { post -> post.id.value to post.author }
    }
}
