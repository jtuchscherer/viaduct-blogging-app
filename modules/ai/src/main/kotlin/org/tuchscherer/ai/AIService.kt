package org.tuchscherer.ai

/**
 * Names of the chat and embedding models currently in use.
 * Returned by [AIService.modelConfig] so callers don't need access to [OllamaConfig] directly.
 */
data class AIModelConfig(val chatModel: String, val embeddingModel: String)

/**
 * Abstraction over AI capabilities used by the application.
 * Use [OllamaAIService] in production and [NoOpAIService] in tests.
 */
interface AIService {
    fun rephrase(content: String, tone: RephraseTone): String
    fun suggestNextItem(existingItems: List<String>): String
    fun generateEmbedding(text: String): FloatArray
    fun isReachable(): Boolean
    fun modelConfig(): AIModelConfig
}

enum class RephraseTone { PROFESSIONAL, CASUAL, CONCISE }

/**
 * Thrown when the AI backend returns an error or is unreachable during an operation.
 */
class AIServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
