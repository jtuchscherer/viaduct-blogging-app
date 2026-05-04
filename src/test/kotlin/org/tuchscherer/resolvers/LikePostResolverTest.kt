package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Like
import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.LikePostMutationResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.Mutation_LikePost_Arguments
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Query
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.UUID

class LikePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private lateinit var mockLike: Like
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val likeId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk()
        postRepository = mockk()

        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, Posts)
        every { mockPost.title } returns "Test Post"

        mockLike = mockk(relaxed = true)
        every { mockLike.id } returns EntityID(likeId, mockk())
        every { mockLike.userId } returns EntityID(userId, mockk())
        every { mockLike.postId } returns EntityID(postId, Posts)
        every { mockLike.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { likeRepository }
                single { postRepository }
            })
        }
    }

    @Test
    fun `LikePostMutationResolver creates new like successfully`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context).postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())).build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) } returns null
        every {
            likeRepository.create(postId = mockPost.id, userId = mockUser.id, createdAt = any())
        } returns mockLike

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(likeId.toString(), result.getId().internalID)
    }

    @Test
    fun `LikePostMutationResolver returns existing like when already liked`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context).postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())).build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.findByPostAndUser(mockPost.id, mockUser.id) } returns mockLike

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertNotNull(result)
        assertEquals(likeId.toString(), result.getId().internalID)
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }

    @Test
    fun `LikePostMutationResolver throws exception when not authenticated`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context).postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())).build()

        assertThrows<AuthenticationException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext()
            )
        }
        verify(exactly = 0) { postRepository.findById(any()) }
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }

    @Test
    fun `LikePostMutationResolver throws exception when post not found`() = runBlocking {
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        val args = Mutation_LikePost_Arguments.Builder(context).postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())).build()

        every { postRepository.findById(postId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }
        verify(exactly = 0) { likeRepository.create(any(), any(), any()) }
    }
}
