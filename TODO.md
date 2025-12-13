# TODO: Unit Test Suite Implementation with Koin DI

## Executive Summary

This document outlines the plan to add a comprehensive unit test suite to the viaduct-blogs application. The current codebase has no unit tests and has several testability issues that need to be addressed. We will introduce **Koin** as a dependency injection framework to make the code testable and maintainable.

**Status**: 🚀 In Progress - Phase 7 Complete + Transaction Refactoring Complete, Phase 8 Next

## Current Progress (Last Updated: 2025-12-12)

### ✅ Completed Phases

- **Phase 1: Setup Foundation** - Test infrastructure created
- **Phase 2: Repository Pattern** - All repositories implemented with 52 tests
- **Phase 3: Service Layer Refactoring** - Services refactored with 35 tests
- **Phase 4: Koin Dependency Injection** - DI fully integrated with 14 tests
- **Phase 5: Convert Singletons to Classes** - All objects converted to classes
- **Phase 5.5: Consolidate Servers** - Auth and GraphQL servers merged into single server on port 8080
- **Phase 6: Refactor Resolvers** - Resolvers refactored to use Koin DI with repositories
- **Phase 7: Resolver Unit Tests** - Comprehensive unit tests for all resolvers (133 tests)
- **Phase 7.5: Transaction Refactoring** - All transaction handling moved from resolvers to repositories
- **Phase 7.6: Frontend Bug Fixes** - Fixed header width CSS issues

### 📊 Test Statistics

- **Total Unit Tests**: 133 (all passing)
- **E2E Tests**: 28 (all passing)
- **Total Tests**: 161

### 🎯 Next Steps

- **Phase 8**: Add integration tests for workflows
- **Phase 9**: Add browser-based E2E tests with Playwright
- **Phase 10**: Create Dockerfile for containerized deployment

---

## Recent Work (2025-12-12)

### Phase 7 Enhancement: Comprehensive Resolver Unit Tests

**Completed:**
- Broke out monolithic resolver test files into individual test files per resolver
- Rewrote smoke tests into comprehensive unit tests with full coverage
- Each resolver now has dedicated test file with multiple test cases

**Files Created:**
- `CreatePostResolverTest.kt` - Tests for CreatePostMutationResolver (3 tests)
- `UpdatePostResolverTest.kt` - Tests for UpdatePostMutationResolver (4 tests)
- `DeletePostResolverTest.kt` - Tests for DeletePostMutationResolver (4 tests)
- `PostsResolverTest.kt` - Tests for PostsResolver query (2 tests)
- `PostResolverTest.kt` - Tests for single post query (2 tests)
- `MyPostsResolverTest.kt` - Tests for user's posts query (3 tests)
- `LikePostResolverTest.kt` - Tests for like mutation (4 tests)
- `UnlikePostResolverTest.kt` - Tests for unlike mutation (4 tests)
- `CreateCommentResolverTest.kt` - Tests for comment creation (3 tests)
- `DeleteCommentResolverTest.kt` - Tests for comment deletion (4 tests)
- `PostCommentsResolverTest.kt` - Tests for post comments query (2 tests)

**Files Deleted:**
- `PostResolversTest.kt` - Split into individual files
- `LikeResolversTest.kt` - Split into individual files
- `CommentResolversTest.kt` - Split into individual files

**Test Coverage:**
- All tests use MockK for mocking repositories
- Tests cover authentication, authorization, success paths, and error cases
- Tests follow Viaduct's `DefaultAbstractResolverTestBase` pattern
- Total: 133 unit tests (all passing)

### Phase 7.5: Transaction Refactoring

**Goal:** Move all database transaction handling from resolvers to repositories for better testability.

**Completed:**
- Added repository methods for entity navigation:
  - `CommentRepository`: `getAuthorForComment()`, `getPostForComment()`, `findByPostId(UUID)`
  - `PostRepository`: `getAuthorForPost()`
  - `LikeRepository`: `getUserForLike()`, `getPostForLike()`, `findByPostId(UUID)`, `countByPostId(UUID)`, `existsByPostAndUser(UUID, UUID)`

