@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.AdminDeleteCommentResolver
import org.tuchscherer.viadapp.resolvers.AdminDeletePostResolver
import org.tuchscherer.viadapp.resolvers.AdminDeleteUserResolver
import org.tuchscherer.viadapp.resolvers.AdminUpdatePostResolver
import org.tuchscherer.viadapp.resolvers.AdminUpdateUserResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.AdminMutations
import viaduct.api.grts.AdminMutations_DeleteComment_Arguments
import viaduct.api.grts.AdminMutations_DeletePost_Arguments
import viaduct.api.grts.AdminMutations_DeleteUser_Arguments
import viaduct.api.grts.AdminMutations_UpdatePost_Arguments
import viaduct.api.grts.AdminMutations_UpdateUser_Arguments
import viaduct.api.grts.AdminUpdatePostInput
import viaduct.api.grts.AdminUpdateUserInput
import viaduct.api.grts.Query
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.User as ViaductUser
import viaduct.api.types.Arguments
import viaduct.api.testing.ResolverTestBase
import java.time.LocalDateTime
import java.util.UUID

/**
 * After moving the admin mutations under @namespaceType (mutation { admin { updateUser ... } }),
 * the resolvers extend AdminMutationsResolvers.X() rather than MutationResolvers.AdminX().
 * That means they're plain field resolvers on the AdminMutations object — runFieldResolver
 * with objectValue = AdminMutations.Builder(...) is the right harness, and the previous split
 * between DefaultAbstractResolverTestBase and MutationResolverTester collapses into one class.
 */
class AdminMutationResolversTest : ResolverTestBase() {

    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var commentRepository: CommentRepository
    private lateinit var likeRepository: LikeRepository
    private lateinit var mockAdminUser: User
    private lateinit var mockRegularUser: User
    private lateinit var mockPost: Post
    private lateinit var mockComment: Comment
    private lateinit var mockDbUser: User

    private val adminUserId = UUID.randomUUID()
    private val regularUserId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()
    private fun adminMutationsObj() = AdminMutations.Builder(context).build()

    private suspend fun <R, A : Arguments> runOnAdminMutations(
        resolver: viaduct.api.FieldResolverBase<AdminMutations, Query, A, R>,
        resolverArgs: A,
        resolverRequestContext: RequestContext,
    ): R = runFieldResolver(resolver) {
        objectValue = adminMutationsObj()
        queryValue = queryObj()
        arguments = resolverArgs
        requestContext = resolverRequestContext
    }

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        postRepository = mockk()
        commentRepository = mockk()
        likeRepository = mockk()

        mockAdminUser = mockk(relaxed = true)
        every { mockAdminUser.id } returns EntityID(adminUserId, mockk())
        every { mockAdminUser.isAdmin } returns true

        mockRegularUser = mockk(relaxed = true)
        every { mockRegularUser.id } returns EntityID(regularUserId, mockk())
        every { mockRegularUser.isAdmin } returns false

