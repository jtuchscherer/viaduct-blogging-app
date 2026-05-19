package org.tuchscherer.ai

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Production [AIService] backed by a locally running Ollama instance via LangChain4j.
 *
 * Each method wraps its LangChain4j call in a try/catch and rethrows failures as
 * [AIServiceException] so callers receive a typed, domain-level error rather than a
 * raw LangChain4j exception.
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

    /** Lightweight probe model used only for [isReachable] checks — short timeout, reused across calls. */
    private val probeModel: OllamaChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl(config.baseUrl)
            .modelName(config.chatModel)
            .timeout(Duration.ofSeconds(5))
            .build()
    }

    override fun rephrase(content: String, tone: RephraseTone): String {
        val toneInstruction = when (tone) {
            RephraseTone.PROFESSIONAL -> "formal and professional"
            RephraseTone.CASUAL -> "casual and friendly"
            RephraseTone.CONCISE -> "concise and to the point"
        }
        val prompt = "Rephrase the following text in a $toneInstruction tone. Return only the rephrased text:\n\n$content"
        return try {
            chatModel.generate(prompt)
        } catch (e: Exception) {
            logger.error("rephrase failed for tone={}", tone, e)
            throw AIServiceException("Failed to rephrase content with tone $tone", e)
        }
    }

    override fun suggestNextItem(existingItems: List<String>): String {
        val itemList = existingItems.joinToString("\n") { "- $it" }
        val prompt = "Given this list of items:\n$itemList\n\nSuggest one additional item that would logically follow. Return only the suggested item text, nothing else."
        return try {
            chatModel.generate(prompt)
        } catch (e: Exception) {
            logger.error("suggestNextItem failed for {} existing items", existingItems.size, e)
            throw AIServiceException("Failed to suggest next item", e)
        }
    }

    override fun generateEmbedding(text: String): FloatArray {
        return try {
            val response = embeddingModel.embed(text)
            response.content().vector()
        } catch (e: Exception) {
            logger.error("generateEmbedding failed", e)
            throw AIServiceException("Failed to generate embedding", e)
        }
    }

    override fun isReachable(): Boolean {
        return try {
            probeModel.generate("ping")
            true
        } catch (e: Exception) {
            logger.debug("Ollama reachability check failed: {}", e.message)
            false
        }
    }
}
