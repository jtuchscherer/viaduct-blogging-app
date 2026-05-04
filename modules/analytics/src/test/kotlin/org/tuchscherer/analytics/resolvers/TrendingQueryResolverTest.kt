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
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.UUID

class TrendingQueryResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postViewRepository: PostViewRepository
    private lateinit var postTypeLookupPort: PostTypeLookupPort

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

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

    // Build a minimal ViaductBlogPost node-ref stub so ctx.nodeRef can return a real GRT.
    private fun stubBlogPost(id: UUID): ViaductBlogPost = ViaductBlogPost.Builder(context)
        .id(context.globalIDFor(ViaductBlogPost.Reflection, id.toString()))
        .title("Stub")
        .content("Stub content")
        .createdAt("2025-01-01T00:00:00")
        .updatedAt("2025-01-01T00:00:00")
        .build()

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

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 10
        val stubPost = stubBlogPost(post1)
        val stubChecklist = mockk<ViaductCheckedListPost>(relaxed = true)
        every { ctx.nodeRef(any<viaduct.api.globalid.GlobalID<ViaductBlogPost>>()) } returns stubPost
        every { ctx.nodeRef(any<viaduct.api.globalid.GlobalID<ViaductCheckedListPost>>()) } returns stubChecklist

        val results = resolver.resolve(ctx)

        // One node ref is produced per post ID returned by the repository
        assertEquals(3, results.size)
    }

    @Test
    fun `returns empty list when no posts have been viewed`() = runBlocking {
        every { postViewRepository.getMostViewed(10) } returns emptyList()

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 10

        val results = resolver.resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `passes limit argument to repository`() = runBlocking {
        // getMostViewed(5) is stubbed; calling getMostViewed(anything else) would throw,
        // which proves the resolver used the correct limit from context.arguments.
        every { postViewRepository.getMostViewed(5) } returns emptyList()

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 5

        val results = resolver.resolve(ctx)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `uses BLOG_POST kind for posts not found in lookup`() = runBlocking {
        // If a post ID is missing from the type map (e.g. stale view data), the resolver
        // should treat it as BLOG_POST rather than silently dropping it.
        val postId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(10) } returns listOf(postId)
        every { postTypeLookupPort.getPostTypes(any()) } returns emptyMap()

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 10
        val stubPost = stubBlogPost(postId)
        every { ctx.nodeRef(any<viaduct.api.globalid.GlobalID<ViaductBlogPost>>()) } returns stubPost
        every { ctx.globalIDFor(ViaductBlogPost.Reflection, any()) } returns
            context.globalIDFor(ViaductBlogPost.Reflection, postId.toString())

        val results = resolver.resolve(ctx)

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

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 10
        val stubBlog = stubBlogPost(blogId)
        val stubChecklist = mockk<ViaductCheckedListPost>(relaxed = true)
        every { ctx.nodeRef(any<viaduct.api.globalid.GlobalID<ViaductBlogPost>>()) } returns stubBlog
        every { ctx.nodeRef(any<viaduct.api.globalid.GlobalID<ViaductCheckedListPost>>()) } returns stubChecklist
        every { ctx.globalIDFor(ViaductBlogPost.Reflection, any()) } returns
            context.globalIDFor(ViaductBlogPost.Reflection, blogId.toString())
        every { ctx.globalIDFor(ViaductCheckedListPost.Reflection, any()) } returns
            context.globalIDFor(ViaductCheckedListPost.Reflection, checklistId.toString())

        val results = resolver.resolve(ctx)

        assertEquals(2, results.size)
    }
}