**Resolvers Refactored:**
- `CommentFieldResolvers.kt` - CommentAuthorResolver, CommentPostResolver now use repositories
- `PostFieldResolvers.kt` - PostAuthorResolver, PostCommentsFieldResolver now use repositories
- `LikeFieldResolvers.kt` - PostLikesResolver, PostLikeCountResolver, PostIsLikedByMeResolver now use repositories
- `LikeObjectFieldResolvers.kt` - LikeUserResolver, LikePostResolver now use repositories

**Result:**
- Zero `transaction {}` blocks in resolver code (only in comments)
- All transaction handling now in repository layer
- Resolvers fully testable without database
- All 133 unit tests passing
- All 28 e2e tests passing

### Phase 7.6: Frontend CSS Fixes

**Issue:** Header not taking full width of browser window, Register button cut off at right edge.

**Root Cause:**
- `body` had `display: flex` and `place-items: center` causing app to be centered rather than full width
- `.container` class had `width: 100%` with padding but no `box-sizing: border-box`, causing overflow

**Fixes:**
1. `frontend/src/index.css`:
   - Removed `display: flex` and `place-items: center` from body
   - Added `#root { width: 100%; min-height: 100vh; }`

2. `frontend/src/App.css`:
   - Added `box-sizing: border-box` to `.container` class

**Result:**
- Header now spans full window width
- All navigation elements properly contained with correct padding
- No content overflow or cutoff

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

### Phase 1: Setup Foundation ✅ COMPLETE (Low Risk, High Value)

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

### Phase 2: Extract Repository Layer ✅ COMPLETE (Medium Risk)

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

### Phase 3: Refactor Services ✅ COMPLETE (Medium Risk)

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

### Phase 4: Create Koin Modules ✅ COMPLETE (Medium Risk)

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

### Phase 5: Convert Singletons to Classes ✅ COMPLETE (Medium-High Risk)

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

### Phase 5.5: Consolidate Servers ✅ COMPLETE (Low-Medium Risk)

**Goal**: Merge AuthServer routing into GraphQLServer to run on a single port.

**Motivation**: Currently running two separate servers (GraphQL on 8080, Auth on 8081) is unnecessary complexity. Both servers can run on the same port with different routes, simplifying deployment and potentially eliminating CORS issues.

#### Tasks:

- ✅ Move auth routes (`/auth/register`, `/auth/login`, `/auth/me`) from AuthServer into GraphQLServer
- ✅ Consolidate to single port (8080) with routes:
  - `POST /graphql` - GraphQL endpoint
  - `POST /auth/register` - User registration
  - `POST /auth/login` - User login
  - `GET /auth/me` - Get current user (JWT protected)
  - `GET /health` - Health check
- ✅ Evaluate CORS configuration:
  - Frontend stays on different port (5173), kept CORS for single origin
  - Current CORS allows `localhost:5173` - maintained for frontend
- ✅ Update GraphQLServer to accept AuthenticationService and JwtService
- ✅ Remove AuthServer class entirely
- ✅ Update ViaductApplication to only start GraphQLServer
- ✅ Update e2e-test.sh to use port 8080 for auth endpoints (change AUTH_URL variable)
- ✅ Update frontend files to use port 8080:
  - `frontend/src/pages/RegisterPage.tsx` (line 21)
  - `frontend/src/pages/LoginPage.tsx` (line 19)
- ✅ Update start.sh to only start one server

**Benefits**:
- Simpler deployment (one port instead of two)
- No CORS complexity for same-origin requests
- Easier local development
- Reduced process management complexity

**Success Criteria**: ✅ Single server on port 8080, all auth and GraphQL routes working, all 28 e2e tests passing

---

### Phase 6: Refactor Resolvers ✅ COMPLETE (Medium Risk)

**Goal**: Update resolvers to use repositories instead of direct database access using Koin dependency injection.

#### Tasks:

18. ✅ Create KoinTenantCodeInjector
    - Implemented custom `TenantCodeInjector` that uses Koin for DI
    - Configured Viaduct to use Koin for resolver instantiation

19. ✅ Refactor Post resolvers
    - Updated `PostMutationResolvers.kt` to use `PostRepository` via constructor injection
    - Updated `PostQueryResolvers.kt` to use `PostRepository` via constructor injection
    - Registered all Post resolvers and field resolvers with Koin

