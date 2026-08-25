package org.tuchscherer.analytics.resolvers

import org.tuchscherer.analytics.port.PostStatusLookupPort

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.TrendingQueryResolver
import org.tuchscherer.viadapp.analytics.resolverbases.QueryResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.Post as ViaductPost
import java.util.UUID

class TrendingQueryResolverTest {

    private lateinit var postViewRepository: PostViewRepository
    private lateinit var postTypeLookupPort: PostTypeLookupPort

    @BeforeEach
    fun setup() {
        postViewRepository = mockk<PostViewRepository>()
        postTypeLookupPort = mockk<PostTypeLookupPort>()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostViewRepository> { postViewRepository }
                // Reports every requested id as published, so these tests stay about ranking
                // and limits rather than about publication status.
                single<PostStatusLookupPort> {
                    object : PostStatusLookupPort {
                        override fun publishedIds(ids: List<java.util.UUID>): Set<java.util.UUID> = ids.toSet()
                    }
                }
                single<PostTypeLookupPort> { postTypeLookupPort }
            })
        }
    }

    private fun mockCtx(): QueryResolvers.Trending.Context {
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.nodeRef(any<GlobalID<ViaductBlogPost>>()) } returns mockk<ViaductBlogPost>(relaxed = true)
        every { ctx.nodeRef(any<GlobalID<ViaductCheckedListPost>>()) } returns mockk<ViaductCheckedListPost>(relaxed = true)
        return ctx
    }

    @Test
    fun `returns one node ref per post returned by repository`() = runBlocking {
        val post1 = UUID.randomUUID()
        val post2 = UUID.randomUUID()
        val post3 = UUID.randomUUID()
        every { postViewRepository.getMostViewed(30) } returns listOf(post2, post1, post3)
        every { postTypeLookupPort.getPostTypes(any()) } returns mapOf(
            post1 to PostKind.BLOG_POST,
            post2 to PostKind.BLOG_POST,
            post3 to PostKind.CHECKLIST_POST,
        )

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        // One node ref is produced per post ID returned by the repository
        assertEquals(3, results.size)
    }

    @Test
    fun `returns empty list when no posts have been viewed`() = runBlocking {
        every { postViewRepository.getMostViewed(30) } returns emptyList()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `over-fetches candidates so filtering unpublished posts cannot under-fill the list`() = runBlocking {
        // Views are ranked before publication status is checked, so asking for exactly `limit`
        // candidates would return fewer than `limit` results once drafts are dropped. The
        // resolver asks for limit * OVER_FETCH_FACTOR instead. Only that call is stubbed, so a
        // request for any other count would throw and fail this test.
        every { postViewRepository.getMostViewed(15) } returns emptyList()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 5

        val results = TrendingQueryResolver().resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `drops posts not found in lookup (deleted posts with orphaned view records)`() = runBlocking {
        // If a post ID is missing from the type map the post was deleted but its view
        // records remain.  The resolver must skip it rather than creating a dead node-ref
        // that the BlogPost resolver would fail to resolve with NotFoundException.
        val postId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(30) } returns listOf(postId)
        every { postTypeLookupPort.getPostTypes(any()) } returns emptyMap()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        // The deleted post must be silently dropped, not included as a broken node-ref
        assertEquals(0, results.size)
    }

    @Test
    fun `handles mix of BlogPost and CheckedListPost IDs`() = runBlocking {
        val blogId = UUID.randomUUID()
        val checklistId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(30) } returns listOf(blogId, checklistId)
        every { postTypeLookupPort.getPostTypes(any()) } returns mapOf(
            blogId to PostKind.BLOG_POST,
            checklistId to PostKind.CHECKLIST_POST,
        )

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        assertEquals(2, results.size)
    }
}
