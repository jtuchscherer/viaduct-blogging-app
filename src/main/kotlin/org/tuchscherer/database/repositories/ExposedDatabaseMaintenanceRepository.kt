package org.tuchscherer.database.repositories

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Likes
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Users
import java.sql.SQLException

class ExposedDatabaseMaintenanceRepository : DatabaseMaintenanceRepository {
    private val logger = LoggerFactory.getLogger(ExposedDatabaseMaintenanceRepository::class.java)

    override fun initializeSchema() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Posts, Comments, Likes)
        }
    }

    override fun healthCheck(): Boolean = try {
        transaction { exec("SELECT 1") { it.next() } ?: false }
    } catch (e: SQLException) {
        logger.warn("Database health check failed", e)
        false
    }
}
