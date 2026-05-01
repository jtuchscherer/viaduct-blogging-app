package org.tuchscherer.analytics

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Sets up an H2 in-memory database containing the analytics table for repository tests.
 * Mirrors the pattern of the root project's DatabaseTestHelper.
 */
object AnalyticsDatabaseTestHelper {

    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:analytics_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(PostViews)
        }
    }

    fun cleanDatabase() {
        transaction {
            SchemaUtils.drop(PostViews)
            SchemaUtils.create(PostViews)
        }
    }

    fun tearDownDatabase() {
        transaction {
            SchemaUtils.drop(PostViews)
        }
    }
}
