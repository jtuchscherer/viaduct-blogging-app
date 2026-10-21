package org.tuchscherer.database.repositories

import org.tuchscherer.checkedlist.port.ChecklistPostRepository
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.PostType
import org.tuchscherer.database.Posts
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/** Persists checklist metadata in the shared Posts table. */
class ExposedChecklistPostRepository(private val posts: PostRepository) : ChecklistPostRepository {

    override fun createCheckedListPost(
        title: String,
        authorId: UUID,
        description: String,
        status: String,
    ): PostData = transaction {
        val now = LocalDateTime.now()
        val post = PostEntity.new {
            this.title = title
            this.content = description  // description is stored in the content column
            this.authorId = EntityID(authorId, Users)
            this.postType = PostType.CHECKED_LIST
            this.status = status
            // A checklist created as published is published now; a draft has no publication time.
            this.publishedAt = if (status == PostStatus.PUBLISHED) now else null
            this.createdAt = now
            this.updatedAt = now
        }
        post.toData()
    }

    override fun getPostData(id: UUID): PostData? = transaction {
        PostEntity.findById(id)
            ?.takeIf { it.postType == PostType.CHECKED_LIST }
            ?.toData()
    }

    override fun getPostsData(ids: List<UUID>): Map<UUID, PostData> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        PostEntity.find {
            (Posts.id inList ids.map { EntityID(it, Posts) }) and
                (Posts.postType eq PostType.CHECKED_LIST)
        }.associate { it.id.value to it.toData() }
    }

    // Published only: the public checklist feed must never surface a draft, and filtering here
    // means a draft is never loaded rather than filtered out afterwards.
    override fun getAllCheckedListPosts(): List<PostData> = transaction {
        PostEntity.find {
            (Posts.postType eq PostType.CHECKED_LIST) and (Posts.status eq PostStatus.PUBLISHED)
        }
            // Ordered by publication, so a long-held draft appears as new once published.
            .orderBy(Posts.publishedAt to SortOrder.DESC)
            .map { it.toData() }
    }

    override fun getCheckedListPostsByAuthorId(authorId: UUID): List<PostData> = transaction {
        PostEntity.find { (Posts.postType eq PostType.CHECKED_LIST) and (Posts.authorId eq authorId) }
            .orderBy(Posts.createdAt to SortOrder.DESC)
            .map { it.toData() }
    }

    override fun getAuthorIdsForPosts(postIds: List<UUID>): Map<UUID, UUID> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        PostEntity.find {
            Posts.id inList postIds.map { EntityID(it, Posts) }
        }.associate { it.id.value to it.authorId.value }
    }

    override fun updateCheckedListPost(id: UUID, title: String?, description: String?): PostData? = transaction {
        val post = PostEntity.findById(id)
            ?.takeIf { it.postType == PostType.CHECKED_LIST }
            ?: return@transaction null
        title?.let { post.title = it }
        description?.let { post.content = it }
        post.updatedAt = LocalDateTime.now()
        post.flush()
        post.toData()
    }

    override fun deleteCheckedListPost(id: UUID): Boolean = transaction {
        val post = PostEntity.findById(id)
            ?.takeIf { it.postType == PostType.CHECKED_LIST }
            ?: return@transaction false
        posts.delete(post.id.value)
    }

    private fun PostEntity.toData() = PostData(
        id = id.value,
        title = title,
        description = content,  // description is stored in Posts.content
        authorId = authorId.value,
        status = status,
        publishedAt = publishedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
