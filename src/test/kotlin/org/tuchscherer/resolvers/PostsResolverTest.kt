@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.*
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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

class PostsResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>(relaxed = true)

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
    fun `PostsResolver returns all posts`() = runBlocking {
        val resolver = PostsResolver(postRepository)

        every { postRepository.findAll() } returns listOf(mockPost)

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(1, result.size)
        assertEquals(postId.toString(), result[0].getId())
        assertEquals("Test Post", result[0].getTitle())
        verify { postRepository.findAll() }
    }

    @Test
    fun `PostsResolver returns empty list when no posts exist`() = runBlocking {
        val resolver = PostsResolver(postRepository)

        every { postRepository.findAll() } returns emptyList()

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = queryObj(),
            queryValue = queryObj(),
            arguments = NoArguments
        )

        assertEquals(0, result.size)
        verify { postRepository.findAll() }
    }

    // ── PostsConnectionResolver unit tests (Phase 15a) ────────────────────────
    // ConnectionFieldExecutionContext is not compatible with DefaultAbstractResolverTestBase,
    // so we call resolve() directly with a relaxed mock context and use runCatching to tolerate
    // the builder failure. The relaxed mock's toOffsetLimit() returns OffsetLimit(0,0) which is
    // fine — these tests verify the repository contract (which methods are called), not the
    // argument parsing which is Viaduct's responsibility.

    @Test
    fun `PostsConnectionResolver calls findPage and count`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        val ctx = mockk<QueryResolvers.PostsConnection.Context>(relaxed = true)
        runCatching { resolver.resolve(ctx) }

        verify { postRepository.findPage(any(), any()) }
        verify { postRepository.count() }
    }

    @Test
    fun `PostsConnectionResolver calls findPage with empty repository`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns emptyList()
        every { postRepository.count() } returns 0L

        val ctx = mockk<QueryResolvers.PostsConnection.Context>(relaxed = true)
        runCatching { resolver.resolve(ctx) }

        verify { postRepository.findPage(any(), any()) }
        verify { postRepository.count() }
    }

    @Test
    fun `PostsConnectionResolver does not call findAll`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        val ctx = mockk<QueryResolvers.PostsConnection.Context>(relaxed = true)
        runCatching { resolver.resolve(ctx) }

        verify(exactly = 0) { postRepository.findAll() }
    }

    @Test
    fun `PostsConnectionResolver encodeCursor produces correct Viaduct cursor format`() {
        // Viaduct cursor format: base64("__viaduct:idx:N")
        val cursor = PostsConnectionResolver.encodeCursor(1)
        assertEquals("X192aWFkdWN0OmlkeDox", cursor)
    }
}
