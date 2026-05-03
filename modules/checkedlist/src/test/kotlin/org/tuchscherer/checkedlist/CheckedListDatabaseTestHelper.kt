package org.tuchscherer.checkedlist

import org.tuchscherer.checkedlist.database.CheckedListItems
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Sets up an H2 in-memory database containing the checkedlist table for repository tests.
 * Mirrors the pattern of the root project's DatabaseTestHelper and analytics module's
 * AnalyticsDatabaseTestHelper.
 */
object CheckedListDatabaseTestHelper {

    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:checkedlist_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(CheckedListItems)
        }
    }

    fun cleanDatabase() {
        transaction {
            SchemaUtils.drop(CheckedListItems)
            SchemaUtils.create(CheckedListItems)
        }
    }

    fun tearDownDatabase() {
        transaction {
            SchemaUtils.drop(CheckedListItems)
        }
    }
}
