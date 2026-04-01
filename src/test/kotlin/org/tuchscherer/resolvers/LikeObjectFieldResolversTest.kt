package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.LikePostResolver
import org.tuchscherer.viadapp.resolvers.LikeUserResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.types.Arguments.NoArguments
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class LikeObjectFieldResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val likeId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    private fun likeObj(id: UUID = likeId) = Like.Builder(context)
        .id(id.toString())
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk<LikeRepository>(relaxed = true)

        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.name } returns "Test User"
        every { mockUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<LikeRepository> { likeRepository }
            })
        }
    }

    // ── LikeUserResolver ────────────────────────────────────────────────

    @Test
    fun `LikeUserResolver returns user for like`() = runBlocking {
        val resolver = LikeUserResolver()
        every { likeRepository.getUserForLike(likeId) } returns mockUser

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = likeObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(userId.toString(), result.getId())
        assertEquals("testuser", result.getUsername())
        assertEquals("test@example.com", result.getEmail())
        verify { likeRepository.getUserForLike(likeId) }
    }

    @Test
    fun `LikeUserResolver throws when like not found`() = runBlocking {
        val resolver = LikeUserResolver()
        every { likeRepository.getUserForLike(likeId) } returns null

        assertThrows<RuntimeException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = likeObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
        verify { likeRepository.getUserForLike(likeId) }
    }

    // ── LikePostResolver ────────────────────────────────────────────────

    @Test
    fun `LikePostResolver returns post for like`() = runBlocking {
        val resolver = LikePostResolver()
        every { likeRepository.getPostForLike(likeId) } returns mockPost

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = likeObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(postId.toString(), result.getId())
        assertEquals("Test Post", result.getTitle())
        assertEquals("Test content", result.getContent())
        verify { likeRepository.getPostForLike(likeId) }
    }

    @Test
    fun `LikePostResolver throws when like not found`() = runBlocking {
        val resolver = LikePostResolver()
        every { likeRepository.getPostForLike(likeId) } returns null

        assertThrows<RuntimeException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = likeObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
        verify { likeRepository.getPostForLike(likeId) }
    }
}
