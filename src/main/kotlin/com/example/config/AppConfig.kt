package com.example.config

/**
 * Application configuration that loads environment-specific settings.
 * Supports test, dev, and production environments.
 */
data class AppConfig(
    val environment: Environment,
    val jwt: JwtConfig,
    val database: DatabaseConfig,
    val server: ServerConfig
) {
    companion object {
        /**
         * Load configuration based on environment variable or default to development.
         */
        fun load(): AppConfig {
            val env = System.getenv("APP_ENV")?.let {
                Environment.valueOf(it.uppercase())
            } ?: Environment.DEV

            return when (env) {
                Environment.TEST -> testConfig()
                Environment.DEV -> devConfig()
                Environment.PROD -> prodConfig()
            }
        }

        private fun testConfig() = AppConfig(
            environment = Environment.TEST,
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "test-secret-key",
                issuer = "blog-app-test",
                expirationHours = 24
            ),
            database = DatabaseConfig(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                driver = "org.h2.Driver"
            ),
            server = ServerConfig(
                graphqlPort = 8080,
                authPort = 8081,
                viaductPackagePrefix = "com.example.viadapp"
            )
        )

        private fun devConfig() = AppConfig(
            environment = Environment.DEV,
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "your-secret-key",
                issuer = "blog-app",
                expirationHours = 24
            ),
            database = DatabaseConfig(
                url = System.getenv("DATABASE_URL")
                    ?: "jdbc:sqlite:${System.getProperty("user.dir")}/blog.db",
                driver = "org.sqlite.JDBC"
            ),
            server = ServerConfig(
                graphqlPort = System.getenv("GRAPHQL_PORT")?.toIntOrNull() ?: 8080,
                authPort = System.getenv("AUTH_PORT")?.toIntOrNull() ?: 8081,
                viaductPackagePrefix = "com.example.viadapp"
            )
        )

        private fun prodConfig() = AppConfig(
            environment = Environment.PROD,
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET")
                    ?: throw IllegalStateException("JWT_SECRET must be set in production"),
                issuer = "blog-app",
                expirationHours = 24
            ),
            database = DatabaseConfig(
                url = System.getenv("DATABASE_URL")
                    ?: throw IllegalStateException("DATABASE_URL must be set in production"),
                driver = System.getenv("DATABASE_DRIVER") ?: "org.sqlite.JDBC"
            ),
            server = ServerConfig(
                graphqlPort = System.getenv("GRAPHQL_PORT")?.toIntOrNull() ?: 8080,
                authPort = System.getenv("AUTH_PORT")?.toIntOrNull() ?: 8081,
                viaductPackagePrefix = "com.example.viadapp"
            )
        )
    }
}

enum class Environment {
    TEST, DEV, PROD
}
