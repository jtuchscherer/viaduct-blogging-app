package org.tuchscherer.integration

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.checkedlist.ChecklistService
import org.tuchscherer.checkedlist.checkedListKoinModule
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.checkedlist.repositories.CheckedListItemRepository
import org.tuchscherer.config.*
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.*
import viaduct.service.ViaductBuilder
import viaduct.service.api.ExecutionInput
import viaduct.service.api.spi.SharedTenantModuleInjectorFactory
import java.util.Base64
import java.util.UUID

/** Real engine and persistence: public node entry points must enforce the same policy as feeds. */
class ChecklistAccessIntegrationTest {
    private lateinit var author: User
    private lateinit var stranger: User
    private lateinit var admin: User
    private lateinit var posts: PostCreationPort
    private lateinit var items: CheckedListItemRepository
    private lateinit var service: ChecklistService
    private val viaduct by lazy {
        ViaductBuilder()
            .withScopedSchemas(ViaductSchemas.all)
            .withTenantModuleInjectorFactory(SharedTenantModuleInjectorFactory(KoinTenantCodeInjector()))
            .build()
    }

    @BeforeEach
    fun setup() {
        DatabaseTestHelper.setupDatabase()
        val koin = startKoin {
            modules(repositoryModule, resolverModule, checkedListKoinModule, checkedListPortModule)
        }.koin
        val users = koin.get<UserRepository>()
        author = users.create("owner", "owner@example.com", "Owner", "hash", "salt")
        stranger = users.create("stranger", "stranger@example.com", "Stranger", "hash", "salt")
        val createdAdmin = users.create("admin", "admin@example.com", "Admin", "hash", "salt")
        admin = requireNotNull(users.updateFields(createdAdmin.id.value, isAdmin = true))
        posts = koin.get()
        items = koin.get()
        service = koin.get()
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
        org.jetbrains.exposed.v1.jdbc.transactions.transaction {
            org.jetbrains.exposed.v1.jdbc.SchemaUtils.drop(org.tuchscherer.checkedlist.database.CheckedListItems)
        }
        DatabaseTestHelper.tearDownDatabase()
    }

    @Test
    fun `anonymous and other users cannot read draft checklist or item nodes`() = runBlocking<Unit> {
        val post = posts.createCheckedListPost("Private title", author.id.value, "Private description", "DRAFT")
        val item = items.addItem(post.id, "Private item")
        for (viewer in listOf(null, stranger)) {
            for ((type, id) in listOf("CheckedListPost" to post.id, "CheckedListItem" to item.id)) {
                val result = node(type, id, viewer)
                assertNull((result["data"] as Map<*, *>)["node"], result.toString())
                assertNotNull(result["errors"], result.toString())
                assertFalse(result.toString().contains("Private"), result.toString())
            }
        }
    }

    @Test
    fun `author and admin can read draft checklist and its items`() = runBlocking<Unit> {
        val post = posts.createCheckedListPost("Private title", author.id.value, "Private description", "DRAFT")
        val item = items.addItem(post.id, "Private item")
        for (viewer in listOf(author, admin)) {
            val result = node("CheckedListPost", post.id, viewer)
            assertNull(result["errors"], result.toString())
            assertTrue(result.toString().contains("Private title"))
            assertTrue(result.toString().contains("Private item"))
            val itemResult = node("CheckedListItem", item.id, viewer)
            assertNull(itemResult["errors"], itemResult.toString())
            assertTrue(itemResult.toString().contains("Private item"))
        }
    }

    @Test
    fun `published checklist and item nodes are readable anonymously`() = runBlocking<Unit> {
        val post = posts.createCheckedListPost("Public title", author.id.value)
        val item = items.addItem(post.id, "Public item")
        for ((type, id) in listOf("CheckedListPost" to post.id, "CheckedListItem" to item.id)) {
            val result = node(type, id, null)
            assertNull(result["errors"], result.toString())
            assertTrue(result.toString().contains("Public"))
        }
    }

    @Test
    fun `every checklist write rejects anonymous users nonauthors and nonowner admins`() = runBlocking<Unit> {
        val post = posts.createCheckedListPost("Original", author.id.value)
        val item = items.addItem(post.id, "Original item")
        val postId = globalId("CheckedListPost", post.id)
        val itemId = globalId("CheckedListItem", item.id)
        val commands = listOf(
            "updateCheckedListPost(input:{id:\"$postId\",title:\"Changed\"}) { id }",
            "deleteCheckedListPost(id:\"$postId\")",
            "addCheckedListItem(input:{postId:\"$postId\",text:\"Extra\"}) { id }",
            "updateCheckedListItem(input:{id:\"$itemId\",text:\"Changed\"}) { id }",
            "toggleCheckedListItem(id:\"$itemId\") { id }",
            "deleteCheckedListItem(id:\"$itemId\")",
        )
        for (viewer in listOf(null, stranger, admin)) {
            for (command in commands) {
                val result = execute("mutation { $command }", viewer)
                assertNotNull(result["errors"], "$command: $result")
                assertEquals("Original", posts.getPostData(post.id)?.title)
                assertEquals(listOf(item), items.getItemsForPost(post.id))
            }
        }
    }

    @Test
    fun `owner can create edit and delete checklist and items through the service`() {
        val post = service.create(author.id.value, "Title", "Description", listOf("One"), "DRAFT")
        assertEquals("DRAFT", post.status)
        assertEquals("Edited", service.update(author.id.value, post.id, "Edited", null).title)
        val added = service.addItem(author.id.value, post.id, "Two")
        assertEquals("Updated", service.updateItem(author.id.value, added.id, "Updated").text)
        assertTrue(service.toggleItem(author.id.value, added.id).checked)
        assertTrue(service.deleteItem(author.id.value, added.id))
        assertTrue(service.delete(author.id.value, post.id))
        assertNull(posts.getPostData(post.id))
        assertTrue(items.getItemsForPost(post.id).isEmpty())
    }

    private fun globalId(type: String, id: UUID): String =
        Base64.getEncoder().encodeToString("$type:$id".toByteArray())

    private suspend fun execute(query: String, viewer: User?): Map<String, Any?> =
        viaduct.execute(ExecutionInput.create(operationText = query, requestContext = viewer?.let { RequestContext(it) }),
            ViaductSchemas.schemaIdFor(ViaductSchemas.PUBLIC)).toSpecification()

    private suspend fun node(type: String, id: UUID, viewer: User?): Map<String, Any?> {
        val globalId = Base64.getEncoder().encodeToString("$type:$id".toByteArray())
        return viaduct.execute(ExecutionInput.create(
            operationText = """{ node(id: "$globalId") {
                ... on CheckedListPost { title description status items { text } }
                ... on CheckedListItem { text }
            } }""",
            requestContext = viewer?.let { RequestContext(it) },
        ), ViaductSchemas.schemaIdFor(ViaductSchemas.PUBLIC)).toSpecification()
    }
}
