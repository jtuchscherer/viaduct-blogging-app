package org.tuchscherer.web

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.JwtService
import org.tuchscherer.auth.UserAlreadyExistsException
import org.tuchscherer.database.repositories.UserRepository

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
    @param:JsonProperty("is_admin")
    val isAdmin: Boolean,
    @param:JsonProperty("created_at")
    val createdAt: String
)

fun Route.authRoutes(
    jwtService: JwtService,
    authService: AuthenticationService,
    userRepository: UserRepository
) {
    post("/auth/register") {
        try {
            val request = call.receive<RegisterRequest>()
            val user = authService.createUser(request.username, request.email, request.name, request.password)
            val token = jwtService.generateToken(user.username, user.id.value.toString())
            call.respond(HttpStatusCode.Created, AuthResponse(token = token, user = user.toUserResponse()))
        } catch (e: UserAlreadyExistsException) {
            call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        }
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val user = authService.authenticateUser(request.username, request.password)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
        val token = jwtService.generateToken(user.username, user.id.value.toString())
        call.respond(HttpStatusCode.OK, AuthResponse(token = token, user = user.toUserResponse()))
    }

    authenticate("auth-jwt") {
        get("/auth/me") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
            val username = principal.payload.getClaim("username").asString()
            val user = userRepository.findByUsername(username)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
            call.respond(HttpStatusCode.OK, user.toUserResponse())
        }
    }
}

private fun org.tuchscherer.database.User.toUserResponse() = UserResponse(
    id = id.value.toString(),
    username = username,
    email = email,
    name = name,
    isAdmin = isAdmin,
    createdAt = createdAt.toString()
)
