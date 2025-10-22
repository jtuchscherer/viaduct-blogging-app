package com.example.config

import com.example.auth.AuthenticationService
import com.example.auth.JwtService
import com.example.auth.PasswordService
import com.example.database.repositories.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject

/**
 * Tests for Koin module configuration.
 * Verifies that all dependencies are properly wired and can be injected.
 */
class KoinModulesTest : KoinTest {

    @AfterEach
    fun cleanup() {
        stopKoin()
    }

    @Test
    fun `configModule provides AppConfig`() {
        startKoin {
            modules(configModule)
        }

        val appConfig: AppConfig by inject()
        assertNotNull(appConfig)
        assertNotNull(appConfig.jwt)
        assertNotNull(appConfig.database)
        assertNotNull(appConfig.server)
    }

    @Test
    fun `configModule provides JwtConfig`() {
        startKoin {
            modules(configModule)
        }

        val jwtConfig: JwtConfig by inject()
        assertNotNull(jwtConfig)
        assertNotNull(jwtConfig.secret)
        assertNotNull(jwtConfig.issuer)
        assertTrue(jwtConfig.expirationHours > 0)
    }

    @Test
    fun `configModule provides DatabaseConfig`() {
        startKoin {
            modules(configModule)
        }

        val databaseConfig: DatabaseConfig by inject()
        assertNotNull(databaseConfig)
        assertNotNull(databaseConfig.url)
        assertNotNull(databaseConfig.driver)
    }

    @Test
    fun `configModule provides ServerConfig`() {
        startKoin {
            modules(configModule)
        }

        val serverConfig: ServerConfig by inject()
        assertNotNull(serverConfig)
        assertTrue(serverConfig.graphqlPort > 0)
        assertTrue(serverConfig.authPort > 0)
    }

    @Test
    fun `repositoryModule provides UserRepository`() {
        startKoin {
            modules(repositoryModule)
        }

        val userRepository: UserRepository by inject()
        assertNotNull(userRepository)
        assertTrue(userRepository is ExposedUserRepository)
    }

    @Test
    fun `repositoryModule provides PostRepository`() {
        startKoin {
            modules(repositoryModule)
        }

        val postRepository: PostRepository by inject()
        assertNotNull(postRepository)
        assertTrue(postRepository is ExposedPostRepository)
    }

    @Test
    fun `repositoryModule provides CommentRepository`() {
        startKoin {
            modules(repositoryModule)
        }

        val commentRepository: CommentRepository by inject()
        assertNotNull(commentRepository)
        assertTrue(commentRepository is ExposedCommentRepository)
    }

    @Test
    fun `repositoryModule provides LikeRepository`() {
        startKoin {
            modules(repositoryModule)
        }

        val likeRepository: LikeRepository by inject()
        assertNotNull(likeRepository)
        assertTrue(likeRepository is ExposedLikeRepository)
    }

    @Test
    fun `serviceModule provides PasswordService`() {
        startKoin {
            modules(serviceModule)
        }

        val passwordService: PasswordService by inject()
        assertNotNull(passwordService)
    }

    @Test
    fun `serviceModule provides JwtService with dependencies`() {
        startKoin {
            modules(configModule, repositoryModule, serviceModule)
        }

        val jwtService: JwtService by inject()
        assertNotNull(jwtService)
    }

    @Test
    fun `serviceModule provides AuthenticationService with dependencies`() {
        startKoin {
            modules(repositoryModule, serviceModule)
        }

        val authService: AuthenticationService by inject()
        assertNotNull(authService)
    }

    @Test
    fun `allModules can be loaded together`() {
        startKoin {
            modules(allModules)
        }

        // Verify key dependencies are available
        val appConfig: AppConfig by inject()
        val userRepository: UserRepository by inject()
        val passwordService: PasswordService by inject()
        val jwtService: JwtService by inject()
        val authService: AuthenticationService by inject()

        assertNotNull(appConfig)
        assertNotNull(userRepository)
        assertNotNull(passwordService)
        assertNotNull(jwtService)
        assertNotNull(authService)
    }

    @Test
    fun `repositories are singletons`() {
        startKoin {
            modules(repositoryModule)
        }

        val userRepository1: UserRepository by inject()
        val userRepository2: UserRepository by inject()

        // Same instance should be returned (singleton)
        assertSame(userRepository1, userRepository2)
    }

    @Test
    fun `services are singletons`() {
        startKoin {
            modules(configModule, repositoryModule, serviceModule)
        }

        val jwtService1: JwtService by inject()
        val jwtService2: JwtService by inject()

        // Same instance should be returned (singleton)
        assertSame(jwtService1, jwtService2)
    }
}
