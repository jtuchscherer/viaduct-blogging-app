package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.example.config.JwtConfig
import com.example.database.User
import com.example.database.repositories.UserRepository
import java.util.*

/**
 * Service for JWT token generation and validation.
 * Uses dependency injection for configuration and user repository.
 */
class JwtService(
    private val config: JwtConfig,
    private val userRepository: UserRepository
) {
    private val jwtAlgorithm = Algorithm.HMAC256(config.secret)

    /**
     * Generate a JWT token for a user.
     */
    fun generateToken(username: String, userId: String): String {
        return JWT.create()
            .withIssuer(config.issuer)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + config.expirationHours * 60 * 60 * 1000))
            .sign(jwtAlgorithm)
    }

    /**
     * Verify a JWT token and extract payload.
     */
    fun verifyToken(token: String): TokenPayload? {
        return try {
            val verifier = JWT.require(jwtAlgorithm)
                .withIssuer(config.issuer)
                .build()

            val decodedJWT = verifier.verify(token)
            TokenPayload(
                username = decodedJWT.getClaim("username").asString(),
                userId = decodedJWT.getClaim("userId").asString()
            )
        } catch (e: JWTVerificationException) {
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