20. ✅ Refactor Comment resolvers
    - Updated `CommentResolvers.kt` to use `CommentRepository` via constructor injection
    - Registered all Comment resolvers and field resolvers with Koin

21. ✅ Refactor Like resolvers
    - Updated `LikeResolvers.kt` to use `LikeRepository` via constructor injection
    - Registered all Like resolvers and field resolvers with Koin

**Implementation**: Used Viaduct's `TenantCodeInjector` SPI with Koin for proper dependency injection. Resolvers now receive repositories via constructor parameters, eliminating the need to pass dependencies through request context.

**Files Created**:
- `src/main/kotlin/com/example/config/KoinTenantCodeInjector.kt` - Custom injector for Viaduct-Koin integration

**Files Modified**:
- `src/main/kotlin/com/example/web/GraphQLServer.kt` - Configure Viaduct with Koin injector
- `src/main/kotlin/com/example/config/KoinModules.kt` - Register all resolvers with Koin
- All resolver files - Added constructor parameters for repository injection

**Success Criteria**: ✅ Resolvers use repositories with proper DI, all 28 e2e tests passing, all 100 unit tests passing

---

### Phase 7: Write Comprehensive Unit Tests ✅ COMPLETE (High Value)

**Goal**: Achieve >80% code coverage with unit tests.

**Status**: ✅ Complete - All resolver smoke tests implemented

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

### Phase 8: Integration Tests ⏳ READY TO START (High Value)

**Goal**: Test complete workflows with real dependencies to validate end-to-end functionality at the service layer.

**Status**: All prerequisites complete. Unit tests (133) and e2e tests (28) passing. Ready to add integration tests.

#### What are Integration Tests?

Integration tests sit between unit tests and e2e tests:
- **Unit tests**: Test individual components with mocked dependencies (133 tests ✅)
- **Integration tests**: Test multiple components working together with real database (H2 in-memory)
- **E2E tests**: Test full stack through HTTP/GraphQL APIs (28 tests ✅)

#### Tasks:

26. ⏳ Write integration tests for authentication flow
    - Test complete user registration → login → token validation flow
    - Use real services (AuthenticationService, JwtService) with real repositories
    - Test with H2 in-memory database
    - Verify password hashing, JWT generation, and user retrieval
    - **Example test scenarios:**
      - Register new user → verify user created in DB → login → verify JWT token valid
      - Register duplicate username → verify error thrown
      - Login with wrong password → verify authentication fails
      - Get user from valid JWT token → verify correct user returned

27. ⏳ Write integration tests for blog features
    - Test complete workflows with real services and repositories
    - Use H2 in-memory database
    - **Example test scenarios:**
      - Create post → verify in DB → update post → verify changes → delete post → verify removed
      - User1 creates post → User2 attempts to update → verify authorization failure
      - Create post → add comments → like post → verify all relationships in DB
      - Delete post → verify cascading deletes (comments, likes)
    - **Suggested test file**: `BlogWorkflowIntegrationTest.kt`

28. ✅ Keep existing e2e tests as regression suite
    - All 28 e2e tests passing ✅
    - E2E tests cover the full stack through HTTP/GraphQL
    - Run e2e tests before each commit

#### Implementation Guidelines:

**Test Structure:**
```kotlin
@ExtendWith(KoinTestExtension::class)
class AuthFlowIntegrationTest : KoinTest {
    private lateinit var authService: AuthenticationService
    private lateinit var jwtService: JwtService
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        // Setup H2 in-memory database
        DatabaseTestHelper.setupTestDatabase()

        // Get real services from Koin
        authService = get()
        jwtService = get()
        userRepository = get()
    }

    @Test
    fun `complete user registration and login flow`() {
        // Register user
        val user = authService.createUser(
            username = "testuser",
            email = "test@example.com",
            name = "Test User",
            password = "password123"
        )

        // Verify user in database
        val dbUser = userRepository.findByUsername("testuser")
        assertNotNull(dbUser)

        // Login
        val authenticatedUser = authService.authenticateUser("testuser", "password123")
        assertNotNull(authenticatedUser)

        // Generate JWT
        val token = jwtService.generateToken(user.username, user.id.value.toString())

        // Verify JWT
        val userFromToken = jwtService.getUserFromToken(token)
        assertEquals(user.id, userFromToken?.id)
    }
}
```

