package com.example.config

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Test configuration and Koin modules for unit and integration tests.
 * Provides test-specific implementations and mocks.
 */
object TestConfig {
    /**
     * Creates a test-specific configuration with in-memory database and test secrets.
     */
    fun testAppConfig(): AppConfig = AppConfig(
        environment = Environment.TEST,
        jwt = JwtConfig(
            secret = "test-secret-key-for-testing-only",
            issuer = "blog-app-test",
            expirationHours = 1
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

    /**
     * Basic Koin module for test configuration.
     * Can be extended with additional test modules as needed.
     */
    fun testConfigModule(): Module = module {
        single { testAppConfig() }
        single { testAppConfig().jwt }
        single { testAppConfig().database }
        single { testAppConfig().server }
    }
}
