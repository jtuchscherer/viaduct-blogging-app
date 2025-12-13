package com.example.resolvers

import com.example.auth.RequestContext
import com.example.database.Post
import com.example.database.User
import com.example.database.repositories.PostRepository
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
 * Comprehensive unit tests for Post resolvers.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class CreatePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()
    private fun mutationObj() = Mutation.Builder(context).build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>(relaxed = true)

        // Setup mock user
        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        // Setup mock post
        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.authorId } returns EntityID(userId, mockk())
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        // Setup Koin for dependency injection in resolvers
        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { postRepository }
            })
        }
    }

    // ========================================
    // CreatePostResolver Tests
    // ========================================

    @Test
    fun `CreatePostResolver creates post successfully`() = runBlocking {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context)
            .title("New Post")
            .content("New content")
            .build()
        val args = Mutation_CreatePost_Arguments.Builder(context)
            .input(input)
            .build()

        every {
            postRepository.create(
                title = "New Post",
                content = "New content",
                authorId = mockUser.id,
                createdAt = any(),
                updatedAt = any()
            )
        } returns mockPost

        val ctx = RequestContext(user = mockUser)

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = ctx
        )

        assertNotNull(result)
        assertEquals(postId.toString(), result.getId())
        assertEquals("Test Post", result.getTitle())
        verify { postRepository.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `CreatePostResolver throws exception when not authenticated`() = runBlocking {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context)
            .title("New Post")
            .content("New content")
            .build()
        val args = Mutation_CreatePost_Arguments.Builder(context)
            .input(input)
            .build()

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext()
            )
        }

        verify(exactly = 0) { postRepository.create(any(), any(), any(), any(), any()) }
    }
}
