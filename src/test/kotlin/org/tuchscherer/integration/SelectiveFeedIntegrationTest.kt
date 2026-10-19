package org.tuchscherer.integration

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.config.ViaductSchemas
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostProjection
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.PostCommentsFieldResolver
import org.tuchscherer.viadapp.resolvers.PostsConnectionResolver
import org.tuchscherer.viadapp.resolvers.PostsResolver
import org.tuchscherer.viadapp.resolvers.BlogPostNodeResolver
import org.tuchscherer.viadapp.resolvers.PostContentResolver
import org.tuchscherer.viadapp.analytics.resolvers.BlogPostReadTimeMinutesBatchResolver
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.spi.CodeInjector
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.inject.Provider

/** Runs the generated selective contexts and required selections in the actual engine. */
class SelectiveFeedIntegrationTest {
    private val repository = mockk<PostRepository>()
    private val comments = mockk<CommentRepository>()
    private val postId = UUID.randomUUID()
    private val authorId = UUID.randomUUID()
    private val globalId = Base64.getEncoder().encodeToString("BlogPost:$postId".toByteArray())
    private val requestedProjections = mutableListOf<Set<PostField>>()

    private val viaduct by lazy {
        val injector = object : CodeInjector {
            override fun <T> getProvider(clazz: Class<T>): Provider<T> = Provider {
                clazz.cast(
                    when (clazz) {
                        PostsResolver::class.java -> PostsResolver(repository)
                        PostsConnectionResolver::class.java -> PostsConnectionResolver(repository)
                        BlogPostNodeResolver::class.java -> BlogPostNodeResolver(repository)
                        PostContentResolver::class.java -> PostContentResolver(repository)
                        PostCommentsFieldResolver::class.java -> PostCommentsFieldResolver(comments)
                        BlogPostReadTimeMinutesBatchResolver::class.java -> BlogPostReadTimeMinutesBatchResolver()
                        else -> error("Unexpected resolver: ${clazz.name}")
                    }
                )
            }
        }
        ViaductBuilder()
            .withScopedSchemas(ViaductSchemas.all)
            .withTenantModuleInjectorFactory(SharedTenantModuleInjectorFactory(injector))
            .build()
    }

    private fun stubFeed(content: String = "Body", status: String = PostStatus.PUBLISHED) {
        every { repository.findPublishedProjection(any(), any(), any()) } answers {
            listOf(PostProjection(postId, PostType.BLOG_POST, emptySet()))
        }
        every { repository.findProjectedByIds(any(), any()) } answers {
            val fields = secondArg<Set<PostField>>()
            requestedProjections += fields
            mapOf(postId to
                PostProjection(
                    postId, PostType.BLOG_POST, fields,
                    title = "Title".takeIf { PostField.TITLE in fields },
                    content = content.takeIf { PostField.CONTENT in fields },
                    status = status,
                    publishedAt = LocalDateTime.of(2026, 10, 19, 12, 0).takeIf { PostField.PUBLISHED_AT in fields },
                    authorId = authorId,
                )
            )
        }
        every { repository.count() } returns 1L
    }

    @Test
    fun `aliases fragments and skipped fields request only their selected SQL columns`() = runBlocking<Unit> {
        stubFeed()

        val result = execute(
            """
            query {
              feed: posts { ...Preview content @skip(if: true) }
            }
            fragment Preview on BlogPost { id headline: title }
            """.trimIndent()
        )

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(mapOf("feed" to listOf(mapOf("id" to globalId, "headline" to "Title"))), result["data"])
        assertTrue(requestedProjections.isNotEmpty())
        assertTrue(requestedProjections.all { it == setOf(PostField.TITLE) }, requestedProjections.toString())
    }

    @Test
    fun `selecting content requests it and preserves publication values`() = runBlocking<Unit> {
        stubFeed()

        val result = execute("{ posts { content status publishedAt } }")

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(
            mapOf("posts" to listOf(mapOf("content" to "Body", "status" to "PUBLISHED", "publishedAt" to "2026-10-19T12:00"))),
            result["data"],
        )
        assertEquals(setOf(PostField.CONTENT, PostField.STATUS, PostField.PUBLISHED_AT), requestedProjections.flatten().toSet())
    }

    @Test
    fun `nested resolver dependency on identity works without scalar payloads`() = runBlocking<Unit> {
        stubFeed()
        every { comments.findByPostId(postId) } returns emptyList()

        val result = execute("{ posts { comments { content } } }")

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(mapOf("posts" to listOf(mapOf("comments" to emptyList<Any>()))), result["data"])
        assertTrue(requestedProjections.all { it.isEmpty() }, requestedProjections.toString())
    }

