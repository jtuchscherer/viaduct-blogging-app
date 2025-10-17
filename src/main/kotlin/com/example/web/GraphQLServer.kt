package com.example.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import viaduct.service.BasicViaductFactory
import viaduct.service.TenantRegistrationInfo
import viaduct.service.api.ExecutionInput


data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>? = null,
    val operationName: String? = null
)

object GraphQLServer {
    private val logger = LoggerFactory.getLogger(GraphQLServer::class.java)

    // Create Viaduct engine instance
    private val viaduct = BasicViaductFactory.create(
        tenantRegistrationInfo = TenantRegistrationInfo(
            tenantPackagePrefix = "com.example.viadapp"
        )
    )

    fun start() {
        embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                jackson()
            }

            routing {
                post("/graphql") {
                    try {
                        val graphqlRequest = call.receive<GraphQLRequest>()

                        // Execute GraphQL query using Viaduct 0.5.0 API
                        val executionInput = ExecutionInput.create(
                            operationText = graphqlRequest.query,
                            variables = graphqlRequest.variables ?: emptyMap()
                        )

                        val result = viaduct.execute(executionInput)


                        // Return result
                        val mapper = jacksonObjectMapper()
                        val response = mapper.writeValueAsString(result.toSpecification())
                        call.respondText(response, ContentType.Application.Json)

                    } catch (e: Exception) {
                        logger.error("GraphQL execution error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("errors" to listOf(mapOf("message" to e.message)))
                        )
                    }
                }

                // Health check endpoint
                get("/health") {
                    call.respond(mapOf("status" to "ok"))
                }
            }
        }.start(wait = false)
    }
}