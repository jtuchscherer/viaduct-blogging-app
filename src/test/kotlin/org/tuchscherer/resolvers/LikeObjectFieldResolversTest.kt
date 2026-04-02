package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.LikePostResolver
import org.tuchscherer.viadapp.resolvers.LikeUserResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.Query
import viaduct.api.grts.User as ViaductUser
import viaduct.api.testing.FieldResolverTester
import viaduct.api.types.Arguments.NoArguments
import java.time.LocalDateTime
import java.util.UUID

class LikeObjectFieldResolversTest {

    private lateinit var likeRepository: LikeRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val likeId = UUID.randomUUID()

    private val userTester = FieldResolverTester.create<ViaductLike, Query, NoArguments, ViaductUser>(ViaductTestConfig.testerConfig)
    private val postTester = FieldResolverTester.create<ViaductLike, Query, NoArguments, ViaductPost>(ViaductTestConfig.testerConfig)

    private fun likeObj(id: UUID = likeId) = ViaductLike.Builder(userTester.context)
        .id(id.toString())
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk(relaxed = true)

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.name } returns "Test User"
        every { mockUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module { single<LikeRepository> { likeRepository } })
        }
    }

    // ── LikeUserResolver ────────────────────────────────────────────────

    @Test
    fun `LikeUserResolver returns user for like`() = runBlocking {
        val resolver = LikeUserResolver()
        every { likeRepository.getUserForLike(likeId) } returns mockUser

        val result = userTester.test(resolver) {
            objectValue = likeObj()
            arguments = NoArguments
        }

        assertEquals(userId.toString(), result.getId())
        assertEquals("testuser", result.getUsername())
        assertEquals("test@example.com", result.getEmail())
        verify { likeRepository.getUserForLike(likeId) }
    }

    @Test
    fun `LikeUserResolver throws when like not found`() {
        val resolver = LikeUserResolver()
        every { likeRepository.getUserForLike(likeId) } returns null

        assertThrows<Exception> {
            runBlocking {
                userTester.test(resolver) {
                    objectValue = likeObj()
                    arguments = NoArguments
                }
            }
        }
        verify { likeRepository.getUserForLike(likeId) }
    }

    // ── LikePostResolver ────────────────────────────────────────────────

    @Test
    fun `LikePostResolver returns post for like`() = runBlocking {
        val resolver = LikePostResolver()
        every { likeRepository.getPostForLike(likeId) } returns mockPost

        val result = postTester.test(resolver) {
            objectValue = likeObj()
            arguments = NoArguments
        }

        assertEquals(postId.toString(), result.getId())
        assertEquals("Test Post", result.getTitle())
        assertEquals("Test content", result.getContent())
        verify { likeRepository.getPostForLike(likeId) }
    }

    @Test
    fun `LikePostResolver throws when like not found`() {
        val resolver = LikePostResolver()
        every { likeRepository.getPostForLike(likeId) } returns null

        assertThrows<Exception> {
            runBlocking {
                postTester.test(resolver) {
                    objectValue = likeObj()
                    arguments = NoArguments
                }
            }
        }
        verify { likeRepository.getPostForLike(likeId) }
    }
}
