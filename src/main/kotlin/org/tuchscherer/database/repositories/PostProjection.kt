package org.tuchscherer.database.repositories

import java.time.LocalDateTime
import java.util.UUID

/** Scalar columns that post resolvers can request without loading a full DAO entity. */
enum class PostField {
    TITLE, CONTENT, STATUS, PUBLISHED_AT, CREATED_AT, UPDATED_AT
}

/**
 * Detached feed row. Identity is always loaded, including when the client only requests
 * fields owned by other resolvers. [fields] distinguishes omitted nullable values from null.
 * Batched node reads always include author/status metadata for visibility checks; that
 * metadata is only published to GraphQL when its field was selected.
 */
data class PostProjection(
    val id: UUID,
    val postType: String,
    val fields: Set<PostField>,
    val title: String? = null,
    val content: String? = null,
    val status: String? = null,
    val publishedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val authorId: UUID? = null,
)
