package org.tuchscherer.analytics

import org.jetbrains.exposed.v1.core.Table

/**
 * Tracks cumulative view counts per post. One row per post; the row is created on
 * first view and the counter is incremented on subsequent views.
 *
 * postId is stored as a VARCHAR(36) UUID string to avoid Kotlin 2.x kotlin.uuid.Uuid
 * experimental API while keeping H2 and SQLite compatibility.
 *
 * The postId column has no FK reference to the Posts table so this table is
 * self-contained within the analytics module's compile-time scope. Referential
 * integrity is maintained by the application logic.
 */
object PostViews : Table("post_views") {
    val postId = varchar("post_id", 36)
    val viewCount = long("view_count").default(0)

    override val primaryKey = PrimaryKey(postId)
}
