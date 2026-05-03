@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.MeResolver
import org.tuchscherer.viadapp.resolvers.UserIsAdminResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.UserResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.Query
import viaduct.api.grts.User as ViaductUser
import viaduct.api.types.Arguments.NoArguments
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class UserResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var userRepository: UserRepository
    private lateinit var mockUser: User
    private val userId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    private fun userObj(id: UUID = userId) = ViaductUser.Builder(context)
        .id(context.globalIDFor(ViaductUser.Reflection, id.toString()))
        .username("testuser")
        .email("test@example.com")
        .name("Test User")
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        userRepository = mockk()

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.name } returns "Test User"
        every { mockUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockUser.isAdmin } returns false

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { userRepository }
            })
        }
    }

    // ── MeResolver ────────────────────────────────────────────────────────────

    @Test
    fun `MeResolver returns authenticated user`() = runBlocking {
        val resolver = MeResolver()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = NoArguments,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(userId.toString(), result!!.getId().internalID)
        assertEquals("testuser", result.getUsername())
        assertEquals("test@example.com", result.getEmail())
        assertEquals("Test User", result.getName())
    }

    @Test
    fun `MeResolver throws AuthenticationException when not authenticated`() = runBlocking {
        val resolver = MeResolver()

        assertThrows<AuthenticationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = queryObj(),
                queryValue = queryObj(),
                arguments = NoArguments,
                requestContext = RequestContext()
            )
        }
    }

    @Test
    fun `MeResolver throws AuthenticationException when requestContext is null`() = runBlocking {
        val resolver = MeResolver()

        assertThrows<AuthenticationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = queryObj(),
                queryValue = queryObj(),
                arguments = NoArguments,
                requestContext = null
            )
        }
    }

    // ── UserIsAdminResolver ───────────────────────────────────────────────────

    private fun batchCtx(id: UUID = userId): UserResolvers.IsAdmin.Context {
        val ctx = mockk<UserResolvers.IsAdmin.Context>(relaxed = true)
        val globalId = this@UserResolversTest.context.globalIDFor(ViaductUser.Reflection, id.toString())
        coEvery { ctx.getObjectValue().getId() } returns globalId
        return ctx
    }

    @Test
    fun `UserIsAdminResolver returns false for regular user`() = runBlocking {
        val resolver = UserIsAdminResolver(userRepository)
        every { mockUser.isAdmin } returns false
        every { userRepository.findByIds(listOf(userId)) } returns mapOf(userId to mockUser)

        val results = resolver.batchResolve(listOf(batchCtx()))

        assertEquals(1, results.size)
        assertFalse(results[0].get())
    }

    @Test
    fun `UserIsAdminResolver returns true for admin user`() = runBlocking {
        val resolver = UserIsAdminResolver(userRepository)
        every { mockUser.isAdmin } returns true
        every { userRepository.findByIds(listOf(userId)) } returns mapOf(userId to mockUser)

        val results = resolver.batchResolve(listOf(batchCtx()))

        assertEquals(1, results.size)
        assertTrue(results[0].get())
    }

    @Test
    fun `UserIsAdminResolver surfaces NotFoundException for missing user instead of defaulting to false`() = runBlocking {
        // A deleted-or-missing user is not the same as a non-admin user;
        // the prior `?: false` path silently misclassified them.
        val resolver = UserIsAdminResolver(userRepository)
        every { userRepository.findByIds(listOf(userId)) } returns emptyMap()

        val results = resolver.batchResolve(listOf(batchCtx()))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        val err = runCatching { results[0].get() }.exceptionOrNull()
        assertTrue(err is org.tuchscherer.auth.NotFoundException, "Expected NotFoundException, got $err")
        assertTrue(err!!.message!!.contains(userId.toString()))
    }

    @Test
    fun `UserIsAdminResolver batches multiple users in single DB call`() = runBlocking {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val user1 = mockk<org.tuchscherer.database.User>(relaxed = true).also {
            every { it.isAdmin } returns false
        }
        val user2 = mockk<org.tuchscherer.database.User>(relaxed = true).also {
            every { it.isAdmin } returns true
        }
        every { userRepository.findByIds(any()) } returns mapOf(id1 to user1, id2 to user2)

        val resolver = UserIsAdminResolver(userRepository)
        val results = resolver.batchResolve(listOf(batchCtx(id1), batchCtx(id2)))

        assertEquals(2, results.size)
        assertFalse(results[0].get())
        assertTrue(results[1].get())
        verify(exactly = 1) { userRepository.findByIds(any()) }
    }
}
