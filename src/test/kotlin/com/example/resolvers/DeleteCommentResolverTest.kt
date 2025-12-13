package com.example.resolvers

import com.example.auth.RequestContext
import com.example.database.Comment
import com.example.database.Posts
import com.example.database.User
import com.example.database.repositories.CommentRepository
import com.example.viadapp.resolvers.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

/**
 * Comprehensive unit tests for DeleteCommentResolver.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class DeleteCommentResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>(relaxed = true)

        // Setup mock user
        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        // Setup mock comment
        mockComment = mockk<Comment>(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())
        every { mockComment.content } returns "Test comment content"
        every { mockComment.authorId } returns EntityID(userId, mockk())
        every { mockComment.postId } returns EntityID(postId, Posts)
        every { mockComment.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        // Setup Koin for dependency injection in resolvers
        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { commentRepository }
            })
        }
    }

    @Test
    fun `DeleteCommentResolver deletes comment successfully`() = runBlocking {
        val resolver = DeleteCommentResolver(commentRepository)
        val args = Mutation_DeleteComment_Arguments.Builder(context)
            .id(commentId.toString())
            .build()

        every { commentRepository.findById(commentId) } returns mockComment
        every { mockComment.authorId } returns mockUser.id
        every { commentRepository.delete(commentId) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertTrue(result)
        verify { commentRepository.findById(commentId) }
        verify { commentRepository.delete(commentId) }
    }

    @Test
    fun `DeleteCommentResolver throws exception when not authenticated`() = runBlocking {
        val resolver = DeleteCommentResolver(commentRepository)
        val args = Mutation_DeleteComment_Arguments.Builder(context)
            .id(commentId.toString())
            .build()

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext()
            )
        }

        verify(exactly = 0) { commentRepository.findById(any()) }
        verify(exactly = 0) { commentRepository.delete(any()) }
    }

    @Test
    fun `DeleteCommentResolver throws exception when comment not found`() = runBlocking {
        val resolver = DeleteCommentResolver(commentRepository)
        val args = Mutation_DeleteComment_Arguments.Builder(context)
            .id(commentId.toString())
            .build()

        every { commentRepository.findById(commentId) } returns null

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }

        verify { commentRepository.findById(commentId) }
        verify(exactly = 0) { commentRepository.delete(any()) }
    }

    @Test
    fun `DeleteCommentResolver throws exception when user is not author`() = runBlocking {
        val resolver = DeleteCommentResolver(commentRepository)
        val differentUserId = UUID.randomUUID()
        val args = Mutation_DeleteComment_Arguments.Builder(context)
            .id(commentId.toString())
            .build()

        every { commentRepository.findById(commentId) } returns mockComment
        every { mockComment.authorId } returns EntityID(differentUserId, mockk())

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }

        verify { commentRepository.findById(commentId) }
        verify(exactly = 0) { commentRepository.delete(any()) }
    }
}
