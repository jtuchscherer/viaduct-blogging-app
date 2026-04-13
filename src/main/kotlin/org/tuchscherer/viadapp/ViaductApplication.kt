package org.tuchscherer.viadapp

import org.tuchscherer.config.allModules
import org.tuchscherer.database.DatabaseFactory
import org.tuchscherer.web.GraphQLServer
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Main application entry point.
 * Uses Koin for dependency injection.
 */
object Application : KoinComponent {
    private val logger = LoggerFactory.getLogger(Application::class.java)
    private val databaseFactory: DatabaseFactory by inject()
    private val graphQLServer: GraphQLServer by inject()

    fun start() {
        databaseFactory.initialize()
        graphQLServer.start()

        logger.info("Server started on http://localhost:8080")
        logger.info("GraphiQL: GET http://localhost:8080/graphiql?path=/graphql")
        logger.info("GraphQL: POST http://localhost:8080/graphql")
        logger.info("Auth: POST /auth/register, POST /auth/login, GET /auth/me")
        logger.info("Server is running. Press Ctrl+C to stop.")

        Thread.currentThread().join()
    }
}

fun main(argv: Array<String>) {
    // Log level is controlled by logback.xml — no programmatic override needed
    startKoin {
        modules(allModules)
    }
    Application.start()
}
