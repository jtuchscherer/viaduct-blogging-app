package org.tuchscherer.ai

import com.sun.net.httpserver.HttpServer
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.ai.tracy.core.TracingManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

/**
 * Tests for [OllamaAIService].
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
        TracingManager.isTracingEnabled = false
    }

    // -------------------------------------------------------------------------
    // isReachable
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Tracy instrumentation
    // -------------------------------------------------------------------------

    @Nested
    inner class TracingTests {

        private lateinit var spanExporter: InMemorySpanExporter

        @BeforeEach
        fun enableTracing() {
            spanExporter = InMemorySpanExporter.create()
            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build()
            TracingManager.setSdk(
                OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build()
            )
            TracingManager.isTracingEnabled = true
        }

        @Test
        fun `rephrase emits a span with operation name and tone attribute`() {
            server.createContext("/api/chat") { exchange ->
                val body = """{"model":"llama3.2","message":{"role":"assistant","content":"Rephrased."},"done":true}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            service.rephrase("hello", RephraseTone.PROFESSIONAL)

            val spans = spanExporter.finishedSpanItems
            assertThat(spans).hasSize(1)
            assertThat(spans[0].name).isEqualTo("ai.rephrase")
            assertThat(spans[0].attributes.asMap().entries.map { it.key.key })
                .contains("ai.tone", "ai.model")
            assertThat(spans[0].status.statusCode).isEqualTo(StatusCode.OK)
        }

        @Test
        fun `rephrase emits an ERROR span when Ollama returns an error`() {
            server.createContext("/api/chat") { exchange ->
                exchange.sendResponseHeaders(500, -1)
                exchange.responseBody.close()
            }

            assertThatThrownBy { service.rephrase("hello", RephraseTone.CASUAL) }
                .isInstanceOf(AIServiceException::class.java)

            val spans = spanExporter.finishedSpanItems
            assertThat(spans).hasSize(1)
            assertThat(spans[0].name).isEqualTo("ai.rephrase")
            assertThat(spans[0].status.statusCode).isEqualTo(StatusCode.ERROR)
        }

        @Test
        fun `suggestNextItem emits a span with item count attribute`() {
            server.createContext("/api/chat") { exchange ->
                val body = """{"model":"llama3.2","message":{"role":"assistant","content":"Suggested item"},"done":true}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            service.suggestNextItem(listOf("Buy milk", "Buy eggs", "Buy bread"))

            val spans = spanExporter.finishedSpanItems
            assertThat(spans).hasSize(1)
            assertThat(spans[0].name).isEqualTo("ai.suggestNextItem")
            assertThat(spans[0].attributes.asMap().entries.map { it.key.key })
                .contains("ai.existingItemCount", "ai.model")
            assertThat(spans[0].status.statusCode).isEqualTo(StatusCode.OK)
        }

        @Test
        fun `generateEmbedding emits a span with the embedding model attribute`() {
            server.createContext("/api/embed") { exchange ->
                val body = """{"model":"nomic-embed-text","embeddings":[[0.1,0.2,0.3]]}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            service.generateEmbedding("some text")

            val spans = spanExporter.finishedSpanItems
            assertThat(spans).hasSize(1)
            assertThat(spans[0].name).isEqualTo("ai.generateEmbedding")
            assertThat(spans[0].attributes.asMap().entries.map { it.key.key })
                .contains("ai.model")
            assertThat(spans[0].status.statusCode).isEqualTo(StatusCode.OK)
        }
    }
}
