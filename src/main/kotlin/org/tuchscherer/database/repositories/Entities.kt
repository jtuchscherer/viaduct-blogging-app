package org.tuchscherer.database.repositories

import org.tuchscherer.database.User
import org.tuchscherer.database.Users
import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Like
import org.tuchscherer.database.Likes

import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.util.UUID

internal class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(Users)

    var username by Users.username
    var email by Users.email
    var name by Users.name
    var passwordHash by Users.passwordHash
    var salt by Users.salt
    var isAdmin by Users.isAdmin
    var createdAt by Users.createdAt

}

internal class PostEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostEntity>(Posts)

    var title by Posts.title
    var content by Posts.content
    var authorId by Posts.authorId
    var postType by Posts.postType
    var status by Posts.status
    var publishedAt by Posts.publishedAt
    var createdAt by Posts.createdAt
    var updatedAt by Posts.updatedAt

}

internal class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(Comments)

    var content by Comments.content
    var postId by Comments.postId
    var authorId by Comments.authorId
    var createdAt by Comments.createdAt

    var post by PostEntity referencedOn Comments.postId
    var author by UserEntity referencedOn Comments.authorId
}

internal class LikeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<LikeEntity>(Likes)

    var postId by Likes.postId
    var userId by Likes.userId
    var createdAt by Likes.createdAt

    var post by PostEntity referencedOn Likes.postId
    var user by UserEntity referencedOn Likes.userId
}

internal fun UserEntity.toRecord() = User(id.value, username, email, name, passwordHash, salt, isAdmin, createdAt)
internal fun PostEntity.toRecord() = Post(
    id = id.value,
    title = title,
    content = content,
    authorId = authorId.value,
    postType = postType,
    status = status,
    publishedAt = publishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
internal fun CommentEntity.toRecord() = Comment(id.value, content, postId.value, authorId.value, createdAt)
internal fun LikeEntity.toRecord() = Like(id.value, postId.value, userId.value, createdAt)
