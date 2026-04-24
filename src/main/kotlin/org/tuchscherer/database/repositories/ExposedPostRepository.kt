package org.tuchscherer.database.repositories

import org.tuchscherer.database.Comment
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Like
import org.tuchscherer.database.Likes
import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.*

/**
 * Exposed ORM implementation of PostRepository.
 */
class ExposedPostRepository : PostRepository {

    override fun findById(id: UUID): Post? = transaction {
        Post.findById(id)
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Post> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        Post.find { Posts.id inList ids.map { EntityID(it, Posts) } }
            .associateBy { it.id.value }
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
        val post = Post.findById(id) ?: return@transaction false
        // Delete dependents first to satisfy FK constraints
        Like.find { Likes.postId eq post.id }.forEach { it.delete() }
        Comment.find { Comments.postId eq post.id }.forEach { it.delete() }
        post.delete()
        true
    }

    override fun findPage(limit: Int, offset: Int): List<Post> = transaction {
        Post.all()
            .orderBy(Posts.createdAt to org.jetbrains.exposed.v1.core.SortOrder.DESC)
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

    override fun getAuthorIdsByPostIds(postIds: List<UUID>): Map<UUID, UUID> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        Post.find { Posts.id inList postIds.map { EntityID(it, Posts) } }
            .associate { it.id.value to it.authorId.value }
    }

    override fun countByAuthorId(authorId: UUID): Long = transaction {
        Post.find { Posts.authorId eq authorId }.count()
    }

    override fun deleteByAuthorId(authorId: UUID): Int = transaction {
        val posts = Post.find { Posts.authorId eq authorId }.toList()
        val count = posts.size
        posts.forEach { post ->
            // Delete dependents first to satisfy FK constraints
            Like.find { Likes.postId eq post.id }.forEach { it.delete() }
            Comment.find { Comments.postId eq post.id }.forEach { it.delete() }
            post.delete()
        }
        count
    }
}
