package com.example.web

import com.example.auth.JwtService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
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
    val operationName: String? = null,
    val extensions: Map<String, Any?>? = null  // Apollo Client includes this field
)

object GraphQLServer {
    const val AUTHENTICATED_USER_KEY = "authenticatedUser"

    private val logger = LoggerFactory.getLogger(GraphQLServer::class.java)
    private val jwtService = JwtService()

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

            install(CORS) {
                allowHost("localhost:5173")
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowCredentials = true
            }

            routing {
                post("/graphql") {
                    try {
                        val graphqlRequest = call.receive<GraphQLRequest>()

                        // Extract JWT token from Authorization header
                        val authHeader = call.request.headers["Authorization"]
                        val token = authHeader?.removePrefix("Bearer ")?.trim()

                        // Get user from token if present (null if no token or invalid)
                        val user = token?.let { jwtService.getUserFromToken(it) }

                        // Create context map with authenticated user
                        val contextMap = mapOf<String, Any?>(
                            AUTHENTICATED_USER_KEY to user
                        )

                        // Execute GraphQL query using Viaduct 0.5.0 API
                        // Pass the context map through requestContext
                        val executionInput = ExecutionInput.create(
                            operationText = graphqlRequest.query,
                            variables = graphqlRequest.variables ?: emptyMap(),
                            requestContext = contextMap
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