package com.example.database

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class User(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<User>(Users)

    var username by Users.username
    var email by Users.email
    var name by Users.name
    var passwordHash by Users.passwordHash
    var salt by Users.salt
    var createdAt by Users.createdAt

    val posts by Post referrersOn Posts.authorId
}

class Post(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Post>(Posts)

    var title by Posts.title
    var content by Posts.content
    var authorId by Posts.authorId
    var createdAt by Posts.createdAt
    var updatedAt by Posts.updatedAt

    var author by User referencedOn Posts.authorId
    val comments by Comment referrersOn Comments.postId
    val likes by Like referrersOn Likes.postId
}

class Comment(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Comment>(Comments)

    var content by Comments.content
    var postId by Comments.postId
    var authorId by Comments.authorId
    var createdAt by Comments.createdAt

    var post by Post referencedOn Comments.postId
    var author by User referencedOn Comments.authorId
}

class Like(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Like>(Likes)

    var postId by Likes.postId
    var userId by Likes.userId
    var createdAt by Likes.createdAt

    var post by Post referencedOn Likes.postId
    var user by User referencedOn Likes.userId
}