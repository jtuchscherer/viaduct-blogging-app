package org.tuchscherer.analytics.resolvers

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
        every { postViewRepository.getMostViewed(10) } returns listOf(post2, post1, post3)
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
        every { postViewRepository.getMostViewed(10) } returns emptyList()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `passes limit argument to repository`() = runBlocking {
        // getMostViewed(5) is stubbed; calling getMostViewed(anything else) would throw,
        // which proves the resolver used the correct limit from context.arguments.
        every { postViewRepository.getMostViewed(5) } returns emptyList()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 5

        val results = TrendingQueryResolver().resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `uses BLOG_POST kind for posts not found in lookup`() = runBlocking {
        // If a post ID is missing from the type map (e.g. stale view data), the resolver
        // should treat it as BLOG_POST rather than silently dropping it.
        val postId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(10) } returns listOf(postId)
        every { postTypeLookupPort.getPostTypes(any()) } returns emptyMap()

        val ctx = mockCtx()
        every { ctx.arguments.limit } returns 10

        val results = TrendingQueryResolver().resolve(ctx)

        // The unknown post is treated as BlogPost rather than dropped
        assertEquals(1, results.size)
    }

    @Test
    fun `handles mix of BlogPost and CheckedListPost IDs`() = runBlocking {
        val blogId = UUID.randomUUID()
        val checklistId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(10) } returns listOf(blogId, checklistId)
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
