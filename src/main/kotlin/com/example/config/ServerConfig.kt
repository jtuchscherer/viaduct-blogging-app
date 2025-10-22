package com.example.config

/**
 * Server configuration for GraphQL and Auth server ports and settings.
 */
data class ServerConfig(
    val graphqlPort: Int = 8080,
    val authPort: Int = 8081,
    val viaductPackagePrefix: String = "com.example.viadapp"
)
