package com.example.auth

import com.example.database.User

data class GraphQLRequestContext(
    val user: User? = null,
    val authToken: String? = null
) {
    val isAuthenticated: Boolean = user != null

    fun requireAuth(): User {
        return user ?: throw AuthenticationException("Authentication required")
    }
}

class AuthenticationException(message: String) : Exception(message)