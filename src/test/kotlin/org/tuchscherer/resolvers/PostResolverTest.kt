@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostResolverTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>()

        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.authorId } returns EntityID(userId, mockk())
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { postRepository }
            })
        }
    }

    @Test
    fun `PostResolver returns post by id`() = runBlocking {
        val resolver = PostResolver(postRepository)
        val args = Query_Post_Arguments.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = args
            }

        assertNotNull(result)
        assertEquals(postId.toString(), result?.getId()?.internalID)
        assertEquals("Test Post", result?.getTitle())
    }

    @Test
    fun `PostResolver returns null when post not found`() = runBlocking {
        val resolver = PostResolver(postRepository)
        val args = Query_Post_Arguments.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns null

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = args
            }

        assertNull(result)
    }
}
