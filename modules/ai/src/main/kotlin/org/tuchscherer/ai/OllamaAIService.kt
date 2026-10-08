package org.tuchscherer.ai

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.ai.tracy.core.TracingManager
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * Production [AIService] backed by a locally running Ollama instance via LangChain4j.
 * Model calls have Tracy spans so latency, errors and model metadata remain observable.
 */
class OllamaAIService(
    private val config: OllamaConfig,
    chatModelFactory: () -> ChatModel = {
        OllamaChatModel.builder().baseUrl(config.baseUrl).modelName(config.chatModel).build()
    },
    embeddingModelFactory: () -> EmbeddingModel = {
        OllamaEmbeddingModel.builder().baseUrl(config.baseUrl).modelName(config.embeddingModel).build()
    },
) : AIService {
    private val logger = LoggerFactory.getLogger(OllamaAIService::class.java)
    private val chatModel by lazy(chatModelFactory)
    private val embeddingModel by lazy(embeddingModelFactory)

    override fun rephrase(content: String, tone: RephraseTone): String {
        val toneInstruction = when (tone) {
            RephraseTone.PROFESSIONAL -> "formal and professional"
            RephraseTone.CASUAL -> "casual and friendly"
            RephraseTone.CONCISE -> "concise and to the point"
        }
        val prompt = "Rephrase the following text in a $toneInstruction tone. " +
            "Return only the rephrased text:\n\n$content"
        return tracedModelCall(
            "ai.rephrase", config.chatModel, "Failed to rephrase content with tone $tone",
            attributes = { setAttribute("ai.tone", tone.name) },
        ) { chatModel.chat(prompt) }
    }

    override fun suggestNextItem(existingItems: List<String>): String {
        val itemList = existingItems.joinToString("\n") { "- $it" }
        val prompt = "Given this list of items:\n$itemList\n\n" +
            "Suggest one additional item that would logically follow. " +
            "Return only the suggested item text, nothing else."
        return tracedModelCall(
            "ai.suggestNextItem", config.chatModel, "Failed to suggest next item",
            attributes = { setAttribute("ai.existingItemCount", existingItems.size.toLong()) },
        ) { chatModel.chat(prompt) }
    }

    override fun generateEmbedding(text: String): FloatArray = tracedModelCall(
        "ai.generateEmbedding", config.embeddingModel, "Failed to generate embedding",
    ) { embeddingModel.embed(text).content().vector() }

    // Translate failures at the third-party adapter boundary, preserving their cause and tracing.
    // SDK/transport failures are not one stable exception hierarchy. Control flow must propagate.
    @Suppress("TooGenericExceptionCaught")
    private fun <T> tracedModelCall(
        operation: String,
        model: String,
        failureMessage: String,
        attributes: Span.() -> Unit = {},
        action: () -> T,
    ): T {
        val span = TracingManager.tracer.spanBuilder(operation)
            .setAttribute("ai.model", model)
            .startSpan()
        val scope = span.makeCurrent()
        return try {
            span.attributes()
            action().also { span.setStatus(StatusCode.OK) }
        } catch (e: Exception) {
            rethrowControlFlow(e)
            logger.error(failureMessage, e)
            span.setStatus(StatusCode.ERROR, e.message ?: failureMessage)
            span.recordException(e)
            throw AIServiceException(failureMessage, e)
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun rethrowControlFlow(failure: Throwable) {
        // LangChain4j's JDK HTTP client wraps InterruptedException in RuntimeException.
        // Walk causes as well as the outer exception; avoid looping on cyclic cause chains.
        var cause: Throwable? = failure
        val seen = mutableSetOf<Throwable>()
        while (cause != null && seen.add(cause)) {
            when (cause) {
                is CancellationException -> throw cause
                is InterruptedException -> {
                    Thread.currentThread().interrupt()
                    throw cause
                }
            }
            cause = cause.cause
        }
    }

    override fun modelConfig(): AIModelConfig = AIModelConfig(
        chatModel = config.chatModel,
        embeddingModel = config.embeddingModel,
    )

    /** Probe /api/tags without loading a model, closing the connection even on failure. */
    override fun isReachable(): Boolean {
        return try {
            val conn = URI("${config.baseUrl}/api/tags").toURL().openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = REACHABILITY_TIMEOUT_MS
                conn.readTimeout = REACHABILITY_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            logger.debug("Ollama reachability check failed", e)
            false
        }
    }
}

private const val REACHABILITY_TIMEOUT_MS = 3_000
