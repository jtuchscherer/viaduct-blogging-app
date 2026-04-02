package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.CommentAuthorResolver
import org.tuchscherer.viadapp.resolvers.CommentPostResolver
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
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.Query
import viaduct.api.grts.User as ViaductUser
import viaduct.api.testing.FieldResolverTester
import viaduct.api.types.Arguments.NoArguments
import java.time.LocalDateTime
import java.util.UUID

class CommentFieldResolversTest {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private val authorTester = FieldResolverTester.create<ViaductComment, Query, NoArguments, ViaductUser>(ViaductTestConfig.testerConfig)
    private val postTester = FieldResolverTester.create<ViaductComment, Query, NoArguments, ViaductPost>(ViaductTestConfig.testerConfig)

    private fun commentObj(id: UUID = commentId) = ViaductComment.Builder(authorTester.context)
        .id(id.toString())
        .content("Test comment")
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk(relaxed = true)

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
            modules(module { single<CommentRepository> { commentRepository } })
        }
    }

    // ── CommentAuthorResolver ───────────────────────────────────────────

    @Test
    fun `CommentAuthorResolver returns author for comment`() = runBlocking {
        val resolver = CommentAuthorResolver()
        every { commentRepository.getAuthorForComment(commentId) } returns mockUser

        val result = authorTester.test(resolver) {
            objectValue = commentObj()
            arguments = NoArguments
        }

        assertEquals(userId.toString(), result.getId())
        assertEquals("testuser", result.getUsername())
        assertEquals("test@example.com", result.getEmail())
        verify { commentRepository.getAuthorForComment(commentId) }
    }

    @Test
    fun `CommentAuthorResolver throws when comment not found`() {
        val resolver = CommentAuthorResolver()
        every { commentRepository.getAuthorForComment(commentId) } returns null

        assertThrows<Exception> {
            runBlocking {
                authorTester.test(resolver) {
                    objectValue = commentObj()
                    arguments = NoArguments
                }
            }
        }
        verify { commentRepository.getAuthorForComment(commentId) }
    }

    // ── CommentPostResolver ─────────────────────────────────────────────

    @Test
    fun `CommentPostResolver returns post for comment`() = runBlocking {
        val resolver = CommentPostResolver()
        every { commentRepository.getPostForComment(commentId) } returns mockPost

        val result = postTester.test(resolver) {
            objectValue = commentObj()
            arguments = NoArguments
        }

        assertEquals(postId.toString(), result.getId())
        assertEquals("Test Post", result.getTitle())
        assertEquals("Test content", result.getContent())
        verify { commentRepository.getPostForComment(commentId) }
    }

    @Test
    fun `CommentPostResolver throws when comment not found`() {
        val resolver = CommentPostResolver()
        every { commentRepository.getPostForComment(commentId) } returns null

        assertThrows<Exception> {
            runBlocking {
                postTester.test(resolver) {
                    objectValue = commentObj()
                    arguments = NoArguments
                }
            }
        }
        verify { commentRepository.getPostForComment(commentId) }
    }
}
