@file:Suppress("ForbiddenImport")

package com.example.viadapp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.example.database.DatabaseConfig
import com.example.web.AuthServer
import org.slf4j.LoggerFactory

fun main(argv: Array<String>) {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    rootLogger.level = Level.WARN

    // Initialize database
    DatabaseConfig.init()

    // Start the auth server
    AuthServer.start()
    println("Auth server started on http://localhost:8081")

    // Start the GraphQL server
    com.example.web.GraphQLServer.start()
    println("GraphQL server started on http://localhost:8080")
    println("GraphQL endpoint: POST http://localhost:8080/graphql")

    // Keep the application running
    println("\nServers are running. Press Ctrl+C to stop.")
    Thread.currentThread().join()
}
