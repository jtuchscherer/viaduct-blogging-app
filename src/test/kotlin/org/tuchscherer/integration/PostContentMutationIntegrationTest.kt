package org.tuchscherer.integration

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.config.ViaductSchemas
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.DatabaseTestHelper
import org.tuchscherer.database.repositories.ExposedPostRepository
import org.tuchscherer.database.repositories.ExposedUserRepository
import org.tuchscherer.viadapp.resolvers.AdminUpdatePostResolver
import org.tuchscherer.viadapp.resolvers.CreatePostResolver
import org.tuchscherer.viadapp.resolvers.PostContentResolver
import org.tuchscherer.viadapp.resolvers.UpdatePostResolver
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.spi.CodeInjector
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import java.util.Base64
import java.util.UUID
import javax.inject.Provider

/** Content is resolved after each mutation has persisted its write, using the real engine/SQL. */
class PostContentMutationIntegrationTest {
    private val posts = ExposedPostRepository()
    private val users = ExposedUserRepository()
    private val viaduct by lazy {
        val injector = object : CodeInjector {
            override fun <T> getProvider(clazz: Class<T>): Provider<T> = Provider {
                clazz.cast(
                    when (clazz) {
                        CreatePostResolver::class.java -> CreatePostResolver(posts)
                        UpdatePostResolver::class.java -> UpdatePostResolver(posts)
                        AdminUpdatePostResolver::class.java -> AdminUpdatePostResolver(posts)
                        PostContentResolver::class.java -> PostContentResolver(posts)
                        else -> error("Unexpected resolver: ${clazz.name}")
                    }
                )
            }
        }
        ViaductBuilder().withScopedSchemas(ViaductSchemas.all)
            .withTenantModuleInjectorFactory(SharedTenantModuleInjectorFactory(injector)).build()
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupDatabase() = DatabaseTestHelper.setupDatabase()

        @AfterAll
        @JvmStatic
        fun tearDownDatabase() = DatabaseTestHelper.tearDownDatabase()
    }

    @AfterEach
    fun cleanDatabase() = DatabaseTestHelper.cleanDatabase()

    @Test
    fun `create mutation returns persisted content through its field resolver`() = runBlocking<Unit> {
        val author = author()

        val result = execute(
            "mutation { createPost(input: { title: \"New title\", content: \"Created body\" }) { title content status } }",
            author,
        )

        assertNull(result["errors"], result.toString())
        assertEquals(
            mapOf("createPost" to mapOf("title" to "New title", "content" to "Created body", "status" to "PUBLISHED")),
            result["data"],
        )
    }

    @Test
    fun `update mutation returns updated persisted content through its field resolver`() = runBlocking<Unit> {
        val author = author()
        val id = globalId(posts.create("Original title", "Original body", author.id).id)

        val result = execute(
            "mutation { updatePost(input: { id: \"$id\", title: \"Updated title\", content: \"Updated body\" }) { id title content } }",
            author,
        )

        assertNull(result["errors"], result.toString())
        assertEquals(
            mapOf("updatePost" to mapOf("id" to id, "title" to "Updated title", "content" to "Updated body")),
            result["data"],
        )
    }

    @Test
    fun `admin update mutation returns updated content through its field resolver`() = runBlocking<Unit> {
        val author = author()
        val id = globalId(posts.create("Original title", "Original body", author.id).id)
        val admin = requireNotNull(users.updateFields(author.id, isAdmin = true))

        val result = execute(
            "mutation { admin { updatePost(input: { id: \"$id\", content: \"Admin body\" }) { id content } } }",
            admin,
            schema = "admin",
        )

        assertNull(result["errors"], result.toString())
        assertEquals(mapOf("admin" to mapOf("updatePost" to mapOf("id" to id, "content" to "Admin body"))), result["data"])
    }

    private fun author() = users.create("author", "author@example.com", "Author", "hash", "salt")

    private fun globalId(id: UUID): String = Base64.getEncoder().encodeToString("BlogPost:$id".toByteArray())

    private suspend fun execute(query: String, viewer: User, schema: String = "public"): Map<String, Any?> =
        viaduct.execute(
            ExecutionInput.builder().operationText(query).requestContext(RequestContext(viewer)).build(),
            ViaductSchemas.schemaIdFor(schema),
        ).toSpecification()
}
