package com.example.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    fun init() {
        // Connect to SQLite database
        Database.connect("jdbc:sqlite:/Users/johannes_tuchscherer/workspace/viaduct-blogs/blog.db", driver = "org.sqlite.JDBC")

        // Create tables if they don't exist
        transaction {
            SchemaUtils.create(Users, Posts, Comments, Likes)
        }
    }
}