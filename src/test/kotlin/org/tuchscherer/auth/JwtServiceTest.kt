package org.tuchscherer.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.tuchscherer.config.JwtConfig
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
    }

    @Test
    fun `getUserFromToken returns null when token is invalid`() {
        val user = jwtService.getUserFromToken("invalid.token.here")

        assertNull(user)
    }

    @Test
    fun `getUserFromToken returns null when user not found`() {
        every { userRepository.findByUsername("testuser") } returns null

        val token = jwtService.generateToken("testuser", "user-id-123")
        val user = jwtService.getUserFromToken(token)

        assertNull(user)
    }

    @Test
    fun `generateToken creates different tokens for different users`() {
        val token1 = jwtService.generateToken("user1", "id1")
        val token2 = jwtService.generateToken("user2", "id2")

        assertNotEquals(token1, token2)
    }

    @ParameterizedTest
    @ValueSource(longs = [1, 24])
    fun `generateToken expires after the configured lifetime`(expirationHours: Long) {
        val issuedAt = Instant.parse("2026-10-07T12:34:56.789Z")
        val service = JwtService(
            jwtConfig.copy(expirationHours = expirationHours),
            userRepository,
            Clock.fixed(issuedAt, ZoneOffset.UTC)
        )

        val token = service.generateToken("testuser", "user-id-123")

        val expectedExpiration = issuedAt.plus(expirationHours, ChronoUnit.HOURS)
            .truncatedTo(ChronoUnit.SECONDS)
        assertEquals(expectedExpiration, JWT.decode(token).expiresAt?.toInstant())
    }

    @Test
    fun `a later login receives a later expiration`() {
        val clock = Clock.fixed(Instant.parse("2026-10-07T12:34:56Z"), ZoneOffset.UTC)
        val service = JwtService(jwtConfig, userRepository, clock)
        val laterService = JwtService(jwtConfig, userRepository, Clock.offset(clock, Duration.ofSeconds(1)))

        val firstToken = JWT.decode(service.generateToken("testuser", "user-id-123"))
        val laterToken = JWT.decode(laterService.generateToken("testuser", "user-id-123"))

        assertEquals(firstToken.expiresAt.toInstant().plusSeconds(1), laterToken.expiresAt.toInstant())
    }

    @Test
    fun `verifyToken accepts a token one second before expiration`() {
        val now = Instant.parse("2026-10-07T12:34:56Z")
        val service = JwtService(jwtConfig, userRepository, Clock.fixed(now, ZoneOffset.UTC))

        val payload = service.verifyToken(tokenExpiringAt(now.plusSeconds(1)))

        assertEquals(TokenPayload("testuser", "user-id-123"), payload)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `verifyToken rejects a correctly signed token at or after expiration`(secondsAfterExpiration: Long) {
        val expiresAt = Instant.parse("2026-10-07T12:34:56Z")
        val clock = Clock.fixed(expiresAt.plusSeconds(secondsAfterExpiration), ZoneOffset.UTC)
        val service = JwtService(jwtConfig, userRepository, clock)

        assertNull(service.verifyToken(tokenExpiringAt(expiresAt)))
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

    private fun tokenExpiringAt(expiresAt: Instant): String = JWT.create()
        .withIssuer(jwtConfig.issuer)
        .withClaim("username", "testuser")
        .withClaim("userId", "user-id-123")
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(jwtConfig.secret))
}