        mockDbUser = mockk(relaxed = true)
        every { mockDbUser.id } returns EntityID(targetUserId, mockk())
        every { mockDbUser.username } returns "targetuser"
        every { mockDbUser.email } returns "target@example.com"
        every { mockDbUser.name } returns "Target User"
        every { mockDbUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockDbUser.isAdmin } returns false

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Original Title"
        every { mockPost.content } returns "Original content"
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 12, 0)

        mockComment = mockk(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { userRepository }
                single { postRepository }
                single { commentRepository }
                single { likeRepository }
            })
        }
    }

    // ── AdminDeletePostResolver ───────────────────────────────────────────────

    @Test
    fun `AdminDeletePostResolver deletes post and returns true`() = runBlocking {
        val resolver = AdminDeletePostResolver(postRepository)
        val args = AdminMutations_DeletePost_Arguments.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { postRepository.delete(postId) } returns true

        val result = runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))

        assertTrue(result)
    }

    @Test
    fun `AdminDeletePostResolver throws NotFoundException when post does not exist`() = runBlocking {
        val resolver = AdminDeletePostResolver(postRepository)
        val args = AdminMutations_DeletePost_Arguments.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns null

        assertThrows<NotFoundException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
        verify(exactly = 0) { postRepository.delete(any()) }
    }

    @Test
    fun `AdminDeletePostResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeletePostResolver(postRepository)
        val args = AdminMutations_DeletePost_Arguments.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockRegularUser))
        }
        verify(exactly = 0) { postRepository.findById(any()) }
    }

    // ── AdminDeleteCommentResolver ────────────────────────────────────────────

    @Test
    fun `AdminDeleteCommentResolver deletes comment and returns true`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = AdminMutations_DeleteComment_Arguments.Builder(context)
            .id(globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        every { commentRepository.findById(commentId) } returns mockComment
        every { commentRepository.delete(commentId) } returns true

        val result = runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))

        assertTrue(result)
    }

    @Test
    fun `AdminDeleteCommentResolver throws NotFoundException when comment does not exist`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = AdminMutations_DeleteComment_Arguments.Builder(context)
            .id(globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        every { commentRepository.findById(commentId) } returns null

        assertThrows<NotFoundException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
        verify(exactly = 0) { commentRepository.delete(any()) }
    }

    @Test
    fun `AdminDeleteCommentResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = AdminMutations_DeleteComment_Arguments.Builder(context)
            .id(globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockRegularUser))
        }
        verify(exactly = 0) { commentRepository.findById(any()) }
    }

    // ── AdminDeleteUserResolver ───────────────────────────────────────────────

    @Test
    fun `AdminDeleteUserResolver deletes user and all their content`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = AdminMutations_DeleteUser_Arguments.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        every { userRepository.findById(targetUserId) } returns mockDbUser
        every { likeRepository.deleteByUserId(targetUserId) } returns 5
        every { commentRepository.deleteByUserId(targetUserId) } returns 3
        every { postRepository.deleteByAuthorId(targetUserId) } returns 2
        every { userRepository.delete(targetUserId) } returns true

        val result = runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))

        assertNotNull(result)
        assertTrue(result.getSuccess())
        assertEquals(2, result.getPostsDeleted())
        assertEquals(3, result.getCommentsDeleted())
        assertEquals(5, result.getLikesDeleted())
    }

    @Test
    fun `AdminDeleteUserResolver throws NotFoundException when user does not exist`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = AdminMutations_DeleteUser_Arguments.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        every { userRepository.findById(targetUserId) } returns null

        assertThrows<NotFoundException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
        verify(exactly = 0) { userRepository.delete(any()) }
    }

    @Test
    fun `AdminDeleteUserResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = AdminMutations_DeleteUser_Arguments.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockRegularUser))
        }
        verify(exactly = 0) { userRepository.findById(any()) }
    }

    // ── AdminUpdateUserResolver ───────────────────────────────────────────────

    @Test
    fun `AdminUpdateUserResolver updates user and returns updated user`() = runBlocking {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("Updated Name")
            .build()
        val args = AdminMutations_UpdateUser_Arguments.Builder(context).input(input).build()

        every { userRepository.updateFields(targetUserId, name = "Updated Name", email = null, isAdmin = null) } returns mockDbUser
        every { mockDbUser.name } returns "Updated Name"

        val result = runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))

        assertNotNull(result)
        assertEquals(targetUserId.toString(), result.getId().internalID)
        assertEquals("Updated Name", result.getName())
    }

    @Test
    fun `AdminUpdateUserResolver throws NotFoundException when user does not exist`() = runBlocking {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("New Name")
            .build()
        val args = AdminMutations_UpdateUser_Arguments.Builder(context).input(input).build()

        every { userRepository.updateFields(any(), any(), any(), any()) } returns null

        assertThrows<NotFoundException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }

    @Test
    fun `AdminUpdateUserResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("New Name")
            .build()
        val args = AdminMutations_UpdateUser_Arguments.Builder(context).input(input).build()

        assertThrows<AuthorizationException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockRegularUser))
        }
    }

    @Test
    fun `AdminUpdateUserResolver throws IllegalArgumentException when name exceeds 255 characters`() = runBlocking {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(context)
            .id(globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("a".repeat(256))
            .build()
        val args = AdminMutations_UpdateUser_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }

    // ── AdminUpdatePostResolver ───────────────────────────────────────────────

    @Test
    fun `AdminUpdatePostResolver updates post and returns updated post`() = runBlocking {
        val resolver = AdminUpdatePostResolver(postRepository)
        val updatedPost = mockk<Post>(relaxed = true)
        every { updatedPost.id } returns EntityID(postId, mockk())
        every { updatedPost.title } returns "New Title"
        every { updatedPost.content } returns "New content"
        every { updatedPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { updatedPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 12, 0)

        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("New Title")
            .content("New content")
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        every { postRepository.updateById(postId, "New Title", "New content") } returns updatedPost

        val result = runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))

        assertNotNull(result)
        assertEquals(postId.toString(), result.getId().internalID)
        assertEquals("New Title", result.getTitle())
        assertEquals("New content", result.getContent())
    }

    @Test
    fun `AdminUpdatePostResolver throws NotFoundException when post does not exist`() = runBlocking {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("New Title")
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        every { postRepository.updateById(postId, "New Title", null) } returns null

        assertThrows<NotFoundException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }

    @Test
    fun `AdminUpdatePostResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("New Title")
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<AuthorizationException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockRegularUser))
        }
    }

    @Test
    fun `AdminUpdatePostResolver throws IllegalArgumentException for blank title`() = runBlocking {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("   ")
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }

    @Test
    fun `AdminUpdatePostResolver throws IllegalArgumentException for blank content`() = runBlocking {
        // Until centralised in PostValidation, AdminUpdatePost only checked
        // content length and silently accepted whitespace-only content,
        // diverging from CreatePost / UpdatePost. Lock that gap shut.
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .content("   ")
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }

    @Test
    fun `AdminUpdatePostResolver throws IllegalArgumentException for title exceeding 500 characters`() = runBlocking {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(context)
            .id(globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .title("a".repeat(501))
            .build()
        val args = AdminMutations_UpdatePost_Arguments.Builder(context).input(input).build()

        assertThrows<IllegalArgumentException> {
            runOnAdminMutations(resolver, args, RequestContext(user = mockAdminUser))
        }
    }
}
