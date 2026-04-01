package com.example.integration

import com.example.auth.AuthenticationService
import com.example.auth.JwtService
import com.example.auth.PasswordService
import com.example.auth.UserAlreadyExistsException
import com.example.config.JwtConfig
import com.example.database.repositories.DatabaseTestHelper
import com.example.database.repositories.ExposedUserRepository
import com.example.database.repositories.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Integration tests for the authentication flow.
 * Uses real services and repositories against an H2 in-memory database.
 */
class AuthFlowIntegrationTest {

    private val userRepository: UserRepository = ExposedUserRepository()
    private val passwordService = PasswordService()
    private val authService = AuthenticationService(passwordService, userRepository)
    private val jwtConfig = JwtConfig(
        secret = "integration-test-secret",
        issuer = "blog-app-test",
        expirationHours = 1
    )
    private val jwtService = JwtService(jwtConfig, userRepository)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupDatabase() = DatabaseTestHelper.setupDatabase()

        @AfterAll
        @JvmStatic
        fun tearDownDatabase() = DatabaseTestHelper.tearDownDatabase()
    }

    @AfterEach
    fun cleanUp() = DatabaseTestHelper.cleanDatabase()

    // ── Registration ────────────────────────────────────────────────────

    @Test
    fun `register creates user with hashed password`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "password123")

        assertNotNull(user.id)
        assertEquals("alice", user.username)
        assertEquals("alice@example.com", user.email)
        assertEquals("Alice", user.name)
        assertNotEquals("password123", user.passwordHash)
        assertTrue(user.salt.isNotBlank())
    }

    @Test
    fun `registered user is persisted in database`() {
        authService.createUser("alice", "alice@example.com", "Alice", "password123")

        val dbUser = userRepository.findByUsername("alice")
        assertNotNull(dbUser)
        assertEquals("alice", dbUser!!.username)
    }

    @Test
    fun `register throws for duplicate username`() {
        authService.createUser("alice", "alice@example.com", "Alice", "password123")

        assertThrows<UserAlreadyExistsException> {
            authService.createUser("alice", "other@example.com", "Alice2", "pass456")
        }
    }

    @Test
    fun `duplicate username registration does not create second user`() {
        authService.createUser("alice", "alice@example.com", "Alice", "password123")
        runCatching { authService.createUser("alice", "other@example.com", "Alice2", "pass456") }

        assertEquals(1, userRepository.findAll().size)
    }

    // ── Authentication ──────────────────────────────────────────────────

    @Test
    fun `authenticate returns user for correct credentials`() {
        authService.createUser("bob", "bob@example.com", "Bob", "secret")

        val result = authService.authenticateUser("bob", "secret")
        assertNotNull(result)
        assertEquals("bob", result!!.username)
    }

    @Test
    fun `authenticate returns null for wrong password`() {
        authService.createUser("bob", "bob@example.com", "Bob", "secret")

        assertNull(authService.authenticateUser("bob", "wrongpassword"))
    }

    @Test
    fun `authenticate returns null for unknown username`() {
        assertNull(authService.authenticateUser("nobody", "password"))
    }

    // ── JWT flow ────────────────────────────────────────────────────────

    @Test
    fun `full flow - register, authenticate, generate JWT, resolve user from token`() {
        val user = authService.createUser("carol", "carol@example.com", "Carol", "pass")
        val authenticated = authService.authenticateUser("carol", "pass")!!

        val token = jwtService.generateToken(authenticated.username, authenticated.id.value.toString())
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val payload = jwtService.verifyToken(token)
        assertNotNull(payload)
        assertEquals("carol", payload!!.username)
        assertEquals(user.id.value.toString(), payload.userId)

        val userFromToken = jwtService.getUserFromToken(token)
        assertNotNull(userFromToken)
        assertEquals("carol", userFromToken!!.username)
    }

    @Test
    fun `getUserFromToken returns null for invalid token`() {
        assertNull(jwtService.getUserFromToken("not.a.valid.token"))
    }

    @Test
    fun `getUserFromToken returns null when user no longer exists`() {
        val user = authService.createUser("dave", "dave@example.com", "Dave", "pass")
        val token = jwtService.generateToken(user.username, user.id.value.toString())

        userRepository.delete(user.id.value)

        assertNull(jwtService.getUserFromToken(token))
    }
}
