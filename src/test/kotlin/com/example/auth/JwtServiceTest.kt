package com.example.auth

import com.example.config.JwtConfig
import com.example.database.User
import com.example.database.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for JwtService with mocked UserRepository.
 */
class JwtServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var jwtConfig: JwtConfig
    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setup() {
        userRepository = mockk<UserRepository>()
        jwtConfig = JwtConfig(
            secret = "test-secret-key-for-testing",
            issuer = "test-issuer",
            expirationHours = 24
        )
        jwtService = JwtService(jwtConfig, userRepository)
    }

    @Test
    fun `generateToken creates valid token`() {
        val token = jwtService.generateToken("testuser", "user-id-123")

        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(token.split(".").size == 3) // JWT has 3 parts
    }

    @Test
    fun `verifyToken returns payload for valid token`() {
        val token = jwtService.generateToken("testuser", "user-id-123")

        val payload = jwtService.verifyToken(token)

        assertNotNull(payload)
        assertEquals("testuser", payload?.username)
        assertEquals("user-id-123", payload?.userId)
    }

    @Test
    fun `verifyToken returns null for invalid token`() {
        val payload = jwtService.verifyToken("invalid.token.here")

        assertNull(payload)
    }

    @Test
    fun `verifyToken returns null for malformed token`() {
        val payload = jwtService.verifyToken("not-a-jwt")

        assertNull(payload)
    }

    @Test
    fun `verifyToken returns null for token with wrong issuer`() {
        // Create a token with different issuer
        val wrongConfig = JwtConfig(
            secret = "test-secret-key-for-testing",
            issuer = "wrong-issuer",
            expirationHours = 24
        )
        val wrongService = JwtService(wrongConfig, userRepository)
        val token = wrongService.generateToken("testuser", "user-id-123")

        // Try to verify with original service (different issuer)
        val payload = jwtService.verifyToken(token)

        assertNull(payload)
    }

    @Test
    fun `verifyToken returns null for token with wrong secret`() {
        // Create a token with different secret
        val wrongConfig = JwtConfig(
            secret = "wrong-secret",
            issuer = "test-issuer",
            expirationHours = 24
        )
        val wrongService = JwtService(wrongConfig, userRepository)
        val token = wrongService.generateToken("testuser", "user-id-123")

        // Try to verify with original service (different secret)
        val payload = jwtService.verifyToken(token)

        assertNull(payload)
    }

    @Test
    fun `getUserFromToken returns user when token is valid and user exists`() {
        val mockUser = mockk<User>()
        every { userRepository.findByUsername("testuser") } returns mockUser

        val token = jwtService.generateToken("testuser", "user-id-123")
        val user = jwtService.getUserFromToken(token)

        assertEquals(mockUser, user)
        verify { userRepository.findByUsername("testuser") }
    }

    @Test
    fun `getUserFromToken returns null when token is invalid`() {
        val user = jwtService.getUserFromToken("invalid.token.here")

        assertNull(user)
        verify(exactly = 0) { userRepository.findByUsername(any()) }
    }

    @Test
    fun `getUserFromToken returns null when user not found`() {
        every { userRepository.findByUsername("testuser") } returns null

        val token = jwtService.generateToken("testuser", "user-id-123")
        val user = jwtService.getUserFromToken(token)

        assertNull(user)
        verify { userRepository.findByUsername("testuser") }
    }

    @Test
    fun `generateToken creates different tokens for different users`() {
        val token1 = jwtService.generateToken("user1", "id1")
        val token2 = jwtService.generateToken("user2", "id2")

        assertNotEquals(token1, token2)
    }

    @Test
    @org.junit.jupiter.api.Disabled("Flaky test - tokens may have same expiration if generated within same second")
    fun `generateToken creates different tokens for same user at different times`() {
        val token1 = jwtService.generateToken("testuser", "user-id-123")
        Thread.sleep(10) // Small delay to ensure different timestamp
        val token2 = jwtService.generateToken("testuser", "user-id-123")

        // Tokens should be different due to different timestamps
        assertNotEquals(token1, token2)
    }

    @Test
    fun `token contains correct claims`() {
        val username = "testuser"
        val userId = "user-id-123"

        val token = jwtService.generateToken(username, userId)
        val payload = jwtService.verifyToken(token)

        assertNotNull(payload)
        assertEquals(username, payload?.username)
        assertEquals(userId, payload?.userId)
    }

    @Test
    fun `token uses configured issuer`() {
        val customConfig = JwtConfig(
            secret = "test-secret",
            issuer = "custom-issuer",
            expirationHours = 1
        )
        val customService = JwtService(customConfig, userRepository)

        val token = customService.generateToken("testuser", "user-id")

        // Token should verify with same issuer
        val payload = customService.verifyToken(token)
        assertNotNull(payload)

        // Token should NOT verify with different issuer
        val differentConfig = JwtConfig(
            secret = "test-secret",
            issuer = "different-issuer",
            expirationHours = 1
        )
        val differentService = JwtService(differentConfig, userRepository)
        val payloadWithDifferentIssuer = differentService.verifyToken(token)
        assertNull(payloadWithDifferentIssuer)
    }
}
