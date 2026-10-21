@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Comment
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostCommentsResolverTest : ResolverTestBase() {

    private val postRepository = mockk<org.tuchscherer.database.repositories.PostRepository>()
    private lateinit var commentRepository: CommentRepository
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>()
        val post = mockk<org.tuchscherer.database.Post>()
        every { post.status } returns "PUBLISHED"
        every { post.authorId } returns userId
        every { postRepository.findById(postId) } returns post

        mockComment = mockk<Comment>(relaxed = true)
        every { mockComment.id } returns commentId
        every { mockComment.content } returns "Test comment content"
        every { mockComment.authorId } returns userId
        every { mockComment.postId } returns postId
        every { mockComment.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { commentRepository }
            })
        }
    }

    @Test
    fun `PostCommentsResolver returns comments for post`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository, postRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { commentRepository.findByPostId(any<UUID>()) } returns listOf(mockComment)

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = args
            }

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getIdOrThrow().internalID)
        assertEquals("Test comment content", result[0].getContentOrThrow())
    }

    @Test
    fun `PostCommentsResolver returns empty list when no comments exist`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository, postRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { commentRepository.findByPostId(any<UUID>()) } returns emptyList()

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = args
            }

        assertEquals(0, result.size)
    }
}
