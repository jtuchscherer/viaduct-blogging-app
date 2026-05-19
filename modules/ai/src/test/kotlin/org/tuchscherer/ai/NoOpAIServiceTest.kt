package org.tuchscherer.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NoOpAIServiceTest {

    private lateinit var service: NoOpAIService

    @BeforeEach
    fun setUp() {
        service = NoOpAIService()
    }

    @Test
    fun `rephrase prefixes content with tone name`() {
        assertThat(service.rephrase("Hello world", RephraseTone.PROFESSIONAL))
            .isEqualTo("[PROFESSIONAL] Hello world")
    }

    @Test
    fun `rephrase casual tone prefixes content with CASUAL`() {
        assertThat(service.rephrase("Hello world", RephraseTone.CASUAL))
            .isEqualTo("[CASUAL] Hello world")
    }

    @Test
    fun `rephrase concise tone prefixes content with CONCISE`() {
        assertThat(service.rephrase("Hello world", RephraseTone.CONCISE))
            .isEqualTo("[CONCISE] Hello world")
    }

    @Test
    fun `rephrase preserves original content including special characters`() {
        val content = "Buy milk & eggs — they're on sale!"
        assertThat(service.rephrase(content, RephraseTone.PROFESSIONAL))
            .isEqualTo("[PROFESSIONAL] $content")
    }

    @Test
    fun `suggestNextItem returns Item N+1 where N is list size`() {
        assertThat(service.suggestNextItem(emptyList())).isEqualTo("Item 1")
        assertThat(service.suggestNextItem(listOf("a"))).isEqualTo("Item 2")
        assertThat(service.suggestNextItem(listOf("a", "b", "c"))).isEqualTo("Item 4")
    }

    @Test
    fun `generateEmbedding returns FloatArray of length 384`() {
        val embedding = service.generateEmbedding("some text")
        assertThat(embedding).hasSize(384)
    }

    @Test
    fun `generateEmbedding returns deterministic values`() {
        val first = service.generateEmbedding("hello")
        val second = service.generateEmbedding("hello")
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `generateEmbedding first element is zero`() {
        val embedding = service.generateEmbedding("text")
        assertThat(embedding[0]).isEqualTo(0f / 384f)
    }

    @Test
    fun `generateEmbedding last element is 383 over 384`() {
        val embedding = service.generateEmbedding("text")
        assertThat(embedding[383]).isEqualTo(383f / 384f)
    }

    @Test
    fun `isReachable always returns true`() {
        assertThat(service.isReachable()).isTrue()
    }
}
