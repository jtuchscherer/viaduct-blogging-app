package org.tuchscherer.ai

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * Unit tests for [OllamaAIService.isReachable].
 *
 * Uses an embedded JDK [HttpServer] to simulate Ollama responses without
 * requiring a real Ollama installation. Each test binds to a random free port
 * so tests can run in parallel without port conflicts.
 */
class OllamaAIServiceTest {

    private lateinit var server: HttpServer
    private lateinit var service: OllamaAIService
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        // Bind to port 0 — OS assigns a free port
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()

        service = OllamaAIService(
            OllamaConfig(
                baseUrl = "http://localhost:$port",
                chatModel = "llama3.2",
                embeddingModel = "nomic-embed-text",
            )
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `isReachable returns true when api-tags responds with 200`() {
        server.createContext("/api/tags") { exchange ->
            val body = """{"models":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }

        assertThat(service.isReachable()).isTrue()
    }

    @Test
    fun `isReachable returns false when api-tags responds with 503`() {
        server.createContext("/api/tags") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.responseBody.close()
        }

        assertThat(service.isReachable()).isFalse()
    }

    @Test
    fun `isReachable returns false when nothing is listening on the port`() {
        // Stop the server so the port is no longer accepting connections
        server.stop(0)

        assertThat(service.isReachable()).isFalse()
    }

    @Test
    fun `isReachable returns false when server accepts connection but hangs`() {
        server.createContext("/api/tags") { exchange ->
            // Accept but never write a response — simulates a slow/hung Ollama
            Thread.sleep(5_000)
            exchange.sendResponseHeaders(200, -1)
            exchange.responseBody.close()
        }

        // isReachable has a 3s read timeout, so this should return false well before 5s
        val start = System.currentTimeMillis()
        assertThat(service.isReachable()).isFalse()
        assertThat(System.currentTimeMillis() - start).isLessThan(5_000)
    }
}
