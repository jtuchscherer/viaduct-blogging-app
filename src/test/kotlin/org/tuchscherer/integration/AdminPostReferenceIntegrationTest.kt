package org.tuchscherer.integration

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.config.ViaductSchemas
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.AdminPostResolver
import org.tuchscherer.viadapp.resolvers.PostResolver
import org.tuchscherer.viadapp.resolvers.PostCommentsFieldResolver
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.spi.CodeInjector
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import javax.inject.Provider

/** Exercises lazy root-field references in the same engine used by the application. */
class AdminPostReferenceIntegrationTest {
    private val postRepository = mockk<PostRepository>()
    private val commentRepository = mockk<CommentRepository>()
    private val postId = UUID.randomUUID()
    private val authorId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val postGlobalId = Base64.getEncoder().encodeToString("BlogPost:$postId".toByteArray())

    private val viaduct by lazy {
        val injector = object : CodeInjector {
            override fun <T> getProvider(clazz: Class<T>): Provider<T> = Provider {
                clazz.cast(
                    when (clazz) {
                        AdminPostResolver::class.java -> AdminPostResolver()
                        PostResolver::class.java -> PostResolver(postRepository)
                        PostCommentsFieldResolver::class.java -> PostCommentsFieldResolver(commentRepository)
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

    @Test
    fun `admin post returns the referenced post and client selection`() = runBlocking<Unit> {
        every { postRepository.findById(postId) } returns post()

        val result = execute("{ admin { post(id: \"$postGlobalId\") { id title content } } }")

        assertEquals(
            mapOf("admin" to mapOf("post" to mapOf("id" to postGlobalId, "title" to "Test Post", "content" to "Body"))),
            result["data"],
        )
        assertNull(result["errors"], result.toString())
    }

    @Test
    fun `admin post returns null when referenced post is missing`() = runBlocking<Unit> {
        every { postRepository.findById(postId) } returns null

        val result = execute("{ admin { post(id: \"$postGlobalId\") { id } } }")

        assertEquals(mapOf("admin" to mapOf("post" to null)), result["data"])
        assertNull(result["errors"], result.toString())
    }

    @Test
    fun `reference resolves nested fields and their required selections`() = runBlocking<Unit> {
        every { postRepository.findById(postId) } returns post()
        val comment = mockk<Comment>()
        every { comment.id } returns EntityID(UUID.randomUUID(), mockk())
        every { comment.content } returns "A comment"
        every { comment.createdAt } returns LocalDateTime.of(2026, 10, 19, 12, 0)
        every { commentRepository.findByPostId(postId) } returns listOf(comment)

        val result = execute("{ admin { post(id: \"$postGlobalId\") { comments { content } } } }")

        assertEquals(
            mapOf("admin" to mapOf("post" to mapOf("comments" to listOf(mapOf("content" to "A comment"))))),
            result["data"],
        )
        assertNull(result["errors"], result.toString())
    }

    @Test
    fun `admin identity reaches referenced lookup so another author's draft is visible`() = runBlocking<Unit> {
        every { postRepository.findById(postId) } returns post(PostStatus.DRAFT)

        val result = execute("{ admin { draft: post(id: \"$postGlobalId\") { title status publishedAt } } }")

        assertEquals(
            mapOf("admin" to mapOf("draft" to mapOf("title" to "Test Post", "status" to "DRAFT", "publishedAt" to null))),
            result["data"],
        )
        assertNull(result["errors"], result.toString())
    }

    @Test
    fun `public schema still rejects admin post references`() = runBlocking<Unit> {
        val result = execute("{ admin { post(id: \"$postGlobalId\") { id } } }", schema = "public")

        assertTrue((result["errors"] as List<*>).isNotEmpty(), result.toString())
        assertNull(result["data"])
    }

    @Test
    fun `referenced lookup failure remains a GraphQL field error`() = runBlocking<Unit> {
        every { postRepository.findById(postId) } throws NotFoundException("Post disappeared")

        val result = execute("{ admin { post(id: \"$postGlobalId\") { id } } }")

        assertEquals(mapOf("admin" to mapOf("post" to null)), result["data"])
        val errors = result["errors"] as List<*>
        assertEquals(1, errors.size, result.toString())
        val error = errors.single() as Map<*, *>
        assertEquals(listOf("admin", "post"), error["path"])
    }

    private suspend fun execute(query: String, schema: String = "admin"): Map<String, Any?> {
        val admin = mockk<User>()
        every { admin.id } returns EntityID(adminId, mockk())
        every { admin.isAdmin } returns true
        return viaduct.execute(
            ExecutionInput.builder()
                .operationText(query)
                .requestContext(RequestContext(admin))
                .build(),
            ViaductSchemas.schemaIdFor(schema),
        ).toSpecification()
    }

    private fun post(status: String = PostStatus.PUBLISHED): Post {
        val result = mockk<Post>()
        every { result.id } returns EntityID(postId, mockk())
        every { result.authorId } returns EntityID(authorId, mockk())
        every { result.title } returns "Test Post"
        every { result.content } returns "Body"
        every { result.status } returns status
        every { result.publishedAt } returns null
        every { result.createdAt } returns LocalDateTime.of(2026, 10, 19, 12, 0)
        every { result.updatedAt } returns LocalDateTime.of(2026, 10, 19, 12, 0)
        return result
    }
}
