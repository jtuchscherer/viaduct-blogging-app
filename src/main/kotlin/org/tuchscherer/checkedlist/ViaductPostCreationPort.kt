package org.tuchscherer.checkedlist

import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostType
import org.tuchscherer.database.Posts
import org.tuchscherer.database.User
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

/**
 * Root-project implementation of [PostCreationPort]. Uses the Posts table (Exposed DAO)
 * directly — the checkedlist module cannot import root-project types, so this bridge lives
 * in the root project and is registered via Koin.
 */
class ViaductPostCreationPort : PostCreationPort {

    override fun createCheckedListPost(title: String, authorId: UUID): PostData = transaction {
        val now = LocalDateTime.now()
        val post = Post.new {
            this.title = title
            this.content = ""   // checklist posts have no prose body
            this.authorId = EntityID(authorId, Posts)
            this.postType = PostType.CHECKED_LIST
            this.createdAt = now
            this.updatedAt = now
        }
        post.toData()
    }

    override fun getPostData(id: UUID): PostData? = transaction {
        Post.findById(id)
            ?.takeIf { it.postType == PostType.CHECKED_LIST }
            ?.toData()
    }

    override fun getPostsData(ids: List<UUID>): Map<UUID, PostData> = transaction {
        if (ids.isEmpty()) return@transaction emptyMap()
        Post.find {
            (Posts.id inList ids.map { EntityID(it, Posts) }) and
                (Posts.postType eq PostType.CHECKED_LIST)
        }.associate { it.id.value to it.toData() }
    }

    override fun getAllCheckedListPosts(): List<PostData> = transaction {
        Post.find { Posts.postType eq PostType.CHECKED_LIST }
            .orderBy(Posts.createdAt to SortOrder.DESC)
            .map { it.toData() }
    }

    override fun getAuthorIdsForPosts(postIds: List<UUID>): Map<UUID, UUID> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        Post.find {
            Posts.id inList postIds.map { EntityID(it, Posts) }
        }.associate { it.id.value to it.authorId.value }
    }

    private fun Post.toData() = PostData(
        id = id.value,
        title = title,
        authorId = authorId.value,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
