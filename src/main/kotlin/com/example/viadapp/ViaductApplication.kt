@file:Suppress("ForbiddenImport")

package com.example.viadapp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.example.config.allModules
import com.example.database.DatabaseFactory
import com.example.web.GraphQLServer
import org.koin.core.context.startKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Main application entry point.
 * Uses Koin for dependency injection.
 */
object Application : KoinComponent {
    private val databaseFactory: DatabaseFactory by inject()
    private val graphQLServer: GraphQLServer by inject()

    fun start() {
        // Initialize database
        databaseFactory.initialize()

        // Start the consolidated server (handles both GraphQL and Auth endpoints)
        graphQLServer.start()
        println("Server started on http://localhost:8080")
        println("GraphQL endpoint: POST http://localhost:8080/graphql")
        println("Auth endpoints:")
        println("  - POST http://localhost:8080/auth/register")
        println("  - POST http://localhost:8080/auth/login")
        println("  - GET  http://localhost:8080/auth/me")

        // Keep the application running
        println("\nServer is running. Press Ctrl+C to stop.")
        Thread.currentThread().join()
    }
}

fun main(argv: Array<String>) {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    rootLogger.level = Level.WARN

    // Initialize Koin dependency injection
    startKoin {
        modules(allModules)
    }

    // Start the application
    Application.start()
}
