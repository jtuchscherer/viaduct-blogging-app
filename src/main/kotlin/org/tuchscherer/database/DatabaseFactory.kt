package org.tuchscherer.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import io.micrometer.core.instrument.MeterRegistry
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.tuchscherer.database.repositories.DatabaseMaintenanceRepository

class DatabaseFactory(
    private val config: org.tuchscherer.config.DatabaseConfig,
    private val meterRegistry: MeterRegistry,
    private val maintenanceRepository: DatabaseMaintenanceRepository,
) {

    fun initialize() {
        if (config.useFlyway) {
            Flyway.configure()
                .dataSource(config.url, config.user, config.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        if (config.usePool) {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                minimumIdle = 2
                connectionTimeout = CONNECTION_TIMEOUT_MS
                idleTimeout = IDLE_TIMEOUT_MS
                maxLifetime = MAX_CONNECTION_LIFETIME_MS
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

        if (!config.useFlyway) {
            maintenanceRepository.initializeSchema()
        }
    }

    fun healthCheck(): Boolean = maintenanceRepository.healthCheck()
}

private const val CONNECTION_TIMEOUT_MS = 30_000L
private const val IDLE_TIMEOUT_MS = 600_000L
private const val MAX_CONNECTION_LIFETIME_MS = 1_800_000L
