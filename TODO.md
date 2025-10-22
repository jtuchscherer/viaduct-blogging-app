# TODO: Unit Test Suite Implementation with Koin DI

## Executive Summary

This document outlines the plan to add a comprehensive unit test suite to the viaduct-blogs application. The current codebase has no unit tests and has several testability issues that need to be addressed. We will introduce **Koin** as a dependency injection framework to make the code testable and maintainable.

**Status**: ✅ Evaluation Complete - Ready for Implementation

---

## Current State Analysis

### Test Infrastructure
- ❌ No `src/test` directory exists
- ✅ JUnit Jupiter dependencies already configured (but unused)
- ✅ E2E tests exist (28 tests in `e2e-test.sh`)
- ❌ No unit tests for any components
- ❌ No mocking framework configured

### Codebase Structure
```
src/main/kotlin/com/example/
├── auth/
│   ├── AuthContext.kt
│   ├── JwtService.kt
│   └── PasswordService.kt (includes AuthenticationService)
├── database/
│   ├── DatabaseConfig.kt
│   ├── Models.kt
│   └── Tables.kt
├── resolvers/
│   ├── CommentResolvers.kt
│   ├── CommentFieldResolvers.kt
│   ├── LikeResolvers.kt
│   ├── LikeFieldResolvers.kt
│   ├── LikeObjectFieldResolvers.kt
│   ├── PostMutationResolvers.kt
│   ├── PostFieldResolvers.kt
│   └── PostQueryResolvers.kt
├── web/
│   ├── AuthServer.kt
│   └── GraphQLServer.kt
└── viadapp/
    └── ViaductApplication.kt
```

---

## Testability Issues Identified

### 1. Hard Dependencies & Tight Coupling

**Problem**: Services are instantiated directly inside classes, making them impossible to mock or replace.

**Example from `GraphQLServer.kt:29-30`**:
```kotlin
object GraphQLServer {
    private val jwtService = JwtService()  // ❌ Hard-coded instance
    private val viaduct = BasicViaductFactory.create(...)  // ❌ Cannot mock
}
```

**Example from `AuthServer.kt:49-50`**:
```kotlin
object AuthServer {
    private val authService = AuthenticationService()  // ❌ Hard-coded
    private val jwtSecret = "your-secret-key"  // ❌ Hard-coded config
}
```

**Impact**:
- Cannot test these classes in isolation
- Cannot inject test doubles or mocks
- Cannot test different configurations

---

### 2. Singleton Pattern (object keyword)

**Problem**: Using Kotlin's `object` keyword creates true singletons that cannot be instantiated with different configurations.

**Affected Classes**:
- `GraphQLServer` (src/main/kotlin/com/example/web/GraphQLServer.kt)
- `AuthServer` (src/main/kotlin/com/example/web/AuthServer.kt)
- `DatabaseConfig` (src/main/kotlin/com/example/database/DatabaseConfig.kt)

**Impact**:
- Cannot create multiple instances for testing
- Cannot mock these objects
- Shared mutable state between tests
- Cannot test with different configurations

---

### 3. No Service Layer Abstraction

**Problem**: Business logic is mixed directly in HTTP handlers and resolvers without a service layer.

**Example from `AuthServer.kt:91-107`**:
```kotlin
post("/auth/register") {
    val request = call.receive<RegisterRequest>()
    val user = transaction {
        // Check if username already exists
        val existingUser = User.find { Users.username eq request.username }.firstOrNull()
        if (existingUser != null) {
            throw RuntimeException("Username already exists")
        }
        authService.createUser(request.username, request.email, request.name, request.password)
    }
    // ... token generation and response ...
}
```

**Issues**:
- Validation logic in HTTP layer
- Database queries in HTTP handlers
- Cannot test business logic without starting server
- Difficult to reuse logic

---

### 4. Direct Database Access Everywhere

**Problem**: All code uses Exposed's `transaction {}` blocks directly. No repository pattern.

**Examples**:
- `JwtService.kt:32-34` - Direct database query in JWT service
- `PasswordService.kt:44-53` - AuthenticationService queries database directly
- All resolvers use `transaction {}` directly

