package org.tuchscherer.resolvers

import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.CreateCommentResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.CreateCommentInput
import viaduct.api.grts.Mutation
import viaduct.api.grts.Mutation_CreateComment_Arguments
import viaduct.api.grts.Query
import viaduct.api.testing.MutationResolverTester
import java.time.LocalDateTime
import java.util.UUID

class CreateCommentResolverTest {

    private lateinit var commentRepository: CommentRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private val tester = MutationResolverTester.create<Query, Mutation, Mutation_CreateComment_Arguments, ViaductComment>(ViaductTestConfig.testerConfig)

    @BeforeEach
    fun setup() {
        commentRepository = mockk(relaxed = true)
        postRepository = mockk(relaxed = true)

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, Posts)
        every { mockPost.title } returns "Test Post"

        mockComment = mockk(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())
        every { mockComment.content } returns "Test comment content"
        every { mockComment.authorId } returns EntityID(userId, mockk())
        every { mockComment.postId } returns EntityID(postId, Posts)
        every { mockComment.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { commentRepository }
                single { postRepository }
            })
        }
    }

    @Test
    fun `CreateCommentResolver creates comment successfully`() = runBlocking {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(tester.context).postId(postId.toString()).content("New comment").build()
        val args = Mutation_CreateComment_Arguments.Builder(tester.context).input(input).build()

        every { postRepository.findById(postId) } returns mockPost
        every {
            commentRepository.create(
                content = "New comment",
                postId = mockPost.id,
                authorId = mockUser.id,
                createdAt = any()
            )
        } returns mockComment

        val result = tester.test(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals(commentId.toString(), result.getId())
        assertEquals("Test comment content", result.getContent())
        verify { postRepository.findById(postId) }
        verify { commentRepository.create(any(), any(), any(), any()) }
    }

    @Test
    fun `CreateCommentResolver throws exception when not authenticated`() {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(tester.context).postId(postId.toString()).content("New comment").build()
        val args = Mutation_CreateComment_Arguments.Builder(tester.context).input(input).build()

        assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext()
                }
            }
        }

        verify(exactly = 0) { commentRepository.create(any(), any(), any(), any()) }
    }

    @Test
    fun `CreateCommentResolver throws exception when post not found`() {
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        val input = CreateCommentInput.Builder(tester.context).postId(postId.toString()).content("New comment").build()
        val args = Mutation_CreateComment_Arguments.Builder(tester.context).input(input).build()

        every { postRepository.findById(postId) } returns null

        assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }

        verify { postRepository.findById(postId) }
        verify(exactly = 0) { commentRepository.create(any(), any(), any(), any()) }
    }
}
