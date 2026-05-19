package org.tuchscherer.ai

data class OllamaConfig(
    val baseUrl: String,
    val chatModel: String,
    val embeddingModel: String,
) {
    companion object {
        fun load(): OllamaConfig = OllamaConfig(
            baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434",
            chatModel = System.getenv("OLLAMA_CHAT_MODEL") ?: "llama3.2",
            embeddingModel = System.getenv("OLLAMA_EMBEDDING_MODEL") ?: "nomic-embed-text",
        )
    }
}
