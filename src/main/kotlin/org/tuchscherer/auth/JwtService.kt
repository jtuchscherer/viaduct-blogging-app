package org.tuchscherer.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import org.tuchscherer.config.JwtConfig
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.UserRepository
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Service for JWT token generation and validation.
 * Uses dependency injection for configuration and user repository.
 */
class JwtService(
    private val config: JwtConfig,
    private val userRepository: UserRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private val jwtAlgorithm = Algorithm.HMAC256(config.secret)
    // Auth0 exposes clock injection on its concrete verification builder.
    private val jwtVerifier = (JWT.require(jwtAlgorithm)
        .withIssuer(config.issuer) as JWTVerifier.BaseVerification).build(clock)

    /**
     * Generate a JWT token for a user.
     */
    fun generateToken(username: String, userId: String): String {
        return JWT.create()
            .withIssuer(config.issuer)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withExpiresAt(clock.instant().plus(config.expirationHours, ChronoUnit.HOURS))
            .sign(jwtAlgorithm)
    }

    /**
     * Verify a JWT token and extract payload.
     */
    fun verifyToken(token: String): TokenPayload? {
        return try {
            val decodedJWT = jwtVerifier.verify(token)
            TokenPayload(
                username = decodedJWT.getClaim("username").asString(),
                userId = decodedJWT.getClaim("userId").asString()
            )
        } catch (_: JWTVerificationException) {
            null
        }
    }

    /**
     * Get user from JWT token.
     * Returns null if token is invalid or user not found.
     */
    fun getUserFromToken(token: String): User? {
        val payload = verifyToken(token) ?: return null
        return userRepository.findByUsername(payload.username)
    }
}

data class TokenPayload(
    val username: String,
    val userId: String
)
