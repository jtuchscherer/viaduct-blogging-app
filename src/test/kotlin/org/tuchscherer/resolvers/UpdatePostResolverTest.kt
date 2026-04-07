package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.UpdatePostResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.Mutation
import viaduct.api.grts.Mutation_UpdatePost_Arguments
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.Query
import viaduct.api.grts.UpdatePostInput
import viaduct.api.testing.MutationResolverTester
import java.time.LocalDateTime
import java.util.UUID

class UpdatePostResolverTest {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    private val tester = MutationResolverTester.create<Query, Mutation, Mutation_UpdatePost_Arguments, ViaductPost>(ViaductTestConfig.testerConfig)

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
    fun `UpdatePostResolver updates post and returns updated title and content`() = runBlocking {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(tester.context)
            .id(tester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("Updated Title")
            .content("Updated content")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(tester.context).input(input).build()

        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns mockUser.id

        val updatedPost = mockk<Post>(relaxed = true)
        every { updatedPost.id } returns EntityID(postId, mockk())
        every { updatedPost.title } returns "Updated Title"
        every { updatedPost.content } returns "Updated content"
        every { updatedPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { updatedPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 12, 0)
        every { postRepository.updateById(postId, "Updated Title", "Updated content") } returns updatedPost

        val result = tester.test(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("Updated Title", result.getTitle())
        assertEquals("Updated content", result.getContent())
    }

    @Test
    fun `UpdatePostResolver throws NotFoundException when post does not exist`() {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(tester.context).id(tester.context.globalIDFor(ViaductPost.Reflection, postId.toString())).title("Updated Title").build()
        val args = Mutation_UpdatePost_Arguments.Builder(tester.context).input(input).build()

        every { postRepository.findById(postId) } returns null

        // MutationResolverTester wraps exceptions in InvocationTargetException
        val e1 = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(NotFoundException::class.java, e1.cause)
    }

    @Test
    fun `UpdatePostResolver throws AuthorizationException when user is not author`() {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(tester.context).id(tester.context.globalIDFor(ViaductPost.Reflection, postId.toString())).title("Updated Title").build()
        val args = Mutation_UpdatePost_Arguments.Builder(tester.context).input(input).build()

        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns EntityID(UUID.randomUUID(), mockk())

        val e = assertThrows<Exception> {
            runBlocking {
                tester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockUser)
                }
            }
        }
        assertInstanceOf(AuthorizationException::class.java, e.cause)
    }

    @Test
    fun `UpdatePostResolver throws IllegalArgumentException for blank title`() {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(tester.context).id(tester.context.globalIDFor(ViaductPost.Reflection, postId.toString())).title("   ").build()
        val args = Mutation_UpdatePost_Arguments.Builder(tester.context).input(input).build()

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
