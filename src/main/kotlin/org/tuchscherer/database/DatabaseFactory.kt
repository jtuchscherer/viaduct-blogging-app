package org.tuchscherer.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Factory for database initialization and connection management.
 * Replaces the singleton DatabaseConfig object for better testability.
 */
class DatabaseFactory(private val config: org.tuchscherer.config.DatabaseConfig) {

    /**
     * Initialize database connection and create tables if they don't exist.
     */
    fun initialize() {
        if (config.user.isNotBlank()) {
            Database.connect(config.url, driver = config.driver, user = config.user, password = config.password)
        } else {
            Database.connect(config.url, driver = config.driver)
        }

        transaction {
            SchemaUtils.create(Users, Posts, Comments, Likes)
        }
    }

    fun healthCheck(): Boolean {
        return try {
            transaction {
                exec("SELECT 1") { true } ?: true
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
