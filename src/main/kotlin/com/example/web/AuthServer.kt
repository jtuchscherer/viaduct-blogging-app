package com.example.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.AuthenticationService
import com.example.auth.JwtService
import com.example.config.JwtConfig
import com.example.config.ServerConfig
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction

data class RegisterRequest(
    val username: String,
    val email: String,
    val name: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: UserResponse
)

data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val name: String,
    @param:JsonProperty("created_at")
    val createdAt: String
)

/**
 * Authentication server using Ktor with JWT support.
 * Now a class instead of object for better testability and dependency injection.
 */
class AuthServer(
    private val authService: AuthenticationService,
    private val jwtService: JwtService,
    private val jwtConfig: JwtConfig,
    private val serverConfig: ServerConfig
) {
    private val jwtAlgorithm by lazy { Algorithm.HMAC256(jwtConfig.secret) }

    fun start() {
        embeddedServer(Netty, port = serverConfig.authPort) {
            install(ContentNegotiation) {
                jackson()
            }

            install(CORS) {
                allowHost("localhost:5173")
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowCredentials = true
            }

            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(jwtAlgorithm)
                            .withIssuer(jwtConfig.issuer)
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.getClaim("username").asString() != null) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }

            routing {
                post("/auth/register") {
                    try {
                        val request = call.receive<RegisterRequest>()

                        val user = transaction {
                            // Check if username already exists
                            val existingUser = com.example.database.User.find {
                                com.example.database.Users.username eq request.username
                            }.firstOrNull()

                            if (existingUser != null) {
                                throw RuntimeException("Username already exists")
                            }

                            authService.createUser(
                                request.username,
                                request.email,
                                request.name,
                                request.password
                            )
                        }

                        val token = jwtService.generateToken(user.username, user.id.value.toString())
                        val response = AuthResponse(
                            token = token,
                            user = UserResponse(
                                id = user.id.value.toString(),
                                username = user.username,
                                email = user.email,
                                name = user.name,
                                createdAt = user.createdAt.toString()
                            )
                        )

                        call.respond(HttpStatusCode.Created, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    }
                }

                post("/auth/login") {
                    try {
                        val request = call.receive<LoginRequest>()

                        val user = transaction {
                            authService.authenticateUser(request.username, request.password)
                        } ?: throw RuntimeException("Invalid credentials")

                        val token = jwtService.generateToken(user.username, user.id.value.toString())
                        val response = AuthResponse(
                            token = token,
                            user = UserResponse(
                                id = user.id.value.toString(),
                                username = user.username,
                                email = user.email,
                                name = user.name,
                                createdAt = user.createdAt.toString()
                            )
                        )

                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
                    }
                }

                authenticate("auth-jwt") {
                    get("/auth/me") {
                        val principal = call.principal<JWTPrincipal>()
                        val username = principal!!.payload.getClaim("username").asString()

                        val user = transaction {
                            com.example.database.User.find {
                                com.example.database.Users.username eq username
                            }.first()
                        }

                        val userResponse = UserResponse(
                            id = user.id.value.toString(),
                            username = user.username,
                            email = user.email,
                            name = user.name,
                            createdAt = user.createdAt.toString()
                        )

                        call.respond(HttpStatusCode.OK, userResponse)
                    }
                }
            }
        }.start(wait = false)
    }
}