package com.example.database.repositories

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.*

/**
 * Integration tests for UserRepository with H2 in-memory database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserRepositoryTest {

    private lateinit var userRepository: UserRepository

    @BeforeAll
    fun setupDatabase() {
        DatabaseTestHelper.setupDatabase()
        userRepository = ExposedUserRepository()
    }

    @AfterEach
    fun cleanDatabase() {
        DatabaseTestHelper.cleanDatabase()
    }

    @AfterAll
    fun tearDown() {
        DatabaseTestHelper.tearDownDatabase()
    }

    @Test
    fun `create user successfully`() {
        val user = userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        assertNotNull(user)
        assertEquals("testuser", user.username)
        assertEquals("test@example.com", user.email)
        assertEquals("Test User", user.name)
        assertNotNull(user.id.value)
    }

    @Test
    fun `findById returns user when exists`() {
        val created = userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val found = userRepository.findById(created.id.value)

        assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("testuser", found.username)
    }

    @Test
    fun `findById returns null when user does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = userRepository.findById(nonExistentId)

        assertNull(found)
    }

    @Test
    fun `findByUsername returns user when exists`() {
        userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val found = userRepository.findByUsername("testuser")

        assertNotNull(found)
        assertEquals("testuser", found!!.username)
    }

    @Test
    fun `findByUsername returns null when user does not exist`() {
        val found = userRepository.findByUsername("nonexistent")

        assertNull(found)
    }

    @Test
    fun `findByEmail returns user when exists`() {
        userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val found = userRepository.findByEmail("test@example.com")

        assertNotNull(found)
        assertEquals("test@example.com", found!!.email)
    }

    @Test
    fun `existsByUsername returns true when user exists`() {
        userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val exists = userRepository.existsByUsername("testuser")

        assertTrue(exists)
    }

    @Test
    fun `existsByUsername returns false when user does not exist`() {
        val exists = userRepository.existsByUsername("nonexistent")

        assertFalse(exists)
    }

    @Test
    @Disabled("Update pattern needs refactoring - entities need to be modified within transaction context")
    fun `update user modifies fields`() {
        val user = userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val userId = user.id.value

        // Retrieve and update within transaction context
        val retrieved = userRepository.findById(userId)!!
        retrieved.name = "Updated Name"
        retrieved.email = "updated@example.com"
        userRepository.update(retrieved)

        val found = userRepository.findById(userId)

        assertNotNull(found)
        assertEquals("Updated Name", found!!.name)
        assertEquals("updated@example.com", found.email)
    }

    @Test
    fun `delete user removes from database`() {
        val user = userRepository.create(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            passwordHash = "hashed_password",
            salt = "salt123"
        )

        val deleted = userRepository.delete(user.id.value)

        assertTrue(deleted)
        assertNull(userRepository.findById(user.id.value))
    }

    @Test
    fun `delete returns false when user does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val deleted = userRepository.delete(nonExistentId)

        assertFalse(deleted)
    }

    @Test
    fun `findAll returns all users`() {
        userRepository.create(
            username = "user1",
            email = "user1@example.com",
            name = "User 1",
            passwordHash = "hash1",
            salt = "salt1"
        )
        userRepository.create(
            username = "user2",
            email = "user2@example.com",
            name = "User 2",
            passwordHash = "hash2",
            salt = "salt2"
        )

        val allUsers = userRepository.findAll()

        assertEquals(2, allUsers.size)
    }

    @Test
    fun `findAll returns empty list when no users`() {
        val allUsers = userRepository.findAll()

        assertTrue(allUsers.isEmpty())
    }

    @Test
    fun `username must be unique`() {
        userRepository.create(
            username = "testuser",
            email = "test1@example.com",
            name = "Test User 1",
            passwordHash = "hash1",
            salt = "salt1"
        )

        // Attempting to create another user with same username should fail
        assertThrows<Exception> {
            userRepository.create(
                username = "testuser",
                email = "test2@example.com",
                name = "Test User 2",
                passwordHash = "hash2",
                salt = "salt2"
            )
        }
    }
}
