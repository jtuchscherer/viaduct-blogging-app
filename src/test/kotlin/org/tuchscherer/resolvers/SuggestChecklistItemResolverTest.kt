package org.tuchscherer.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.ai.AIServiceException
import org.tuchscherer.ai.NoOpAIService
import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.tuchscherer.viadapp.checkedlist.resolverbases.MutationResolvers
import org.tuchscherer.viadapp.resolvers.SuggestChecklistItemMutationResolver
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import viaduct.api.grts.Mutation_SuggestChecklistItem_Arguments
import viaduct.api.grts.Query
import viaduct.api.testing.ResolverTestBase
import java.util.UUID

class SuggestChecklistItemResolverTest : ResolverTestBase() {

    private lateinit var mockUser: User
    private val userId = UUID.randomUUID()

    private fun queryObj() = Query.Builder(context).build()

    @BeforeEach
    fun setup() {
        mockUser = mockk(relaxed = true)
        every { mockUser.id } returns EntityID(userId, mockk())
        every { mockUser.username } returns "testuser"

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
    }

    @Test
    fun `returns suggested text when given 3 or more items`() = runBlocking {
        val resolver = SuggestChecklistItemMutationResolver(NoOpAIService())
        val args = Mutation_SuggestChecklistItem_Arguments.Builder(context)
            .existingItems(listOf("Buy milk", "Buy eggs", "Buy bread"))
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        // NoOpAIService returns "Item ${size + 1}"
        assertEquals("Item 4", result.getSuggestedText())
    }

    @Test
    fun `returns suggestion with exactly 3 items (boundary)`() = runBlocking {
        val resolver = SuggestChecklistItemMutationResolver(NoOpAIService())
        val args = Mutation_SuggestChecklistItem_Arguments.Builder(context)
            .existingItems(listOf("A", "B", "C"))
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("Item 4", result.getSuggestedText())
    }

    @Test
    fun `throws IllegalArgumentException for fewer than 3 items`() {
        // Validation fires before building the result — mock the Context directly
        val resolver = SuggestChecklistItemMutationResolver(NoOpAIService())
        val ctx = mockk<MutationResolvers.SuggestChecklistItem.Context>(relaxed = true)
        every { ctx.arguments.existingItems } returns listOf("A", "B")
        every { ctx.requestContext } returns RequestContext(user = mockUser)

        assertThrows<IllegalArgumentException> {
            runBlocking { resolver.resolve(ctx) }
        }
    }

    @Test
    fun `throws IllegalArgumentException for empty item list`() {
        val resolver = SuggestChecklistItemMutationResolver(NoOpAIService())
        val ctx = mockk<MutationResolvers.SuggestChecklistItem.Context>(relaxed = true)
        every { ctx.arguments.existingItems } returns emptyList()
        every { ctx.requestContext } returns RequestContext(user = mockUser)

        assertThrows<IllegalArgumentException> {
            runBlocking { resolver.resolve(ctx) }
        }
    }

    @Test
    fun `throws AuthenticationException when not authenticated`() {
        val resolver = SuggestChecklistItemMutationResolver(NoOpAIService())
        val args = Mutation_SuggestChecklistItem_Arguments.Builder(context)
            .existingItems(listOf("A", "B", "C"))
            .build()

        assertThrows<AuthenticationException> {
            runBlocking {
                runMutationFieldResolver(resolver) {
                    queryValue = queryObj()
                    arguments = args
                    requestContext = RequestContext()
                }
            }
        }
    }

    @Test
    fun `propagates AIServiceException when AI service fails`() {
        val failingAiService = mockk<org.tuchscherer.ai.AIService>()
        every { failingAiService.suggestNextItem(any()) } throws AIServiceException("Ollama is down")

        val resolver = SuggestChecklistItemMutationResolver(failingAiService)
        val ctx = mockk<MutationResolvers.SuggestChecklistItem.Context>(relaxed = true)
        every { ctx.arguments.existingItems } returns listOf("A", "B", "C")
        every { ctx.requestContext } returns RequestContext(user = mockUser)

        val ex = assertThrows<AIServiceException> {
            runBlocking { resolver.resolve(ctx) }
        }
        assertEquals("Ollama is down", ex.message)
    }
}
