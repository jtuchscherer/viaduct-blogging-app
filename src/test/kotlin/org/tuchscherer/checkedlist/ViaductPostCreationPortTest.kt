package org.tuchscherer.checkedlist

import org.tuchscherer.database.repositories.DatabaseTestHelper
import org.tuchscherer.database.repositories.ExposedUserRepository
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID

/**
 * Integration tests for [ViaductPostCreationPort] against an H2 in-memory database.
 *
 * This port is the primary bridge between the checkedlist module and the Posts table.
 * It owns all CRUD for CheckedListPost rows, including cascade-deleting likes and
 * comments when a post is deleted.  A unit-only test with mocks would just mirror the
 * implementation; these integration tests verify the actual SQL behaviour.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ViaductPostCreationPortTest {

    private lateinit var port: ViaductPostCreationPort
    private lateinit var authorId: UUID

    @BeforeAll
    fun setupDatabase() {
        DatabaseTestHelper.setupDatabase()
        port = ViaductPostCreationPort()

        // Every post needs a valid author row for the FK constraint.
        val userRepo = ExposedUserRepository()
        val user = userRepo.create(
            username = "test_author",
            email = "test_author@example.com",
            name = "Test Author",
            passwordHash = "hash",
            salt = "salt",
        )
        authorId = user.id.value
    }

    @AfterEach
    fun cleanDatabase() {
        DatabaseTestHelper.cleanDatabase()
        // Re-create the test user after each clean so tests are independent.
        val userRepo = ExposedUserRepository()
        val user = userRepo.create(
            username = "test_author",
            email = "test_author@example.com",
            name = "Test Author",
            passwordHash = "hash",
            salt = "salt",
        )
        authorId = user.id.value
    }

    @AfterAll
    fun tearDownDatabase() {
        DatabaseTestHelper.tearDownDatabase()
    }

    // ── createCheckedListPost ────────────────────────────────────────────────

    @Test
    fun `createCheckedListPost stores title, description, and authorId`() {
        val post = port.createCheckedListPost("My List", authorId, "A grocery list")

        assertEquals("My List", post.title)
        assertEquals("A grocery list", post.description)
        assertEquals(authorId, post.authorId)
    }

    @Test
    fun `createCheckedListPost returns a non-null UUID`() {
        val post = port.createCheckedListPost("List", authorId, "")
        assertNotNull(post.id)
    }

    @Test
    fun `createCheckedListPost stores description in the content column`() {
        val post = port.createCheckedListPost("Desc Test", authorId, "My description text")
        // getPostData round-trips description through the content column
        val retrieved = port.getPostData(post.id)!!
        assertEquals("My description text", retrieved.description)
    }

    // ── getPostData ───────────────────────────────────────────────────────────

    @Test
    fun `getPostData returns the post for an existing checklist post`() {
        val created = port.createCheckedListPost("Fetch Me", authorId, "desc")
        val found = port.getPostData(created.id)

        assertNotNull(found)
        assertEquals("Fetch Me", found!!.title)
    }

    @Test
    fun `getPostData returns null for an unknown ID`() {
        assertNull(port.getPostData(UUID.randomUUID()))
    }

    @Test
    fun `getPostData returns null for a BlogPost ID (wrong post type)`() {
        // A blog post created by ExposedPostRepository has type=BLOG_POST, not CHECKED_LIST
        val blogPost = org.tuchscherer.database.repositories.ExposedPostRepository()
            .create("A Blog Post", "content", authorId)

        assertNull(port.getPostData(blogPost.id.value))
    }

    // ── getPostsData ──────────────────────────────────────────────────────────

    @Test
    fun `getPostsData returns a map of all requested checklist posts`() {
        val p1 = port.createCheckedListPost("List 1", authorId, "")
        val p2 = port.createCheckedListPost("List 2", authorId, "")

        val result = port.getPostsData(listOf(p1.id, p2.id))

        assertEquals(2, result.size)
        assertEquals("List 1", result[p1.id]!!.title)
        assertEquals("List 2", result[p2.id]!!.title)
    }

    @Test
    fun `getPostsData returns empty map for empty input`() {
        assertTrue(port.getPostsData(emptyList()).isEmpty())
    }

    @Test
    fun `getPostsData excludes non-checklist posts from results`() {
        val blogPost = org.tuchscherer.database.repositories.ExposedPostRepository()
            .create("Blog", "content", authorId)
        val checklist = port.createCheckedListPost("CL", authorId, "")

        val result = port.getPostsData(listOf(blogPost.id.value, checklist.id))

        assertEquals(1, result.size)
        assertTrue(result.containsKey(checklist.id))
    }

    // ── getAllCheckedListPosts ────────────────────────────────────────────────

    @Test
    fun `getAllCheckedListPosts returns all checklist posts ordered newest first`() {
        val p1 = port.createCheckedListPost("First", authorId, "")
        val p2 = port.createCheckedListPost("Second", authorId, "")

        val all = port.getAllCheckedListPosts()

        assertTrue(all.size >= 2)
        // Newest-first ordering: p2 was created after p1
        val ids = all.map { it.id }
        assertTrue(ids.indexOf(p2.id) < ids.indexOf(p1.id))
    }

    @Test
    fun `getAllCheckedListPosts does not include blog posts`() {
        org.tuchscherer.database.repositories.ExposedPostRepository()
            .create("Blog Post", "content", authorId)

        val all = port.getAllCheckedListPosts()

        assertTrue(all.none { it.title == "Blog Post" })
    }

    // ── updateCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `updateCheckedListPost changes the title`() {
        val post = port.createCheckedListPost("Old Title", authorId, "desc")

        val updated = port.updateCheckedListPost(post.id, title = "New Title", description = null)

        assertNotNull(updated)
        assertEquals("New Title", updated!!.title)
        assertEquals("desc", updated.description) // description unchanged
    }

    @Test
    fun `updateCheckedListPost changes the description`() {
        val post = port.createCheckedListPost("Title", authorId, "old desc")

        val updated = port.updateCheckedListPost(post.id, title = null, description = "new desc")

        assertNotNull(updated)
        assertEquals("Title", updated!!.title) // title unchanged
        assertEquals("new desc", updated.description)
    }

    @Test
    fun `updateCheckedListPost returns null for an unknown ID`() {
        val result = port.updateCheckedListPost(UUID.randomUUID(), title = "X", description = null)
        assertNull(result)
    }

    // ── deleteCheckedListPost ─────────────────────────────────────────────────

    @Test
    fun `deleteCheckedListPost returns true and removes the post`() {
        val post = port.createCheckedListPost("To Delete", authorId, "")

        val deleted = port.deleteCheckedListPost(post.id)

        assertTrue(deleted)
        assertNull(port.getPostData(post.id))
    }

    @Test
    fun `deleteCheckedListPost returns false for an unknown ID`() {
        assertFalse(port.deleteCheckedListPost(UUID.randomUUID()))
    }

    @Test
    fun `deleteCheckedListPost cascades to likes on the post`() {
        val post = port.createCheckedListPost("Liked List", authorId, "")
        // Create a like on the post
        val likeRepo = org.tuchscherer.database.repositories.ExposedLikeRepository()
        val like = likeRepo.create(postId = post.id, userId = authorId)

        port.deleteCheckedListPost(post.id)

        assertNull(likeRepo.findById(like.id.value))
    }

    @Test
    fun `deleteCheckedListPost cascades to comments on the post`() {
        val post = port.createCheckedListPost("Commented List", authorId, "")
        val commentRepo = org.tuchscherer.database.repositories.ExposedCommentRepository()
        val comment = commentRepo.create("A comment", post.id, authorId)

        port.deleteCheckedListPost(post.id)

        assertNull(commentRepo.findById(comment.id.value))
    }

    // ── getAuthorIdsForPosts ──────────────────────────────────────────────────

    @Test
    fun `getAuthorIdsForPosts returns author IDs for checklist posts`() {
        val post = port.createCheckedListPost("Author Lookup", authorId, "")

        val result = port.getAuthorIdsForPosts(listOf(post.id))

        assertEquals(authorId, result[post.id])
    }

    @Test
    fun `getAuthorIdsForPosts returns empty map for empty input`() {
        assertTrue(port.getAuthorIdsForPosts(emptyList()).isEmpty())
    }
}
