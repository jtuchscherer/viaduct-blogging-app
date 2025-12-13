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
 * Comprehensive unit tests for UpdatePostResolver.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class UpdatePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

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

    @Test
    fun `UpdatePostResolver updates post successfully`() = runBlocking {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context)
            .id(postId.toString())
            .title("Updated Title")
            .content("Updated content")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(context)
            .input(input)
            .build()

        // Mock for authorization check
        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns mockUser.id

        // Mock for the actual update - return an updated mock post
        val updatedPost = mockk<Post>(relaxed = true)
        every { updatedPost.id } returns EntityID(postId, mockk())
        every { updatedPost.title } returns "Updated Title"
        every { updatedPost.content } returns "Updated content"
        every { updatedPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { updatedPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 12, 0)
        every { postRepository.updateById(postId, "Updated Title", "Updated content") } returns updatedPost

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals("Updated Title", result.getTitle())
        assertEquals("Updated content", result.getContent())
        verify { postRepository.findById(postId) }
        verify { postRepository.updateById(postId, "Updated Title", "Updated content") }
    }

    @Test
    fun `UpdatePostResolver throws exception when post not found`() = runBlocking {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context)
            .id(postId.toString())
            .title("Updated Title")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(context)
            .input(input)
            .build()

        every { postRepository.findById(postId) } returns null

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }

        verify { postRepository.findById(postId) }
    }

    @Test
    fun `UpdatePostResolver throws exception when user is not author`() = runBlocking {
        val resolver = UpdatePostResolver(postRepository)
        val differentUserId = UUID.randomUUID()
        val input = UpdatePostInput.Builder(context)
            .id(postId.toString())
            .title("Updated Title")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(context)
            .input(input)
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns EntityID(differentUserId, mockk())

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }

        verify { postRepository.findById(postId) }
    }
}
