package com.example.database.repositories

import com.example.database.Comments
import com.example.database.Likes
import com.example.database.Posts
import com.example.database.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Helper class to set up and tear down H2 in-memory database for repository tests.
 */
object DatabaseTestHelper {
    private var database: Database? = null

    /**
     * Initialize H2 in-memory database with test schema.
     * Call this in @BeforeEach or @BeforeAll.
     */
    fun setupDatabase() {
        database = Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Users, Posts, Comments, Likes)
        }
    }

    /**
     * Clean up all tables after each test.
     * Call this in @AfterEach.
     */
    fun cleanDatabase() {
        transaction {
            SchemaUtils.drop(Likes, Comments, Posts, Users)
            SchemaUtils.create(Users, Posts, Comments, Likes)
        }
    }

    /**
     * Tear down database connection.
     * Call this in @AfterAll.
     */
    fun tearDownDatabase() {
        transaction {
            SchemaUtils.drop(Likes, Comments, Posts, Users)
        }
    }
}
