package org.tuchscherer.checkedlist.repositories

import org.tuchscherer.checkedlist.database.CheckedListItems
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.time.LocalDateTime
import java.util.UUID

/**
 * Exposed ORM implementation of [CheckedListItemRepository].
 *
 * The table is created eagerly in the init block (same pattern as
 * [org.tuchscherer.analytics.repositories.ExposedPostViewRepository]) so this module
 * needs no changes to [org.tuchscherer.database.DatabaseFactory].
 */
class ExposedCheckedListItemRepository : CheckedListItemRepository {

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(CheckedListItems)
        }
    }

    override fun addItem(postId: UUID, text: String): CheckedListItemData {
        val newId = UUID.randomUUID().toString()
        val postIdStr = postId.toString()
        val createdAt = LocalDateTime.now().toString()

        return transaction {
            // Use count as the next 0-based position so items are numbered sequentially.
            val nextPosition = CheckedListItems
                .selectAll()
                .where { CheckedListItems.postId eq postIdStr }
                .count()
                .toInt()

            CheckedListItems.insert {
                it[id] = newId
                it[CheckedListItems.postId] = postIdStr
                it[CheckedListItems.text] = text
                it[checked] = false
                it[position] = nextPosition
                it[CheckedListItems.createdAt] = createdAt
            }

            CheckedListItemData(
                id = UUID.fromString(newId),
                postId = postId,
                text = text,
                checked = false,
                position = nextPosition,
                createdAt = createdAt,
            )
        }
    }

    override fun getItem(id: UUID): CheckedListItemData? = transaction {
        CheckedListItems
            .selectAll()
            .where { CheckedListItems.id eq id.toString() }
            .firstOrNull()
            ?.toData()
    }

    override fun getItemsForPost(postId: UUID): List<CheckedListItemData> = transaction {
        CheckedListItems
            .selectAll()
            .where { CheckedListItems.postId eq postId.toString() }
            .orderBy(CheckedListItems.position to SortOrder.ASC)
            .map { it.toData() }
    }

    override fun getItemsForPosts(postIds: List<UUID>): Map<UUID, List<CheckedListItemData>> {
        if (postIds.isEmpty()) return emptyMap()
        val idStrs = postIds.map { it.toString() }
        return transaction {
            CheckedListItems
                .selectAll()
                .where { CheckedListItems.postId inList idStrs }
                .orderBy(CheckedListItems.position to SortOrder.ASC)
                .map { it.toData() }
                .groupBy { it.postId }
        }
    }

    override fun toggleItem(id: UUID): CheckedListItemData? = transaction {
        val existing = CheckedListItems
            .selectAll()
            .where { CheckedListItems.id eq id.toString() }
            .firstOrNull() ?: return@transaction null

        val newChecked = !existing[CheckedListItems.checked]
        CheckedListItems.update({ CheckedListItems.id eq id.toString() }) {
            it[checked] = newChecked
        }

        existing.toData().copy(checked = newChecked)
    }

    override fun deleteItem(id: UUID): Boolean = transaction {
        CheckedListItems.deleteWhere { CheckedListItems.id eq id.toString() } > 0
    }

    override fun deleteItemsForPost(postId: UUID): Int = transaction {
        CheckedListItems.deleteWhere { CheckedListItems.postId eq postId.toString() }
    }

    override fun getPostIdForItem(id: UUID): UUID? = transaction {
        CheckedListItems
            .selectAll()
            .where { CheckedListItems.id eq id.toString() }
            .firstOrNull()
            ?.let { UUID.fromString(it[CheckedListItems.postId]) }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun org.jetbrains.exposed.v1.core.ResultRow.toData() = CheckedListItemData(
        id = UUID.fromString(this[CheckedListItems.id]),
        postId = UUID.fromString(this[CheckedListItems.postId]),
        text = this[CheckedListItems.text],
        checked = this[CheckedListItems.checked],
        position = this[CheckedListItems.position],
        createdAt = this[CheckedListItems.createdAt],
    )
}
