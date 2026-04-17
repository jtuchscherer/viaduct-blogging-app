package org.tuchscherer.database.repositories

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
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

        Assertions.assertNotNull(user)
        assertEquals("testuser", user.username)
        assertEquals("test@example.com", user.email)
        assertEquals("Test User", user.name)
        Assertions.assertNotNull(user.id.value)
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

        Assertions.assertNotNull(found)
        assertEquals(created.id.value, found!!.id.value)
        assertEquals("testuser", found.username)
    }

    @Test
    fun `findById returns null when user does not exist`() {
        val nonExistentId = UUID.randomUUID()
        val found = userRepository.findById(nonExistentId)

        Assertions.assertNull(found)
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

        Assertions.assertNotNull(found)
        assertEquals("testuser", found!!.username)
    }

    @Test
    fun `findByUsername returns null when user does not exist`() {
        val found = userRepository.findByUsername("nonexistent")

        Assertions.assertNull(found)
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

        Assertions.assertNotNull(found)
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
        Assertions.assertNull(userRepository.findById(user.id.value))
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

    @Test
    fun `findPage returns correct slice`() {
        for (i in 1..5) {
            userRepository.create(
                username = "pageuser$i",
                email = "pageuser$i@example.com",
                name = "Page User $i",
                passwordHash = "hash",
                salt = "salt"
            )
        }

        val page = userRepository.findPage(limit = 2, offset = 0)

        assertEquals(2, page.size)
    }

    @Test
    fun `findPage respects offset`() {
        for (i in 1..5) {
            userRepository.create(
                username = "offsetuser$i",
                email = "offsetuser$i@example.com",
                name = "Offset User $i",
                passwordHash = "hash",
                salt = "salt"
            )
        }

        val firstPage = userRepository.findPage(limit = 2, offset = 0)
        val secondPage = userRepository.findPage(limit = 2, offset = 2)

        assertEquals(2, secondPage.size)
        // Pages must not overlap
        val firstPageIds = firstPage.map { it.id.value }.toSet()
        val secondPageIds = secondPage.map { it.id.value }.toSet()
        assertTrue(firstPageIds.intersect(secondPageIds).isEmpty())
    }

    @Test
    fun `findPage beyond end returns empty list`() {
        userRepository.create(
            username = "singleuser",
            email = "single@example.com",
            name = "Single User",
            passwordHash = "hash",
            salt = "salt"
        )

        val page = userRepository.findPage(limit = 10, offset = 100)

        assertTrue(page.isEmpty())
    }

    @Test
    fun `update persists user field changes`() {
        val user = userRepository.create(
            username = "updateuser",
            email = "update@example.com",
            name = "Original Name",
            passwordHash = "hash",
            salt = "salt"
        )

        transaction { user.name = "Updated Name" }
        userRepository.update(user)

        val found = userRepository.findById(user.id.value)
        assertTrue(found != null)
        assertEquals("Updated Name", found!!.name)
    }

    @Test
    fun `count returns total number of users`() {
        userRepository.create(username = "user1", email = "u1@example.com", name = "U1", passwordHash = "h", salt = "s")
        userRepository.create(username = "user2", email = "u2@example.com", name = "U2", passwordHash = "h", salt = "s")

        val count = userRepository.count()

        assertEquals(2L, count)
    }

    @Test
    fun `count returns zero when no users`() {
        val count = userRepository.count()

        assertEquals(0L, count)
    }
}
