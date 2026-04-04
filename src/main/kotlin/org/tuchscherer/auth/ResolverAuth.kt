package org.tuchscherer.auth

import org.tuchscherer.database.User

/**
 * Extracts and validates the authenticated user from a resolver context's requestContext.
 * Throws [AuthenticationException] if the context is missing or no user is present.
 */
fun requireAuth(requestContext: Any?): User {
    val rc = requestContext as? RequestContext
        ?: throw AuthenticationException("Authentication required. Please provide a valid JWT token.")
    return rc.user
        ?: throw AuthenticationException("Authentication required. Please provide a valid JWT token.")
}