**Example from `PostMutationResolvers.kt:19-26`**:
```kotlin
return transaction {
    val post = DatabasePost.new {
        title = input.title
        content = input.content
        authorId = authenticatedUser.id
        createdAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }
    // ... build response ...
}
```

**Impact**:
- Cannot test without a real database
- Tight coupling to Exposed ORM
- Cannot mock database access
- Slow tests (database I/O)
- Cannot test error scenarios easily

---

### 5. Configuration Management

**Problem**: Configuration values are hardcoded throughout the codebase.

**Examples**:
- `JwtService.kt:11` - `private val jwtSecret = "your-secret-key"`
- `AuthServer.kt:50` - `private val jwtSecret = "your-secret-key"`
- `DatabaseConfig.kt:10` - Hardcoded database path

**Impact**:
- Cannot use different configs for test/dev/prod
- Security risk (secrets in code)
- Cannot test with test-specific configuration

---

## Koin Evaluation

### Why Koin?

**✅ Pros:**
1. **Kotlin-first**: DSL designed specifically for Kotlin
2. **Lightweight**: No code generation, no annotation processing
3. **Easy to learn**: Simple, readable DSL
4. **Ktor integration**: Official Koin-Ktor plugin available
5. **Testing support**: Easy to override modules for tests
6. **Constructor injection**: Clean, testable code
7. **Scoping**: Control singleton vs factory instances
8. **Active community**: Well-maintained, good documentation

**❌ Cons:**
1. Runtime resolution (vs compile-time like Dagger)
2. Less type-safety than Dagger
3. Slightly more boilerplate than manual DI

### Koin vs Alternatives

| Feature | Koin | Dagger | Hilt | Manual DI |
|---------|------|--------|------|-----------|
| Setup complexity | Low | High | Medium | Low |
| Kotlin-friendliness | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Compile-time safety | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| Test support | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| Boilerplate | Low | High | Medium | Medium |
| Learning curve | Easy | Steep | Medium | Easy |

**Recommendation**: ✅ **Koin** is the best fit for our Kotlin server-side application.

---

## Implementation Plan

### Phase 1: Setup Foundation (Low Risk, High Value)

**Goal**: Set up test infrastructure and dependencies without touching production code.

#### Tasks:
1. ✅ Add Koin dependencies to `build.gradle.kts`
   - `io.insert-koin:koin-core`
   - `io.insert-koin:koin-ktor`
   - `io.insert-koin:koin-test`
   - `io.insert-koin:koin-test-junit5`

2. ✅ Add MockK for mocking
   - `io.mockk:mockk`

3. ✅ Add H2 for in-memory test database
   - `com.h2database:h2`

4. ✅ Create test directory structure
   ```
   src/test/kotlin/com/example/
   ├── auth/
   ├── database/
   ├── resolvers/
   └── web/
   ```

5. ✅ Create configuration management
   - `src/main/kotlin/com/example/config/AppConfig.kt`
   - Support for test/dev/prod environments

**Success Criteria**: Dependencies added, test structure created, can run `./gradlew test`

---

### Phase 2: Extract Repository Layer (Medium Risk)

**Goal**: Create a repository pattern to abstract database access.

#### Tasks:

6. ✅ Create repository interfaces
   ```kotlin
   // src/main/kotlin/com/example/database/repositories/UserRepository.kt
   interface UserRepository {
       fun findById(id: UUID): User?
       fun findByUsername(username: String): User?
       fun create(username: String, email: String, name: String, passwordHash: String, salt: String): User
       fun exists(username: String): Boolean
   }

   // Implementation using Exposed
   class ExposedUserRepository : UserRepository {
       override fun findById(id: UUID): User? = transaction {
           User.findById(id)
       }
       // ... other methods ...
   }
   ```

7. ✅ Create repositories for:
   - `UserRepository` (interface + ExposedUserRepository)
   - `PostRepository` (interface + ExposedPostRepository)
   - `CommentRepository` (interface + ExposedCommentRepository)
   - `LikeRepository` (interface + ExposedLikeRepository)

8. ✅ Write repository tests with in-memory H2 database
   - Test CRUD operations
   - Test edge cases (not found, duplicates, etc.)

