package com.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Factory for database initialization and connection management.
 * Replaces the singleton DatabaseConfig object for better testability.
 */
class DatabaseFactory(private val config: com.example.config.DatabaseConfig) {

    /**
     * Initialize database connection and create tables if they don't exist.
     */
    fun initialize() {
        Database.connect(config.url, driver = config.driver)

        transaction {
            SchemaUtils.create(Users, Posts, Comments, Likes)
        }
    }

    /**
     * Get database connection.
     */
    fun getDatabase(): Database {
        return Database.connect(config.url, driver = config.driver)
    }
}