**Benefits of Integration Tests:**
- Catch issues in component interactions
- Validate database constraints and relationships
- Test transaction boundaries
- Faster than e2e tests (no HTTP/GraphQL overhead)
- More realistic than unit tests (real database)

**Success Criteria**:
- 10-15 integration tests implemented
- All integration tests passing
- Test complete workflows (auth, blog CRUD, comments, likes)
- All 133 unit tests still passing
- All 28 e2e tests still passing
- Integration tests run in <5 seconds

---

### Phase 9: Browser-Based E2E Tests ⏳ TODO (High Value)

**Goal**: Convert existing GraphQL-based e2e tests to true browser-based end-to-end tests using Playwright or Cypress, testing the complete user experience through the actual UI.

**Motivation**: The current e2e tests (`e2e-test.sh`) test the GraphQL API directly with curl commands. While valuable, they don't test:
- The React frontend rendering and interactions
- User flows through the actual UI
- Client-side state management
- Form validation and error handling in the browser
- Real user experience scenarios

#### Why Playwright?

**Recommendation**: ✅ **Playwright** is the best fit for this project.

| Feature | Playwright | Cypress |
|---------|------------|---------|
| Language support | JS/TS, Python, .NET, Java | JS/TS only |
| Browser support | Chromium, Firefox, WebKit | Chromium, Firefox, WebKit |
| Parallel execution | Built-in | Paid feature |
| Speed | Fast | Medium |
| API testing | Built-in | Plugin required |
| Mobile emulation | Excellent | Good |
| Learning curve | Medium | Easy |
| CI integration | Excellent | Excellent |

**Playwright advantages:**
- Native TypeScript support (matches our React frontend)
- Built-in parallel test execution (free)
- Auto-wait for elements (less flaky tests)
- Built-in test generator (`npx playwright codegen`)
- Network interception for testing error states
- Screenshots and video recording on failure

#### Tasks:

29. ⏳ Set up Playwright in frontend
    ```bash
    cd frontend
    npm init playwright@latest
    ```
    - Install Playwright and dependencies
    - Configure `playwright.config.ts`
    - Set up test directory structure (`frontend/e2e/`)
    - Configure base URL and timeouts

30. ⏳ Create test utilities and fixtures
    - Create `frontend/e2e/fixtures/auth.ts` for login helper
    - Create test user setup/teardown utilities
    - Configure screenshot on failure
    - Set up test data factories

31. ⏳ Implement authentication flow tests
    - **Registration flow:**
      - Navigate to register page
      - Fill out registration form
      - Submit and verify redirect to login
      - Verify success message
    - **Login flow:**
      - Navigate to login page
      - Fill credentials
      - Submit and verify redirect to home
      - Verify user is logged in (header shows username)
    - **Logout flow:**
      - Click logout button
      - Verify redirect and logged out state
    - **Error handling:**
      - Invalid credentials show error
      - Duplicate username shows error
      - Required field validation works

32. ⏳ Implement blog post flow tests
    - **Create post:**
      - Login as user
      - Click "New Post" button
      - Fill title and content
      - Submit and verify post appears
    - **View post:**
      - Navigate to post list
      - Click on post title
      - Verify post detail page shows correct content
    - **Edit post:**
      - Navigate to own post
      - Click edit button
      - Modify content
      - Save and verify changes
    - **Delete post:**
      - Navigate to own post
      - Click delete button
      - Confirm deletion
      - Verify post removed from list
    - **Authorization:**
      - Verify edit/delete buttons only show for post author
      - Verify non-author cannot access edit page directly

33. ⏳ Implement social features tests
    - **Like/Unlike:**
      - Login as user
      - Navigate to a post
      - Click like button, verify count increases
      - Click again, verify unlike works
    - **Comments:**
      - Navigate to post
      - Add comment, verify it appears
      - Delete own comment
      - Verify non-author cannot delete others' comments

34. ⏳ Implement cross-browser testing
    - Configure Playwright to run on Chromium, Firefox, WebKit
    - Verify all tests pass on all browsers
    - Set up CI matrix for browser testing

#### Example Test Structure:

