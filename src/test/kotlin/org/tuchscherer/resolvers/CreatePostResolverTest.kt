package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.CreatePostResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.CreatePostInput
import viaduct.api.grts.Mutation_CreatePost_Arguments
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.UUID

class CreatePostResolverTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        postRepository = mockk()

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns userId
        every { mockUser.username } returns "testuser"

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns postId
        every { mockPost.title } returns "Test Post"
        every { mockPost.authorId } returns userId
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.status } returns PostStatus.PUBLISHED

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module { single { postRepository } })
        }
    }

    @Test
    fun `CreatePostResolver creates post and returns owned id title and status`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("New Post").content("New content").build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        every {
            postRepository.create(
                title = "New Post",
                content = "New content",
                authorId = mockUser.id,
                createdAt = any(),
                updatedAt = any()
            )
        } returns mockPost

        val result = runMutationFieldResolver(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockUser)
            }

        assertNotNull(result)
        assertEquals(postId.toString(), result.getIdOrThrow().internalID)
        assertEquals("Test Post", result.getTitleOrThrow())
        assertEquals(PostStatus.PUBLISHED, result.getStatusOrThrow().name)
    }

    @Test
    fun `CreatePostResolver throws AuthenticationException when not authenticated`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("New Post").content("New content").build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        assertThrows<AuthenticationException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext()
                }
        }
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for blank title`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("   ").content("Some content").build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
                }
        }
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for blank content`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("Valid title").content("   ").build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
                }
        }
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for title exceeding 500 characters`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("a".repeat(501)).content("Some content").build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
                }
        }
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for content exceeding 100000 characters`() = runBlocking<Unit> {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(context).title("Valid title").content("a".repeat(100_001)).build()
        val args = Mutation_CreatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
                }
        }
    }
}
