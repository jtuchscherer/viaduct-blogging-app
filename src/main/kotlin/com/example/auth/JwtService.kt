package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.example.database.User
import com.example.database.Users
import org.jetbrains.exposed.sql.transactions.transaction

class JwtService {
    private val jwtSecret = "your-secret-key" // Same as AuthServer
    private val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)

    fun verifyToken(token: String): TokenPayload? {
        return try {
            val verifier = JWT.require(jwtAlgorithm)
                .withIssuer("blog-app")
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

    fun getUserFromToken(token: String): User? {
        val payload = verifyToken(token) ?: return null
        return transaction {
            User.find { Users.username eq payload.username }.firstOrNull()
        }
    }
}

data class TokenPayload(
    val username: String,
    val userId: String
)