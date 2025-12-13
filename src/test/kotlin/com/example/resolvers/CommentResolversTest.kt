package com.example.resolvers

import com.example.auth.RequestContext
import com.example.database.Comment
import com.example.database.Post
import com.example.database.Posts
import com.example.database.User
import com.example.database.repositories.CommentRepository
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
 * Comprehensive unit tests for Comment resolvers.
 * Tests the actual resolver logic with mocked dependencies using Viaduct's test infrastructure.
 */
class CommentResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()
    private fun mutationObj() = Mutation.Builder(context).build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>(relaxed = true)
        postRepository = mockk<PostRepository>(relaxed = true)

        // Setup mock user
        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        // Setup mock post
        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, Posts)
        every { mockPost.title } returns "Test Post"

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
                single { postRepository }
            })
        }
    }

    // ========================================
    // CreateCommentResolver Tests
    // ========================================

    @Test
    fun `CreateCommentResolver creates comment successfully`() = runBlocking {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(context)
            .postId(postId.toString())
            .content("New comment")
            .build()
        val args = Mutation_CreateComment_Arguments.Builder(context)
            .input(input)
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every {
            commentRepository.create(
                content = "New comment",
                postId = mockPost.id,
                authorId = mockUser.id,
                createdAt = any()
            )
        } returns mockComment

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(commentId.toString(), result.getId())
        assertEquals("Test comment content", result.getContent())
        verify { postRepository.findById(postId) }
        verify { commentRepository.create(any(), any(), any(), any()) }
    }

    @Test
    fun `CreateCommentResolver throws exception when not authenticated`() = runBlocking {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(context)
            .postId(postId.toString())
            .content("New comment")
            .build()
        val args = Mutation_CreateComment_Arguments.Builder(context)
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

        verify(exactly = 0) { commentRepository.create(any(), any(), any(), any()) }
    }

    @Test
    fun `CreateCommentResolver throws exception when post not found`() = runBlocking {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(context)
            .postId(postId.toString())
            .content("New comment")
            .build()
        val args = Mutation_CreateComment_Arguments.Builder(context)
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
        verify(exactly = 0) { commentRepository.create(any(), any(), any(), any()) }
    }

    // ========================================
    // DeleteCommentResolver Tests
    // ========================================

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

    // ========================================
    // PostCommentsResolver Tests
    // ========================================

    @Test
    fun `PostCommentsResolver returns comments for post`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { commentRepository.findByPostId(any()) } returns listOf(mockComment)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getId())
        assertEquals("Test comment content", result[0].getContent())
        verify { commentRepository.findByPostId(any()) }
    }

    @Test
    fun `PostCommentsResolver returns empty list when no comments exist`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(postId.toString())
            .build()

        every { commentRepository.findByPostId(any()) } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(0, result.size)
        verify { commentRepository.findByPostId(any()) }
    }
}
