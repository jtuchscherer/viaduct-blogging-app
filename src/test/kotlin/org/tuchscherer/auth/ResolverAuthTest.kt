package org.tuchscherer.auth

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [requireAuth] and [requireAdmin].
 *
 * These helpers are called by nearly every authenticated resolver, so a regression
 * here (e.g. accidentally returning null instead of throwing) would silently expose
 * protected operations.  No database or Koin context is needed — pure logic tests.
 */
class ResolverAuthTest {

    private fun mockUser(isAdmin: Boolean = false): org.tuchscherer.database.User {
        val user = mockk<org.tuchscherer.database.User>()
        every { user.isAdmin } returns isAdmin
        return user
    }

    // ── requireAuth ───────────────────────────────────────────────────────────

    @Test
    fun `requireAuth returns the user from a valid RequestContext`() {
        val user = mockUser()
        val rc = mockk<RequestContext>()
        every { rc.user } returns user

        val result = requireAuth(rc)
        assertEquals(user, result)
    }

    @Test
    fun `requireAuth throws AuthenticationException when context is null`() {
        val ex = assertThrows<AuthenticationException> { requireAuth(null) }
        assert(ex.message!!.contains("Authentication required"))
    }

    @Test
    fun `requireAuth throws AuthenticationException when context is an unknown type`() {
        val ex = assertThrows<AuthenticationException> { requireAuth("not-a-context") }
        assert(ex.message!!.contains("Authentication required"))
    }

    @Test
    fun `requireAuth throws AuthenticationException when RequestContext has no user`() {
        val rc = mockk<RequestContext>()
        every { rc.user } returns null

        val ex = assertThrows<AuthenticationException> { requireAuth(rc) }
        assert(ex.message!!.contains("Authentication required"))
    }

    // ── requireAdmin ──────────────────────────────────────────────────────────

    @Test
    fun `requireAdmin returns the user when the user is an admin`() {
        val user = mockUser(isAdmin = true)
        val rc = mockk<RequestContext>()
        every { rc.user } returns user

        val result = requireAdmin(rc)
        assertEquals(user, result)
    }

    @Test
    fun `requireAdmin throws AuthorizationException when the user is not an admin`() {
        val user = mockUser(isAdmin = false)
        val rc = mockk<RequestContext>()
        every { rc.user } returns user

        assertThrows<AuthorizationException> { requireAdmin(rc) }
    }

    @Test
    fun `requireAdmin throws AuthenticationException when context is null`() {
        assertThrows<AuthenticationException> { requireAdmin(null) }
    }

    @Test
    fun `requireAdmin throws AuthenticationException when user is null`() {
        val rc = mockk<RequestContext>()
        every { rc.user } returns null

        assertThrows<AuthenticationException> { requireAdmin(rc) }
    }
}
