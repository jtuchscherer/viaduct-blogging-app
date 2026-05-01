package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.util.UUID

class TrendingQueryResolverTest : DefaultAbstractResolverTestBase() {

    private lateinit var postViewRepository: PostViewRepository

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    @BeforeEach
    fun setup() {
        postViewRepository = mockk<PostViewRepository>()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostViewRepository> { postViewRepository }
            })
        }
    }

    // Build a minimal ViaductBlogPost node-ref stub so ctx.nodeRef(any()) can return a real GRT.
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

        val resolver = TrendingQueryResolver()
        val ctx = mockk<QueryResolvers.Trending.Context>(relaxed = true)
        every { ctx.arguments.limit } returns 10
        // nodeRef has a generic return type T — MockK's relaxed auto-stub can't infer the
        // concrete type at runtime, so we provide an explicit stub returning a real GRT.
        val stubPost = stubBlogPost(post1)
        every { ctx.nodeRef(any<GlobalID<ViaductBlogPost>>()) } returns stubPost

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
}
