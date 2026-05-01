package org.tuchscherer.analytics.repositories

import org.tuchscherer.analytics.AnalyticsDatabaseTestHelper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostViewRepositoryTest {

    private lateinit var repo: PostViewRepository

    @BeforeAll
    fun setupDatabase() {
        AnalyticsDatabaseTestHelper.setupDatabase()
        repo = ExposedPostViewRepository()
    }

    @BeforeEach
    fun clean() {
        AnalyticsDatabaseTestHelper.cleanDatabase()
    }

    @AfterAll
    fun tearDown() {
        AnalyticsDatabaseTestHelper.tearDownDatabase()
    }

    @Test
    fun `first increment creates row with viewCount 1`() {
        val postId = UUID.randomUUID()

        repo.incrementViewCount(postId)

        val counts = repo.bulkGetViewCounts(listOf(postId))
        assertEquals(1L, counts[postId])
    }

    @Test
    fun `second increment upserts viewCount to 2`() {
        val postId = UUID.randomUUID()

        repo.incrementViewCount(postId)
        repo.incrementViewCount(postId)

        val counts = repo.bulkGetViewCounts(listOf(postId))
        assertEquals(2L, counts[postId])
    }

    @Test
    fun `bulkGetViewCounts returns correct map for multiple posts`() {
        val post1 = UUID.randomUUID()
        val post2 = UUID.randomUUID()
        val post3 = UUID.randomUUID()

        repo.incrementViewCount(post1)
        repo.incrementViewCount(post1)
        repo.incrementViewCount(post2)

        val counts = repo.bulkGetViewCounts(listOf(post1, post2, post3))

        assertEquals(2L, counts[post1])
        assertEquals(1L, counts[post2])
        // post3 never viewed — absent from map
        assertFalse(counts.containsKey(post3))
    }

    @Test
    fun `bulkGetViewCounts returns empty map for empty input`() {
        val result = repo.bulkGetViewCounts(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMostViewed returns posts ordered by view count descending`() {
        val post1 = UUID.randomUUID()
        val post2 = UUID.randomUUID()
        val post3 = UUID.randomUUID()

        // post2 gets 3 views, post1 gets 2, post3 gets 1
        repeat(2) { repo.incrementViewCount(post1) }
        repeat(3) { repo.incrementViewCount(post2) }
        repeat(1) { repo.incrementViewCount(post3) }

        val result = repo.getMostViewed(10)

        assertEquals(3, result.size)
        assertEquals(post2, result[0])
        assertEquals(post1, result[1])
        assertEquals(post3, result[2])
    }

    @Test
    fun `getMostViewed respects the limit`() {
        val ids = List(5) { UUID.randomUUID() }
        ids.forEachIndexed { index, id ->
            repeat(index + 1) { repo.incrementViewCount(id) }
        }

        val result = repo.getMostViewed(3)

        assertEquals(3, result.size)
    }

    @Test
    fun `getMostViewed returns empty list when no views recorded`() {
        val result = repo.getMostViewed(10)
        assertTrue(result.isEmpty())
    }
}