```typescript
// frontend/e2e/auth.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('user can register and login', async ({ page }) => {
    // Generate unique username for test isolation
    const username = `testuser_${Date.now()}`;

    // Navigate to register page
    await page.goto('/register');

    // Fill registration form
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="email"]', `${username}@test.com`);
    await page.fill('input[name="name"]', 'Test User');
    await page.fill('input[name="password"]', 'password123');

    // Submit
    await page.click('button[type="submit"]');

    // Verify redirect to login
    await expect(page).toHaveURL('/login');

    // Login with new credentials
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', 'password123');
    await page.click('button[type="submit"]');

    // Verify logged in - should see username in header
    await expect(page.locator('header')).toContainText(username);
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/login');

    await page.fill('input[name="username"]', 'nonexistent');
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');

    // Verify error message appears
    await expect(page.locator('.error-message')).toBeVisible();
  });
});
```

```typescript
// frontend/e2e/posts.spec.ts
import { test, expect } from '@playwright/test';
import { login } from './fixtures/auth';

test.describe('Blog Posts', () => {
  test.beforeEach(async ({ page }) => {
    // Login before each test
    await login(page, 'testuser', 'password123');
  });

  test('user can create a new post', async ({ page }) => {
    // Click new post button
    await page.click('text=New Post');

    // Fill post form
    const title = `Test Post ${Date.now()}`;
    await page.fill('input[name="title"]', title);
    await page.fill('textarea[name="content"]', 'This is test content for the post.');

    // Submit
    await page.click('button[type="submit"]');

    // Verify post was created - should redirect to post detail
    await expect(page.locator('h1')).toContainText(title);
  });

  test('user can like and unlike a post', async ({ page }) => {
    // Navigate to a post
    await page.goto('/');
    await page.click('.post-card h2 a >> nth=0');

    // Get initial like count
    const likeButton = page.locator('button:has-text("Like")');
    const initialText = await likeButton.textContent();

    // Click like
    await likeButton.click();

    // Verify count changed
    await expect(likeButton).not.toHaveText(initialText!);
  });
});
```

#### Playwright Configuration:

```typescript
// frontend/playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  // Start dev server before running tests
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
```

#### Migration Plan:

1. **Keep existing `e2e-test.sh`** - These GraphQL API tests are still valuable for:
   - Fast API regression testing
   - CI/CD pipeline (faster than browser tests)
   - Testing API edge cases without UI

2. **Add Playwright tests incrementally** - Start with critical user flows:
   - Authentication (register, login, logout)
   - Post CRUD (create, read, update, delete)
   - Social features (likes, comments)

3. **Run both test suites** - Different purposes:
   - `e2e-test.sh`: API contract testing (fast, ~30s)
   - Playwright: User experience testing (slower, ~2-3 min)

**Success Criteria**:
- Playwright installed and configured in frontend
- 15-20 browser-based E2E tests covering all major user flows
- Tests pass on Chromium, Firefox, and WebKit
- All tests isolated (no shared state between tests)
- Screenshots/videos captured on failure
- Tests run in <3 minutes total
- Existing `e2e-test.sh` tests still passing (28 tests)
- CI-ready configuration

---

### Phase 10: Docker Deployment ⏳ TODO (Medium Risk)

**Goal**: Create Docker container for easy deployment and distribution.

**Motivation**: Containerizing the application makes it easy to:
- Deploy to cloud platforms (AWS, GCP, Azure)
- Run consistently across different environments
- Simplify local development setup for new developers
- Include all dependencies in a single image

#### Tasks:

35. ⏳ Create Dockerfile
    - Use multi-stage build to optimize image size
    - Stage 1: Build with Gradle (include all build dependencies)
    - Stage 2: Runtime with minimal JRE
    - Copy compiled JAR and resources to runtime stage

36. ⏳ Configure application for Docker
    - Make database path configurable via environment variables
    - Support SQLite database in Docker volume
    - Configure server ports via environment variables
    - Add health check endpoint support

37. ⏳ Create .dockerignore file
    - Exclude build artifacts, node_modules, etc.
    - Reduce build context size

38. ⏳ Add docker-compose.yml (optional)
    - Define application service
    - Set up volume for database persistence
    - Configure environment variables
    - Optional: Include frontend service

