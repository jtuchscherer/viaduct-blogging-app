package org.tuchscherer.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.JwtService
import org.tuchscherer.config.JwtConfig
import org.tuchscherer.config.ServerConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import viaduct.service.api.ExecutionInput
import viaduct.service.BasicViaductFactory
import viaduct.service.TenantRegistrationInfo

data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>? = null,
    val operationName: String? = null,
    val extensions: Map<String, Any?>? = null
)

/**
 * Ktor server hosting the GraphQL endpoint and delegating auth routes to AuthRoutes.
 */
class GraphQLServer(
    private val jwtService: JwtService,
    private val authService: AuthenticationService,
    private val jwtConfig: JwtConfig,
    private val serverConfig: ServerConfig
) {

    private val logger = LoggerFactory.getLogger(GraphQLServer::class.java)
    private val jwtAlgorithm by lazy { Algorithm.HMAC256(jwtConfig.secret) }

    private val viaduct = BasicViaductFactory.create(
        tenantRegistrationInfo = TenantRegistrationInfo(
            tenantPackagePrefix = "org.tuchscherer.viadapp",
            tenantCodeInjector = org.tuchscherer.config.KoinTenantCodeInjector()
        )
    )

    fun start() {
        embeddedServer(Netty, port = serverConfig.graphqlPort) {
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

            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(jwtAlgorithm)
                            .withIssuer(jwtConfig.issuer)
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.getClaim("username").asString() != null) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }

            routing {
                post("/graphql") {
                    try {
                        val graphqlRequest = call.receive<GraphQLRequest>()

                        val authHeader = call.request.headers["Authorization"]
                        val token = authHeader?.removePrefix("Bearer ")?.trim()
                        val user = token?.let { jwtService.getUserFromToken(it) }
                        val requestContext = user?.let { org.tuchscherer.auth.RequestContext(user = it) }

                        val executionInput = ExecutionInput.create(
                            operationText = graphqlRequest.query,
                            variables = graphqlRequest.variables ?: emptyMap(),
                            requestContext = requestContext
                        )

                        val result = viaduct.execute(executionInput)

                        val mapper = jacksonObjectMapper()
                        call.respondText(mapper.writeValueAsString(result.toSpecification()), ContentType.Application.Json)

                    } catch (e: Exception) {
                        logger.error("GraphQL execution error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("errors" to listOf(mapOf("message" to e.message)))
                        )
                    }
                }

                get("/graphiql") {
                    val resource = this::class.java.classLoader.getResource("graphiql/index.html")
                    if (resource != null) {
                        call.respondText(resource.readText(), ContentType.Text.Html)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "GraphiQL not found")
                    }
                }

                get("/health") {
                    call.respond(mapOf("status" to "ok"))
                }

                authRoutes(jwtService, authService)
            }
        }.start(wait = false)
    }
}