package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.UpdatePostResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import viaduct.api.grts.Mutation_UpdatePost_Arguments
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.UpdatePostInput
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.UUID

class UpdatePostResolverTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        postRepository = mockk()

        mockUser = mockk()
        every { mockUser.id } returns EntityID(userId, mockk())

        mockPost = mockk()
        every { mockPost.authorId } returns mockUser.id
    }

    @Test
    fun `UpdatePostResolver updates post and returns updated title and content`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("Updated Title")
            .content("Updated content")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        every { postRepository.findById(postId) } returns mockPost
        val updatedPost = mockk<Post>()
        every { updatedPost.id } returns EntityID(postId, mockk())
        every { updatedPost.title } returns "Updated Title"
        every { updatedPost.content } returns "Updated content"
        every { updatedPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { updatedPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 12, 0)
        every { updatedPost.status } returns PostStatus.PUBLISHED
        every { updatedPost.publishedAt } returns null
        every { postRepository.updateById(postId, "Updated Title", "Updated content") } returns updatedPost

        val result = runMutationFieldResolver(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("Updated Title", result.getTitleOrThrow())
        assertEquals("Updated content", result.getContentOrThrow())
    }

    @Test
    fun `UpdatePostResolver throws NotFoundException when post does not exist`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).title("Updated Title").build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        every { postRepository.findById(postId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `UpdatePostResolver reports a post that disappears during update`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("Updated Title")
            .build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()
        every { postRepository.findById(postId) } returns mockPost
        every { postRepository.updateById(postId, "Updated Title", null) } returns null

        val error = assertThrows<NotFoundException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }

        assertEquals("Failed to update post", error.message)
    }

    @Test
    fun `UpdatePostResolver throws AuthorizationException when user is not author`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).title("Updated Title").build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        every { postRepository.findById(postId) } returns mockPost
        every { mockPost.authorId } returns EntityID(UUID.randomUUID(), mockk())

        assertThrows<AuthorizationException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `UpdatePostResolver throws IllegalArgumentException for blank title`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).title("   ").build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `UpdatePostResolver throws IllegalArgumentException for title exceeding 500 characters`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).title("a".repeat(501)).build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `UpdatePostResolver throws IllegalArgumentException for blank content`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).content("   ").build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `UpdatePostResolver throws IllegalArgumentException for content exceeding 100000 characters`() = runBlocking<Unit> {
        val resolver = UpdatePostResolver(postRepository)
        val input = UpdatePostInput.Builder(context).id(globalIDFor(ViaductBlogPost.Reflection, postId.toString())).content("a".repeat(100_001)).build()
        val args = Mutation_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }
}
