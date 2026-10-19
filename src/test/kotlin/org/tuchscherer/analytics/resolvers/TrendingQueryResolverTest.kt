package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.analytics.port.PostStatusLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.QueryResolvers
import org.tuchscherer.viadapp.analytics.resolvers.TrendingQueryResolver
import viaduct.java.api.globalid.GlobalID
import viaduct.java.api.reflect.Type
import viaduct.java.grts.BlogPost
import viaduct.java.grts.CheckedListPost
import viaduct.java.grts.Post
import java.util.UUID

class TrendingQueryResolverTest {
    private val repository = mockk<PostViewRepository>()
    private val typeLookup = mockk<PostTypeLookupPort>()
    private val statusLookup = mockk<PostStatusLookupPort>()
    private val resolver = TrendingQueryResolver(repository, typeLookup, statusLookup)

    private fun context(limit: Int? = 10): QueryResolvers.Trending.Context {
        val ctx = mockk<QueryResolvers.Trending.Context>()
        every { ctx.arguments.limit } returns limit
        return ctx
    }

    private fun reference(ctx: QueryResolvers.Trending.Context, id: UUID, kind: PostKind): Post =
        when (kind) {
            PostKind.BLOG_POST -> {
                val globalId = mockk<GlobalID<BlogPost>>()
                val result = mockk<BlogPost>()
                every { ctx.globalIDFor(any<Type<BlogPost>>(), id.toString()) } returns globalId
                every { ctx.ref(globalId) } returns result
                result
            }
            PostKind.CHECKLIST_POST -> {
                val globalId = mockk<GlobalID<CheckedListPost>>()
                val result = mockk<CheckedListPost>()
                every { ctx.globalIDFor(any<Type<CheckedListPost>>(), id.toString()) } returns globalId
                every { ctx.ref(globalId) } returns result
                result
            }
        }

    @Test
    fun `returns one node ref per post returned by repository in ranking order`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val third = UUID.randomUUID()
        every { repository.getMostViewed(30) } returns listOf(second, first, third)
        every { statusLookup.publishedIds(listOf(second, first, third)) } returns setOf(first, second, third)
        every { typeLookup.getPostTypes(listOf(second, first, third)) } returns mapOf(
            first to PostKind.BLOG_POST, second to PostKind.BLOG_POST, third to PostKind.CHECKLIST_POST,
        )
        val ctx = context()
        val secondPost = reference(ctx, second, PostKind.BLOG_POST)
        val firstPost = reference(ctx, first, PostKind.BLOG_POST)
        val thirdPost = reference(ctx, third, PostKind.CHECKLIST_POST)

        assertEquals(listOf(secondPost, firstPost, thirdPost), resolver.resolve(ctx).join())
    }

    @Test
    fun `returns empty list when no posts have been viewed`() {
        every { repository.getMostViewed(30) } returns emptyList()
        assertTrue(resolver.resolve(context()).join().isEmpty())
    }

    @Test
    fun `over-fetches candidates so filtering unpublished posts cannot under-fill the list`() {
        every { repository.getMostViewed(15) } returns emptyList()
        assertTrue(resolver.resolve(context(5)).join().isEmpty())
    }

    @Test
    fun `drops posts not found in lookup (deleted posts with orphaned view records)`() {
        val deleted = UUID.randomUUID()
        every { repository.getMostViewed(30) } returns listOf(deleted)
        every { statusLookup.publishedIds(listOf(deleted)) } returns setOf(deleted)
        every { typeLookup.getPostTypes(listOf(deleted)) } returns emptyMap()
        assertTrue(resolver.resolve(context()).join().isEmpty())
    }

    @Test
    fun `handles mix of BlogPost and CheckedListPost IDs`() {
        val blog = UUID.randomUUID()
        val checklist = UUID.randomUUID()
        val ids = listOf(blog, checklist)
        every { repository.getMostViewed(30) } returns ids
        every { statusLookup.publishedIds(ids) } returns ids.toSet()
        every { typeLookup.getPostTypes(ids) } returns mapOf(blog to PostKind.BLOG_POST, checklist to PostKind.CHECKLIST_POST)
        val ctx = context()
        val blogRef = reference(ctx, blog, PostKind.BLOG_POST)
        val checklistRef = reference(ctx, checklist, PostKind.CHECKLIST_POST)
        assertEquals(listOf(blogRef, checklistRef), resolver.resolve(ctx).join())
    }

    @Test
    fun `null limit retains the default of ten`() {
        every { repository.getMostViewed(30) } returns emptyList()
        assertTrue(resolver.resolve(context(null)).join().isEmpty())
    }

    @Test
    fun `filters drafts before applying requested limit`() {
        val draft = UUID.randomUUID()
        val blog = UUID.randomUUID()
        val checklist = UUID.randomUUID()
        val ids = listOf(draft, blog, checklist)
        every { repository.getMostViewed(3) } returns ids
        every { statusLookup.publishedIds(ids) } returns setOf(blog, checklist)
        every { typeLookup.getPostTypes(listOf(blog)) } returns mapOf(blog to PostKind.BLOG_POST)
        val ctx = context(1)
        val blogRef = reference(ctx, blog, PostKind.BLOG_POST)
        assertEquals(listOf(blogRef), resolver.resolve(ctx).join())
    }

    @Test
    fun `returns no references when every candidate is a draft`() {
        val draft = UUID.randomUUID()
        every { repository.getMostViewed(30) } returns listOf(draft)
        every { statusLookup.publishedIds(listOf(draft)) } returns emptySet()
        assertTrue(resolver.resolve(context()).join().isEmpty())
    }
}
