package org.tuchscherer.ai

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.ai.tracy.core.TracingManager
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI

/**
 * Production [AIService] backed by a locally running Ollama instance via LangChain4j.
 *
 * Each method wraps its LangChain4j call in a Tracy span so latency, errors, and
 * model metadata are observable in any configured OpenTelemetry backend.
 */
class OllamaAIService(private val config: OllamaConfig) : AIService {

    private val logger = LoggerFactory.getLogger(OllamaAIService::class.java)

    private val chatModel: OllamaChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl(config.baseUrl)
            .modelName(config.chatModel)
            .build()
    }

    private val embeddingModel: OllamaEmbeddingModel by lazy {
        OllamaEmbeddingModel.builder()
            .baseUrl(config.baseUrl)
            .modelName(config.embeddingModel)
            .build()
    }

    override fun rephrase(content: String, tone: RephraseTone): String {
        val span = TracingManager.tracer.spanBuilder("ai.rephrase")
            .setAttribute("ai.model", config.chatModel)
            .setAttribute("ai.tone", tone.name)
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            val toneInstruction = when (tone) {
                RephraseTone.PROFESSIONAL -> "formal and professional"
                RephraseTone.CASUAL -> "casual and friendly"
                RephraseTone.CONCISE -> "concise and to the point"
            }
            val prompt = "Rephrase the following text in a $toneInstruction tone. Return only the rephrased text:\n\n$content"
            val result = chatModel.generate(prompt)
            span.setStatus(StatusCode.OK)
            result
        } catch (e: Exception) {
            logger.error("rephrase failed for tone={}", tone, e)
            span.setStatus(StatusCode.ERROR, e.message ?: "rephrase failed")
            span.recordException(e)
            throw AIServiceException("Failed to rephrase content with tone $tone", e)
        } finally {
            scope.close()
            span.end()
        }
    }

    override fun suggestNextItem(existingItems: List<String>): String {
        val span = TracingManager.tracer.spanBuilder("ai.suggestNextItem")
            .setAttribute("ai.model", config.chatModel)
            .setAttribute("ai.existingItemCount", existingItems.size.toLong())
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            val itemList = existingItems.joinToString("\n") { "- $it" }
            val prompt = "Given this list of items:\n$itemList\n\nSuggest one additional item that would logically follow. Return only the suggested item text, nothing else."
            val result = chatModel.generate(prompt)
            span.setStatus(StatusCode.OK)
            result
        } catch (e: Exception) {
            logger.error("suggestNextItem failed for {} existing items", existingItems.size, e)
            span.setStatus(StatusCode.ERROR, e.message ?: "suggestNextItem failed")
            span.recordException(e)
            throw AIServiceException("Failed to suggest next item", e)
        } finally {
            scope.close()
            span.end()
        }
    }

    override fun generateEmbedding(text: String): FloatArray {
        val span = TracingManager.tracer.spanBuilder("ai.generateEmbedding")
            .setAttribute("ai.model", config.embeddingModel)
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            val response = embeddingModel.embed(text)
            val result = response.content().vector()
            span.setStatus(StatusCode.OK)
            result
        } catch (e: Exception) {
            logger.error("generateEmbedding failed", e)
            span.setStatus(StatusCode.ERROR, e.message ?: "generateEmbedding failed")
            span.recordException(e)
            throw AIServiceException("Failed to generate embedding", e)
        } finally {
            scope.close()
            span.end()
        }
    }

    override fun modelConfig(): AIModelConfig = AIModelConfig(
        chatModel = config.chatModel,
        embeddingModel = config.embeddingModel,
    )

    /**
     * Checks Ollama reachability via a lightweight GET /api/tags request.
     * This avoids loading any model into memory, so it responds instantly
     * even when the chat model hasn't been used yet (cold start).
     */
    override fun isReachable(): Boolean {
        return try {
            val url = URI("${config.baseUrl}/api/tags").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3_000
            conn.readTimeout = 3_000
            conn.requestMethod = "GET"
            val status = conn.responseCode
            conn.disconnect()
            status in 200..299
        } catch (e: Exception) {
            logger.debug("Ollama reachability check failed: {}", e.message)
            false
        }
    }
}
