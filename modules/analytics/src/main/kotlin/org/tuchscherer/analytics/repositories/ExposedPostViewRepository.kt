package org.tuchscherer.analytics.repositories

import org.tuchscherer.analytics.PostViews
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * Exposed ORM implementation of [PostViewRepository].
 *
 * Table initialisation is deferred to the first use rather than at app startup, so this module
 * needs no changes to [org.tuchscherer.database.DatabaseFactory]. The table is created via
 * [SchemaUtils.createMissingTablesAndColumns] inside the first transaction, which runs after
 * Ktor has already processed the initial request (i.e. after the DB connection is established).
 *
 * For safety the init block creates the table eagerly when the singleton is constructed.
 */
class ExposedPostViewRepository : PostViewRepository {

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(PostViews)
        }
    }

    override fun incrementViewCount(postId: UUID) {
        val idStr = postId.toString()
        transaction {
            val existing = PostViews
                .selectAll()
                .where { PostViews.postId eq idStr }
                .firstOrNull()

            if (existing == null) {
                PostViews.insert {
                    it[PostViews.postId] = idStr
                    it[viewCount] = 1L
                }
            } else {
                PostViews.update({ PostViews.postId eq idStr }) {
                    it[viewCount] = existing[viewCount] + 1L
                }
            }
        }
    }

    override fun bulkGetViewCounts(postIds: List<UUID>): Map<UUID, Long> = transaction {
        if (postIds.isEmpty()) return@transaction emptyMap()
        val idStrs = postIds.map { it.toString() }
        PostViews
            .selectAll()
            .where { PostViews.postId inList idStrs }
            .associate { UUID.fromString(it[PostViews.postId]) to it[PostViews.viewCount] }
    }

    override fun getMostViewed(limit: Int): List<UUID> = transaction {
        PostViews
            .selectAll()
            .orderBy(PostViews.viewCount to SortOrder.DESC)
            .limit(limit)
            .map { UUID.fromString(it[PostViews.postId]) }
    }

    override fun getTotalViews(): Long = transaction {
        PostViews
            .select(PostViews.viewCount.sum())
            .firstOrNull()
            ?.get(PostViews.viewCount.sum()) ?: 0L
    }
}
