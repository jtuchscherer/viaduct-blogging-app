package org.tuchscherer.checkedlist

import io.mockk.every
import io.mockk.mockk
import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.tuchscherer.database.Users
import java.util.UUID

/**
 * Unit tests for [ViaductCheckedListCurrentUserProvider].
 *
 * This bridge adapts the opaque Viaduct request-context to the checkedlist
 * module's [CheckedListCurrentUserProvider] port.  Getting the auth behaviour
 * wrong here silently opens every authenticated checkedlist mutation.
 */
class ViaductCheckedListCurrentUserProviderTest {

    private val provider = ViaductCheckedListCurrentUserProvider()

    private fun mockUser(id: UUID): User {
        val user = mockk<User>()
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns id
        every { user.id } returns entityId
        return user
    }

    // ── success path ──────────────────────────────────────────────────────────

    @Test
    fun `getCurrentUserId returns the user UUID from a valid RequestContext`() {
        val userId = UUID.randomUUID()
        val rc = mockk<RequestContext>()
        every { rc.user } returns mockUser(userId)

        val result = provider.getCurrentUserId(rc)
        assertEquals(userId, result)
    }

    // ── failure paths ─────────────────────────────────────────────────────────

    @Test
    fun `getCurrentUserId throws AuthenticationException when context is null`() {
        assertThrows<AuthenticationException> { provider.getCurrentUserId(null) }
    }

    @Test
    fun `getCurrentUserId throws AuthenticationException when context is not a RequestContext`() {
        assertThrows<AuthenticationException> { provider.getCurrentUserId("unexpected-type") }
    }

    @Test
    fun `getCurrentUserId throws AuthenticationException when user is null`() {
        val rc = mockk<RequestContext>()
        every { rc.user } returns null

        assertThrows<AuthenticationException> { provider.getCurrentUserId(rc) }
    }
}