39. ⏳ Update README with Docker instructions
    - How to build Docker image
    - How to run container
    - Environment variable documentation
    - Volume mount instructions

**Example Dockerfile Structure**:
```dockerfile
# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew build --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Create directory for database
RUN mkdir -p /app/data

# Environment variables
ENV DATABASE_PATH=/app/data/blog.db
ENV SERVER_PORT=8080
ENV JWT_SECRET=change-me-in-production

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Environment Variables**:
- `DATABASE_PATH`: Path to SQLite database file (default: `/app/data/blog.db`)
- `SERVER_PORT`: Server port (default: `8080`)
- `JWT_SECRET`: JWT signing secret (must be set in production)
- `JWT_ISSUER`: JWT issuer (default: `blog-app`)
- `APP_ENV`: Environment (test/dev/prod)

**Benefits**:
- ✅ Consistent deployment across environments
- ✅ Simplified dependency management
- ✅ Easy scaling and orchestration
- ✅ Isolated runtime environment
- ✅ Version control for entire stack

**Success Criteria**:
- Docker image builds successfully
- Container runs and serves application on port 8080
- Database persists in mounted volume
- All e2e tests pass against Dockerized application
- Image size optimized (<200MB for runtime)

---

## Testing Strategy

### Test Pyramid

```
        /\
       /  \     Browser E2E Tests (15-20 Playwright tests)
      /____\    ↑ Test real user experience through UI
     /      \
    /        \   API E2E Tests (28 tests via e2e-test.sh)
   /__________\  ↑ Test GraphQL API contracts
  /            \
 /  Integration \ Integration Tests (10-15 tests)
