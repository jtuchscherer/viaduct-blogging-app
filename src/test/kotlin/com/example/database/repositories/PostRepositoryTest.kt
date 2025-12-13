package com.example.database.repositories

import com.example.database.User
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
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

        assertNotNull(post)
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

        assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("Test Post", found.title)
    }

    @Test
    fun `findById returns null when post does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = postRepository.findById(nonExistentId)

        assertNull(found)
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
    @Disabled("Update pattern needs refactoring - entities need to be modified within transaction context")
    fun `update post modifies fields`() {
        val post = postRepository.create(
            title = "Original Title",
            content = "Original Content",
            authorId = testUser.id
        )

        val postId = post.id.value

        // Retrieve and update within transaction context
        val retrieved = postRepository.findById(postId)!!
        retrieved.title = "Updated Title"
        retrieved.content = "Updated Content"
        postRepository.update(retrieved)

        val found = postRepository.findById(postId)

        assertNotNull(found)
        assertEquals("Updated Title", found!!.title)
        assertEquals("Updated Content", found.content)
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

        assertNotNull(updated)
        assertEquals("Updated Title", updated!!.title)
        assertEquals("Updated Content", updated.content)

        // Verify the changes persisted
        val found = postRepository.findById(postId)
        assertNotNull(found)
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

        assertNotNull(updated)
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

        assertNull(result)
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
        assertNull(postRepository.findById(post.id.value))
    }

    @Test
    fun `delete returns false when post does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val deleted = postRepository.delete(nonExistentId)

        assertFalse(deleted)
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
}
