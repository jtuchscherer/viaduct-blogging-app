package com.example.resolvers

import com.example.database.Post
import com.example.database.User
import com.example.database.repositories.CommentRepository
import com.example.viadapp.resolvers.CommentAuthorResolver
import com.example.viadapp.resolvers.CommentPostResolver
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

class CommentFieldResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    private fun commentObj(id: UUID = commentId) = Comment.Builder(context)
        .id(id.toString())
        .content("Test comment")
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>(relaxed = true)

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
                single<CommentRepository> { commentRepository }
            })
        }
    }

    // ── CommentAuthorResolver ───────────────────────────────────────────

    @Test
    fun `CommentAuthorResolver returns author for comment`() = runBlocking {
        val resolver = CommentAuthorResolver()
        every { commentRepository.getAuthorForComment(commentId) } returns mockUser

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = commentObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(userId.toString(), result.getId())
        assertEquals("testuser", result.getUsername())
        assertEquals("test@example.com", result.getEmail())
        verify { commentRepository.getAuthorForComment(commentId) }
    }

    @Test
    fun `CommentAuthorResolver throws when comment not found`() = runBlocking {
        val resolver = CommentAuthorResolver()
        every { commentRepository.getAuthorForComment(commentId) } returns null

        assertThrows<RuntimeException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = commentObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
        verify { commentRepository.getAuthorForComment(commentId) }
    }

    // ── CommentPostResolver ─────────────────────────────────────────────

    @Test
    fun `CommentPostResolver returns post for comment`() = runBlocking {
        val resolver = CommentPostResolver()
        every { commentRepository.getPostForComment(commentId) } returns mockPost

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = commentObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(postId.toString(), result.getId())
        assertEquals("Test Post", result.getTitle())
        assertEquals("Test content", result.getContent())
        verify { commentRepository.getPostForComment(commentId) }
    }

    @Test
    fun `CommentPostResolver throws when comment not found`() = runBlocking {
        val resolver = CommentPostResolver()
        every { commentRepository.getPostForComment(commentId) } returns null

        assertThrows<RuntimeException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = commentObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
        verify { commentRepository.getPostForComment(commentId) }
    }
}
