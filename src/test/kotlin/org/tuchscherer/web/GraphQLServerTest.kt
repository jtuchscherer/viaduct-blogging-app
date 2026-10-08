package org.tuchscherer.web

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.config.JwtConfig
import org.tuchscherer.config.ServerConfig
import viaduct.service.api.ExecutionResult
import kotlin.coroutines.cancellation.CancellationException

class GraphQLServerTest {
    private val executor = mockk<SchemaRoutingExecutor>()
    private val server = GraphQLServer(
        AuthDependencies(mockk(), mockk(), mockk(), JwtConfig("test-secret", "test")),
        ServerConfig(),
        ObservabilityDependencies(mockk(), mockk()),
        executor,
        mockk(),
    )

    @Test
    fun `successful execution returns GraphQL JSON`() = testApplication {
        val result = mockk<ExecutionResult>()
        every { result.toSpecification() } returns mapOf("data" to mapOf("posts" to emptyList<Any>()))
        coEvery { executor.execute(any(), SchemaRoutingExecutor.Audience.PUBLIC) } returns result
        application { installGraphQLRoute() }

        val response = client.graphqlRequest()
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"data":{"posts":[]}}""", response.bodyAsText())
    }

    @Test
    fun `unexpected execution failure produces HTTP 500`() = testApplication {
        coEvery { executor.execute(any(), any()) } throws IllegalStateException("execution failed")
        application { installGraphQLRoute() }

        val response = client.graphqlRequest()
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"errors":[{"message":"execution failed"}]}""", response.bodyAsText())
    }

    @Test
    fun `anonymous admin requests remain forbidden`() = testApplication {
        application { installGraphQLRoute() }
        val response = client.graphqlRequest("admin")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `execution cancellation reaches the caller instead of producing HTTP 500`() = testApplication {
        val cancellation = CancellationException("request cancelled")
        coEvery { executor.execute(any(), any()) } throws cancellation
        var propagated: CancellationException? = null
        application { installGraphQLRoute { propagated = it } }

        val response = client.graphqlRequest()
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertSame(cancellation, propagated)
    }

    private suspend fun HttpClient.graphqlRequest(schema: String? = null) = post("/graphql") {
        contentType(ContentType.Application.Json)
        schema?.let { header("X-Schema", it) }
        setBody("""{"query":"{ posts { id } }"}""")
    }

    private fun Application.installGraphQLRoute(onCancellation: ((CancellationException) -> Unit)? = null) {
        install(ContentNegotiation) { jackson() }
        routing {
            post("/graphql") {
                try {
                    server.handleGraphQL(call)
                } catch (e: CancellationException) {
                    // Observe the cancellation at the caller boundary without cancelling the test host.
                    if (onCancellation == null) throw e
                    onCancellation(e)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
