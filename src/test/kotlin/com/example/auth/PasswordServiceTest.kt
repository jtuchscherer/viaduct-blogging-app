package com.example.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for PasswordService.
 * These test pure functions without any external dependencies.
 */
class PasswordServiceTest {

    private val passwordService = PasswordService()

    @Test
    fun `generateSalt creates non-empty salt`() {
        val salt = passwordService.generateSalt()

        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())
        assertEquals(32, salt.length) // 16 bytes * 2 hex chars per byte
    }

    @Test
    fun `generateSalt creates unique salts`() {
        val salt1 = passwordService.generateSalt()
        val salt2 = passwordService.generateSalt()

        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `hashPassword with same password and salt produces same hash`() {
        val password = "test123"
        val salt = passwordService.generateSalt()

        val hash1 = passwordService.hashPassword(password, salt)
        val hash2 = passwordService.hashPassword(password, salt)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashPassword with different passwords produces different hashes`() {
        val salt = passwordService.generateSalt()

        val hash1 = passwordService.hashPassword("password1", salt)
        val hash2 = passwordService.hashPassword("password2", salt)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashPassword with different salts produces different hashes`() {
        val password = "test123"
        val salt1 = passwordService.generateSalt()
        val salt2 = passwordService.generateSalt()

        val hash1 = passwordService.hashPassword(password, salt1)
        val hash2 = passwordService.hashPassword(password, salt2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashPassword produces 64 character hex string`() {
        val password = "test123"
        val salt = passwordService.generateSalt()

        val hash = passwordService.hashPassword(password, salt)

        assertEquals(64, hash.length) // SHA-256 = 32 bytes * 2 hex chars per byte
        assertTrue(hash.matches(Regex("^[0-9a-f]+$"))) // Only hex characters
    }

    @Test
    fun `verifyPassword returns true for correct password`() {
        val password = "test123"
        val salt = passwordService.generateSalt()
        val hash = passwordService.hashPassword(password, salt)

        val result = passwordService.verifyPassword(password, salt, hash)

        assertTrue(result)
    }

    @Test
    fun `verifyPassword returns false for incorrect password`() {
        val correctPassword = "test123"
        val wrongPassword = "wrong456"
        val salt = passwordService.generateSalt()
        val hash = passwordService.hashPassword(correctPassword, salt)

        val result = passwordService.verifyPassword(wrongPassword, salt, hash)

        assertFalse(result)
    }

    @Test
    fun `verifyPassword returns false for incorrect salt`() {
        val password = "test123"
        val correctSalt = passwordService.generateSalt()
        val wrongSalt = passwordService.generateSalt()
        val hash = passwordService.hashPassword(password, correctSalt)

        val result = passwordService.verifyPassword(password, wrongSalt, hash)

        assertFalse(result)
    }

    @Test
    fun `verifyPassword is case sensitive`() {
        val password = "Test123"
        val salt = passwordService.generateSalt()
        val hash = passwordService.hashPassword(password, salt)

        val resultCorrectCase = passwordService.verifyPassword("Test123", salt, hash)
        val resultWrongCase = passwordService.verifyPassword("test123", salt, hash)

        assertTrue(resultCorrectCase)
        assertFalse(resultWrongCase)
    }

    @Test
    fun `hashPassword handles empty password`() {
        val emptyPassword = ""
        val salt = passwordService.generateSalt()

        val hash = passwordService.hashPassword(emptyPassword, salt)

        assertNotNull(hash)
        assertEquals(64, hash.length)
        assertTrue(passwordService.verifyPassword(emptyPassword, salt, hash))
    }

    @Test
    fun `hashPassword handles special characters`() {
        val specialPassword = "p@ssw0rd!#$%^&*()"
        val salt = passwordService.generateSalt()

        val hash = passwordService.hashPassword(specialPassword, salt)

        assertNotNull(hash)
        assertTrue(passwordService.verifyPassword(specialPassword, salt, hash))
    }

    @Test
    fun `hashPassword handles unicode characters`() {
        val unicodePassword = "пароль密码🔐"
        val salt = passwordService.generateSalt()

        val hash = passwordService.hashPassword(unicodePassword, salt)

        assertNotNull(hash)
        assertTrue(passwordService.verifyPassword(unicodePassword, salt, hash))
    }
}
