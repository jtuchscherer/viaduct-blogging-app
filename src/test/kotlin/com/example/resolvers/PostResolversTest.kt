package com.example.resolvers

import com.example.auth.RequestContext
import com.example.database.Post
import com.example.database.User
import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.*
import io.ktor.http.cio.Request
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
class PostResolversTest : DefaultAbstractResolverTestBase() {

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

    // ========================================
    // UpdatePostResolver Tests
    // ========================================

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

    // ========================================
    // DeletePostResolver Tests
    // ========================================

    @Test
    fun `DeletePostResolver deletes post successfully`() = runBlocking {
        val resolver = DeletePostResolver(postRepository)
        val args = Mutation_DeletePost_Arguments.Builder(context)
            .id(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns mockUser.id
        every { postRepository.delete(postId) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertTrue(result)
        verify { postRepository.findById(postId) }
        verify { postRepository.delete(postId) }
    }

    @Test
    fun `DeletePostResolver throws exception when user is not author`() = runBlocking {
        val resolver = DeletePostResolver(postRepository)
        val differentUserId = UUID.randomUUID()
        val args = Mutation_DeletePost_Arguments.Builder(context)
            .id(postId.toString())
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
        verify(exactly = 0) { postRepository.delete(any()) }
    }

    // ========================================
    // PostsResolver Tests
    // ========================================

//    @Test
//    fun `PostsResolver returns all posts`() = runBlocking {
//        val resolver = PostsResolver(postRepository)
//        val args = Query_Posts_Arguments.Builder(context).build()
//
//        every { postRepository.findAll() } returns listOf(mockPost)
//
//        val result = runFieldResolver(
//            resolver = resolver,
//            objectValue = queryObj(),
//            queryValue = queryObj(),
//            arguments = args
//        )
//
//        assertEquals(1, result.size)
//        assertEquals(postId.toString(), result[0].getId())
//        assertEquals("Test Post", result[0].getTitle())
//        verify { postRepository.findAll() }
//    }

//    @Test
//    fun `PostsResolver returns empty list when no posts exist`() = runBlocking {
//        val resolver = PostsResolver(postRepository)
//        val args = Query_Posts_Arguments.Builder(context).build()
//
//        every { postRepository.findAll() } returns emptyList()
//
//        val result = runFieldResolver(
//            resolver = resolver,
//            objectValue = queryObj(),
//            queryValue = queryObj(),
//            arguments = args
//        )
//
//        assertEquals(0, result.size)
//        verify { postRepository.findAll() }
//    }

    // ========================================
    // PostResolver Tests
    // ========================================

    @Test
    fun `PostResolver returns post by id`() = runBlocking {
        val resolver = PostResolver(postRepository)
        val args = Query_Post_Arguments.Builder(context)
            .id(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns mockPost

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertNotNull(result)
        assertEquals(postId.toString(), result?.getId())
        assertEquals("Test Post", result?.getTitle())
        verify { postRepository.findById(postId) }
    }

    @Test
    fun `PostResolver returns null when post not found`() = runBlocking {
        val resolver = PostResolver(postRepository)
        val args = Query_Post_Arguments.Builder(context)
            .id(postId.toString())
            .build()

        every { postRepository.findById(postId) } returns null

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertNull(result)
        verify { postRepository.findById(postId) }
    }

    // ========================================
    // MyPostsResolver Tests
    // ========================================

//    @Test
//    fun `MyPostsResolver returns posts for authenticated user`() = runBlocking {
//        val resolver = MyPostsResolver(postRepository)
//        val args = Query_MyPosts_Arguments.Builder(context).build()
//
//        every { postRepository.findByAuthorId(mockUser.id) } returns listOf(mockPost)
//
//        val result = runFieldResolver(
//            resolver = resolver,
//            objectValue = queryObj(),
//            queryValue = queryObj(),
//            arguments = args,
//            requestContext = RequestContext(user = mockUser)
//        )
//
//        assertEquals(1, result.size)
//        assertEquals(postId.toString(), result[0].getId())
//        verify { postRepository.findByAuthorId(mockUser.id) }
//    }

//    @Test
//    fun `MyPostsResolver throws exception when not authenticated`() = runBlocking {
//        val resolver = MyPostsResolver(postRepository)
//        val args = Query_MyPosts_Arguments.Builder(context).build()
//
//        assertThrows<RuntimeException> {
//            runFieldResolver(
//                resolver = resolver,
//                objectValue = queryObj(),
//                queryValue = queryObj(),
//                arguments = args,
//                requestContext = emptyMap<String, Any>() as ExecutionContext?
//            )
//        }
//
//        verify(exactly = 0) { postRepository.findByAuthorId(any()) }
//    }
}
