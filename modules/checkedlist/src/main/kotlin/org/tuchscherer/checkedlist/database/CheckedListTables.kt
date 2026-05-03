package org.tuchscherer.checkedlist.database

import org.jetbrains.exposed.v1.core.Table

/**
 * Stores the items belonging to a CheckedListPost.
 *
 * postId and id are stored as VARCHAR(36) UUID strings to stay self-contained
 * within the checkedlist module's compile-time scope (no FK to Posts table).
 * Referential integrity is maintained by application logic: items are deleted
 * when their parent post is deleted.
 */
object CheckedListItems : Table("checked_list_items") {
    val id = varchar("id", 36)
    val postId = varchar("post_id", 36)
    val text = text("text")
    val checked = bool("checked").default(false)
    val position = integer("position").default(0)
    val createdAt = varchar("created_at", 50)

    override val primaryKey = PrimaryKey(id)
}
