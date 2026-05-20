package org.tuchscherer.resolvers

import org.tuchscherer.ai.AIServiceException
import org.tuchscherer.ai.NoOpAIService
import org.tuchscherer.auth.AuthenticationException
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.User
import org.tuchscherer.viadapp.resolvers.RephraseContentResolver
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.context.GlobalContext
import viaduct.api.grts.Mutation_RephraseContent_Arguments
import viaduct.api.grts.Query
import viaduct.api.grts.RephraseTone
import viaduct.api.testing.ResolverTestBase
import java.util.UUID

class RephraseContentResolverTest : ResolverTestBase() {

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
    fun `RephraseContentResolver returns rephrased content for valid input`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Hello world")
            .tone(RephraseTone.PROFESSIONAL)
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("[PROFESSIONAL] Hello world", result.getRephrasedContent())
    }

    @Test
    fun `RephraseContentResolver uses PROFESSIONAL as default tone when tone is null`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Some content")
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("[PROFESSIONAL] Some content", result.getRephrasedContent())
    }

    @Test
    fun `RephraseContentResolver returns CASUAL rephrased content`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Hello world")
            .tone(RephraseTone.CASUAL)
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("[CASUAL] Hello world", result.getRephrasedContent())
    }

    @Test
    fun `RephraseContentResolver returns CONCISE rephrased content`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Hello world")
            .tone(RephraseTone.CONCISE)
            .build()

        val result = runMutationFieldResolver(resolver) {
            queryValue = queryObj()
            arguments = args
            requestContext = RequestContext(user = mockUser)
        }

        assertNotNull(result)
        assertEquals("[CONCISE] Hello world", result.getRephrasedContent())
    }

    @Test
    fun `RephraseContentResolver throws IllegalArgumentException for blank content`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("   ")
            .build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                queryValue = queryObj()
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `RephraseContentResolver throws IllegalArgumentException for content exceeding 50000 characters`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("a".repeat(50_001))
            .build()

        assertThrows<IllegalArgumentException> {
            runMutationFieldResolver(resolver) {
                queryValue = queryObj()
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
    }

    @Test
    fun `RephraseContentResolver throws AuthenticationException when not authenticated`() = runBlocking {
        val resolver = RephraseContentResolver(NoOpAIService())
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Hello world")
            .build()

        assertThrows<AuthenticationException> {
            runMutationFieldResolver(resolver) {
                queryValue = queryObj()
                arguments = args
                requestContext = RequestContext()
            }
        }
    }

    @Test
    fun `RephraseContentResolver propagates AIServiceException when AI service fails`() = runBlocking {
        val failingAiService = mockk<org.tuchscherer.ai.AIService>()
        every {
            failingAiService.rephrase(any(), any())
        } throws AIServiceException("Ollama is down")

        val resolver = RephraseContentResolver(failingAiService)
        val args = Mutation_RephraseContent_Arguments.Builder(context)
            .content("Hello world")
            .build()

        val ex = assertThrows<AIServiceException> {
            runMutationFieldResolver(resolver) {
                queryValue = queryObj()
                arguments = args
                requestContext = RequestContext(user = mockUser)
            }
        }
        assertNotNull(ex.message)
        assert(ex.message!!.contains("Ollama is down"))
    }
}
