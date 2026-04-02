@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Comment
import org.tuchscherer.database.Posts
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.PostAuthorResolver
import org.tuchscherer.viadapp.resolvers.PostCommentsFieldResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.PostResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.types.Arguments.NoArguments
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostFieldResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    private fun postObj(id: UUID = postId) = Post.Builder(context)
        .id(id.toString())
        .title("Test Post")
        .content("Test content")
        .createdAt("2025-01-01T10:00:00")
        .updatedAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>(relaxed = true)
        commentRepository = mockk<CommentRepository>(relaxed = true)

        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.name } returns "Test User"
        every { mockUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockComment = mockk<Comment>(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())
        every { mockComment.content } returns "Test comment"
        every { mockComment.postId } returns EntityID(postId, Posts)
        every { mockComment.authorId } returns EntityID(userId, mockk())
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
    fun `PostAuthorResolver calls getAuthorsByPostIds in batch for found posts`() = runBlocking {
        val resolver = PostAuthorResolver()
        every { postRepository.getAuthorsByPostIds(listOf(postId)) } returns mapOf(postId to mockUser)

        val ctx = mockk<PostResolvers.Author.Context>(relaxed = true)
        coEvery { ctx.objectValue.getId() } returns postId.toString()

        // ViaductUser.Builder(ctx) requires a real framework InternalContext — full result is
        // verified via integration tests. Here we confirm the batch repository method is called.
        runCatching { resolver.batchResolve(listOf(ctx)) }
        verify { postRepository.getAuthorsByPostIds(listOf(postId)) }
    }

    @Test
    fun `PostAuthorResolver returns error FieldValue when post not found`() = runBlocking {
        val resolver = PostAuthorResolver()
        every { postRepository.getAuthorsByPostIds(listOf(postId)) } returns emptyMap()

        val ctx = mockk<PostResolvers.Author.Context>(relaxed = true)
        coEvery { ctx.objectValue.getId() } returns postId.toString()

        val results = resolver.batchResolve(listOf(ctx))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        verify { postRepository.getAuthorsByPostIds(listOf(postId)) }
    }

    // ── PostCommentsFieldResolver ───────────────────────────────────────

    @Test
    fun `PostCommentsFieldResolver returns comments for post`() = runBlocking {
        val resolver = PostCommentsFieldResolver()
        every { commentRepository.findByPostId(postId) } returns listOf(mockComment)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getId())
        assertEquals("Test comment", result[0].getContent())
        verify { commentRepository.findByPostId(postId) }
    }

    @Test
    fun `PostCommentsFieldResolver returns empty list when no comments`() = runBlocking {
        val resolver = PostCommentsFieldResolver()
        every { commentRepository.findByPostId(postId) } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(0, result.size)
        verify { commentRepository.findByPostId(postId) }
    }
}