**Success Criteria**: All repositories tested, interface-based design allows mocking

---

### Phase 3: Refactor Services (Medium Risk)

**Goal**: Update existing services to use dependency injection and repositories.

#### Tasks:

9. ✅ Refactor `PasswordService`
   - Already testable (pure functions)
   - Add unit tests

10. ✅ Refactor `JwtService`
    ```kotlin
    // Before:
    class JwtService {
        private val jwtSecret = "your-secret-key"

        fun getUserFromToken(token: String): User? {
            val payload = verifyToken(token) ?: return null
            return transaction {
                User.find { Users.username eq payload.username }.firstOrNull()
            }
        }
    }

    // After:
    class JwtService(
        private val config: JwtConfig,
        private val userRepository: UserRepository
    ) {
        fun getUserFromToken(token: String): User? {
            val payload = verifyToken(token) ?: return null
            return userRepository.findByUsername(payload.username)
        }
    }
    ```

11. ✅ Refactor `AuthenticationService`
    ```kotlin
    class AuthenticationService(
        private val passwordService: PasswordService,
        private val userRepository: UserRepository
    ) {
        fun createUser(username: String, email: String, name: String, password: String): User {
            if (userRepository.exists(username)) {
                throw UserAlreadyExistsException("Username already exists")
            }
            val salt = passwordService.generateSalt()
            val passwordHash = passwordService.hashPassword(password, salt)
            return userRepository.create(username, email, name, passwordHash, salt)
        }

        fun authenticateUser(username: String, password: String): User? {
            val user = userRepository.findByUsername(username) ?: return null
            return if (passwordService.verifyPassword(password, user.salt, user.passwordHash)) {
                user
            } else null
        }
    }
    ```

12. ✅ Write service tests with mocked repositories
    - Test business logic in isolation
    - Test error conditions
    - Test edge cases

**Success Criteria**: Services use constructor injection, fully unit tested

---

### Phase 4: Create Koin Modules (Medium Risk)

**Goal**: Set up Koin dependency injection configuration.

#### Tasks:

13. ✅ Create Koin modules
    ```kotlin
    // src/main/kotlin/com/example/config/KoinModules.kt

    val configModule = module {
        single { AppConfig.load() }
        single { JwtConfig(getProperty("jwt.secret"), getProperty("jwt.issuer")) }
        single { DatabaseConfig(getProperty("db.url")) }
    }

    val databaseModule = module {
        single { DatabaseFactory(get<DatabaseConfig>()) }
        single<UserRepository> { ExposedUserRepository() }
        single<PostRepository> { ExposedPostRepository() }
        single<CommentRepository> { ExposedCommentRepository() }
        single<LikeRepository> { ExposedLikeRepository() }
    }

    val serviceModule = module {
        single { PasswordService() }
        single { JwtService(get(), get()) }
        single { AuthenticationService(get(), get()) }
    }

    val serverModule = module {
        single { GraphQLServer(get(), get()) }
        single { AuthServer(get(), get(), get()) }
    }
    ```

14. ✅ Initialize Koin in `ViaductApplication.kt`
    ```kotlin
    fun main() {
        startKoin {
            modules(configModule, databaseModule, serviceModule, serverModule)
        }

        val graphQLServer = KoinJavaComponent.get<GraphQLServer>(GraphQLServer::class.java)
        val authServer = KoinJavaComponent.get<AuthServer>(AuthServer::class.java)

        graphQLServer.start()
        authServer.start()
    }
    ```

**Success Criteria**: Application starts with Koin, dependencies injected correctly

---

### Phase 5: Convert Singletons to Classes (Medium-High Risk)

**Goal**: Remove `object` keyword and make classes injectable.

#### Tasks:

15. ✅ Convert `DatabaseConfig` from object to class
    ```kotlin
    // Before:
    object DatabaseConfig {
        fun init() {
            Database.connect("jdbc:sqlite:...")
            transaction { SchemaUtils.create(...) }
        }
    }

    // After:
    class DatabaseFactory(private val config: DatabaseConfig) {
        fun initialize() {
            Database.connect(config.url, driver = config.driver)
            transaction { SchemaUtils.create(Users, Posts, Comments, Likes) }
        }

        fun getDatabase(): Database = Database.connect(config.url, driver = config.driver)
    }
    ```

