package org.tuchscherer.database

import java.time.LocalDateTime
import java.util.UUID

/** Immutable snapshots, fully materialized inside the repository transaction. */
data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val name: String,
    val passwordHash: String,
    val salt: String,
    val isAdmin: Boolean,
    val createdAt: LocalDateTime,
)

data class Post(
    val id: UUID,
    val title: String,
    val content: String,
    val authorId: UUID,
    val postType: String,
    val status: String,
    val publishedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class Comment(
    val id: UUID,
    val content: String,
    val postId: UUID,
    val authorId: UUID,
    val createdAt: LocalDateTime,
)

data class Like(
    val id: UUID,
    val postId: UUID,
    val userId: UUID,
    val createdAt: LocalDateTime,
)
