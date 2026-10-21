package org.tuchscherer.database.repositories

import org.tuchscherer.analytics.PostViews
import org.tuchscherer.checkedlist.database.CheckedListItems
import org.tuchscherer.database.Comments
import org.tuchscherer.database.Likes
import org.tuchscherer.database.Posts
import org.tuchscherer.database.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Users, Posts, Comments, Likes, CheckedListItems, PostViews)
        }
    }

    /**
     * Clean up all tables after each test.
     * Call this in @AfterEach.
     */
    fun cleanDatabase() {
        transaction {
            SchemaUtils.drop(PostViews, CheckedListItems, Likes, Comments, Posts, Users)
            SchemaUtils.create(Users, Posts, Comments, Likes, CheckedListItems, PostViews)
        }
    }

    /**
     * Tear down database connection.
     * Call this in @AfterAll.
     */
    fun tearDownDatabase() {
        transaction {
            SchemaUtils.drop(PostViews, CheckedListItems, Likes, Comments, Posts, Users)
        }
    }
}