16. ✅ Convert `GraphQLServer` from object to class
    ```kotlin
    class GraphQLServer(
        private val jwtService: JwtService,
        private val config: ServerConfig
    ) {
        private val viaduct = BasicViaductFactory.create(
            tenantRegistrationInfo = TenantRegistrationInfo(
                tenantPackagePrefix = config.viaductPackagePrefix
            )
        )

        fun start() {
            embeddedServer(Netty, port = config.graphqlPort) {
                // ... configuration ...
            }.start(wait = false)
        }
    }
    ```

17. ✅ Convert `AuthServer` from object to class
    ```kotlin
    class AuthServer(
        private val authService: AuthenticationService,
        private val jwtService: JwtService,
        private val config: ServerConfig
    ) {
        fun start() {
            embeddedServer(Netty, port = config.authPort) {
                // ... configuration ...
            }.start(wait = false)
        }
    }
    ```

**Success Criteria**: No `object` declarations (except constants), all servers injectable

---

### Phase 6: Refactor Resolvers (Medium Risk)

**Goal**: Update resolvers to use repositories instead of direct database access.

#### Tasks:

18. ✅ Refactor Post resolvers
    - Update `PostMutationResolvers.kt` to use `PostRepository`
    - Update `PostQueryResolvers.kt` to use `PostRepository`
    - Update `PostFieldResolvers.kt` to use `UserRepository`

19. ✅ Refactor Comment resolvers
    - Update `CommentResolvers.kt` to use `CommentRepository`
    - Update `CommentFieldResolvers.kt` to use `UserRepository`

20. ✅ Refactor Like resolvers
    - Update `LikeResolvers.kt` to use `LikeRepository`
    - Update `LikeFieldResolvers.kt` to use `LikeRepository`

**Note**: This requires understanding how to inject dependencies into Viaduct resolvers. May need to pass repositories via request context or use Viaduct's DI integration.

**Success Criteria**: Resolvers use repositories, no direct `transaction {}` blocks

---

### Phase 7: Write Comprehensive Unit Tests (High Value)

**Goal**: Achieve >80% code coverage with unit tests.

#### Tasks:

21. ✅ Write unit tests for PasswordService
    ```kotlin
    class PasswordServiceTest {
        private val passwordService = PasswordService()

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
        fun `verifyPassword returns true for correct password`() {
            val password = "test123"
            val salt = passwordService.generateSalt()
            val hash = passwordService.hashPassword(password, salt)
            assertTrue(passwordService.verifyPassword(password, salt, hash))
        }

        @Test
        fun `verifyPassword returns false for incorrect password`() {
            val password = "test123"
            val wrongPassword = "wrong"
            val salt = passwordService.generateSalt()
            val hash = passwordService.hashPassword(password, salt)
            assertFalse(passwordService.verifyPassword(wrongPassword, salt, hash))
        }
    }
    ```

22. ✅ Write unit tests for JwtService (with mocked UserRepository)
    ```kotlin
    class JwtServiceTest {
        private val userRepository = mockk<UserRepository>()
        private val config = JwtConfig(secret = "test-secret", issuer = "test-issuer")
        private val jwtService = JwtService(config, userRepository)

        @Test
        fun `verifyToken returns payload for valid token`() {
            val token = jwtService.generateToken("testuser", "user-id-123")
            val payload = jwtService.verifyToken(token)

            assertNotNull(payload)
            assertEquals("testuser", payload?.username)
            assertEquals("user-id-123", payload?.userId)
        }

        @Test
        fun `verifyToken returns null for invalid token`() {
            val payload = jwtService.verifyToken("invalid.token.here")
            assertNull(payload)
        }

        @Test
        fun `getUserFromToken returns user when token is valid and user exists`() {
            val mockUser = mockk<User>()
            every { userRepository.findByUsername("testuser") } returns mockUser

            val token = jwtService.generateToken("testuser", "user-id-123")
            val user = jwtService.getUserFromToken(token)

            assertEquals(mockUser, user)
            verify { userRepository.findByUsername("testuser") }
        }

        @Test
        fun `getUserFromToken returns null when user not found`() {
            every { userRepository.findByUsername("testuser") } returns null

            val token = jwtService.generateToken("testuser", "user-id-123")
            val user = jwtService.getUserFromToken(token)

            assertNull(user)
        }
    }
    ```

