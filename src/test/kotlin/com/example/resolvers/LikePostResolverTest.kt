package com.example.resolvers

import com.example.auth.RequestContext
import com.example.database.Like
import com.example.database.Post
import com.example.database.Posts
import com.example.database.User
import com.example.database.repositories.LikeRepository
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
 * Comprehensive unit tests for LikePostMutationResolver.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class LikePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private lateinit var mockLike: Like
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val likeId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk<LikeRepository>(relaxed = true)
        postRepository = mockk<PostRepository>(relaxed = true)

        // Setup mock user
        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        // Setup mock post
        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, Posts)
        every { mockPost.title } returns "Test Post"

        // Setup mock like
        mockLike = mockk<Like>(relaxed = true)
        every { mockLike.id } returns EntityID(likeId, mockk())
        every { mockLike.userId } returns EntityID(userId, mockk())
        every { mockLike.postId } returns EntityID(postId, Posts)
        every { mockLike.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        // Setup Koin for dependency injection in resolvers
        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { likeRepository }
                single { postRepository }
            })
        }
    }

    @Test
    fun `LikePostMutationResolver creates new like successfully`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) } returns null
        every {
            likeRepository.create(
                postId = mockPost.id,
                userId = mockUser.id,
                createdAt = any()
            )
        } returns mockLike

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(likeId.toString(), result.getId())
        verify { postRepository.findById(postId) }
        verify { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) }
        verify { likeRepository.create(any(), any(), any()) }
    }

    @Test
    fun `LikePostMutationResolver returns existing like when already liked`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) } returns mockLike

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(likeId.toString(), result.getId())
        verify { postRepository.findById(postId) }
        verify { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) }
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }

    @Test
    fun `LikePostMutationResolver throws exception when not authenticated`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        assertThrows<RuntimeException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext()
            )
        }

        verify(exactly = 0) { postRepository.findById(any()) }
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }

    @Test
    fun `LikePostMutationResolver throws exception when post not found`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context)
            .postId(postId.toString())
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
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }
}
