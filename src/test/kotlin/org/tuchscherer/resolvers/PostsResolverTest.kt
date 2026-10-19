@file:Suppress("DEPRECATION")
@file:OptIn(viaduct.apiannotations.InternalApi::class, viaduct.apiannotations.ExperimentalApi::class)

package org.tuchscherer.resolvers

import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostProjection
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.*
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import viaduct.api.types.OffsetLimit
import viaduct.api.grts.*
import viaduct.api.internal.InternalContext
import viaduct.api.mocks.MockConnectionFieldExecutionContext
import viaduct.api.types.Arguments.NoArguments
import viaduct.api.testing.ResolverTestBase
import java.util.*

class PostsResolverTest : ResolverTestBase() {

    private lateinit var postRepository: PostRepository
    private lateinit var mockPost: PostProjection
    private val postId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        postRepository = mockk<PostRepository>()

        mockPost = PostProjection(
            postId, PostType.BLOG_POST, setOf(PostField.TITLE), title = "Test Post"
        )
    }

    @Test
    fun `PostsResolver returns all posts`() = runBlocking<Unit> {
        val resolver = PostsResolver(postRepository)

        every { postRepository.findPublishedProjection(any(), null, 0) } returns listOf(mockPost)

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = NoArguments
            selections = mkSelectionSetFactory().selectionsOn(BlogPost.Reflection, "id title", emptyMap())
            }

        assertEquals(1, result.size)
        assertEquals(postId.toString(), result[0].getIdOrThrow().internalID)
    }

    @Test
    fun `PostsResolver returns empty list when no posts exist`() = runBlocking<Unit> {
        val resolver = PostsResolver(postRepository)

        every { postRepository.findPublishedProjection(any(), null, 0) } returns emptyList()

        val result = runFieldResolver(resolver) {
            objectValue = queryObj()
            queryValue = queryObj()
            arguments = NoArguments
            selections = mkSelectionSetFactory().selectionsOn(BlogPost.Reflection, "id title", emptyMap())
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
        // fromSlice requires this to be false before taking that path; only backward pagination
        // needs a total count, and these fixtures paginate forward.
        every { args.requiresTotalCountForOffsetLimit() } returns false
        val selections = mkSelectionSetFactory().selectionsOn(PostsConnection.Reflection, "edges { cursor node { id title } } totalCount pageInfo { hasNextPage hasPreviousPage startCursor endCursor }", emptyMap())
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
    fun `PostsConnectionResolver returns connection with correct totalCount`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPublishedProjection(any(), any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(1, result!!.getTotalCountOrThrow())
    }

    @Test
    fun `PostsConnectionResolver returns empty connection for empty repository`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPublishedProjection(any(), any(), any()) } returns emptyList()
        every { postRepository.count() } returns 0L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(0, result!!.getTotalCountOrThrow())
    }

    @Test
    fun `PostsConnectionResolver populates pageInfo with hasNextPage when more pages exist`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPublishedProjection(any(), any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 5L

        val result = resolver.resolve(buildConnectionContext(first = 1, offset = 0))

        assertNotNull(result)
        val pageInfo = result!!.getPageInfoOrThrow()
        assertTrue(pageInfo.getHasNextPageOrThrow())
        assertFalse(pageInfo.getHasPreviousPageOrThrow())
        assertNotNull(pageInfo.getStartCursorOrThrow())
        assertNotNull(pageInfo.getEndCursorOrThrow())
    }

    @Test
    fun `PostsConnectionResolver populates pageInfo with hasPreviousPage when offset is positive`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPublishedProjection(any(), any(), any()) } returns listOf(mockPost)
        every { postRepository.count() } returns 5L

        val result = resolver.resolve(buildConnectionContext(first = 1, offset = 2))

        assertNotNull(result)
        val pageInfo = result!!.getPageInfoOrThrow()
        assertTrue(pageInfo.getHasPreviousPageOrThrow())
        assertTrue(pageInfo.getHasNextPageOrThrow())
    }

    @Test
    fun `PostsConnectionResolver returns empty edges and null cursors when page is empty`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        every { postRepository.findPublishedProjection(any(), any(), any()) } returns emptyList()
        every { postRepository.count() } returns 0L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(emptyList<PostEdge>(), result!!.getEdgesOrThrow())
        val pageInfo = result.getPageInfoOrThrow()
        assertFalse(pageInfo.getHasNextPageOrThrow())
        assertNull(pageInfo.getStartCursorOrThrow())
        assertNull(pageInfo.getEndCursorOrThrow())
    }

    @Test
    fun `PostsConnectionResolver includes CheckedListPost nodes in the connection`() = runBlocking<Unit> {
        val resolver = PostsConnectionResolver(postRepository)
        val checkedListPost = PostProjection(UUID.randomUUID(), PostType.CHECKED_LIST, emptySet())

        every { postRepository.findPublishedProjection(any(), any(), any()) } returns listOf(mockPost, checkedListPost)
        every { postRepository.count() } returns 2L

        val result = resolver.resolve(buildConnectionContext())

        assertNotNull(result)
        assertEquals(2, result!!.getTotalCountOrThrow())
        val nodes = result.getEdgesOrThrow()?.map { it?.getNodeOrThrow() }
        assertEquals(2, nodes?.size)
        assertInstanceOf(BlogPost::class.java, nodes?.get(0))
        assertInstanceOf(CheckedListPost::class.java, nodes?.get(1))
    }
}