23. ✅ Write unit tests for AuthenticationService
    - Test user creation
    - Test duplicate username detection
    - Test authentication success/failure
    - Test password verification

24. ✅ Write unit tests for repositories (integration tests with H2)
    - Test all CRUD operations
    - Test queries
    - Test constraints (unique, foreign keys)

25. ✅ Write unit tests for resolvers (with mocked repositories)
    - Test all mutations
    - Test all queries
    - Test authorization logic
    - Test error cases

**Success Criteria**: >80% code coverage, all business logic tested

---

### Phase 8: Integration Tests (High Value)

**Goal**: Test complete workflows with real dependencies.

#### Tasks:

26. ✅ Write integration tests for authentication flow
    - Register user → Login → Get user info
    - Test with real database (H2 in-memory)
    - Test JWT token generation and validation

27. ✅ Write integration tests for blog features
    - Create post → Update post → Delete post
    - Create comment → Delete comment
    - Like post → Unlike post
    - Test authorization (can only edit own posts)

28. ✅ Keep existing e2e tests as regression suite
    - Ensure all 28 e2e tests still pass
    - E2E tests cover the full stack

**Success Criteria**: Integration tests pass, e2e tests pass, full stack tested

---

## Testing Strategy

### Test Pyramid

```
      /\
     /  \      E2E Tests (28 tests via e2e-test.sh)
    /____\     ↑ Existing, keep as regression suite
   /      \
  /        \   Integration Tests (10-15 tests)
 /__________\  ↑ Test with real DB, test workflows
/            \
/   Unit      \ Unit Tests (50+ tests)
/    Tests     \↑ Test individual components
```

### Test Types

1. **Unit Tests** (Fast, Isolated)
   - Services: Test business logic with mocked dependencies
   - Repositories: Test with in-memory H2 database
   - Utilities: Test pure functions (PasswordService)
   - Goal: 50+ tests, <1 second total runtime

2. **Integration Tests** (Medium speed)
   - Test multiple components together
   - Use real database (H2 in-memory)
   - Test complete workflows
   - Goal: 10-15 tests, <5 seconds total runtime

3. **E2E Tests** (Slow, Comprehensive)
   - Keep existing 28 tests in `e2e-test.sh`
   - Test full stack with real database
   - Run less frequently (pre-commit, CI/CD)
   - Goal: Maintain current 28 tests

### Test Configuration

```kotlin
// src/test/kotlin/com/example/TestConfig.kt

fun testKoinModule() = module {
    single { JwtConfig(secret = "test-secret", issuer = "test") }
    single { DatabaseConfig(url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1") }
    single<UserRepository> { ExposedUserRepository() }
    // ... other test-specific configurations ...
}
```

---

## Success Criteria

### Phase 1-2 Complete
- ✅ Can run `./gradlew test`
- ✅ Test directory structure created
- ✅ Repository layer exists and tested
- ✅ 10+ repository tests passing

### Phase 3-5 Complete
- ✅ All services use dependency injection
- ✅ No hardcoded dependencies
- ✅ Application starts with Koin
- ✅ All e2e tests still pass (no regressions)
- ✅ 30+ unit tests passing

### Phase 6-7 Complete
- ✅ Resolvers use repositories
- ✅ No direct `transaction {}` in resolvers
- ✅ 50+ unit tests passing
- ✅ >80% code coverage

### Phase 8 Complete (Final)
- ✅ 10-15 integration tests passing
- ✅ All 28 e2e tests still passing
- ✅ 60+ total unit/integration tests
- ✅ Test suite runs in <10 seconds
- ✅ CI/CD ready

---

## Risk Mitigation

### Risks & Mitigations

1. **Breaking existing functionality**
   - Mitigation: Run e2e tests after each phase
   - Keep e2e tests as regression suite
   - Incremental refactoring

