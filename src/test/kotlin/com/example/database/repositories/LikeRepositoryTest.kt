package com.example.database.repositories

import com.example.database.Post
import com.example.database.User
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

/**
 * Integration tests for LikeRepository with H2 in-memory database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LikeRepositoryTest {

    private lateinit var likeRepository: LikeRepository
    private lateinit var postRepository: PostRepository
    private lateinit var userRepository: UserRepository
    private lateinit var testUser: User
    private lateinit var testPost: Post

    @BeforeAll
    fun setupDatabase() {
        DatabaseTestHelper.setupDatabase()
        likeRepository = ExposedLikeRepository()
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
    fun `create like successfully`() {
        val like = likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        assertNotNull(like)
        assertEquals(testPost.id, like.postId)
        assertEquals(testUser.id, like.userId)
    }

    @Test
    fun `findById returns like when exists`() {
        val created = likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        val found = likeRepository.findById(created.id.value)

        assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
    }

    @Test
    fun `findById returns null when like does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = likeRepository.findById(nonExistentId)

        assertNull(found)
    }

    @Test
    fun `findByPostId returns all likes for post`() {
        val user2 = userRepository.create(
            username = "user2",
            email = "user2@example.com",
            name = "User 2",
            passwordHash = "hash",
            salt = "salt"
        )

        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )
        likeRepository.create(
            postId = testPost.id,
            userId = user2.id
        )

        val likes = likeRepository.findByPostId(testPost.id)

        assertEquals(2, likes.size)
    }

    @Test
    fun `findByUserId returns all likes by user`() {
        val post2 = postRepository.create(
            title = "Post 2",
            content = "Content 2",
            authorId = testUser.id
        )

        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )
        likeRepository.create(
            postId = post2.id,
            userId = testUser.id
        )

        val likes = likeRepository.findByUserId(testUser.id)

        assertEquals(2, likes.size)
    }

    @Test
    fun `findByPostAndUser returns like when exists`() {
        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        val found = likeRepository.findByPostAndUser(testPost.id, testUser.id)

        assertNotNull(found)
        assertEquals(testPost.id, found!!.postId)
        assertEquals(testUser.id, found.userId)
    }

    @Test
    fun `findByPostAndUser returns null when like does not exist`() {
        val found = likeRepository.findByPostAndUser(testPost.id, testUser.id)

        assertNull(found)
    }

    @Test
    fun `existsByPostAndUser returns true when like exists`() {
        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        val exists = likeRepository.existsByPostAndUser(testPost.id, testUser.id)

        assertTrue(exists)
    }

    @Test
    fun `existsByPostAndUser returns false when like does not exist`() {
        val exists = likeRepository.existsByPostAndUser(testPost.id, testUser.id)

        assertFalse(exists)
    }

    @Test
    fun `delete like removes from database`() {
        val like = likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        val deleted = likeRepository.delete(like.id.value)

        assertTrue(deleted)
        assertNull(likeRepository.findById(like.id.value))
    }

    @Test
    fun `delete returns false when like does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val deleted = likeRepository.delete(nonExistentId)

        assertFalse(deleted)
    }

    @Test
    fun `deleteByPostAndUser removes like`() {
        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        val deleted = likeRepository.deleteByPostAndUser(testPost.id, testUser.id)

        assertTrue(deleted)
        assertFalse(likeRepository.existsByPostAndUser(testPost.id, testUser.id))
    }

    @Test
    fun `deleteByPostAndUser returns false when like does not exist`() {
        val deleted = likeRepository.deleteByPostAndUser(testPost.id, testUser.id)

        assertFalse(deleted)
    }

    @Test
    fun `countByPostId returns correct count`() {
        val user2 = userRepository.create(
            username = "user2",
            email = "user2@example.com",
            name = "User 2",
            passwordHash = "hash",
            salt = "salt"
        )

        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )
        likeRepository.create(
            postId = testPost.id,
            userId = user2.id
        )

        val count = likeRepository.countByPostId(testPost.id)

        assertEquals(2, count)
    }

    @Test
    fun `cannot create duplicate like for same post and user`() {
        likeRepository.create(
            postId = testPost.id,
            userId = testUser.id
        )

        // Attempting to create duplicate like should fail due to unique constraint
        assertThrows<Exception> {
            likeRepository.create(
                postId = testPost.id,
                userId = testUser.id
            )
        }
    }
}
