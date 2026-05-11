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
import viaduct.api.types.OffsetLimit
import viaduct.api.grts.*
import viaduct.api.internal.InternalContext
import viaduct.api.mocks.MockConnectionFieldExecutionContext
import viaduct.api.types.Arguments.NoArguments
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.*

class PostsResolverTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockPost: Post
    private val userId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

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

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

        assertEquals(1, result.size)
        assertEquals(postId.toString(), result[0].getId().internalID)
        assertEquals("Test Post", result[0].getTitle())
    }

    @Test
    fun `PostsResolver returns empty list when no posts exist`() = runBlocking {
        val resolver = PostsResolver(postRepository)

        every { postRepository.findAll() } returns emptyList()

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = NoArguments
            }

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
        // 0.30 ConnectionBuilder.fromSlice internally calls arguments.toOffsetLimit(maxLimit)
        // with Viaduct's own default to derive the offset for cursor encoding, so match any int.
        every { args.toOffsetLimit(any<Int>()) } returns OffsetLimit(offset, first)
        val selections = mkSelectionSetFactory().selectionsOn(PostsConnection.Reflection, "postsConnection", emptyMap())
        val mockConnCtx = MockConnectionFieldExecutionContext<Query, Query, Query_PostsConnection_Arguments, PostsConnection>(
            objectValue = Query.Builder(context).build(),
            queryValue = Query.Builder(context).build(),
            arguments = args,
            requestContext = null,
            selectionsValue = selections,
            internalContext = context as InternalContext,
            queryResults = buildContextQueryMap(emptyList()),
            selectionSetFactory = mkSelectionSetFactory()
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
    fun `PostsConnectionResolver populates pageInfo with hasNextPage when more pages exist`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 5L

        val result = resolver.resolve(buildConnectionContext(first = 1, offset = 0))

        assertNotNull(result)
        val pageInfo = result!!.getPageInfo()
        assertTrue(pageInfo.getHasNextPage())
        assertFalse(pageInfo.getHasPreviousPage())
        assertNotNull(pageInfo.getStartCursor())
        assertNotNull(pageInfo.getEndCursor())
    }

    @Test
    fun `PostsConnectionResolver populates pageInfo with hasPreviousPage when offset is positive`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 5L

        val result = resolver.resolve(buildConnectionContext(first = 1, offset = 2))

        assertNotNull(result)
        val pageInfo = result!!.getPageInfo()
        assertTrue(pageInfo.getHasPreviousPage())
        assertTrue(pageInfo.getHasNextPage())
    }

    @Test
    fun `PostsConnectionResolver returns empty edges and null cursors when page is empty`() = runBlocking {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPage(any(), any()) } returns emptyList()
        every { postRepository.count() } returns 0L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(0, result!!.getEdges()?.size ?: 0)
        val pageInfo = result.getPageInfo()
        assertFalse(pageInfo.getHasNextPage())
        assertNull(pageInfo.getStartCursor())
        assertNull(pageInfo.getEndCursor())
    }
}
