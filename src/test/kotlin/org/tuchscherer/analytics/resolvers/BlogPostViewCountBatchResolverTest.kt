package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolverbases.BlogPostResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import viaduct.java.api.globalid.GlobalID
import viaduct.java.grts.BlogPost as ViaductBlogPost
import java.util.UUID

class BlogPostViewCountBatchResolverTest {

    private lateinit var postViewRepository: PostViewRepository
    private val postId1 = UUID.randomUUID()
    private val postId2 = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        postViewRepository = mockk<PostViewRepository>()
    }

    private fun mockContext(postId: UUID): BlogPostResolvers.ViewCount.Context {
        val globalId = mockk<GlobalID<ViaductBlogPost>>()
        every { globalId.internalID } returns postId.toString()
        val ctx = mockk<BlogPostResolvers.ViewCount.Context>()
        every { ctx.getObjectValue().getIdOrThrow() } returns globalId
        return ctx
    }

    @Test
    fun `returns correct view count for each post in batch`() = runBlocking<Unit> {
        val resolver = BlogPostViewCountBatchResolver(postViewRepository)
        every { postViewRepository.bulkGetViewCounts(any()) } returns
            mapOf(postId1 to 5L, postId2 to 3L)

        val results = resolver.resolveBatch(listOf(mockContext(postId1), mockContext(postId2)))

        assertEquals(2, results.size)
        assertFalse(results[0].isError)
        assertFalse(results[1].isError)
        assertEquals(5, results[0].get())
        assertEquals(3, results[1].get())
    }

    @Test
    fun `returns 0 for a post that has never been viewed`() = runBlocking<Unit> {
        val resolver = BlogPostViewCountBatchResolver(postViewRepository)
        every { postViewRepository.bulkGetViewCounts(any()) } returns emptyMap()

        val results = resolver.resolveBatch(listOf(mockContext(postId1)))

        assertEquals(1, results.size)
        assertFalse(results[0].isError)
        assertEquals(0, results[0].get())
    }

    @Test
    fun `returns default 0 for every post in batch when repository has no data`() = runBlocking<Unit> {
        val resolver = BlogPostViewCountBatchResolver(postViewRepository)
        every { postViewRepository.bulkGetViewCounts(any()) } returns emptyMap()

        val results = resolver.resolveBatch(listOf(mockContext(postId1), mockContext(postId2)))

        assertEquals(2, results.size)
        assertEquals(0, results[0].get())
        assertEquals(0, results[1].get())
    }
    @Test
    fun `empty batch returns no results`() {
        every { postViewRepository.bulkGetViewCounts(emptyList()) } returns emptyMap()
        assertTrue(BlogPostViewCountBatchResolver(postViewRepository).resolveBatch(emptyList()).isEmpty())
    }

    @Test
    fun `distinct contexts for the same parent retain independent results`() {
        val first = mockContext(postId1)
        val repeated = mockContext(postId1)
        every { postViewRepository.bulkGetViewCounts(listOf(postId1, postId1)) } returns mapOf(postId1 to 9L)
        val result = BlogPostViewCountBatchResolver(postViewRepository).batchResolve(listOf(first, repeated)).join()
        assertEquals(2, result.size)
        assertEquals(9, result[first])
        assertEquals(9, result[repeated])
    }

    @Test
    fun `repository failure propagates instead of returning a false zero`() {
        every { postViewRepository.bulkGetViewCounts(any()) } throws IllegalStateException("storage unavailable")
        val resolver = BlogPostViewCountBatchResolver(postViewRepository)
        assertThrows(IllegalStateException::class.java) {
            resolver.resolveBatch(listOf(mockContext(postId1)))
        }
    }
}
