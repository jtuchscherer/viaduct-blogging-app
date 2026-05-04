package org.tuchscherer.analytics.resolvers

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolvers.CheckedListPostViewCountBatchResolver
import org.tuchscherer.viadapp.analytics.resolverbases.CheckedListPostResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import java.util.UUID

class CheckedListPostViewCountBatchResolverTest {

    private lateinit var postViewRepository: PostViewRepository
    private val postId1 = UUID.randomUUID()
    private val postId2 = UUID.randomUUID()

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

    private fun mockContext(postId: UUID): CheckedListPostResolvers.ViewCount.Context {
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns postId.toString()
        val ctx = mockk<CheckedListPostResolvers.ViewCount.Context>(relaxed = true)
        coEvery { ctx.getObjectValue().getId() } returns globalId
        return ctx
    }

    @Test
    fun `returns correct view count for each post in batch`() = runBlocking {
        val resolver = CheckedListPostViewCountBatchResolver()
        every { postViewRepository.bulkGetViewCounts(any()) } returns
            mapOf(postId1 to 7L, postId2 to 2L)

        val results = resolver.batchResolve(listOf(mockContext(postId1), mockContext(postId2)))

        assertEquals(2, results.size)
        assertFalse(results[0].isError)
        assertFalse(results[1].isError)
        assertEquals(7, results[0].get())
        assertEquals(2, results[1].get())
    }

    @Test
    fun `returns 0 for a checkedlist post that has never been viewed`() = runBlocking {
        val resolver = CheckedListPostViewCountBatchResolver()
        every { postViewRepository.bulkGetViewCounts(any()) } returns emptyMap()

        val results = resolver.batchResolve(listOf(mockContext(postId1)))

        assertEquals(1, results.size)
        assertFalse(results[0].isError)
        assertEquals(0, results[0].get())
    }

    @Test
    fun `returns default 0 for every post in batch when repository has no data`() = runBlocking {
        val resolver = CheckedListPostViewCountBatchResolver()
        every { postViewRepository.bulkGetViewCounts(any()) } returns emptyMap()

        val results = resolver.batchResolve(listOf(mockContext(postId1), mockContext(postId2)))

        assertEquals(2, results.size)
        assertEquals(0, results[0].get())
        assertEquals(0, results[1].get())
    }
}
