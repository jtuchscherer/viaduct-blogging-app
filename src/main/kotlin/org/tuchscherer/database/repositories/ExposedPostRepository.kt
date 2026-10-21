package org.tuchscherer.database.repositories

import org.tuchscherer.analytics.PostViews
import org.tuchscherer.checkedlist.database.CheckedListItems
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Like
import org.tuchscherer.database.Likes
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.PostType
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.select
import java.time.LocalDateTime
import java.util.UUID

/**
 * Exposed ORM implementation of PostRepository.
 */
class ExposedPostRepository : PostRepository {

    /**
     * What the public blog feed consists of: published blog posts.
     *
     * Named once because [findAll], [findPage] and [count] must all agree — if `count` counted
     * rows that `findPage` will not return, `totalCount` would advertise drafts the caller cannot
     * fetch. Three copies of the same predicate is how that drifts.
     */
    private val publishedBlogPosts
        get() = (Posts.postType eq PostType.BLOG_POST) and (Posts.status eq PostStatus.PUBLISHED)

    override fun findById(id: UUID): Post? = transaction {
        Post.findById(id)
    }

    override fun findByIds(ids: List<UUID>): Map<UUID, Post> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        Post.find { Posts.id inList ids.map { EntityID(it, Posts) } }
            .associateBy { it.id.value }
    }

    // Deliberately unfiltered by status: an author must see their own drafts here.
    override fun findByAuthorId(authorId: UUID): List<Post> = transaction {
        Post.find { (Posts.authorId eq authorId) and (Posts.postType eq PostType.BLOG_POST) }.toList()
    }

    // Published only: the public feed must never surface a draft, and filtering here means a
    // draft is not loaded at all rather than filtered out later.
    override fun findAll(): List<Post> = transaction {
        Post.find { publishedBlogPosts }.toList()
    }

    override fun findPublishedProjection(
        fields: Set<PostField>,
        limit: Int?,
        offset: Int
    ): List<PostProjection> = transaction {
        val columns = listOf(Posts.id, Posts.postType) + fields.map { it.column() }
        val query = Posts.select(columns).where { publishedBlogPosts }
            // Equal publication timestamps need a stable tie-breaker across page requests.
            .orderBy(Posts.publishedAt to SortOrder.DESC, Posts.id to SortOrder.ASC)
            .offset(offset.toLong())
        if (limit != null) query.limit(limit)
        query.map { it.toProjection(fields) }
    }

    override fun findProjectedByIds(
        ids: List<UUID>,
        fields: Set<PostField>
    ): Map<UUID, PostProjection> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        val columns = (listOf(Posts.id, Posts.postType, Posts.status, Posts.authorId) +
            fields.map { it.column() }).distinct()
        Posts.select(columns).where { Posts.id inList ids.map { EntityID(it, Posts) } }
            .map { it.toProjection(fields) }
            .associateBy { it.id }
    }

    private fun ResultRow.toProjection(fields: Set<PostField>) = PostProjection(
        id = this[Posts.id].value,
        postType = this[Posts.postType],
        fields = fields.toSet(),
        title = getOrNull(Posts.title),
        content = getOrNull(Posts.content),
        status = getOrNull(Posts.status),
        publishedAt = getOrNull(Posts.publishedAt),
        createdAt = getOrNull(Posts.createdAt),
        updatedAt = getOrNull(Posts.updatedAt),
        authorId = getOrNull(Posts.authorId)?.value,
    )

    private fun PostField.column(): Column<*> = when (this) {
        PostField.TITLE -> Posts.title
        PostField.CONTENT -> Posts.content
        PostField.STATUS -> Posts.status
        PostField.PUBLISHED_AT -> Posts.publishedAt
        PostField.CREATED_AT -> Posts.createdAt
        PostField.UPDATED_AT -> Posts.updatedAt
    }

    override fun create(
        title: String,
        content: String,
        authorId: UUID,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime,
        status: String
    ): Post = transaction {
        Post.new {
            this.title = title
            this.content = content
            this.authorId = EntityID(authorId, Users)
            this.status = status
            // A post created as published is published now; a draft has no publication time.
            this.publishedAt = if (status == PostStatus.PUBLISHED) createdAt else null
            this.createdAt = createdAt
            this.updatedAt = updatedAt
        }
    }

    override fun publishedIds(ids: List<UUID>): Set<UUID> = transaction {
        if (ids.isEmpty()) return@transaction emptySet()
        Post.find {
            (Posts.id inList ids.map { EntityID(it, Posts) }) and (Posts.status eq PostStatus.PUBLISHED)
        }.map { it.id.value }.toSet()
    }

    override fun updateStatus(id: UUID, status: String, publishedAt: LocalDateTime?): Post? =
        transaction {
            Post.findById(id)?.also {
                it.status = status
                it.publishedAt = publishedAt
                it.updatedAt = LocalDateTime.now()
                it.flush()
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
        deleteWithDependents(post)
        true
    }

    override fun findPage(limit: Int, offset: Int): List<Post> = transaction {
        Post.find { publishedBlogPosts }
            // Ordered by publication rather than creation, so a long-held draft appears as new
            // when it is finally published.
            .orderBy(Posts.publishedAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .toList()
    }

    override fun count(): Long = transaction {
        Post.find { publishedBlogPosts }.count()
    }

    override fun getAuthorIdsByPostIds(postIds: List<UUID>): Map<UUID, UUID> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        Posts.select(Posts.id, Posts.authorId)
            .where { Posts.id inList postIds.map { EntityID(it, Posts) } }
            .associate { it[Posts.id].value to it[Posts.authorId].value }
    }

    override fun countByAuthorId(authorId: UUID): Long = transaction {
        Post.find { Posts.authorId eq authorId }.count()
    }

    override fun deleteByAuthorId(authorId: UUID): Int = transaction {
        val posts = Post.find { Posts.authorId eq authorId }.toList()
        posts.forEach(::deleteWithDependents)
        posts.size
    }

    /**
     * Delete [post] along with its social, checklist, and analytics data. Dependents go first so
     * the foreign-key constraints on Likes.postId and Comments.postId stay
     * satisfied. Must be called inside an existing transaction.
     */
    private fun deleteWithDependents(post: Post) {
        Like.find { Likes.postId eq post.id }.forEach { it.delete() }
        Comment.find { Comments.postId eq post.id }.forEach { it.delete() }
        CheckedListItems.deleteWhere { CheckedListItems.postId eq post.id.value.toString() }
        PostViews.deleteWhere { PostViews.postId eq post.id.value.toString() }
        post.delete()
    }
}
