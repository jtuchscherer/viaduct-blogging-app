package org.tuchscherer.resolvers

import org.tuchscherer.viadapp.resolvers.PostValidation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/**
 * Pin the contract of PostValidation so any future change to title or
 * content limits requires touching this file. Three resolvers
 * (CreatePost, UpdatePost, AdminUpdatePost) all delegate here, and
 * before centralisation AdminUpdatePost was missing the isNotBlank
 * check on content — these tests would have caught that drift.
 */
class PostValidationTest {

    // ── validateTitle ────────────────────────────────────────────────────────

    @Test
    fun `validateTitle accepts a typical title`() {
        assertDoesNotThrow { PostValidation.validateTitle("My first post") }
    }

    @Test
    fun `validateTitle accepts title at the max length`() {
        val maxTitle = "a".repeat(PostValidation.MAX_TITLE_LENGTH)
        assertDoesNotThrow { PostValidation.validateTitle(maxTitle) }
    }

    @Test
    fun `validateTitle rejects empty string`() {
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateTitle("") }
        assertEquals("Title cannot be blank", ex.message)
    }

    @Test
    fun `validateTitle rejects whitespace-only string`() {
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateTitle("   \t\n  ") }
        assertEquals("Title cannot be blank", ex.message)
    }

    @Test
    fun `validateTitle rejects title one over the max length with formatted message`() {
        val tooLong = "a".repeat(PostValidation.MAX_TITLE_LENGTH + 1)
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateTitle(tooLong) }
        // Pin the human-readable formatting (thousands separator) — it shows
        // up in error messages the e2e suite asserts against.
        assertEquals("Title cannot exceed 500 characters", ex.message)
    }

    // ── validateContent ──────────────────────────────────────────────────────

    @Test
    fun `validateContent accepts a typical body`() {
        assertDoesNotThrow {
            PostValidation.validateContent("<p>Hello world</p>")
        }
    }

    @Test
    fun `validateContent accepts content at the max length`() {
        val maxContent = "a".repeat(PostValidation.MAX_CONTENT_LENGTH)
        assertDoesNotThrow { PostValidation.validateContent(maxContent) }
    }

    @Test
    fun `validateContent rejects empty string`() {
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateContent("") }
        assertEquals("Content cannot be blank", ex.message)
    }

    @Test
    fun `validateContent rejects whitespace-only string`() {
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateContent("   \n  ") }
        assertEquals("Content cannot be blank", ex.message)
    }

    @Test
    fun `validateContent rejects content one over the max length with formatted message`() {
        val tooLong = "a".repeat(PostValidation.MAX_CONTENT_LENGTH + 1)
        val ex = assertThrows<IllegalArgumentException> { PostValidation.validateContent(tooLong) }
        // Pin the comma-separated formatting — frontend e2e validation matches
        // against /100,000/ in the error message.
        assertEquals("Content cannot exceed 100,000 characters", ex.message)
    }
}
