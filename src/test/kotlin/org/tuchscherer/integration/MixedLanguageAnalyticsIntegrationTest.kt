package org.tuchscherer.integration

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.tuchscherer.analytics.analyticsKoinModule
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.checkedlist.checkedListKoinModule
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.config.KoinTenantCodeInjector
import org.tuchscherer.config.ViaductSchemas
import org.tuchscherer.config.analyticsPortModule
import org.tuchscherer.config.resolverModule
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.PostType
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostProjection
import org.tuchscherer.database.repositories.UserRepository
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.spi.FlagManager
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import java.time.LocalDateTime
import java.util.Base64
import java.util.Collections
import java.util.UUID

/** Real Kotlin and Java resolver execution with the production Koin injector and tenant registry. */
class MixedLanguageAnalyticsIntegrationTest {
    private val blog = UUID.randomUUID()
    private val secondBlog = UUID.randomUUID()
    private val checklist = UUID.randomUUID()
    private val emptyChecklist = UUID.randomUUID()
    private val draft = UUID.randomUUID()
    private val missing = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val time = LocalDateTime.of(2026, 10, 19, 12, 0)
    private val repository = mockk<PostRepository>()
    private val items = mockk<CheckedListItemRepository>()
    private val creation = mockk<PostCreationPort>()
    private val views = CountingViews()
    private val posts = linkedMapOf<UUID, Post>()

    private val viaduct by lazy {
        ViaductBuilder()
            .withScopedSchemas(ViaductSchemas.all)
            .withTenantModuleInjectorFactory(SharedTenantModuleInjectorFactory(KoinTenantCodeInjector()))
            .withFlagManager(object : FlagManager {
                override fun isEnabled(flag: FlagManager.Flag): Boolean =
                    flag == FlagManager.Flags.ENABLE_RESOLVER_OUTPUT_MISSING_FIELD_ERRORS || FlagManager.Default.isEnabled(flag)
            })
            .build()
    }

    @BeforeEach
    fun setup() {
        posts[blog] = post(blog, PostType.BLOG_POST, "Blog", List(400) { "word" }.joinToString(" "))
        posts[secondBlog] = post(secondBlog, PostType.BLOG_POST, "Empty blog", "")
        posts[checklist] = post(checklist, PostType.CHECKED_LIST, "Checklist", "Description")
        posts[emptyChecklist] = post(emptyChecklist, PostType.CHECKED_LIST, "Empty checklist", "")
        posts[draft] = post(draft, PostType.BLOG_POST, "Private draft", "Draft body", PostStatus.DRAFT)
        every { repository.findByIds(any()) } answers {
            firstArg<List<UUID>>().mapNotNull { id -> posts[id]?.let { id to it } }.toMap()
        }
        every { repository.findById(any()) } answers { posts[firstArg<UUID>()] }
        every { repository.findProjectedByIds(any(), any()) } answers {
            val fields = secondArg<Set<PostField>>()
            firstArg<List<UUID>>().mapNotNull { id -> posts[id]?.let { id to projection(it, fields) } }.toMap()
        }
        every { repository.publishedIds(any()) } answers {
            firstArg<List<UUID>>().filter { posts[it]?.status == PostStatus.PUBLISHED }.toSet()
        }
        every { repository.count() } returns posts.size.toLong()
        every { creation.getPostsData(any()) } answers {
            firstArg<List<UUID>>().mapNotNull { id ->
                posts[id]?.takeIf { it.postType == PostType.CHECKED_LIST }?.let { id to postData(it) }
            }.toMap()
        }
        every { items.getItemsForPost(checklist) } returns listOf(
            CheckedListItemData(UUID.randomUUID(), checklist, List(200) { "word" }.joinToString(" "), false, 0, time.toString()),
        )
        every { items.getItemsForPost(emptyChecklist) } returns emptyList()
        views.counts.putAll(mapOf(blog to 5L, secondBlog to 3L, checklist to 7L))
        views.ranking = listOf(checklist, blog, secondBlog, emptyChecklist)
        val users = mockk<UserRepository>()
        val comments = mockk<CommentRepository>()
        val likes = mockk<LikeRepository>()
        every { users.count() } returns 1L
        every { comments.count() } returns 0L
        every { likes.count() } returns 0L
        GlobalContext.getOrNull()?.let { stopKoin() }
        startKoin {
            modules(
                resolverModule, checkedListKoinModule, analyticsKoinModule, analyticsPortModule,
                module {
                    single<PostRepository> { repository }
                    single<PostViewRepository> { views }
                    single<PostCreationPort> { creation }
                    single<CheckedListItemRepository> { items }
                    single<UserRepository> { users }
                    single<CommentRepository> { comments }
                    single<LikeRepository> { likes }
                },
            )
        }
    }

    @AfterEach
    fun cleanup() = stopKoin()

    @Test
    fun `Java analytics resolves Kotlin nodes and nested checklist dependencies in one operation`() = runBlocking<Unit> {
        val selection = """{
            __typename id title
            ... on BlogPost { viewCount readTimeMinutes }
            ... on CheckedListPost { viewCount readTimeMinutes }
        }"""
        val result = execute("{ trending $selection repeated: trending $selection }")
        assertNull(result["errors"], result.toString())
        val expected = listOf(
            expectedPost(checklist, "CheckedListPost", "Checklist", 7, 1.0),
            expectedPost(blog, "BlogPost", "Blog", 5, 2.0),
            expectedPost(secondBlog, "BlogPost", "Empty blog", 3, 0.5),
            expectedPost(emptyChecklist, "CheckedListPost", "Empty checklist", 0, 0.5),
        )
        assertEquals(mapOf("trending" to expected, "repeated" to expected), result["data"])
        // A concrete batch is one persistence round-trip, even for multiple parents and aliases.
        assertEquals(2, views.bulkReads.size, views.bulkReads.toString())
        assertEquals(setOf(setOf(blog, secondBlog), setOf(checklist, emptyChecklist)), views.bulkReads.map { it.toSet() }.toSet())
    }

