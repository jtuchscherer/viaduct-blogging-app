package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.CreatePostResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.CreatePostInput
import viaduct.api.grts.Mutation
import viaduct.api.grts.Mutation_CreatePost_Arguments
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Query
import viaduct.api.testing.MutationResolverTester
import java.time.LocalDateTime
import java.util.UUID

class CreatePostResolverTest {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    private val tester = MutationResolverTester.create<Query, Mutation, Mutation_CreatePost_Arguments, ViaductBlogPost>(ViaductTestConfig.testerConfig)

    @BeforeEach
    fun setup() {
        postRepository = mockk()

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.authorId } returns EntityID(userId, mockk())
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module { single { postRepository } })
        }
    }

    @Test
    fun `CreatePostResolver creates post and returns id, title, content`() = runBlocking {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("New Post").content("New content").build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        every {
            postRepository.create(
                title = "New Post",
                content = "New content",
                authorId = mockUser.id,
                createdAt = any(),
                updatedAt = any()
            )
        } returns mockPost

        val result = tester.test(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals(postId.toString(), result.getId().internalID)
        assertEquals("Test Post", result.getTitle())
        assertEquals("Test content", result.getContent())
    }

    @Test
    fun `CreatePostResolver throws AuthenticationException when not authenticated`() {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("New Post").content("New content").build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        // MutationResolverTester wraps exceptions in InvocationTargetException
        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext()
                }
            }
        }
        assertInstanceOf(AuthenticationException::class.java, e.cause)
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for blank title`() {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("   ").content("Some content").build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for blank content`() {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("Valid title").content("   ").build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for title exceeding 500 characters`() {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("a".repeat(501)).content("Some content").build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }

    @Test
    fun `CreatePostResolver throws IllegalArgumentException for content exceeding 100000 characters`() {
        val resolver = CreatePostResolver(postRepository)
        val input = CreatePostInput.Builder(tester.context).title("Valid title").content("a".repeat(100_001)).build()
        val args = Mutation_CreatePost_Arguments.Builder(tester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }
}
