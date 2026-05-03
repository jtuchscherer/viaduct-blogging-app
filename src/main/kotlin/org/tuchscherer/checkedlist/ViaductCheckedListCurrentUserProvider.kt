package org.tuchscherer.checkedlist

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import java.util.UUID

/**
 * Root-project implementation of [CheckedListCurrentUserProvider].
 *
 * Casts the opaque Viaduct request context to [RequestContext] to extract the
 * authenticated user's UUID. Throws [AuthenticationException] if the user is not
 * authenticated — the checkedlist module cannot import root-project auth types, so this
 * bridge lives in the root project and is registered via Koin.
 */
class ViaductCheckedListCurrentUserProvider : CheckedListCurrentUserProvider {

    override fun getCurrentUserId(requestContext: Any?): UUID {
        val rc = requestContext as? RequestContext
            ?: throw AuthenticationException("Authentication required")
        val user = rc.user
            ?: throw AuthenticationException("Authentication required")
        return user.id.value
    }
}
