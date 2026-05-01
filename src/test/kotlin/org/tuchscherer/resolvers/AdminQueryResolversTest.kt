@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.AdminCommentsResolver
import org.tuchscherer.viadapp.resolvers.AdminPostResolver
import org.tuchscherer.viadapp.resolvers.AdminPostsResolver
import org.tuchscherer.viadapp.resolvers.AdminStatsResolver
import org.tuchscherer.viadapp.resolvers.AdminUserContentCountsResolver
import org.tuchscherer.viadapp.resolvers.AdminUserResolver
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
import viaduct.api.grts.BlogPost as ViaductBlogPost
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
    private lateinit var likeRepository: LikeRepository

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
        likeRepository = mockk()

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
                single { likeRepository }
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

    @Test
    fun `AdminUsersResolver throws when totalCount exceeds Int MAX_VALUE (toCountInt guard)`() = runBlocking {
        val resolver = AdminUsersResolver(userRepository)
        val args = AdminQueries_Users_Arguments.Builder(context).limit(10).offset(0).build()
        every { userRepository.findPage(10, 0) } returns emptyList()
        every { userRepository.count() } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
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

    @Test
    fun `AdminPostsResolver throws when totalCount exceeds Int MAX_VALUE (toCountInt guard)`() = runBlocking {
        val resolver = AdminPostsResolver(postRepository)
        val args = AdminQueries_Posts_Arguments.Builder(context).limit(10).offset(0).build()
        every { postRepository.findPage(10, 0) } returns emptyList()
        every { postRepository.count() } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
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

    @Test
    fun `AdminCommentsResolver throws when totalCount exceeds Int MAX_VALUE (toCountInt guard)`() = runBlocking {
        val resolver = AdminCommentsResolver(commentRepository)
        val args = AdminQueries_Comments_Arguments.Builder(context).limit(10).offset(0).build()
        every { commentRepository.findPage(10, 0) } returns emptyList()
        every { commentRepository.count() } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    // ── AdminStatsResolver ────────────────────────────────────────────────────

    @Test
    fun `AdminStatsResolver returns aggregate counts`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)

        every { userRepository.count() } returns 5L
        every { postRepository.count() } returns 10L
        every { commentRepository.count() } returns 20L
        every { likeRepository.count() } returns 30L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = viaduct.api.types.Arguments.NoArguments,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(5, result.getUserCount())
        assertEquals(10, result.getPostCount())
        assertEquals(20, result.getCommentCount())
        assertEquals(30, result.getLikeCount())
    }

    // Overflow guards — each test pins one toCountInt() call site. If any site
    // reverts to a silent toInt(), the matching test fails. Testing each source
    // independently catches partial reverts that an aggregate test would miss.
    @Test
    fun `AdminStatsResolver throws when userCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)
        every { userRepository.count() } returns Int.MAX_VALUE.toLong() + 1L
        every { postRepository.count() } returns 0L
        every { commentRepository.count() } returns 0L
        every { likeRepository.count() } returns 0L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = viaduct.api.types.Arguments.NoArguments,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminStatsResolver throws when postCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)
        every { userRepository.count() } returns 0L
        every { postRepository.count() } returns Int.MAX_VALUE.toLong() + 1L
        every { commentRepository.count() } returns 0L
        every { likeRepository.count() } returns 0L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = viaduct.api.types.Arguments.NoArguments,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminStatsResolver throws when commentCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)
        every { userRepository.count() } returns 0L
        every { postRepository.count() } returns 0L
        every { commentRepository.count() } returns Int.MAX_VALUE.toLong() + 1L
        every { likeRepository.count() } returns 0L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = viaduct.api.types.Arguments.NoArguments,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminStatsResolver throws when likeCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)
        every { userRepository.count() } returns 0L
        every { postRepository.count() } returns 0L
        every { commentRepository.count() } returns 0L
        every { likeRepository.count() } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = viaduct.api.types.Arguments.NoArguments,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminStatsResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminStatsResolver(userRepository, postRepository, commentRepository, likeRepository)

        assertThrows<AuthorizationException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = viaduct.api.types.Arguments.NoArguments,
                requestContext = RequestContext(user = mockRegularUser)
            )
        }
    }

    // ── AdminUserResolver ─────────────────────────────────────────────────────

    @Test
    fun `AdminUserResolver returns user when found`() = runBlocking {
        val resolver = AdminUserResolver(userRepository)
        val args = AdminQueries_User_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()

        every { userRepository.findById(dbUserId) } returns mockDbUser

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertNotNull(result)
        assertEquals(dbUserId.toString(), result!!.getId().internalID)
        assertEquals("dbuser", result.getUsername())
    }

    @Test
    fun `AdminUserResolver returns null when user not found`() = runBlocking {
        val resolver = AdminUserResolver(userRepository)
        val args = AdminQueries_User_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()

        every { userRepository.findById(dbUserId) } returns null

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertNull(result)
    }

    @Test
    fun `AdminUserResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminUserResolver(userRepository)
        val args = AdminQueries_User_Arguments.Builder(context)
            .id(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()

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

    // ── AdminUserContentCountsResolver ────────────────────────────────────────

    @Test
    fun `AdminUserContentCountsResolver returns content counts for user`() = runBlocking {
        val resolver = AdminUserContentCountsResolver(postRepository, commentRepository, likeRepository)
        val args = AdminQueries_UserContentCounts_Arguments.Builder(context)
            .userId(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()

        every { postRepository.countByAuthorId(dbUserId) } returns 3L
        every { commentRepository.countByUserId(dbUserId) } returns 7L
        every { likeRepository.countByUserId(dbUserId) } returns 15L

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertEquals(3, result.getPostCount())
        assertEquals(7, result.getCommentCount())
        assertEquals(15, result.getLikeCount())
    }

    @Test
    fun `AdminUserContentCountsResolver throws when postCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminUserContentCountsResolver(postRepository, commentRepository, likeRepository)
        val args = AdminQueries_UserContentCounts_Arguments.Builder(context)
            .userId(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()
        every { postRepository.countByAuthorId(dbUserId) } returns Int.MAX_VALUE.toLong() + 1L
        every { commentRepository.countByUserId(dbUserId) } returns 0L
        every { likeRepository.countByUserId(dbUserId) } returns 0L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminUserContentCountsResolver throws when commentCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminUserContentCountsResolver(postRepository, commentRepository, likeRepository)
        val args = AdminQueries_UserContentCounts_Arguments.Builder(context)
            .userId(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()
        every { postRepository.countByAuthorId(dbUserId) } returns 0L
        every { commentRepository.countByUserId(dbUserId) } returns Int.MAX_VALUE.toLong() + 1L
        every { likeRepository.countByUserId(dbUserId) } returns 0L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminUserContentCountsResolver throws when likeCount exceeds Int MAX_VALUE`() = runBlocking {
        val resolver = AdminUserContentCountsResolver(postRepository, commentRepository, likeRepository)
        val args = AdminQueries_UserContentCounts_Arguments.Builder(context)
            .userId(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()
        every { postRepository.countByAuthorId(dbUserId) } returns 0L
        every { commentRepository.countByUserId(dbUserId) } returns 0L
        every { likeRepository.countByUserId(dbUserId) } returns Int.MAX_VALUE.toLong() + 1L

        assertThrows<IllegalArgumentException> {
            runFieldResolver(
                resolver = resolver,
                objectValue = adminQueriesObj(),
                queryValue = queryObj(),
                arguments = args,
                requestContext = RequestContext(user = mockAdminUser)
            )
        }
    }

    @Test
    fun `AdminUserContentCountsResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminUserContentCountsResolver(postRepository, commentRepository, likeRepository)
        val args = AdminQueries_UserContentCounts_Arguments.Builder(context)
            .userId(context.globalIDFor(viaduct.api.grts.User.Reflection, dbUserId.toString()))
            .build()

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

    // ── AdminPostResolver ─────────────────────────────────────────────────────

    @Test
    fun `AdminPostResolver returns post when found`() = runBlocking {
        val resolver = AdminPostResolver(postRepository)
        val args = AdminQueries_Post_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns mockPost

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertNotNull(result)
        assertEquals(postId.toString(), result!!.getId().internalID)
        assertEquals("Test Post", result.getTitle())
    }

    @Test
    fun `AdminPostResolver returns null when post not found`() = runBlocking {
        val resolver = AdminPostResolver(postRepository)
        val args = AdminQueries_Post_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

        every { postRepository.findById(postId) } returns null

        val result = runFieldResolver(
            resolver = resolver,
            objectValue = adminQueriesObj(),
            queryValue = queryObj(),
            arguments = args,
            requestContext = RequestContext(user = mockAdminUser)
        )

        assertNull(result)
    }

    @Test
    fun `AdminPostResolver throws AuthorizationException for non-admin user`() = runBlocking {
        val resolver = AdminPostResolver(postRepository)
        val args = AdminQueries_Post_Arguments.Builder(context)
            .id(context.globalIDFor(ViaductBlogPost.Reflection, postId.toString()))
            .build()

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
