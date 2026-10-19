package org.tuchscherer.database.repositories

import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.PostType
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostProjectionRepositoryTest {
    private val repository = ExposedPostRepository()
    private lateinit var authorId: UUID

    @BeforeAll
    fun setupDatabase() = DatabaseTestHelper.setupDatabase()

    @BeforeEach
    fun setupAuthor() {
        authorId = ExposedUserRepository().create("author", "a@example.com", "Author", "hash", "salt").id.value
    }

    @AfterEach
    fun cleanDatabase() = DatabaseTestHelper.cleanDatabase()

    @AfterAll
    fun tearDownDatabase() = DatabaseTestHelper.tearDownDatabase()

    @Test
    fun `title projection excludes content column from SQL and returned values`() {
        val post = repository.create("Title", "Large body", authorId)
        val sql = mutableListOf<String>()
        val projected = transaction {
            defaultLogger.addLogger(captureSql(sql))
            repository.findProjectedByIds(listOf(post.id.value), setOf(PostField.TITLE))
        }.values.single()

        assertEquals(post.id.value, projected.id)
        assertEquals("Title", projected.title)
        assertNull(projected.content)
        assertEquals(setOf(PostField.TITLE), projected.fields)
        val select = sql.single { it.startsWith("SELECT", ignoreCase = true) }.substringBefore(" FROM ").lowercase()
        assertTrue("title" in select, select)
        assertFalse("content" in select, select)
    }

    @Test
    fun `all scalar columns are available when explicitly selected`() {
        val createdAt = LocalDateTime.of(2026, 10, 19, 12, 0, 0, 123456789)
        val updatedAt = createdAt.plusHours(1).withNano(987654321)
        val created = repository.create("Title", "Body", authorId, createdAt = createdAt, updatedAt = updatedAt)
        // H2 rounds timestamps to microseconds; the newly created DAO retains input nanos.
        // Compare both read paths against persisted values, independent of OS clock precision.
        val post = requireNotNull(repository.findById(created.id.value))

        val projected = repository.findPublishedProjection(PostField.entries.toSet()).single()

        assertEquals(post.title, projected.title)
        assertEquals(post.content, projected.content)
        assertEquals(post.status, projected.status)
        assertEquals(post.publishedAt, projected.publishedAt)
        assertEquals(post.createdAt, projected.createdAt)
        assertEquals(post.updatedAt, projected.updatedAt)
    }

    @Test
    fun `batched author lookup excludes content from SQL`() {
        val post = repository.create("Title", "Large body", authorId)
        val sql = mutableListOf<String>()
        val authors = transaction {
            defaultLogger.addLogger(captureSql(sql))
            repository.getAuthorIdsByPostIds(listOf(post.id.value, UUID.randomUUID()))
        }

        assertEquals(mapOf(post.id.value to authorId), authors)
        val select = sql.single { it.startsWith("SELECT", ignoreCase = true) }.substringBefore(" FROM ").lowercase()
        assertTrue("author_id" in select, select)
        assertFalse("content" in select, select)
    }

    @Test
    fun `empty projection still returns published identity without loading content`() {
        val post = repository.create("Title", "Body", authorId)

        val projected = repository.findPublishedProjection(emptySet()).single()

        assertEquals(post.id.value, projected.id)
        assertEquals(PostType.BLOG_POST, projected.postType)
        assertTrue(projected.fields.isEmpty())
        assertNull(projected.title)
        assertNull(projected.content)
    }

    @Test
    fun `batched node projection includes visibility metadata but omits unrequested content`() {
        val post = repository.create("Private", "Secret body", authorId, status = PostStatus.DRAFT)
        val missing = UUID.randomUUID()
        val fields = setOf(PostField.TITLE)
        val rows = repository.findProjectedByIds(listOf(post.id.value, missing), fields)

        assertEquals(setOf(post.id.value), rows.keys)
        val projected = requireNotNull(rows[post.id.value])
        assertEquals("Private", projected.title)
        assertEquals(authorId, projected.authorId)
        assertEquals(PostStatus.DRAFT, projected.status)
        assertNull(projected.content)
        assertEquals(fields, projected.fields)
        assertTrue(repository.findProjectedByIds(emptyList(), fields).isEmpty())
    }

    @Test
    fun `public projection excludes drafts and checked lists`() {
        val published = repository.create("Public", "Body", authorId)
        repository.create("Draft", "Secret", authorId, status = PostStatus.DRAFT)
        val checklist = repository.create("Checklist", "", authorId)
        transaction { checklist.postType = PostType.CHECKED_LIST }

        val projected = repository.findPublishedProjection(emptySet())

        assertEquals(listOf(published.id.value), projected.map { it.id })
        assertEquals(1L, repository.count())
    }

    @Test
    fun `publication ordering and pagination remain stable across field projections`() {
        val time = LocalDateTime.of(2026, 10, 19, 12, 0)
        val old = repository.create("Old", "Body", authorId, createdAt = time.minusDays(1))
        val first = repository.create("A", "Body", authorId, createdAt = time)
        val second = repository.create("B", "Body", authorId, createdAt = time)
        val expected = listOf(first.id.value, second.id.value).sortedBy { it.toString() } + old.id.value

        val idsOnly = repository.findPublishedProjection(emptySet()).map { it.id }
        val withContent = repository.findPublishedProjection(setOf(PostField.CONTENT)).map { it.id }
        val page = repository.findPublishedProjection(setOf(PostField.TITLE), limit = 1, offset = 1)

        assertEquals(expected, idsOnly)
        assertEquals(idsOnly, withContent)
        assertEquals(listOf(expected[1]), page.map { it.id })
        assertTrue(repository.findPublishedProjection(emptySet(), limit = 2, offset = 3).isEmpty())
    }

    private fun captureSql(statements: MutableList<String>) = object : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            statements += context.sql(transaction)
        }
    }
}
