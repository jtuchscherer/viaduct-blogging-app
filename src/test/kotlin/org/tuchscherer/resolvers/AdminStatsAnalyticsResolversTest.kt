package org.tuchscherer.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.resolvers.AdminStatsTopPostsResolver
import org.tuchscherer.viadapp.resolvers.AdminStatsTotalViewsResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.AdminStatsResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.BlogPost as ViaductBlogPost
import java.util.UUID

class AdminStatsTotalViewsResolverTest {

    private lateinit var postViewRepository: PostViewRepository

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

    private fun mockCtx(): AdminStatsResolvers.TotalViews.Context =
        mockk<AdminStatsResolvers.TotalViews.Context>(relaxed = true)

    @Test
    fun `returns 0 when no views have been recorded`() = runBlocking {
        every { postViewRepository.getTotalViews() } returns 0L

        val result = AdminStatsTotalViewsResolver(postViewRepository).resolve(mockCtx())

        assertEquals(0, result)
    }

    @Test
    fun `returns total view count across all posts`() = runBlocking {
        every { postViewRepository.getTotalViews() } returns 42L

        val result = AdminStatsTotalViewsResolver(postViewRepository).resolve(mockCtx())

        assertEquals(42, result)
    }

    @Test
    fun `throws when total views exceed Int MAX_VALUE`() = runBlocking {
        every { postViewRepository.getTotalViews() } returns (Int.MAX_VALUE.toLong() + 1L)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                AdminStatsTotalViewsResolver(postViewRepository).resolve(mockCtx())
            }
        }
    }
}

class AdminStatsTopPostsResolverTest {

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

    private fun mockCtx(): AdminStatsResolvers.TopPosts.Context {
        val ctx = mockk<AdminStatsResolvers.TopPosts.Context>(relaxed = true)
        every { ctx.nodeRef(any<GlobalID<ViaductBlogPost>>()) } returns mockk<ViaductBlogPost>(relaxed = true)
        return ctx
    }

    @Test
    fun `returns empty list when no posts have been viewed`() = runBlocking {
        every { postViewRepository.getMostViewed(5) } returns emptyList()

        val result = AdminStatsTopPostsResolver(postViewRepository, postTypeLookupPort).resolve(mockCtx())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns one node ref per BlogPost in the top list`() = runBlocking {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { postViewRepository.getMostViewed(5) } returns listOf(id1, id2)
        every { postTypeLookupPort.getPostTypes(any()) } returns mapOf(
            id1 to PostKind.BLOG_POST,
            id2 to PostKind.BLOG_POST,
        )

        val result = AdminStatsTopPostsResolver(postViewRepository, postTypeLookupPort).resolve(mockCtx())

        assertEquals(2, result.size)
    }

    @Test
    fun `excludes CheckedListPost IDs from the result`() = runBlocking {
        val blogId = UUID.randomUUID()
        val checklistId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(5) } returns listOf(blogId, checklistId)
        every { postTypeLookupPort.getPostTypes(any()) } returns mapOf(
            blogId to PostKind.BLOG_POST,
            checklistId to PostKind.CHECKLIST_POST,
        )

        val result = AdminStatsTopPostsResolver(postViewRepository, postTypeLookupPort).resolve(mockCtx())

        assertEquals(1, result.size)
    }

    @Test
    fun `treats unknown post type as BlogPost`() = runBlocking {
        val unknownId = UUID.randomUUID()
        every { postViewRepository.getMostViewed(5) } returns listOf(unknownId)
        every { postTypeLookupPort.getPostTypes(any()) } returns emptyMap()

        val result = AdminStatsTopPostsResolver(postViewRepository, postTypeLookupPort).resolve(mockCtx())

        assertEquals(1, result.size)
    }
}
