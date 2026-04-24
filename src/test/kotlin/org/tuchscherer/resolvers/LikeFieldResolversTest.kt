@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Like
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.PostCommentCountResolver
import org.tuchscherer.viadapp.resolvers.PostIsLikedByMeResolver
import org.tuchscherer.viadapp.resolvers.PostLikeCountResolver
import org.tuchscherer.viadapp.resolvers.PostLikesResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.types.Arguments.NoArguments
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class LikeFieldResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var mockUser: User
    private lateinit var mockLike: Like
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val likeId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    private fun postObj(id: UUID = postId) = ViaductPost.Builder(context)
        .id(context.globalIDFor(ViaductPost.Reflection, id.toString()))
        .title("Test Post")
        .content("Test content")
        .createdAt("2025-01-01T10:00:00")
        .updatedAt("2025-01-01T10:00:00")
        .build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk<LikeRepository>()
        commentRepository = mockk<CommentRepository>()

        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        mockLike = mockk<Like>(relaxed = true)
        every { mockLike.id } returns EntityID(likeId, mockk())
        every { mockLike.userId } returns EntityID(userId, mockk())
        every { mockLike.postId } returns EntityID(postId, mockk())
        every { mockLike.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<LikeRepository> { likeRepository }
                single<CommentRepository> { commentRepository }
            })
        }
    }

    // ── PostLikesResolver ───────────────────────────────────────────────

    @Test
    fun `PostLikesResolver returns likes for post`() = runBlocking {
        val resolver = PostLikesResolver()
        every { likeRepository.findByPostId(postId) } returns listOf(mockLike)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(1, result.size)
        assertEquals(likeId.toString(), result[0].getId().internalID)
    }

    @Test
    fun `PostLikesResolver returns empty list when no likes`() = runBlocking {
        val resolver = PostLikesResolver()
        every { likeRepository.findByPostId(postId) } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(0, result.size)
    }

    // ── PostLikeCountResolver ───────────────────────────────────────────

    @Test
    fun `PostLikeCountResolver returns count for post`() = runBlocking {
        val resolver = PostLikeCountResolver()
        every { likeRepository.countByPostId(postId) } returns 5L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(5, result)
    }

    @Test
    fun `PostLikeCountResolver throws when count exceeds Int MAX_VALUE (toCountInt guard)`() = runBlocking {
        val resolver = PostLikeCountResolver()
        every { likeRepository.countByPostId(postId) } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = postObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
    }

    @Test
    fun `PostLikeCountResolver returns zero when no likes`() = runBlocking {
        val resolver = PostLikeCountResolver()
        every { likeRepository.countByPostId(postId) } returns 0L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(0, result)
    }

    // ── PostIsLikedByMeResolver ─────────────────────────────────────────

    @Test
    fun `PostIsLikedByMeResolver returns true when user has liked the post`() = runBlocking {
        val resolver = PostIsLikedByMeResolver()
        every { likeRepository.existsByPostAndUser(postId, userId) } returns true

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments,
            requestContext = RequestContext(user = mockUser)
        )

        assertTrue(result)
    }

    @Test
    fun `PostIsLikedByMeResolver returns false when user has not liked the post`() = runBlocking {
        val resolver = PostIsLikedByMeResolver()
        every { likeRepository.existsByPostAndUser(postId, userId) } returns false

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments,
            requestContext = RequestContext(user = mockUser)
        )

        assertFalse(result)
    }

    @Test
    fun `PostIsLikedByMeResolver returns false when unauthenticated`() = runBlocking {
        val resolver = PostIsLikedByMeResolver()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments,
            requestContext = RequestContext()
        )

        assertFalse(result)
        verify(exactly = 0) { likeRepository.existsByPostAndUser(any<UUID>(), any<UUID>()) }
    }

    // ── PostCommentCountResolver ────────────────────────────────────────────

    @Test
    fun `PostCommentCountResolver returns comment count for post`() = runBlocking {
        val resolver = PostCommentCountResolver()
        every { commentRepository.countByPostId(postId) } returns 4L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(4, result)
    }

    @Test
    fun `PostCommentCountResolver returns zero when no comments`() = runBlocking {
        val resolver = PostCommentCountResolver()
        every { commentRepository.countByPostId(postId) } returns 0L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = postObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(0, result)
    }

    @Test
    fun `PostCommentCountResolver throws when count exceeds Int MAX_VALUE (toCountInt guard)`() = runBlocking {
        val resolver = PostCommentCountResolver()
        every { commentRepository.countByPostId(postId) } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = postObj(),
                queryValue = queryObj(),
                arguments = NoArguments
            )
        }
    }
}
