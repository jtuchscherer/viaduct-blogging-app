@file:Suppress("DEPRECATION")
@file:OptIn(viaduct.apiannotations.InternalApi::class, viaduct.apiannotations.ExperimentalApi::class)

package org.tuchscherer.resolvers

import org.tuchscherer.database.Post
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.*
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.connection.OffsetLimit
import viaduct.api.grts.*
import viaduct.api.internal.InternalContext
import viaduct.api.mocks.MockConnectionFieldExecutionContext
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
        assertEquals(postId.toString(), result[0].getId().internalID)
        assertEquals("Test Post", result[0].getTitle())
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
    }

    // ── PostsConnectionResolver unit tests ───────────────────────────────────
    // Uses MockConnectionFieldExecutionContext (Viaduct test-fixtures) so we get a
    // real InternalContext-backed context. No relaxed mocks, no runCatching — resolve()
    // completes successfully and we can assert on the returned PostsConnection.

    private fun buildConnectionContext(
        first: Int = PostsConnectionResolver.DEFAULT_PAGE_SIZE,
        offset: Int = 0
    ): QueryResolvers.PostsConnection.Context {
        val args = mockk<Query_PostsConnection_Arguments>()
        every { args.toOffsetLimit(PostsConnectionResolver.DEFAULT_PAGE_SIZE) } returns
            OffsetLimit(offset, first, false)
        val selections = ossSelectionSetFactory.selectionsOn(PostsConnection.Reflection, "postsConnection", emptyMap())
        val mockConnCtx = MockConnectionFieldExecutionContext<Query, Query, Query_PostsConnection_Arguments, PostsConnection>(
            objectValue = Query.Builder(context).build(),
            queryValue = Query.Builder(context).build(),
            arguments = args,
            requestContext = null,
            selectionsValue = selections,
            internalContext = context as InternalContext,
            queryResults = buildContextQueryMap(emptyList()),
            selectionSetFactory = ossSelectionSetFactory
        )
        return QueryResolvers.PostsConnection.Context(mockConnCtx)
    }

    @Test
    fun `PostsConnectionResolver returns connection with correct totalCount`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(1, result!!.getTotalCount())
    }

    @Test
    fun `PostsConnectionResolver returns empty connection for empty repository`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns emptyList()
        every { postRepository.count() } returns 0L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(0, result!!.getTotalCount())
    }

    @Test
    fun `PostsConnectionResolver does not call findAll`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        resolver.resolve(buildConnectionContext())

        verify(exactly = 0) { postRepository.findAll() }
    }

    @Test
    fun `PostsConnectionResolver encodeCursor produces correct Viaduct cursor format`() {
        // Viaduct cursor format: base64("__viaduct:idx:N")
        val cursor = PostsConnectionResolver.encodeCursor(1)
        assertEquals("X192aWFkdWN0OmlkeDox", cursor)
    }
}
