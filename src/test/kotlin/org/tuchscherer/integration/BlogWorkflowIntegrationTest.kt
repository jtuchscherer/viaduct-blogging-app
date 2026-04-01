package org.tuchscherer.integration

import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.PasswordService
import org.tuchscherer.database.repositories.DatabaseTestHelper
import org.tuchscherer.database.repositories.ExposedCommentRepository
import org.tuchscherer.database.repositories.ExposedLikeRepository
import org.tuchscherer.database.repositories.ExposedPostRepository
import org.tuchscherer.database.repositories.ExposedUserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Integration tests for blog workflows.
 * Uses real repositories against an H2 in-memory database.
 */
class BlogWorkflowIntegrationTest {

    private val userRepository = ExposedUserRepository()
    private val postRepository = ExposedPostRepository()
    private val commentRepository = ExposedCommentRepository()
    private val likeRepository = ExposedLikeRepository()
    private val authService = AuthenticationService(PasswordService(), userRepository)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupDatabase() = DatabaseTestHelper.setupDatabase()

        @AfterAll
        @JvmStatic
        fun tearDownDatabase() = DatabaseTestHelper.tearDownDatabase()
    }

    @AfterEach
    fun cleanUp() = DatabaseTestHelper.cleanDatabase()

    // ── Post CRUD ───────────────────────────────────────────────────────

    @Test
    fun `create and retrieve post`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Hello World", "My first post", user.id)

        val found = postRepository.findById(post.id.value)
        assertNotNull(found)
        assertEquals("Hello World", found!!.title)
        assertEquals("My first post", found.content)
        assertEquals(user.id, found.authorId)
    }

    @Test
    fun `update post changes title and content`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Original Title", "Original content", user.id)

        val updated = postRepository.updateById(post.id.value, title = "Updated Title", content = "Updated content")

        assertNotNull(updated)
        assertEquals("Updated Title", updated!!.title)
        assertEquals("Updated content", updated.content)
        assertTrue(updated.updatedAt >= post.updatedAt)
    }

    @Test
    fun `delete post removes it from database`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("To Delete", "content", user.id)

        val deleted = postRepository.delete(post.id.value)

        assertTrue(deleted)
        assertNull(postRepository.findById(post.id.value))
    }

    @Test
    fun `delete non-existent post returns false`() {
        assertFalse(postRepository.delete(java.util.UUID.randomUUID()))
    }

    @Test
    fun `findAll returns all posts`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        postRepository.create("Post 1", "content 1", user.id)
        postRepository.create("Post 2", "content 2", user.id)
        postRepository.create("Post 3", "content 3", user.id)

        val posts = postRepository.findAll()
        assertEquals(3, posts.size)
    }

    @Test
    fun `findByAuthorId returns only that author's posts`() {
        val alice = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val bob = authService.createUser("bob", "bob@example.com", "Bob", "pass")
        postRepository.create("Alice Post 1", "content", alice.id)
        postRepository.create("Alice Post 2", "content", alice.id)
        postRepository.create("Bob Post", "content", bob.id)

        val alicePosts = postRepository.findByAuthorId(alice.id)
        val bobPosts = postRepository.findByAuthorId(bob.id)

        assertEquals(2, alicePosts.size)
        assertEquals(1, bobPosts.size)
        assertTrue(alicePosts.all { it.authorId == alice.id })
    }

    // ── Comments ────────────────────────────────────────────────────────

    @Test
    fun `create comment and retrieve by post`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)
        commentRepository.create("Great post!", post.id, user.id, LocalDateTime.now())

        val comments = commentRepository.findByPostId(post.id)
        assertEquals(1, comments.size)
        assertEquals("Great post!", comments[0].content)
        assertEquals(user.id, comments[0].authorId)
    }

    @Test
    fun `delete comment removes it`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)
        val comment = commentRepository.create("A comment", post.id, user.id, LocalDateTime.now())

        assertTrue(commentRepository.delete(comment.id.value))
        assertNull(commentRepository.findById(comment.id.value))
    }

    @Test
    fun `getAuthorForComment returns correct user`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)
        val comment = commentRepository.create("Hello", post.id, user.id, LocalDateTime.now())

        val author = commentRepository.getAuthorForComment(comment.id.value)
        assertNotNull(author)
        assertEquals("alice", author!!.username)
    }

    @Test
    fun `getPostForComment returns correct post`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("My Post", "content", user.id)
        val comment = commentRepository.create("Hello", post.id, user.id, LocalDateTime.now())

        val foundPost = commentRepository.getPostForComment(comment.id.value)
        assertNotNull(foundPost)
        assertEquals("My Post", foundPost!!.title)
    }

    // ── Likes ───────────────────────────────────────────────────────────

    @Test
    fun `like a post and verify count`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)

        likeRepository.create(post.id, user.id, LocalDateTime.now())

        assertEquals(1L, likeRepository.countByPostId(post.id))
        assertTrue(likeRepository.existsByPostAndUser(post.id, user.id))
    }

    @Test
    fun `unlike a post removes like and updates count`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)
        likeRepository.create(post.id, user.id, LocalDateTime.now())

        likeRepository.deleteByPostAndUser(post.id, user.id)

        assertEquals(0L, likeRepository.countByPostId(post.id))
        assertFalse(likeRepository.existsByPostAndUser(post.id, user.id))
    }

    @Test
    fun `multiple users liking a post increments count correctly`() {
        val alice = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val bob = authService.createUser("bob", "bob@example.com", "Bob", "pass")
        val carol = authService.createUser("carol", "carol@example.com", "Carol", "pass")
        val post = postRepository.create("Popular Post", "content", alice.id)

        likeRepository.create(post.id, alice.id, LocalDateTime.now())
        likeRepository.create(post.id, bob.id, LocalDateTime.now())
        likeRepository.create(post.id, carol.id, LocalDateTime.now())

        assertEquals(3L, likeRepository.countByPostId(post.id))
        assertTrue(likeRepository.existsByPostAndUser(post.id, alice.id))
        assertTrue(likeRepository.existsByPostAndUser(post.id, bob.id))
        assertTrue(likeRepository.existsByPostAndUser(post.id, carol.id))
    }

    @Test
    fun `getUserForLike and getPostForLike return correct entities`() {
        val user = authService.createUser("alice", "alice@example.com", "Alice", "pass")
        val post = postRepository.create("Post", "content", user.id)
        val like = likeRepository.create(post.id, user.id, LocalDateTime.now())

        val likeUser = likeRepository.getUserForLike(like.id.value)
        val likePost = likeRepository.getPostForLike(like.id.value)

        assertNotNull(likeUser)
        assertEquals("alice", likeUser!!.username)
        assertNotNull(likePost)
        assertEquals("Post", likePost!!.title)
    }

    // ── Cross-entity workflows ──────────────────────────────────────────

    @Test
    fun `full blog workflow - post with comments and likes`() {
        val author = authService.createUser("author", "author@example.com", "Author", "pass")
        val reader1 = authService.createUser("reader1", "r1@example.com", "Reader1", "pass")
        val reader2 = authService.createUser("reader2", "r2@example.com", "Reader2", "pass")

        // Author creates a post
        val post = postRepository.create("Great Article", "Lots of content here.", author.id)
        assertEquals(1L, postRepository.count())

        // Two readers comment
        commentRepository.create("Loved it!", post.id, reader1.id, LocalDateTime.now())
        commentRepository.create("Very informative", post.id, reader2.id, LocalDateTime.now())
        assertEquals(2, commentRepository.findByPostId(post.id).size)

        // Both readers like the post
        likeRepository.create(post.id, reader1.id, LocalDateTime.now())
        likeRepository.create(post.id, reader2.id, LocalDateTime.now())
        assertEquals(2L, likeRepository.countByPostId(post.id))

        // Author updates the post
        val updated = postRepository.updateById(post.id.value, title = "Great Article (Updated)")
        assertEquals("Great Article (Updated)", updated!!.title)

        // Reader1 unlikes
        likeRepository.deleteByPostAndUser(post.id, reader1.id)
        assertEquals(1L, likeRepository.countByPostId(post.id))
        assertFalse(likeRepository.existsByPostAndUser(post.id, reader1.id))
        assertTrue(likeRepository.existsByPostAndUser(post.id, reader2.id))
    }
}
