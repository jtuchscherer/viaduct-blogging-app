package org.tuchscherer.config

/**
 * JWT configuration for token generation and validation.
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val expirationHours: Long = 24
)
