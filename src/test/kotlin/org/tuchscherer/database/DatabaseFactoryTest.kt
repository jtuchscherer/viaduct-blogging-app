package org.tuchscherer.database

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.config.DatabaseConfig
import org.tuchscherer.database.repositories.ExposedDatabaseMaintenanceRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

class DatabaseFactoryTest {
    private val registry = SimpleMeterRegistry()
    private val config = DatabaseConfig("jdbc:h2:mem:health-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
    private val factory = DatabaseFactory(config, registry, ExposedDatabaseMaintenanceRepository())
    private lateinit var database: Database

    @AfterEach
    fun tearDown() {
        if (::database.isInitialized) TransactionManager.closeAndUnregister(database)
        registry.close()
    }

    @Test
    fun `health check succeeds when the database accepts a query`() {
        database = Database.connect(config.url, driver = config.driver)
        assertTrue(factory.healthCheck())
    }

    @Test
    fun `initialization creates the application tables and can run twice`() {
        factory.initialize()
        database = transaction { db }
        factory.initialize()
        database = transaction { db }
        transaction {
            val tables = SchemaUtils.listTables().map { it.substringAfterLast('.').uppercase() }
            assertTrue(tables.containsAll(listOf("USERS", "POSTS", "COMMENTS", "LIKES")))
            SchemaUtils.drop(Likes, Comments, Posts, Users)
        }
        assertTrue(factory.healthCheck())
    }

    @Test
    fun `health check returns false on database connection failure`() {
        val source = mockk<DataSource>()
        every { source.connection } throws SQLException("database unavailable")
        database = Database.connect(source)
        assertFalse(factory.healthCheck())
    }

    @Test
    fun `health check does not hide unexpected programming failures`() {
        val failure = IllegalStateException("unexpected connection provider failure")
        val source = mockk<DataSource>()
        every { source.connection } throws failure
        database = Database.connect(source)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { factory.healthCheck() })
    }
}
