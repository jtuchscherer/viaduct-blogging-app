@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Comment
import org.tuchscherer.database.Posts
import org.tuchscherer.database.repositories.CommentRepository
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
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Post as ViaductPost
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostCommentsResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var commentRepository: CommentRepository
    private lateinit var mockComment: Comment
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        commentRepository = mockk<CommentRepository>()

        mockComment = mockk<Comment>(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())
        every { mockComment.content } returns "Test comment content"
        every { mockComment.authorId } returns EntityID(userId, mockk())
        every { mockComment.postId } returns EntityID(postId, Posts)
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
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .build()

        every { commentRepository.findByPostId(any<EntityID<UUID>>()) } returns listOf(mockComment)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(1, result.size)
        assertEquals(commentId.toString(), result[0].getId().internalID)
        assertEquals("Test comment content", result[0].getContent())
    }

    @Test
    fun `PostCommentsResolver returns empty list when no comments exist`() = runBlocking {
        val resolver = PostCommentsResolver(commentRepository)
        val args = Query_PostComments_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .build()

        every { commentRepository.findByPostId(any<EntityID<UUID>>()) } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = args
        )

        assertEquals(0, result.size)
    }
}