2. **Koin configuration errors**
   - Mitigation: Start with simple modules
   - Test Koin configuration independently
   - Use Koin's check functionality

3. **Viaduct resolver DI integration**
   - Mitigation: Research Viaduct DI support first
   - May need to pass repositories via context
   - Fallback: Manual injection in resolvers

4. **Performance impact**
   - Mitigation: Benchmark before/after
   - Koin has minimal overhead
   - Repository pattern shouldn't affect performance

5. **Database transactions in repositories**
   - Mitigation: Test transaction boundaries
   - Document transaction handling
   - Consider UnitOfWork pattern if needed

---

## Implementation Order (Recommended)

1. **Start here**: Phase 1 (Setup) - Zero risk
2. **Then**: Phase 2 (Repositories) - Learn pattern, get quick wins
3. **Then**: Phase 3 (Services) - Build on repository pattern
4. **Then**: Phase 4 (Koin modules) - Wire everything together
5. **Then**: Phase 5 (Singletons) - Big refactor, test thoroughly
6. **Then**: Phase 6 (Resolvers) - May require research
7. **Then**: Phase 7 (Unit tests) - Write tests as you go
8. **Finally**: Phase 8 (Integration tests) - Validate everything works

**Estimated Timeline**: 2-3 days for full implementation

---

## Files to Create

### Configuration
- `src/main/kotlin/com/example/config/AppConfig.kt`
- `src/main/kotlin/com/example/config/JwtConfig.kt`
- `src/main/kotlin/com/example/config/DatabaseConfig.kt`
- `src/main/kotlin/com/example/config/ServerConfig.kt`
- `src/main/kotlin/com/example/config/KoinModules.kt`

### Repositories
- `src/main/kotlin/com/example/database/repositories/UserRepository.kt`
- `src/main/kotlin/com/example/database/repositories/PostRepository.kt`
- `src/main/kotlin/com/example/database/repositories/CommentRepository.kt`
- `src/main/kotlin/com/example/database/repositories/LikeRepository.kt`

### Tests
- `src/test/kotlin/com/example/TestConfig.kt`
- `src/test/kotlin/com/example/auth/PasswordServiceTest.kt`
- `src/test/kotlin/com/example/auth/JwtServiceTest.kt`
- `src/test/kotlin/com/example/auth/AuthenticationServiceTest.kt`
- `src/test/kotlin/com/example/database/repositories/UserRepositoryTest.kt`
- `src/test/kotlin/com/example/database/repositories/PostRepositoryTest.kt`
- `src/test/kotlin/com/example/database/repositories/CommentRepositoryTest.kt`
- `src/test/kotlin/com/example/database/repositories/LikeRepositoryTest.kt`
- `src/test/kotlin/com/example/resolvers/PostResolversTest.kt`
- `src/test/kotlin/com/example/resolvers/CommentResolversTest.kt`
- `src/test/kotlin/com/example/resolvers/LikeResolversTest.kt`
- `src/test/kotlin/com/example/integration/AuthFlowIntegrationTest.kt`
- `src/test/kotlin/com/example/integration/BlogFeatureIntegrationTest.kt`

---

## Dependencies to Add

```kotlin
// build.gradle.kts

dependencies {
    // Existing dependencies...

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-ktor:3.5.0")

    // Testing
    testImplementation("io.insert-koin:koin-test:3.5.0")
    testImplementation("io.insert-koin:koin-test-junit5:3.5.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.assertj:assertj-core:3.24.2") // Optional: better assertions

    // Already have:
    // testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    // testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    // testImplementation("org.junit.platform:junit-platform-launcher:1.9.2")
    // testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
}
```

---

## Next Steps

**Ready to proceed?** Start with Phase 1: Setup Foundation

**Command to begin:**
```bash
# Review this plan
cat TODO.md

# Start implementation
# First task: Update build.gradle.kts with new dependencies
```

**Questions before starting?**
- Should we proceed phase by phase?
- Any concerns about Koin?
- Want to adjust the plan?

---

**Document Status**: ✅ Ready for Implementation
**Last Updated**: 2025-10-21
**Author**: Claude Code (with human review)
