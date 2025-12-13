package com.example.resolvers

import com.example.database.Comment
import com.example.database.Posts
import com.example.database.repositories.CommentRepository
import com.example.viadapp.resolvers.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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
 * Comprehensive unit tests for PostCommentsResolver.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class PostCommentsResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>(relaxed = true)

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
    fun `PostCommentsResolver returns comments for post`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { commentRepository.findByPostId(any<EntityID<UUID>>()) } returns listOf(mockComment)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getId())
        assertEquals("Test comment content", result[0].getContent())
        verify { commentRepository.findByPostId(any<EntityID<UUID>>()) }
    }

    @Test
    fun `PostCommentsResolver returns empty list when no comments exist`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { commentRepository.findByPostId(any<EntityID<UUID>>()) } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(0, result.size)
        verify { commentRepository.findByPostId(any<EntityID<UUID>>()) }
    }
}
