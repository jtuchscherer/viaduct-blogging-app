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
import viaduct.api.grts.*
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import viaduct.api.testing.MutationResolverTester
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

// ── Boolean-returning admin mutations + AdminDeleteUser ───────────────────────
// Uses DefaultAbstractResolverTestBase because MutationResolverTester requires R : CompositeOutput,
// and Boolean / AdminDeleteUserResult may not satisfy that bound.

class AdminDeleteMutationResolversTest : DefaultAbstractResolverTestBase() {

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

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()

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

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())

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
        val args = Mutation_AdminDeletePost_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost
        every { postRepository.delete(postId) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertTrue(result)
    }

    @Test
    fun `AdminDeletePostResolver throws NotFoundException when post does not exist`() = runBlocking {
        val resolver = AdminDeletePostResolver(postRepository)
        val args = Mutation_AdminDeletePost_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
        verify(exactly = 0) { postRepository.delete(any()) }
    }

    @Test
    fun `AdminDeletePostResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeletePostResolver(postRepository)
        val args = Mutation_AdminDeletePost_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
        verify(exactly = 0) { postRepository.findById(any()) }
    }

    // ── AdminDeleteCommentResolver ────────────────────────────────────────────

    @Test
    fun `AdminDeleteCommentResolver deletes comment and returns true`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = Mutation_AdminDeleteComment_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        every { commentRepository.findById(commentId) } returns mockComment
        every { commentRepository.delete(commentId) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertTrue(result)
    }

    @Test
    fun `AdminDeleteCommentResolver throws NotFoundException when comment does not exist`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = Mutation_AdminDeleteComment_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        every { commentRepository.findById(commentId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
        verify(exactly = 0) { commentRepository.delete(any()) }
    }

    @Test
    fun `AdminDeleteCommentResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeleteCommentResolver(commentRepository)
        val args = Mutation_AdminDeleteComment_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.Comment.Reflection, commentId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
        verify(exactly = 0) { commentRepository.findById(any()) }
    }

    // ── AdminDeleteUserResolver ───────────────────────────────────────────────

    @Test
    fun `AdminDeleteUserResolver deletes user and all their content`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = Mutation_AdminDeleteUser_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        every { userRepository.findById(targetUserId) } returns mockDbUser
        every { likeRepository.deleteByUserId(targetUserId) } returns 5
        every { commentRepository.deleteByUserId(targetUserId) } returns 3
        every { postRepository.deleteByAuthorId(targetUserId) } returns 2
        every { userRepository.delete(targetUserId) } returns true

        val result = runMutationFieldResolver(
            resolver = resolver,
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertNotNull(result)
        assertTrue(result.getSuccess())
        assertEquals(2, result.getPostsDeleted())
        assertEquals(3, result.getCommentsDeleted())
        assertEquals(5, result.getLikesDeleted())
    }

    @Test
    fun `AdminDeleteUserResolver throws NotFoundException when user does not exist`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = Mutation_AdminDeleteUser_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        every { userRepository.findById(targetUserId) } returns null

        assertThrows<NotFoundException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
        verify(exactly = 0) { userRepository.delete(any()) }
    }

    @Test
    fun `AdminDeleteUserResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminDeleteUserResolver(userRepository, postRepository, commentRepository, likeRepository)
        val args = Mutation_AdminDeleteUser_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .build()

        assertThrows<AuthorizationException> {
            runMutationFieldResolver(
                resolver = resolver,
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
        verify(exactly = 0) { userRepository.findById(any()) }
    }
}

// ── GRT-returning admin mutations (AdminUpdateUser, AdminUpdatePost) ──────────
// These use MutationResolverTester which supports CompositeOutput return types.

class AdminUpdateMutationResolversTest {

    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var mockAdminUser: User
    private lateinit var mockRegularUser: User
    private lateinit var mockDbUser: User
    private lateinit var mockPost: Post

    private val adminUserId = UUID.randomUUID()
    private val regularUserId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val postId = UUID.randomUUID()

    private val updateUserTester = MutationResolverTester.create<Query, Mutation, Mutation_AdminUpdateUser_Arguments, ViaductUser>(ViaductTestConfig.testerConfig)
    private val updatePostTester = MutationResolverTester.create<Query, Mutation, Mutation_AdminUpdatePost_Arguments, ViaductPost>(ViaductTestConfig.testerConfig)

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        postRepository = mockk()

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

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { userRepository }
                single { postRepository }
            })
        }
    }

    // ── AdminUpdateUserResolver ───────────────────────────────────────────────

    @Test
    fun `AdminUpdateUserResolver updates user and returns updated user`() = runBlocking {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(updateUserTester.context)
            .id(updateUserTester.context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("Updated Name")
            .build()
        val args = Mutation_AdminUpdateUser_Arguments.Builder(updateUserTester.context).input(input).build()

        every { userRepository.findById(targetUserId) } returns mockDbUser
        every { mockDbUser.name = "Updated Name" } just Runs
        every { mockDbUser.name } returns "Updated Name"

        val result = updateUserTester.test(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockAdminUser)
        }

        assertNotNull(result)
        assertEquals(targetUserId.toString(), result.getId().internalID)
        assertEquals("Updated Name", result.getName())
    }

    @Test
    fun `AdminUpdateUserResolver throws NotFoundException when user does not exist`() {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(updateUserTester.context)
            .id(updateUserTester.context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("New Name")
            .build()
        val args = Mutation_AdminUpdateUser_Arguments.Builder(updateUserTester.context).input(input).build()

        every { userRepository.findById(targetUserId) } returns null

        val e = assertThrows<Exception> {
            runBlocking {
                updateUserTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockAdminUser)
                }
            }
        }
        assertInstanceOf(NotFoundException::class.java, e.cause)
    }

    @Test
    fun `AdminUpdateUserResolver throws AuthorizationException for non-admin user`() {
        val resolver = AdminUpdateUserResolver(userRepository)
        val input = AdminUpdateUserInput.Builder(updateUserTester.context)
            .id(updateUserTester.context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("New Name")
            .build()
        val args = Mutation_AdminUpdateUser_Arguments.Builder(updateUserTester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                updateUserTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockRegularUser)
                }
            }
        }
        assertInstanceOf(AuthorizationException::class.java, e.cause)
    }

    @Test
    fun `AdminUpdateUserResolver throws IllegalArgumentException when name exceeds 255 characters`() {
        val resolver = AdminUpdateUserResolver(userRepository)
        every { userRepository.findById(targetUserId) } returns mockDbUser
        val input = AdminUpdateUserInput.Builder(updateUserTester.context)
            .id(updateUserTester.context.globalIDFor(ViaductUser.Reflection, targetUserId.toString()))
            .name("a".repeat(256))
            .build()
        val args = Mutation_AdminUpdateUser_Arguments.Builder(updateUserTester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                updateUserTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockAdminUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
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

        val input = AdminUpdatePostInput.Builder(updatePostTester.context)
            .id(updatePostTester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("New Title")
            .content("New content")
            .build()
        val args = Mutation_AdminUpdatePost_Arguments.Builder(updatePostTester.context).input(input).build()

        every { postRepository.updateById(postId, "New Title", "New content") } returns updatedPost

        val result = updatePostTester.test(resolver) {
            arguments = args
            requestContext = RequestContext(user = mockAdminUser)
        }

        assertNotNull(result)
        assertEquals(postId.toString(), result.getId().internalID)
        assertEquals("New Title", result.getTitle())
        assertEquals("New content", result.getContent())
    }

    @Test
    fun `AdminUpdatePostResolver throws NotFoundException when post does not exist`() {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(updatePostTester.context)
            .id(updatePostTester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("New Title")
            .build()
        val args = Mutation_AdminUpdatePost_Arguments.Builder(updatePostTester.context).input(input).build()

        every { postRepository.updateById(postId, "New Title", null) } returns null

        val e = assertThrows<Exception> {
            runBlocking {
                updatePostTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockAdminUser)
                }
            }
        }
        assertInstanceOf(NotFoundException::class.java, e.cause)
    }

    @Test
    fun `AdminUpdatePostResolver throws AuthorizationException for non-admin user`() {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(updatePostTester.context)
            .id(updatePostTester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("New Title")
            .build()
        val args = Mutation_AdminUpdatePost_Arguments.Builder(updatePostTester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                updatePostTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockRegularUser)
                }
            }
        }
        assertInstanceOf(AuthorizationException::class.java, e.cause)
    }

    @Test
    fun `AdminUpdatePostResolver throws IllegalArgumentException for blank title`() {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(updatePostTester.context)
            .id(updatePostTester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("   ")
            .build()
        val args = Mutation_AdminUpdatePost_Arguments.Builder(updatePostTester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                updatePostTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockAdminUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }

    @Test
    fun `AdminUpdatePostResolver throws IllegalArgumentException for title exceeding 500 characters`() {
        val resolver = AdminUpdatePostResolver(postRepository)
        val input = AdminUpdatePostInput.Builder(updatePostTester.context)
            .id(updatePostTester.context.globalIDFor(ViaductPost.Reflection, postId.toString()))
            .title("a".repeat(501))
            .build()
        val args = Mutation_AdminUpdatePost_Arguments.Builder(updatePostTester.context).input(input).build()

        val e = assertThrows<Exception> {
            runBlocking {
                updatePostTester.test(resolver) {
                    arguments = args
                    requestContext = RequestContext(user = mockAdminUser)
                }
            }
        }
        assertInstanceOf(IllegalArgumentException::class.java, e.cause)
    }
}
