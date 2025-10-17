package com.example.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : UUIDTable("users") {
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 255)
    val name = varchar("name", 255)
    val passwordHash = varchar("password_hash", 255)
    val salt = varchar("salt", 255)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object Posts : UUIDTable("posts") {
    val title = varchar("title", 500)
    val content = text("content")
    val authorId = reference("author_id", Users)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}

object Comments : UUIDTable("comments") {
    val content = text("content")
    val postId = reference("post_id", Posts)
    val authorId = reference("author_id", Users)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object Likes : UUIDTable("likes") {
    val postId = reference("post_id", Posts)
    val userId = reference("user_id", Users)
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    init {
        uniqueIndex(postId, userId)
    }
}