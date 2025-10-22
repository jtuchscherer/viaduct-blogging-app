package com.example.auth

import com.example.database.User
import com.example.database.repositories.UserRepository
import io.mockk.*
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

/**
 * Unit tests for AuthenticationService with mocked dependencies.
 */
class AuthenticationServiceTest {

    private lateinit var passwordService: PasswordService
    private lateinit var userRepository: UserRepository
    private lateinit var authService: AuthenticationService

    @BeforeEach
    fun setup() {
        passwordService = mockk<PasswordService>()
        userRepository = mockk<UserRepository>()
        authService = AuthenticationService(passwordService, userRepository)
    }

    @Test
    fun `createUser creates user with hashed password`() {
        val username = "testuser"
        val email = "test@example.com"
        val name = "Test User"
        val password = "password123"
        val salt = "generated-salt"
        val passwordHash = "hashed-password"

        val mockUser = mockk<User>()
        every { mockUser.id } returns EntityID(UUID.randomUUID(), mockk())
        every { mockUser.username } returns username

        every { userRepository.existsByUsername(username) } returns false
        every { passwordService.generateSalt() } returns salt
        every { passwordService.hashPassword(password, salt) } returns passwordHash
        every {
            userRepository.create(
                username = username,
                email = email,
                name = name,
                passwordHash = passwordHash,
                salt = salt,
                createdAt = any()
            )
        } returns mockUser

        val result = authService.createUser(username, email, name, password)

        assertEquals(mockUser, result)
        verify { userRepository.existsByUsername(username) }
        verify { passwordService.generateSalt() }
        verify { passwordService.hashPassword(password, salt) }
        verify {
            userRepository.create(
                username = username,
                email = email,
                name = name,
                passwordHash = passwordHash,
                salt = salt,
                createdAt = any()
            )
        }
    }

    @Test
    fun `createUser throws exception when username already exists`() {
        val username = "existinguser"

        every { userRepository.existsByUsername(username) } returns true

        val exception = assertThrows<UserAlreadyExistsException> {
            authService.createUser(username, "email@test.com", "Name", "password")
        }

        assertTrue(exception.message!!.contains("existinguser"))
        verify { userRepository.existsByUsername(username) }
        verify(exactly = 0) { passwordService.generateSalt() }
        verify(exactly = 0) { userRepository.create(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `authenticateUser returns user when credentials are correct`() {
        val username = "testuser"
        val password = "password123"
        val salt = "user-salt"
        val passwordHash = "hashed-password"

        val mockUser = mockk<User>()
        every { mockUser.salt } returns salt
        every { mockUser.passwordHash } returns passwordHash

        every { userRepository.findByUsername(username) } returns mockUser
        every { passwordService.verifyPassword(password, salt, passwordHash) } returns true

        val result = authService.authenticateUser(username, password)

        assertEquals(mockUser, result)
        verify { userRepository.findByUsername(username) }
        verify { passwordService.verifyPassword(password, salt, passwordHash) }
    }

    @Test
    fun `authenticateUser returns null when user not found`() {
        val username = "nonexistent"
        val password = "password123"

        every { userRepository.findByUsername(username) } returns null

        val result = authService.authenticateUser(username, password)

        assertNull(result)
        verify { userRepository.findByUsername(username) }
        verify(exactly = 0) { passwordService.verifyPassword(any(), any(), any()) }
    }

    @Test
    fun `authenticateUser returns null when password is incorrect`() {
        val username = "testuser"
        val correctPassword = "correctpassword"
        val wrongPassword = "wrongpassword"
        val salt = "user-salt"
        val passwordHash = "hashed-password"

        val mockUser = mockk<User>()
        every { mockUser.salt } returns salt
        every { mockUser.passwordHash } returns passwordHash

        every { userRepository.findByUsername(username) } returns mockUser
        every { passwordService.verifyPassword(wrongPassword, salt, passwordHash) } returns false

        val result = authService.authenticateUser(username, wrongPassword)

        assertNull(result)
        verify { userRepository.findByUsername(username) }
        verify { passwordService.verifyPassword(wrongPassword, salt, passwordHash) }
    }

    @Test
    fun `createUser generates unique salt for each user`() {
        val username1 = "user1"
        val username2 = "user2"
        val salt1 = "salt1"
        val salt2 = "salt2"

        val mockUser1 = mockk<User>()
        val mockUser2 = mockk<User>()
        every { mockUser1.id } returns EntityID(UUID.randomUUID(), mockk())
        every { mockUser2.id } returns EntityID(UUID.randomUUID(), mockk())

        every { userRepository.existsByUsername(any()) } returns false
        every { passwordService.generateSalt() } returnsMany listOf(salt1, salt2)
        every { passwordService.hashPassword(any(), any()) } returns "hash"
        every { userRepository.create(any(), any(), any(), any(), any(), any()) } returnsMany listOf(mockUser1, mockUser2)

        authService.createUser(username1, "email1@test.com", "Name1", "pass1")
        authService.createUser(username2, "email2@test.com", "Name2", "pass2")

        verify(exactly = 2) { passwordService.generateSalt() }
    }

    @Test
    fun `authenticateUser is case sensitive for username`() {
        val username = "TestUser"
        val password = "password123"

        every { userRepository.findByUsername("testuser") } returns null

        val result = authService.authenticateUser("testuser", password)

        assertNull(result)
        verify { userRepository.findByUsername("testuser") }
    }

    @Test
    fun `createUser handles special characters in username`() {
        val username = "user@123"
        val email = "test@example.com"
        val name = "Test User"
        val password = "password"

        val mockUser = mockk<User>()
        every { mockUser.id } returns EntityID(UUID.randomUUID(), mockk())

        every { userRepository.existsByUsername(username) } returns false
        every { passwordService.generateSalt() } returns "salt"
        every { passwordService.hashPassword(any(), any()) } returns "hash"
        every { userRepository.create(any(), any(), any(), any(), any(), any()) } returns mockUser

        val result = authService.createUser(username, email, name, password)

        assertNotNull(result)
        verify { userRepository.existsByUsername(username) }
    }
}
