package org.tuchscherer.config

/**
 * Database configuration for connection settings.
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String = "",
    val password: String = "",
    val poolSize: Int = 10,
    val usePool: Boolean = false,
    val useFlyway: Boolean = false,
)