/________________\↑ Test with real DB, test workflows
/                  \
/    Unit Tests     \ Unit Tests (133+ tests)
/____________________\↑ Test individual components
```

### Test Types

1. **Unit Tests** (Fast, Isolated)
   - Services: Test business logic with mocked dependencies
   - Repositories: Test with in-memory H2 database
   - Utilities: Test pure functions (PasswordService)
   - Goal: 133+ tests, <3 seconds total runtime

2. **Integration Tests** (Medium speed)
   - Test multiple components together
   - Use real database (H2 in-memory)
   - Test complete workflows
   - Goal: 10-15 tests, <5 seconds total runtime

3. **API E2E Tests** (Medium-slow)
   - Keep existing 28 tests in `e2e-test.sh`
   - Test GraphQL API contracts directly with curl
   - Run on every commit
   - Goal: Maintain current 28 tests, <30 seconds runtime

4. **Browser E2E Tests** (Slow, Comprehensive)
   - Test real user experience through Playwright
   - Cross-browser testing (Chromium, Firefox, WebKit)
   - Run less frequently (pre-commit, CI/CD)
   - Goal: 15-20 tests, <3 minutes total runtime

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

### Phase 3-5 Complete ✅
- ✅ All services use dependency injection
- ✅ No hardcoded dependencies (all objects converted to classes)
- ✅ Application starts with Koin
- ✅ All e2e tests still pass (no regressions - 28/28)
- ✅ 100 unit tests passing (96 passed, 4 skipped)
- ✅ DatabaseFactory, GraphQLServer, AuthServer all injectable
- ✅ ViaductApplication uses Koin DI

### Phase 6-7 Complete ✅
- ✅ Resolvers use repositories
- ✅ Mutation/query resolvers refactored (field resolvers still use direct DB access)
- ✅ 124 unit tests passing (exceeds goal of 100+)
- ✅ Resolver smoke tests (20 tests covering all resolvers)
- ✅ Good code coverage for core business logic

### Phase 8 Complete ⏳ TODO
- ⏳ 10-15 integration tests passing
- ✅ All 28 API e2e tests still passing
- ✅ 133 unit tests (achieved)
- ⏳ Integration test suite
- ⏳ Test suite runs in <10 seconds (currently ~3s for unit tests)

### Phase 9 Complete ⏳ TODO
- ⏳ 15-20 browser E2E tests passing
- ⏳ Tests pass on Chromium, Firefox, WebKit
- ⏳ Playwright configuration complete
- ✅ All 28 API e2e tests still passing (maintained)

### Phase 10 Complete ⏳ TODO
- ⏳ Docker image builds successfully
- ⏳ All tests pass in Dockerized environment
- ⏳ CI/CD ready

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

1. ✅ **Phase 1** (Setup) - Zero risk
2. ✅ **Phase 2** (Repositories) - Learn pattern, get quick wins
3. ✅ **Phase 3** (Services) - Build on repository pattern
4. ✅ **Phase 4** (Koin modules) - Wire everything together
5. ✅ **Phase 5** (Singletons) - Big refactor, test thoroughly
6. ✅ **Phase 6** (Resolvers) - May require research
7. ✅ **Phase 7** (Unit tests) - Write tests as you go
8. ⏳ **Phase 8** (Integration tests) - Validate component interactions
9. ⏳ **Phase 9** (Browser E2E tests) - Test real user experience
10. ⏳ **Phase 10** (Docker) - Containerize for deployment

---

## Files Created (Phases 1-5)

### Configuration ✅
- ✅ `src/main/kotlin/com/example/config/AppConfig.kt`
- ✅ `src/main/kotlin/com/example/config/JwtConfig.kt`
- ✅ `src/main/kotlin/com/example/config/DatabaseConfig.kt`
- ✅ `src/main/kotlin/com/example/config/ServerConfig.kt`
- ✅ `src/main/kotlin/com/example/config/KoinModules.kt`

### Database ✅
- ✅ `src/main/kotlin/com/example/database/DatabaseFactory.kt`

### Repositories ✅
- ✅ `src/main/kotlin/com/example/database/repositories/UserRepository.kt` (interface)
- ✅ `src/main/kotlin/com/example/database/repositories/ExposedUserRepository.kt`
- ✅ `src/main/kotlin/com/example/database/repositories/PostRepository.kt` (interface)
- ✅ `src/main/kotlin/com/example/database/repositories/ExposedPostRepository.kt`
- ✅ `src/main/kotlin/com/example/database/repositories/CommentRepository.kt` (interface)
- ✅ `src/main/kotlin/com/example/database/repositories/ExposedCommentRepository.kt`
- ✅ `src/main/kotlin/com/example/database/repositories/LikeRepository.kt` (interface)
- ✅ `src/main/kotlin/com/example/database/repositories/ExposedLikeRepository.kt`

### Services ✅
- ✅ `src/main/kotlin/com/example/auth/AuthenticationService.kt` (extracted from PasswordService.kt)

### Tests ✅
- ✅ `src/test/kotlin/com/example/config/TestConfig.kt`
- ✅ `src/test/kotlin/com/example/config/AppConfigTest.kt`
- ✅ `src/test/kotlin/com/example/config/KoinModulesTest.kt`
- ✅ `src/test/kotlin/com/example/auth/PasswordServiceTest.kt`
- ✅ `src/test/kotlin/com/example/auth/JwtServiceTest.kt`
- ✅ `src/test/kotlin/com/example/auth/AuthenticationServiceTest.kt`
- ✅ `src/test/kotlin/com/example/database/repositories/DatabaseTestHelper.kt`
- ✅ `src/test/kotlin/com/example/database/repositories/UserRepositoryTest.kt`
- ✅ `src/test/kotlin/com/example/database/repositories/PostRepositoryTest.kt`
- ✅ `src/test/kotlin/com/example/database/repositories/CommentRepositoryTest.kt`
- ✅ `src/test/kotlin/com/example/database/repositories/LikeRepositoryTest.kt`

### Tests TODO (Phases 6-8)
- ✅ `src/test/kotlin/com/example/resolvers/PostResolversTest.kt` (11 tests - smoke tests for instantiation)
- ✅ `src/test/kotlin/com/example/resolvers/CommentResolversTest.kt` (5 tests - smoke tests for instantiation)
- ✅ `src/test/kotlin/com/example/resolvers/LikeResolversTest.kt` (4 tests - smoke tests for instantiation)
- ⏳ `src/test/kotlin/com/example/integration/AuthFlowIntegrationTest.kt`
- ⏳ `src/test/kotlin/com/example/integration/BlogFeatureIntegrationTest.kt`

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

**Document Status**: ✅ Actively Maintained
**Last Updated**: 2025-12-12
**Author**: Claude Code (with human review)
