package org.tuchscherer.database.repositories

import org.tuchscherer.database.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.*

/**
 * Integration tests for PostRepository with H2 in-memory database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostRepositoryTest {

    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private lateinit var testUser: User

    @BeforeAll
    fun setupDatabase() {
        DatabaseTestHelper.setupDatabase()
        postRepository = ExposedPostRepository()
        userRepository = ExposedUserRepository()
    }

    @BeforeEach
    fun createTestUser() {
        testUser = userRepository.create(
            username = "testauthor",
            email = "author@example.com",
            name = "Test Author",
            passwordHash = "hash",
            salt = "salt"
        )
    }

    @AfterEach
    fun cleanDatabase() {
        DatabaseTestHelper.cleanDatabase()
    }

    @AfterAll
    fun tearDown() {
        DatabaseTestHelper.tearDownDatabase()
    }

    @Test
    fun `create post successfully`() {
        val post = postRepository.create(
            title = "Test Post",
            content = "This is test content",
            authorId = testUser.id
        )

        Assertions.assertNotNull(post)
        assertEquals("Test Post", post.title)
        assertEquals("This is test content", post.content)
        assertEquals(testUser.id, post.authorId)
    }

    @Test
    fun `findById returns post when exists`() {
        val created = postRepository.create(
            title = "Test Post",
            content = "Content",
            authorId = testUser.id
        )

        val found = postRepository.findById(created.id.value)

        Assertions.assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("Test Post", found.title)
    }

    @Test
    fun `findById returns null when post does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = postRepository.findById(nonExistentId)

        Assertions.assertNull(found)
    }

    @Test
    fun `findByIds returns map of matching posts and omits missing ids`() {
        val post1 = postRepository.create(title = "P1", content = "c", authorId = testUser.id)
        val post2 = postRepository.create(title = "P2", content = "c", authorId = testUser.id)
        val missingId = UUID.randomUUID()

        val result = postRepository.findByIds(listOf(post1.id.value, post2.id.value, missingId))

        assertEquals(2, result.size)
        assertEquals("P1", result[post1.id.value]?.title)
        assertEquals("P2", result[post2.id.value]?.title)
        assertFalse(result.containsKey(missingId))
    }

    @Test
    fun `findByIds returns empty map for empty input`() {
        assertTrue(postRepository.findByIds(emptyList()).isEmpty())
    }

    @Test
    fun `findByAuthorId returns all posts by author`() {
        postRepository.create(
            title = "Post 1",
            content = "Content 1",
            authorId = testUser.id
        )
        postRepository.create(
            title = "Post 2",
            content = "Content 2",
            authorId = testUser.id
        )

        val posts = postRepository.findByAuthorId(testUser.id)

        assertEquals(2, posts.size)
    }

    @Test
    fun `findByAuthorId returns empty list when author has no posts`() {
        val posts = postRepository.findByAuthorId(testUser.id)

        assertTrue(posts.isEmpty())
    }

    @Test
    fun `findAll returns all posts`() {
        postRepository.create(
            title = "Post 1",
            content = "Content 1",
            authorId = testUser.id
        )
        postRepository.create(
            title = "Post 2",
            content = "Content 2",
            authorId = testUser.id
        )

        val allPosts = postRepository.findAll()

        assertEquals(2, allPosts.size)
    }

    @Test
    fun `updateById modifies post fields`() {
        val post = postRepository.create(
            title = "Original Title",
            content = "Original Content",
            authorId = testUser.id
        )

        val postId = post.id.value

        val updated = postRepository.updateById(
            id = postId,
            title = "Updated Title",
            content = "Updated Content"
        )

        Assertions.assertNotNull(updated)
        assertEquals("Updated Title", updated!!.title)
        assertEquals("Updated Content", updated.content)

        // Verify the changes persisted
        val found = postRepository.findById(postId)
        Assertions.assertNotNull(found)
        assertEquals("Updated Title", found!!.title)
        assertEquals("Updated Content", found.content)
    }

    @Test
    fun `updateById with partial update modifies only specified fields`() {
        val post = postRepository.create(
            title = "Original Title",
            content = "Original Content",
            authorId = testUser.id
        )

        val postId = post.id.value

        // Update only title
        val updated = postRepository.updateById(
            id = postId,
            title = "New Title",
            content = null
        )

        Assertions.assertNotNull(updated)
        assertEquals("New Title", updated!!.title)
        assertEquals("Original Content", updated.content) // Content should remain unchanged
    }

    @Test
    fun `updateById returns null when post does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val result = postRepository.updateById(
            id = nonExistentId,
            title = "New Title"
        )

        Assertions.assertNull(result)
    }

    @Test
    fun `delete post removes from database`() {
        val post = postRepository.create(
            title = "Test Post",
            content = "Content",
            authorId = testUser.id
        )

        val deleted = postRepository.delete(post.id.value)

        assertTrue(deleted)
        Assertions.assertNull(postRepository.findById(post.id.value))
    }

    @Test
    fun `delete returns false when post does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val deleted = postRepository.delete(nonExistentId)

        assertFalse(deleted)
    }

    @Test
    fun `delete post cascades to comments and likes`() {
        val commentRepository = ExposedCommentRepository()
        val likeRepository = ExposedLikeRepository()

        val post = postRepository.create(title = "Post", content = "Content", authorId = testUser.id)
        commentRepository.create(
            content = "A comment",
            postId = post.id,
            authorId = testUser.id,
            createdAt = LocalDateTime.now()
        )
        likeRepository.create(postId = post.id, userId = testUser.id, createdAt = LocalDateTime.now())

        val deleted = postRepository.delete(post.id.value)

        assertTrue(deleted)
        Assertions.assertNull(postRepository.findById(post.id.value))
        assertEquals(0, commentRepository.countByPostId(post.id.value))
        assertEquals(0, likeRepository.countByPostId(post.id.value))
    }

    @Test
    fun `count returns total number of posts`() {
        postRepository.create(
            title = "Post 1",
            content = "Content 1",
            authorId = testUser.id
        )
        postRepository.create(
            title = "Post 2",
            content = "Content 2",
            authorId = testUser.id
        )

        val count = postRepository.count()

        assertEquals(2, count)
    }

    @Test
    fun `findPage returns limited slice ordered by createdAt desc`() {
        postRepository.create(title = "Oldest", content = "c", authorId = testUser.id)
        Thread.sleep(10)
        postRepository.create(title = "Middle", content = "c", authorId = testUser.id)
        Thread.sleep(10)
        postRepository.create(title = "Newest", content = "c", authorId = testUser.id)

        val page = postRepository.findPage(limit = 2, offset = 0)

        assertEquals(2, page.size)
        assertEquals("Newest", page[0].title)
        assertEquals("Middle", page[1].title)
    }

    @Test
    fun `findPage with offset skips earlier results`() {
        postRepository.create(title = "Oldest", content = "c", authorId = testUser.id)
        Thread.sleep(10)
        postRepository.create(title = "Middle", content = "c", authorId = testUser.id)
        Thread.sleep(10)
        postRepository.create(title = "Newest", content = "c", authorId = testUser.id)

        val page = postRepository.findPage(limit = 2, offset = 1)

        assertEquals(2, page.size)
        assertEquals("Middle", page[0].title)
        assertEquals("Oldest", page[1].title)
    }

    @Test
    fun `findPage returns empty list when offset exceeds total`() {
        postRepository.create(title = "Only Post", content = "c", authorId = testUser.id)

        val page = postRepository.findPage(limit = 10, offset = 5)

        assertTrue(page.isEmpty())
    }

    @Test
    fun `countByAuthor returns posts count for specific author`() {
        postRepository.create(
            title = "Post 1",
            content = "Content 1",
            authorId = testUser.id
        )
        postRepository.create(
            title = "Post 2",
            content = "Content 2",
            authorId = testUser.id
        )

        val count = postRepository.countByAuthor(testUser.id)

        assertEquals(2, count)
    }

    @Test
    fun `getAuthorIdsByPostIds returns map of post ID to author ID`() {
        val post1 = postRepository.create(title = "Post 1", content = "c", authorId = testUser.id)
        val post2 = postRepository.create(title = "Post 2", content = "c", authorId = testUser.id)

        val result = postRepository.getAuthorIdsByPostIds(listOf(post1.id.value, post2.id.value))

        assertEquals(2, result.size)
        assertEquals(testUser.id.value, result[post1.id.value])
        assertEquals(testUser.id.value, result[post2.id.value])
    }

    @Test
    fun `getAuthorIdsByPostIds returns empty map for empty input`() {
        val result = postRepository.getAuthorIdsByPostIds(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `update persists post field changes`() {
        val post = postRepository.create(
            title = "Original Title",
            content = "Original Content",
            authorId = testUser.id
        )

        org.jetbrains.exposed.v1.jdbc.transactions.transaction {
            post.title = "Updated Title"
            post.content = "Updated Content"
        }
        postRepository.update(post)

        val found = postRepository.findById(post.id.value)
        Assertions.assertNotNull(found)
        assertEquals("Updated Title", found!!.title)
        assertEquals("Updated Content", found.content)
    }

    @Test
    fun `countByAuthorId returns post count for author`() {
        postRepository.create(title = "Post 1", content = "c", authorId = testUser.id)
        postRepository.create(title = "Post 2", content = "c", authorId = testUser.id)

        val count = postRepository.countByAuthorId(testUser.id.value)

        assertEquals(2L, count)
    }

    @Test
    fun `countByAuthorId returns zero when author has no posts`() {
        val count = postRepository.countByAuthorId(UUID.randomUUID())

        assertEquals(0L, count)
    }

    @Test
    fun `deleteByAuthorId removes all posts by author and cascades dependents`() {
        val commentRepository = ExposedCommentRepository()
        val likeRepository = ExposedLikeRepository()

        val post1 = postRepository.create(title = "Post 1", content = "c", authorId = testUser.id)
        val post2 = postRepository.create(title = "Post 2", content = "c", authorId = testUser.id)

        commentRepository.create(content = "comment", postId = post1.id, authorId = testUser.id)
        likeRepository.create(postId = post2.id, userId = testUser.id)

        val deleted = postRepository.deleteByAuthorId(testUser.id.value)

        assertEquals(2, deleted)
        Assertions.assertNull(postRepository.findById(post1.id.value))
        Assertions.assertNull(postRepository.findById(post2.id.value))
        assertEquals(0, commentRepository.countByPostId(post1.id.value))
        assertEquals(0, likeRepository.countByPostId(post2.id.value))
    }

    @Test
    fun `deleteByAuthorId returns zero when author has no posts`() {
        val deleted = postRepository.deleteByAuthorId(UUID.randomUUID())

        assertEquals(0, deleted)
    }

    @Test
    fun `updateById with both fields null leaves post unchanged`() {
        val post = postRepository.create(
            title = "Original Title",
            content = "Original Content",
            authorId = testUser.id
        )

        val updated = postRepository.updateById(id = post.id.value, title = null, content = null)

        Assertions.assertNotNull(updated)
        assertEquals("Original Title", updated!!.title)
        assertEquals("Original Content", updated.content)
    }
}
