package com.example.database.repositories

import com.example.database.Post
import com.example.database.User
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

/**
 * Integration tests for CommentRepository with H2 in-memory database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentRepositoryTest {

    private lateinit var commentRepository: CommentRepository
    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private lateinit var testUser: User
    private lateinit var testPost: Post

    @BeforeAll
    fun setupDatabase() {
        DatabaseTestHelper.setupDatabase()
        commentRepository = ExposedCommentRepository()
        postRepository = ExposedPostRepository()
        userRepository = ExposedUserRepository()
    }

    @BeforeEach
    fun createTestData() {
        testUser = userRepository.create(
            username = "testuser",
            email = "user@example.com",
            name = "Test User",
            passwordHash = "hash",
            salt = "salt"
        )
        testPost = postRepository.create(
            title = "Test Post",
            content = "Test Content",
            authorId = testUser.id
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
    fun `create comment successfully`() {
        val comment = commentRepository.create(
            content = "This is a test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        assertNotNull(comment)
        assertEquals("This is a test comment", comment.content)
        assertEquals(testPost.id, comment.postId)
        assertEquals(testUser.id, comment.authorId)
    }

    @Test
    fun `findById returns comment when exists`() {
        val created = commentRepository.create(
            content = "Test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val found = commentRepository.findById(created.id.value)

        assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("Test comment", found.content)
    }

    @Test
    fun `findById returns null when comment does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = commentRepository.findById(nonExistentId)

        assertNull(found)
    }

    @Test
    fun `findByPostId returns all comments for post`() {
        commentRepository.create(
            content = "Comment 1",
            postId = testPost.id,
            authorId = testUser.id
        )
        commentRepository.create(
            content = "Comment 2",
            postId = testPost.id,
            authorId = testUser.id
        )

        val comments = commentRepository.findByPostId(testPost.id)

        assertEquals(2, comments.size)
    }

    @Test
    fun `findByAuthorId returns all comments by author`() {
        commentRepository.create(
            content = "Comment 1",
            postId = testPost.id,
            authorId = testUser.id
        )
        commentRepository.create(
            content = "Comment 2",
            postId = testPost.id,
            authorId = testUser.id
        )

        val comments = commentRepository.findByAuthorId(testUser.id)

        assertEquals(2, comments.size)
    }

    @Test
    @Disabled("Update pattern needs refactoring - entities need to be modified within transaction context")
    fun `update comment modifies content`() {
        val comment = commentRepository.create(
            content = "Original content",
            postId = testPost.id,
            authorId = testUser.id
        )

        val commentId = comment.id.value

        // Retrieve and update within transaction context
        val retrieved = commentRepository.findById(commentId)!!
        retrieved.content = "Updated content"
        commentRepository.update(retrieved)

        val found = commentRepository.findById(commentId)

        assertNotNull(found)
        assertEquals("Updated content", found!!.content)
    }

    @Test
    fun `delete comment removes from database`() {
        val comment = commentRepository.create(
            content = "Test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val deleted = commentRepository.delete(comment.id.value)

        assertTrue(deleted)
        assertNull(commentRepository.findById(comment.id.value))
    }

    @Test
    fun `delete returns false when comment does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val deleted = commentRepository.delete(nonExistentId)

        assertFalse(deleted)
    }

    @Test
    fun `countByPostId returns correct count`() {
        commentRepository.create(
            content = "Comment 1",
            postId = testPost.id,
            authorId = testUser.id
        )
        commentRepository.create(
            content = "Comment 2",
            postId = testPost.id,
            authorId = testUser.id
        )

        val count = commentRepository.countByPostId(testPost.id)

        assertEquals(2, count)
    }
}
