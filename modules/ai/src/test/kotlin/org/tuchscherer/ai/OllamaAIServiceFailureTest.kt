package org.tuchscherer.ai

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.coroutines.cancellation.CancellationException

class OllamaAIServiceFailureTest {
    @ParameterizedTest(name = "{0} preserves cancellation")
    @MethodSource("operations")
    fun `cancellation propagates without being wrapped`(name: String, operation: (AIService) -> Any) {
        val cancellation = CancellationException("cancelled")
        val thrown = assertThrows(CancellationException::class.java) { operation(failingService(cancellation)) }
        assertSame(cancellation, thrown, name)
    }

    @ParameterizedTest(name = "{0} preserves interruption")
    @MethodSource("operations")
    fun `interruption propagates and restores the thread flag`(name: String, operation: (AIService) -> Any) {
        val interruption = InterruptedException("interrupted")
        try {
            val thrown = assertThrows(InterruptedException::class.java) { operation(failingService(interruption)) }
            assertSame(interruption, thrown, name)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted() // Keep the JUnit worker usable for subsequent tests.
        }
    }

    @ParameterizedTest(name = "{0} preserves failure cause")
    @MethodSource("operations")
    fun `model failures retain their original cause`(name: String, operation: (AIService) -> Any) {
        val failure = IllegalStateException("model unavailable")
        val thrown = assertThrows(AIServiceException::class.java) { operation(failingService(failure)) }
        assertSame(failure, thrown.cause, name)
    }

    @ParameterizedTest(name = "{0} preserves wrapped cancellation")
    @MethodSource("operations")
    fun `SDK-wrapped cancellation propagates`(name: String, operation: (AIService) -> Any) {
        val cancellation = CancellationException("cancelled")
        val wrapper = IllegalStateException("SDK failure", RuntimeException(cancellation))
        val thrown = assertThrows(CancellationException::class.java) { operation(failingService(wrapper)) }
        assertSame(cancellation, thrown, name)
    }

    @ParameterizedTest(name = "{0} preserves wrapped interruption")
    @MethodSource("operations")
    fun `SDK-wrapped interruption propagates and restores the thread flag`(
        name: String,
        operation: (AIService) -> Any,
    ) {
        val interruption = InterruptedException("interrupted")
        try {
            val wrapper = RuntimeException(interruption)
            val thrown = assertThrows(InterruptedException::class.java) { operation(failingService(wrapper)) }
            assertSame(interruption, thrown, name)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @ParameterizedTest(name = "{0} handles cyclic failure causes")
    @MethodSource("operations")
    fun `cyclic failure causes still produce an adapter error`(name: String, operation: (AIService) -> Any) {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)
        val thrown = assertThrows(AIServiceException::class.java) { operation(failingService(first)) }
        assertSame(first, thrown.cause, name)
    }

    private fun failingService(failure: Exception) = OllamaAIService(
        OllamaConfig(baseUrl = "http://localhost:1", chatModel = "test", embeddingModel = "test"),
        chatModelFactory = {
            object : ChatModel {
                override fun chat(message: String): String = throw failure
            }
        },
        embeddingModelFactory = { EmbeddingModel { throw failure } },
    )

    companion object {
        @JvmStatic
        fun operations(): List<Arguments> = listOf(
            Arguments.of("rephrase", { service: AIService -> service.rephrase("hello", RephraseTone.CASUAL) }),
            Arguments.of("suggestNextItem", { service: AIService -> service.suggestNextItem(listOf("first")) }),
            Arguments.of("generateEmbedding", { service: AIService -> service.generateEmbedding("hello") }),
        )
    }
}
