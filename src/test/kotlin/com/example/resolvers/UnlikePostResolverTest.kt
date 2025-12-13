package com.example.resolvers

import com.example.auth.RequestContext
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
import java.util.*

/**
 * Comprehensive unit tests for UnlikePostResolver.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class UnlikePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

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
    fun `UnlikePostResolver unlikes post successfully`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.deleteByPostAndUser(mockPost.id, mockUser.id) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertTrue(result)
        verify { postRepository.findById(postId) }
        verify { likeRepository.deleteByPostAndUser(mockPost.id, mockUser.id) }
    }

    @Test
    fun `UnlikePostResolver returns false when like does not exist`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.deleteByPostAndUser(mockPost.id, mockUser.id) } returns false

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertFalse(result)
        verify { postRepository.findById(postId) }
        verify { likeRepository.deleteByPostAndUser(mockPost.id, mockUser.id) }
    }

    @Test
    fun `UnlikePostResolver throws exception when not authenticated`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
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
        verify(exactly = 0) { likeRepository.deleteByPostAndUser(any(), any()) }
    }

    @Test
    fun `UnlikePostResolver throws exception when post not found`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
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
        verify(exactly = 0) { likeRepository.deleteByPostAndUser(any(), any()) }
    }
}
