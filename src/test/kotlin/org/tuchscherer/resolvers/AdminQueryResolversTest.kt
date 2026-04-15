@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.AdminCommentsResolver
import org.tuchscherer.viadapp.resolvers.AdminPostsResolver
import org.tuchscherer.viadapp.resolvers.AdminUsersResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import viaduct.api.grts.*
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.engine.runtime.execution.DefaultCoroutineInterop
import viaduct.tenant.testing.DefaultAbstractResolverTestBase
import java.time.LocalDateTime
import java.util.*

class AdminQueryResolversTest : DefaultAbstractResolverTestBase() {

    private lateinit var userRepository: UserRepository
    private lateinit var postRepository: PostRepository
    private lateinit var commentRepository: CommentRepository

    private lateinit var mockAdminUser: User
    private lateinit var mockRegularUser: User
    private lateinit var mockDbUser: User
    private lateinit var mockPost: Post
    private lateinit var mockComment: Comment

    private val adminUserId = UUID.randomUUID()
    private val regularUserId = UUID.randomUUID()
    private val dbUserId = UUID.randomUUID()
    private val postId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()

    override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()

    private fun queryObj() = Query.Builder(context).build()
    private fun adminQueriesObj() = AdminQueries.Builder(context).build()

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        postRepository = mockk()
        commentRepository = mockk()

        mockAdminUser = mockk(relaxed = true)
        every { mockAdminUser.id } returns EntityID(adminUserId, mockk())
        every { mockAdminUser.isAdmin } returns true

        mockRegularUser = mockk(relaxed = true)
        every { mockRegularUser.id } returns EntityID(regularUserId, mockk())
        every { mockRegularUser.isAdmin } returns false

        mockDbUser = mockk(relaxed = true)
        every { mockDbUser.id } returns EntityID(dbUserId, mockk())
        every { mockDbUser.username } returns "dbuser"
        every { mockDbUser.email } returns "db@example.com"
        every { mockDbUser.name } returns "DB User"
        every { mockDbUser.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockPost = mockk(relaxed = true)
        every { mockPost.id } returns EntityID(postId, mockk())
        every { mockPost.title } returns "Test Post"
        every { mockPost.content } returns "Test content"
        every { mockPost.authorId } returns EntityID(dbUserId, mockk())
        every { mockPost.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)
        every { mockPost.updatedAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        mockComment = mockk(relaxed = true)
        every { mockComment.id } returns EntityID(commentId, mockk())
        every { mockComment.content } returns "Test comment"
        every { mockComment.createdAt } returns LocalDateTime.of(2025, 1, 1, 10, 0)

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single { userRepository }
                single { postRepository }
                single { commentRepository }
            })
        }
    }

    // ── AdminUsersResolver ────────────────────────────────────────────────────

    @Test
    fun `AdminUsersResolver returns paginated users with totalCount`() = runBlocking {
        val resolver = AdminUsersResolver(userRepository)
        val args = AdminQueries_Users_Arguments.Builder(context).limit(10).offset(0).build()

        every { userRepository.findPage(10, 0) } returns listOf(mockDbUser)
        every { userRepository.count() } returns 1L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(1, result.getTotalCount())
        assertEquals(1, result.getUsers().size)
        assertEquals(dbUserId.toString(), result.getUsers()[0].getId().internalID)
    }

    @Test
    fun `AdminUsersResolver respects limit and offset arguments`() = runBlocking {
        val resolver = AdminUsersResolver(userRepository)
        val args = AdminQueries_Users_Arguments.Builder(context).limit(5).offset(10).build()

        every { userRepository.findPage(5, 10) } returns listOf(mockDbUser)
        every { userRepository.count() } returns 11L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(11, result.getTotalCount())
        assertEquals(1, result.getUsers().size)
    }

    @Test
    fun `AdminUsersResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminUsersResolver(userRepository)
        val args = AdminQueries_Users_Arguments.Builder(context).limit(10).offset(0).build()

        assertThrows<AuthorizationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
    }

    // ── AdminPostsResolver ────────────────────────────────────────────────────

    @Test
    fun `AdminPostsResolver returns paginated posts with totalCount`() = runBlocking {
        val resolver = AdminPostsResolver(postRepository)
        val args = AdminQueries_Posts_Arguments.Builder(context).limit(10).offset(0).build()

        every { postRepository.findPage(10, 0) } returns listOf(mockPost)
        every { postRepository.count() } returns 1L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(1, result.getTotalCount())
        assertEquals(1, result.getPosts().size)
        assertEquals(postId.toString(), result.getPosts()[0].getId().internalID)
    }

    @Test
    fun `AdminPostsResolver respects limit and offset arguments`() = runBlocking {
        val resolver = AdminPostsResolver(postRepository)
        val args = AdminQueries_Posts_Arguments.Builder(context).limit(5).offset(10).build()

        every { postRepository.findPage(5, 10) } returns listOf(mockPost)
        every { postRepository.count() } returns 15L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(15, result.getTotalCount())
        assertEquals(1, result.getPosts().size)
    }

    @Test
    fun `AdminPostsResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminPostsResolver(postRepository)
        val args = AdminQueries_Posts_Arguments.Builder(context).limit(10).offset(0).build()

        assertThrows<AuthorizationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
    }

    // ── AdminCommentsResolver ─────────────────────────────────────────────────

    @Test
    fun `AdminCommentsResolver returns paginated comments with totalCount`() = runBlocking {
        val resolver = AdminCommentsResolver(commentRepository)
        val args = AdminQueries_Comments_Arguments.Builder(context).limit(10).offset(0).build()

        every { commentRepository.findPage(10, 0) } returns listOf(mockComment)
        every { commentRepository.count() } returns 1L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(1, result.getTotalCount())
        assertEquals(1, result.getComments().size)
        assertEquals(commentId.toString(), result.getComments()[0].getId().internalID)
    }

    @Test
    fun `AdminCommentsResolver respects limit and offset arguments`() = runBlocking {
        val resolver = AdminCommentsResolver(commentRepository)
        val args = AdminQueries_Comments_Arguments.Builder(context).limit(5).offset(10).build()

        every { commentRepository.findPage(5, 10) } returns listOf(mockComment)
        every { commentRepository.count() } returns 20L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(20, result.getTotalCount())
        assertEquals(1, result.getComments().size)
    }

    @Test
    fun `AdminCommentsResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminCommentsResolver(commentRepository)
        val args = AdminQueries_Comments_Arguments.Builder(context).limit(10).offset(0).build()

        assertThrows<AuthorizationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
    }
}
