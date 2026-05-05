@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Post
import org.tuchscherer.database.Posts
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.*

class UnlikePostResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var likeRepository: LikeRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockUser: User
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        likeRepository = mockk<LikeRepository>()
        postRepository = mockk<PostRepository>()

        mockUser = mockk<User>(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        mockPost = mockk<Post>(relaxed = true)
        every { mockPost.id } returns EntityID(postId, Posts)
        every { mockPost.title } returns "Test Post"

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { likeRepository }
                single { postRepository }
            })
        }
    }

    @Test
    fun `UnlikePostResolver unlikes post successfully`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.deleteByPostAndUser(mockPost.id.value, mockUser.id.value) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertTrue(result)
    }

    @Test
    fun `UnlikePostResolver returns false when like does not exist`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { likeRepository.deleteByPostAndUser(mockPost.id.value, mockUser.id.value) } returns false

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockUser)
        )

        assertFalse(result)
    }

    @Test
    fun `UnlikePostResolver throws exception when not authenticated`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        assertThrows<AuthenticationException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext()
            )
        }

        verify(exactly = 0) { postRepository.findById(any()) }
        verify(exactly = 0) { likeRepository.deleteByPostAndUser(any(), any()) }
    }

    @Test
    fun `UnlikePostResolver throws exception when post not found`() = runBlocking {
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        val args = Mutation_UnlikePost_Arguments.Builder(context)
            .postId(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockUser)
            )
        }

        verify(exactly = 0) { likeRepository.deleteByPostAndUser(any(), any()) }
    }
}
