package org.tuchscherer.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import org.tuchscherer.auth.AuthenticationService
import org.tuchscherer.auth.JwtService
import org.tuchscherer.config.JwtConfig
import org.tuchscherer.config.ServerConfig
import org.tuchscherer.database.DatabaseFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import viaduct.service.api.ExecutionInput
import viaduct.service.BasicViaductFactory
import viaduct.service.TenantRegistrationInfo
import java.util.UUID

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
    private val serverConfig: ServerConfig,
    private val meterRegistry: MeterRegistry,
    private val databaseFactory: DatabaseFactory
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
                allowHost(serverConfig.corsOrigin)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowCredentials = true
            }

            install(CallId) {
                generate { UUID.randomUUID().toString() }
                replyToHeader(HttpHeaders.XRequestId)
            }

            install(CallLogging) {
                mdc("requestId") { it.callId }
                filter { call -> call.request.path() != "/health" }
            }

            install(MicrometerMetrics) {
                registry = meterRegistry
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
                        val operationName = graphqlRequest.operationName ?: "anonymous"

                        val authHeader = call.request.headers["Authorization"]
                        val token = authHeader?.removePrefix("Bearer ")?.trim()
                        val user = token?.let { jwtService.getUserFromToken(it) }
                        val requestContext = user?.let { org.tuchscherer.auth.RequestContext(user = it) }

                        val executionInput = ExecutionInput.create(
                            operationText = graphqlRequest.query,
                            variables = graphqlRequest.variables ?: emptyMap(),
                            requestContext = requestContext
                        )

                        val startMs = System.currentTimeMillis()
                        val result = viaduct.execute(executionInput)
                        val durationMs = System.currentTimeMillis() - startMs

                        val hasErrors = result.toSpecification()["errors"] != null
                        if (hasErrors) {
                            logger.warn("GraphQL operation='{}' duration={}ms errors=true", operationName, durationMs)
                        } else {
                            logger.info("GraphQL operation='{}' duration={}ms", operationName, durationMs)
                        }

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
                    val dbUp = databaseFactory.healthCheck()
                    val version = System.getenv("APP_VERSION") ?: "unknown"
                    val status = if (dbUp) "UP" else "DOWN"
                    val response = mapOf(
                        "status" to status,
                        "db" to if (dbUp) "UP" else "DOWN",
                        "version" to version
                    )
                    if (dbUp) {
                        call.respond(HttpStatusCode.OK, response)
                    } else {
                        logger.warn("Health check failed: database is unreachable")
                        call.respond(HttpStatusCode.ServiceUnavailable, response)
                    }
                }

                get("/metrics") {
                    val prometheusRegistry = meterRegistry as? PrometheusMeterRegistry
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Prometheus registry not configured")
                    call.respondText(prometheusRegistry.scrape(), ContentType.Text.Plain)
                }

                authRoutes(jwtService, authService)
            }
        }.start(wait = false)
    }
}
