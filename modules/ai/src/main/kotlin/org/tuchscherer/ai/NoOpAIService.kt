package org.tuchscherer.ai

/**
 * Deterministic stub implementation of [AIService] for use in tests and environments
 * where Ollama is not available. All methods return predictable, fixed values.
 */
class NoOpAIService : AIService {

    override fun rephrase(content: String, tone: RephraseTone): String =
        "[${tone.name}] $content"

    override fun suggestNextItem(existingItems: List<String>): String =
        "Item ${existingItems.size + 1}"

    override fun generateEmbedding(text: String): FloatArray =
        FloatArray(384) { it.toFloat() / 384f }

    override fun isReachable(): Boolean = true

    override fun modelConfig(): AIModelConfig = AIModelConfig(
        chatModel = "llama3.2",
        embeddingModel = "nomic-embed-text",
    )
}
