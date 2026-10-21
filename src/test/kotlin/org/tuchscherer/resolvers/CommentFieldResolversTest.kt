package org.tuchscherer.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostType
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.CommentAuthorResolver
import org.tuchscherer.viadapp.resolvers.CommentPostResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Query
import viaduct.api.types.Arguments.NoArguments
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.UUID

class CommentFieldResolversTest : ResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    private fun commentObj(id: UUID = commentId) = ViaductComment.Builder(context)
        .id(globalIDFor(ViaductComment.Reflection, id.toString()))
        .content("Test comment")
        .createdAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk()

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns userId
        every { mockUser.username } returns "testuser"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.name } returns "Test User"
        every { mockUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns postId
        every { mockPost.title } returns "Test Post"
        every { mockPost.postType } returns PostType.BLOG_POST
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.status } returns PostStatus.PUBLISHED

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module { single<CommentRepository> { commentRepository } })
        }
    }

    // ── CommentAuthorResolver ───────────────────────────────────────────

    @Test
    fun `CommentAuthorResolver returns author for comment`() = runBlocking<Unit> {
        val resolver = CommentAuthorResolver(commentRepository)
        every { commentRepository.getAuthorForComment(commentId) } returns mockUser

        val result = runFieldResolver(resolver) {
            objectValue = commentObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

        assertEquals(userId.toString(), result.getIdOrThrow().internalID)
        assertEquals("testuser", result.getUsernameOrThrow())
        assertEquals("test@example.com", result.getEmailOrThrow())
    }

    @Test
    fun `CommentAuthorResolver throws when comment not found`() = runBlocking<Unit> {
        val resolver = CommentAuthorResolver(commentRepository)
        every { commentRepository.getAuthorForComment(commentId) } returns null

        assertThrows<NotFoundException> {
            runFieldResolver(resolver) {
                objectValue = commentObj()
                queryValue = queryObj()
                arguments = NoArguments
                }
        }
    }

    // ── CommentPostResolver ─────────────────────────────────────────────

    @Test
    fun `CommentPostResolver returns post for comment`() = runBlocking<Unit> {
        val resolver = CommentPostResolver(commentRepository)
        every { commentRepository.getPostForComment(commentId) } returns mockPost

        val result = runFieldResolver(resolver) {
            objectValue = commentObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

        val blogPost = result as ViaductBlogPost
        assertEquals(postId.toString(), blogPost.getIdOrThrow().internalID)
        assertEquals("Test Post", blogPost.getTitleOrThrow())
        assertEquals(PostStatus.PUBLISHED, blogPost.getStatusOrThrow().name)
    }

    @Test
    fun `CommentPostResolver throws when comment not found`() = runBlocking<Unit> {
        val resolver = CommentPostResolver(commentRepository)
        every { commentRepository.getPostForComment(commentId) } returns null

        assertThrows<NotFoundException> {
            runFieldResolver(resolver) {
                objectValue = commentObj()
                queryValue = queryObj()
                arguments = NoArguments
                }
        }
    }
}
