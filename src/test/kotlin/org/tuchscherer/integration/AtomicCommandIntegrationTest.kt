package org.tuchscherer.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tuchscherer.admin.AdminUserDeletionService
import org.tuchscherer.analytics.repositories.ExposedPostViewRepository
import org.tuchscherer.checkedlist.ChecklistService
import org.tuchscherer.checkedlist.ViaductPostCreationPort
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.checkedlist.repositories.ExposedCheckedListItemRepository
import org.tuchscherer.database.repositories.*
import java.util.UUID

class AtomicCommandIntegrationTest {
    private val users = ExposedUserRepository()
    private val posts = ExposedPostRepository()
    private val comments = ExposedCommentRepository()
    private val likes = ExposedLikeRepository()
    private val transactions = ExposedTransactionRunner()
    private lateinit var items: CheckedListItemRepository
    private lateinit var views: ExposedPostViewRepository
    private val checklistPosts = ViaductPostCreationPort()
    private lateinit var authorId: UUID

    @BeforeEach
    fun setup() {
        DatabaseTestHelper.setupDatabase()
        items = ExposedCheckedListItemRepository()
        views = ExposedPostViewRepository()
        authorId = users.create("author", "author@example.com", "Author", "hash", "salt").id.value
    }

    @AfterEach
    fun cleanup() = DatabaseTestHelper.tearDownDatabase()

    @Test
    fun `failed second item rolls back the post and first item`() {
        var createdPostId: UUID? = null
        val failingItems = object : CheckedListItemRepository by items {
            override fun addItem(postId: UUID, text: String): CheckedListItemData {
                createdPostId = postId
                if (text == "fail") error("Simulated storage failure")
                return items.addItem(postId, text)
            }
        }
        val service = ChecklistService(checklistPosts, failingItems, transactions)
        assertThrows<IllegalStateException> {
            service.create(authorId, "Atomic checklist", "", listOf("first", "fail"), "PUBLISHED")
        }
        assertEquals(0, posts.countByAuthorId(authorId))
        assertTrue(items.getItemsForPost(requireNotNull(createdPostId)).isEmpty())
    }

    @Test
    fun `failed post deletion restores deleted items`() {
        val post = checklistPosts.createCheckedListPost("Checklist", authorId)
        val item = items.addItem(post.id, "Keep me")
        val failingPosts = object : PostCreationPort by checklistPosts {
            override fun deleteCheckedListPost(id: UUID): Boolean = error("Simulated storage failure")
        }
        assertThrows<IllegalStateException> {
            ChecklistService(failingPosts, items, transactions).delete(authorId, post.id)
        }
        assertNotNull(checklistPosts.getPostData(post.id))
        assertEquals(listOf(item), items.getItemsForPost(post.id))
    }

    @Test
    fun `failed final user deletion rolls back content and module dependents`() {
        val post = checklistPosts.createCheckedListPost("Checklist", authorId)
        val item = items.addItem(post.id, "Keep me")
        val comment = comments.create("Comment", post.id, authorId)
        val like = likes.create(post.id, authorId)
        views.incrementViewCount(post.id)
        val failingUsers = object : UserRepository by users {
            override fun delete(id: UUID): Boolean = error("Simulated storage failure")
        }
        assertThrows<IllegalStateException> {
            AdminUserDeletionService(failingUsers, posts, comments, likes, transactions).delete(authorId)
        }
        assertNotNull(users.findById(authorId))
        assertNotNull(posts.findById(post.id))
        assertNotNull(comments.findById(comment.id.value))
        assertNotNull(likes.findById(like.id.value))
        assertEquals(listOf(item), items.getItemsForPost(post.id))
        assertEquals(1L, views.getTotalViews())
    }

    @Test
    fun `user deletion removes all post types and their module dependents`() {
        val checklist = checklistPosts.createCheckedListPost("Checklist", authorId)
        val blog = posts.create("Blog", "Content", authorId)
        items.addItem(checklist.id, "Item")
        views.incrementViewCount(checklist.id)
        views.incrementViewCount(blog.id.value)
        comments.create("Comment", checklist.id, authorId)
        likes.create(checklist.id, authorId)

        val result = AdminUserDeletionService(users, posts, comments, likes, transactions).delete(authorId)

        assertTrue(result.success)
        assertEquals(2, result.posts)
        assertEquals(1, result.comments)
        assertEquals(1, result.likes)
        assertNull(users.findById(authorId))
        assertNull(posts.findById(checklist.id))
        assertNull(posts.findById(blog.id.value))
        assertTrue(items.getItemsForPost(checklist.id).isEmpty())
        assertEquals(0L, views.getTotalViews())
    }

    @Test
    fun `direct post deletion also removes checklist and analytics dependents`() {
        val post = checklistPosts.createCheckedListPost("Checklist", authorId)
        items.addItem(post.id, "Item")
        views.incrementViewCount(post.id)
        assertTrue(posts.delete(post.id))
        assertTrue(items.getItemsForPost(post.id).isEmpty())
        assertEquals(0L, views.getTotalViews())
    }
}