    @Test
    fun `connection projects fields through edge node selections and retains pagination`() = runBlocking<Unit> {
        stubFeed()

        val result = execute("{ postsConnection(first: 1) { totalCount edges { node { id ... on BlogPost { title } } } pageInfo { hasNextPage hasPreviousPage } } }")

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(
            mapOf(
                "postsConnection" to mapOf(
                    "totalCount" to 1,
                    "edges" to listOf(mapOf("node" to mapOf("id" to globalId, "title" to "Title"))),
                    "pageInfo" to mapOf("hasNextPage" to false, "hasPreviousPage" to false),
                )
            ),
            result["data"],
        )
        assertTrue(requestedProjections.all { it == setOf(PostField.TITLE) }, requestedProjections.toString())
    }

    @Test
    fun `analytics batch resolver receives its implicit content dependency for lists`() = runBlocking<Unit> {
        stubFeed(content = List(400) { "word" }.joinToString(" "))

        val result = execute("{ posts { id readTimeMinutes } }")

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(mapOf("posts" to listOf(mapOf("id" to globalId, "readTimeMinutes" to 2.0))), result["data"])
        assertTrue(PostField.CONTENT in requestedProjections.flatten(), requestedProjections.toString())
    }

    @Test
    fun `analytics batch resolver receives its implicit content dependency through connection nodes`() = runBlocking<Unit> {
        stubFeed(content = List(400) { "word" }.joinToString(" "))

        val result = execute("{ postsConnection(first: 1) { edges { node { id ... on BlogPost { readTimeMinutes } } } } }")

        assertNull(result["errors"], result.toString() + requestedProjections.toString())
        assertEquals(
            mapOf("postsConnection" to mapOf("edges" to listOf(mapOf("node" to mapOf("id" to globalId, "readTimeMinutes" to 2.0))))),
            result["data"],
        )
        assertTrue(PostField.CONTENT in requestedProjections.flatten(), requestedProjections.toString())
    }

    @Test
    fun `connection totalCount can be selected without edges`() = runBlocking<Unit> {
        stubFeed()

        val result = execute("{ postsConnection { totalCount } }")

        assertNull(result["errors"], result.toString())
        assertEquals(mapOf("postsConnection" to mapOf("totalCount" to 1)), result["data"])
        assertTrue(requestedProjections.all { it.isEmpty() }, requestedProjections.toString())
    }

    @Test
    fun `connection pageInfo can be selected without edges`() = runBlocking<Unit> {
        stubFeed()

        val result = execute("{ postsConnection { pageInfo { hasNextPage } } }")

        assertNull(result["errors"], result.toString())
        assertEquals(mapOf("postsConnection" to mapOf("pageInfo" to mapOf("hasNextPage" to false))), result["data"])
        assertTrue(requestedProjections.all { it.isEmpty() }, requestedProjections.toString())
    }

    @Test
    fun `projected draft node stays hidden from anonymous and unrelated viewers`() = runBlocking<Unit> {
        stubFeed(status = PostStatus.DRAFT)

        for (viewer in listOf(null, viewer(UUID.randomUUID(), isAdmin = false))) {
            val result = execute("{ node(id: \"$globalId\") { ... on BlogPost { title } } }", viewer)

            assertEquals(mapOf("node" to null), result["data"])
            assertTrue((result["errors"] as List<*>).isNotEmpty(), result.toString())
        }
    }

    @Test
    fun `projected draft node remains visible to owner and admin`() = runBlocking<Unit> {
        stubFeed(status = PostStatus.DRAFT)

        for (viewer in listOf(viewer(authorId, isAdmin = false), viewer(UUID.randomUUID(), isAdmin = true))) {
            val result = execute("{ node(id: \"$globalId\") { ... on BlogPost { title status } } }", viewer)

            assertNull(result["errors"], result.toString())
            assertEquals(mapOf("node" to mapOf("title" to "Title", "status" to "DRAFT")), result["data"])
        }
    }

    private fun viewer(id: UUID, isAdmin: Boolean): User = mockk<User>().also {
        every { it.id } returns EntityID(id, mockk())
        every { it.isAdmin } returns isAdmin
    }

    private suspend fun execute(query: String, viewer: User? = null): Map<String, Any?> = viaduct.execute(
        ExecutionInput.builder().operationText(query).requestContext(RequestContext(viewer)).build(),
        ViaductSchemas.schemaIdFor("public"),
    ).toSpecification()
}