    @Test
    fun `anonymous Java view mutations update Kotlin admin totals and reject drafts or missing posts`() = runBlocking<Unit> {
        val result = execute("""mutation {
            blog: recordPostView(postId: "${globalId("BlogPost", blog)}")
            checklist: recordPostView(postId: "${globalId("CheckedListPost", checklist)}")
            draft: recordPostView(postId: "${globalId("BlogPost", draft)}")
            missing: recordPostView(postId: "${globalId("BlogPost", missing)}")
        }""")
        assertNull(result["errors"], result.toString())
        assertEquals(mapOf("blog" to true, "checklist" to true, "draft" to false, "missing" to false), result["data"])
        assertEquals(6L, views.counts[blog])
        assertEquals(8L, views.counts[checklist])
        assertFalse(views.counts.containsKey(draft))
        assertFalse(views.counts.containsKey(missing))
        val admin = mockk<User>()
        every { admin.id } returns EntityID(UUID.randomUUID(), mockk())
        every { admin.isAdmin } returns true
        val totals = execute("{ admin { stats { totalViews } } }", "admin", RequestContext(admin))
        assertNull(totals["errors"], totals.toString())
        assertEquals(mapOf("admin" to mapOf("stats" to mapOf("totalViews" to 17))), totals["data"])
    }

    @Test
    fun `mixed trending excludes drafts and deleted posts without disturbing rank order`() = runBlocking<Unit> {
        views.ranking = listOf(draft, missing, checklist, blog)
        val result = execute("{ trending(limit: 2) { id title } }")
        assertNull(result["errors"], result.toString())
        assertEquals(
            mapOf("trending" to listOf(
                mapOf("id" to globalId("CheckedListPost", checklist), "title" to "Checklist"),
                mapOf("id" to globalId("BlogPost", blog), "title" to "Blog"),
            )),
            result["data"],
        )
    }

    @Test
    fun `malformed view IDs produce GraphQL errors without incrementing totals`() = runBlocking<Unit> {
        val result = execute("""mutation { recordPostView(postId: "not-valid-base64!!!") }""")
        assertTrue((result["errors"] as List<*>).isNotEmpty(), result.toString())
        assertEquals(15L, views.getTotalViews())
    }

    @Test
    fun `Kotlin nested dependency failures propagate through Java read time`() = runBlocking<Unit> {
        views.ranking = listOf(checklist)
        every { items.getItemsForPost(checklist) } throws IllegalStateException("checklist storage unavailable")
        val result = execute("{ trending { ... on CheckedListPost { readTimeMinutes } } }")
        assertTrue((result["errors"] as List<*>).isNotEmpty(), result.toString())
    }

    private suspend fun execute(query: String, schema: String = "public", context: RequestContext = RequestContext()): Map<String, Any?> =
        viaduct.execute(
            ExecutionInput.builder().operationText(query).requestContext(context).build(),
            ViaductSchemas.schemaIdFor(schema),
        ).toSpecification()

    private fun globalId(type: String, id: UUID): String =
        Base64.getEncoder().encodeToString("$type:$id".toByteArray())

    private fun expectedPost(id: UUID, type: String, title: String, count: Int, readTime: Double) =
        mapOf("__typename" to type, "id" to globalId(type, id), "title" to title, "viewCount" to count, "readTimeMinutes" to readTime)

    private fun post(id: UUID, kind: String, title: String, content: String, status: String = PostStatus.PUBLISHED): Post {
        val value = mockk<Post>()
        every { value.id } returns EntityID(id, mockk())
        every { value.postType } returns kind
        every { value.title } returns title
        every { value.content } returns content
        every { value.authorId } returns EntityID(author, mockk())
        every { value.status } returns status
        every { value.publishedAt } returns if (status == PostStatus.PUBLISHED) time else null
        every { value.createdAt } returns time
        every { value.updatedAt } returns time
        return value
    }

    private fun postData(post: Post) = PostData(
        post.id.value, post.title, post.content, post.authorId.value, post.status,
        post.publishedAt?.toString(), post.createdAt.toString(), post.updatedAt.toString(),
    )

    private fun projection(post: Post, fields: Set<PostField>) = PostProjection(
        id = post.id.value,
        postType = post.postType,
        fields = fields,
        title = post.title.takeIf { PostField.TITLE in fields },
        content = post.content.takeIf { PostField.CONTENT in fields },
        // Visibility metadata is always available, independent of the requested output fields.
        status = post.status,
        authorId = post.authorId.value,
        publishedAt = post.publishedAt.takeIf { PostField.PUBLISHED_AT in fields },
        createdAt = post.createdAt.takeIf { PostField.CREATED_AT in fields },
        updatedAt = post.updatedAt.takeIf { PostField.UPDATED_AT in fields },
    )

    private class CountingViews : PostViewRepository {
        val counts = linkedMapOf<UUID, Long>()
        var ranking = emptyList<UUID>()
        val bulkReads: MutableList<List<UUID>> = Collections.synchronizedList(mutableListOf())
        override fun incrementViewCount(postId: UUID) { counts[postId] = (counts[postId] ?: 0L) + 1L }
        override fun bulkGetViewCounts(postIds: List<UUID>): Map<UUID, Long> {
            bulkReads += postIds.toList()
            return postIds.mapNotNull { id -> counts[id]?.let { id to it } }.toMap()
        }
        override fun getMostViewed(limit: Int): List<UUID> = ranking.take(limit)
        override fun getTotalViews(): Long = counts.values.sum()
    }
}
