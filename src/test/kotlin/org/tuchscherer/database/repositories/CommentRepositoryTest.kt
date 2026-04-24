package org.tuchscherer.database.repositories

import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
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

        Assertions.assertNotNull(comment)
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

        Assertions.assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("Test comment", found.content)
    }

    @Test
    fun `findById returns null when comment does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = commentRepository.findById(nonExistentId)

        Assertions.assertNull(found)
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

        val comments = commentRepository.findByPostId(testPost.id.value)

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
    fun `delete comment removes from database`() {
        val comment = commentRepository.create(
            content = "Test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val deleted = commentRepository.delete(comment.id.value)

        assertTrue(deleted)
        Assertions.assertNull(commentRepository.findById(comment.id.value))
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

        val count = commentRepository.countByPostId(testPost.id.value)

        assertEquals(2, count)
    }

    @Test
    fun `findPage returns correct slice`() {
        for (i in 1..5) {
            commentRepository.create(
                content = "Page comment $i",
                postId = testPost.id,
                authorId = testUser.id
            )
        }

        val page = commentRepository.findPage(limit = 2, offset = 0)

        assertEquals(2, page.size)
    }

    @Test
    fun `findPage respects offset`() {
        for (i in 1..5) {
            commentRepository.create(
                content = "Offset comment $i",
                postId = testPost.id,
                authorId = testUser.id
            )
        }

        val firstPage = commentRepository.findPage(limit = 2, offset = 0)
        val secondPage = commentRepository.findPage(limit = 2, offset = 2)

        assertEquals(2, secondPage.size)
        val firstPageIds = firstPage.map { it.id.value }.toSet()
        val secondPageIds = secondPage.map { it.id.value }.toSet()
        assertTrue(firstPageIds.intersect(secondPageIds).isEmpty())
    }

    @Test
    fun `findPage beyond end returns empty list`() {
        commentRepository.create(
            content = "Only comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val page = commentRepository.findPage(limit = 10, offset = 100)

        assertTrue(page.isEmpty())
    }

    @Test
    fun `findByPostId by UUID returns comments for post`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val comments = commentRepository.findByPostId(testPost.id.value)

        assertEquals(2, comments.size)
    }

    @Test
    fun `findByPostId by UUID returns empty list when no comments`() {
        val comments = commentRepository.findByPostId(UUID.randomUUID())

        assertTrue(comments.isEmpty())
    }

    @Test
    fun `update persists comment content changes`() {
        val comment = commentRepository.create(
            content = "Original content",
            postId = testPost.id,
            authorId = testUser.id
        )

        transaction { comment.content = "Updated content" }
        commentRepository.update(comment)

        val found = commentRepository.findById(comment.id.value)
        Assertions.assertNotNull(found)
        assertEquals("Updated content", found!!.content)
    }

    @Test
    fun `countByPostId by UUID returns correct count`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val count = commentRepository.countByPostId(testPost.id.value)

        assertEquals(2L, count)
    }

    @Test
    fun `count returns total number of comments`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val count = commentRepository.count()

        assertEquals(2L, count)
    }

    @Test
    fun `findAll returns all comments`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val all = commentRepository.findAll()

        assertEquals(2, all.size)
    }

    @Test
    fun `countByUserId returns comment count for user`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val count = commentRepository.countByUserId(testUser.id.value)

        assertEquals(2L, count)
    }

    @Test
    fun `countByUserId returns zero for user with no comments`() {
        val count = commentRepository.countByUserId(UUID.randomUUID())

        assertEquals(0L, count)
    }

    @Test
    fun `deleteByUserId removes all comments by user`() {
        commentRepository.create(content = "Comment 1", postId = testPost.id, authorId = testUser.id)
        commentRepository.create(content = "Comment 2", postId = testPost.id, authorId = testUser.id)

        val deleted = commentRepository.deleteByUserId(testUser.id.value)

        assertEquals(2, deleted)
        assertEquals(0L, commentRepository.countByUserId(testUser.id.value))
    }

    @Test
    fun `deleteByUserId returns zero when user has no comments`() {
        val deleted = commentRepository.deleteByUserId(UUID.randomUUID())

        assertEquals(0, deleted)
    }

    @Test
    fun `getAuthorForComment returns author when comment exists`() {
        val comment = commentRepository.create(
            content = "Test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val author = commentRepository.getAuthorForComment(comment.id.value)

        Assertions.assertNotNull(author)
        assertEquals(testUser.id.value, author!!.id.value)
    }

    @Test
    fun `getPostForComment returns post when comment exists`() {
        val comment = commentRepository.create(
            content = "Test comment",
            postId = testPost.id,
            authorId = testUser.id
        )

        val post = commentRepository.getPostForComment(comment.id.value)

        Assertions.assertNotNull(post)
        assertEquals(testPost.id.value, post!!.id.value)
    }
}
