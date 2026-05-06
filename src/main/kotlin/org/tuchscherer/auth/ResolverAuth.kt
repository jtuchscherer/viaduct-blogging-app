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

/**
 * Returns the authenticated user if one is present, or null for anonymous requests.
 * Use this for fields that are readable without authentication but may show personalised
 * data (e.g. "is this post liked by me?").
 */
fun optionalAuth(requestContext: Any?): User? =
    (requestContext as? RequestContext)?.user

/**
 * Extracts and validates an admin user from a resolver context's requestContext.
 * Throws [AuthenticationException] if not authenticated, [AuthorizationException] if not admin.
 */
fun requireAdmin(requestContext: Any?): User {
    val user = requireAuth(requestContext)
    if (!user.isAdmin) {
        throw AuthorizationException("Admin access required")
    }
    return user
}
