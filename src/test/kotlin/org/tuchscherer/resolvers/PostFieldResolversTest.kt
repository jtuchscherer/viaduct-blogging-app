@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Comment
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.PostAuthorResolver
import org.tuchscherer.viadapp.resolvers.PostCommentsFieldResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.BlogPostResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.User as ViaductUser
import viaduct.api.types.Arguments.NoArguments
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostFieldResolversTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    private fun postObj(id: UUID = postId) = ViaductBlogPost.Builder(context)
        .id(globalIDFor(ViaductBlogPost.Reflection, id.toString()))
        .title("Test Post")
        .content("Test content")
        .createdAt("2025-01-01T10:00:00")
        .updatedAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>()
        commentRepository = mockk<CommentRepository>()

        mockComment = mockk<Comment>()
        every { mockComment.id } returns commentId
        every { mockComment.content } returns "Test comment"
        every { mockComment.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostRepository> { postRepository }
                single<CommentRepository> { commentRepository }
            })
        }
    }

    // ── PostAuthorResolver ──────────────────────────────────────────────

    @Test
    fun `PostAuthorResolver returns the post author reference`() = runBlocking {
        val resolver = PostAuthorResolver(postRepository)
        every { postRepository.getAuthorIdsByPostIds(listOf(postId)) } returns mapOf(postId to userId)

        val authorId = globalIDFor(ViaductUser.Reflection, userId.toString())
        val author = ViaductUser.Builder(context).id(authorId).build()
        val ctx = mockk<BlogPostResolvers.Author.Context>()
        coEvery { ctx.getObjectValue() } returns postObj()
        every { ctx.globalIDFor(ViaductUser.Reflection, userId.toString()) } returns authorId
        every { ctx.ref(authorId) } returns author

        val results = resolver.batchResolve(listOf(ctx))

        assertEquals(authorId, results.single().get().getIdOrThrow())
    }

    @Test
    fun `PostAuthorResolver returns error FieldValue when post not found`() = runBlocking {
        val resolver = PostAuthorResolver(postRepository)
        every { postRepository.getAuthorIdsByPostIds(listOf(postId)) } returns emptyMap()

        val ctx = mockk<BlogPostResolvers.Author.Context>()
        coEvery { ctx.getObjectValue() } returns postObj()

        val results = resolver.batchResolve(listOf(ctx))

        assertEquals(1, results.size)
        val error = assertThrows(NotFoundException::class.java) { results.single().get() }
        assertEquals("Post not found: $postId", error.message)
    }

    // ── PostCommentsFieldResolver ───────────────────────────────────────

    @Test
    fun `PostCommentsFieldResolver returns comments for post`() = runBlocking {
        val resolver = PostCommentsFieldResolver(commentRepository)
        every { commentRepository.findByPostId(postId) } returns listOf(mockComment)

        val result = runFieldResolver(resolver) {
            objectValue = postObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getIdOrThrow().internalID)
        assertEquals("Test comment", result[0].getContentOrThrow())
    }

    @Test
    fun `PostCommentsFieldResolver returns empty list when no comments`() = runBlocking {
        val resolver = PostCommentsFieldResolver(commentRepository)
        every { commentRepository.findByPostId(postId) } returns emptyList()

        val result = runFieldResolver(resolver) {
            objectValue = postObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

        assertEquals(0, result.size)
    }
}
