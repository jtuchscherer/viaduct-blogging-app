package org.tuchscherer.config

/**
 * Database configuration for connection settings.
 */
data class DatabaseConfig(
    val url: String,
    val driver: String
)
