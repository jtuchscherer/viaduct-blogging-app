package org.tuchscherer.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import io.micrometer.core.instrument.MeterRegistry
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseFactory(
    private val config: org.tuchscherer.config.DatabaseConfig,
    private val meterRegistry: MeterRegistry,
) {

    fun initialize() {
        if (config.usePool) {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                minimumIdle = 2
                connectionTimeout = 30_000
                idleTimeout = 600_000
                maxLifetime = 1_800_000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                metricsTrackerFactory = MicrometerMetricsTrackerFactory(meterRegistry)
            }
            Database.connect(HikariDataSource(hikari))
        } else if (config.user.isNotBlank()) {
            Database.connect(config.url, driver = config.driver, user = config.user, password = config.password)
        } else {
            Database.connect(config.url, driver = config.driver)
        }

        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Posts, Comments, Likes)
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
