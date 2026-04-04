package org.tuchscherer.web

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.JwtService

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

fun Route.authRoutes(jwtService: JwtService, authService: AuthenticationService) {
    post("/auth/register") {
        try {
            val request = call.receive<RegisterRequest>()

            val user = authService.createUser(
                request.username,
                request.email,
                request.name,
                request.password
            )

            val token = jwtService.generateToken(user.username, user.id.value.toString())
            call.respond(HttpStatusCode.Created, AuthResponse(
                token = token,
                user = UserResponse(
                    id = user.id.value.toString(),
                    username = user.username,
                    email = user.email,
                    name = user.name,
                    createdAt = user.createdAt.toString()
                )
            ))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        }
    }

    post("/auth/login") {
        try {
            val request = call.receive<LoginRequest>()

            val user = transaction {
                authService.authenticateUser(request.username, request.password)
            } ?: throw Exception("Invalid credentials")

            val token = jwtService.generateToken(user.username, user.id.value.toString())
            call.respond(HttpStatusCode.OK, AuthResponse(
                token = token,
                user = UserResponse(
                    id = user.id.value.toString(),
                    username = user.username,
                    email = user.email,
                    name = user.name,
                    createdAt = user.createdAt.toString()
                )
            ))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
        }
    }

    authenticate("auth-jwt") {
        get("/auth/me") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
            val username = principal.payload.getClaim("username").asString()

            val user = transaction {
                org.tuchscherer.database.User.find {
                    org.tuchscherer.database.Users.username eq username
                }.firstOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

            call.respond(HttpStatusCode.OK, UserResponse(
                id = user.id.value.toString(),
                username = user.username,
                email = user.email,
                name = user.name,
                createdAt = user.createdAt.toString()
            ))
        }
    }
}
