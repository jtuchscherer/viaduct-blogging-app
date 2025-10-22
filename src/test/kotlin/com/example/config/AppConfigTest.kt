package com.example.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Smoke test to verify test infrastructure is working.
 */
class AppConfigTest {

    @Test
    fun `test config loads successfully`() {
        val config = TestConfig.testAppConfig()

        assertEquals(Environment.TEST, config.environment)
        assertEquals("blog-app-test", config.jwt.issuer)
        assertTrue(config.jwt.secret.isNotEmpty())
        assertTrue(config.database.url.contains("h2:mem"))
    }

    @Test
    fun `test config uses in-memory database`() {
        val config = TestConfig.testAppConfig()

        assertTrue(config.database.url.contains("mem"))
        assertEquals("org.h2.Driver", config.database.driver)
    }

    @Test
    fun `test config has correct server ports`() {
        val config = TestConfig.testAppConfig()

        assertEquals(8080, config.server.graphqlPort)
        assertEquals(8081, config.server.authPort)
        assertEquals("com.example.viadapp", config.server.viaductPackagePrefix)
    }
}
