# TODO: Viaduct Blogging App — Implementation Plan

**Status**: 🚀 In Progress — Phases 1–9 + 11 + 13 + 14 Complete, Phase 10 Next

**Last Updated**: 2026-04-02

## Test Statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration tests (`./gradlew test`) | 182 (4 skipped) | ✅ All passing |
| API E2E tests (`./query-tests.sh`) | 38 | ✅ All passing |
| Browser E2E tests (Playwright, 27 tests × 3 browsers) | 81 runs | ✅ All passing |

## Completed Phases

| Phase | Summary |
|---|---|
| 1 — Setup Foundation | Test infrastructure, JUnit, MockK, H2, Koin dependencies |
| 2 — Repository Pattern | `UserRepository`, `PostRepository`, `CommentRepository`, `LikeRepository` interfaces + Exposed implementations |
| 3 — Service Layer | `AuthenticationService`, `JwtService`, `PasswordService` refactored with constructor injection |
| 4 — Koin DI | `KoinModules.kt` wires all dependencies; `KoinTenantCodeInjector` integrates with Viaduct |
| 5 — Singletons → Classes | `DatabaseConfig`, `GraphQLServer`, `AuthServer` converted from `object` to injectable classes |
| 5.5 — Consolidate Servers | Auth routes merged into GraphQLServer; single server on port 8080 |
| 6 — Refactor Resolvers | All resolvers use constructor-injected repositories; zero `transaction {}` in resolver layer |
| 7 — Resolver Unit Tests | 133 unit tests across all resolvers using MockK |
| 7.5 — Transaction Refactoring | All DB transactions moved to repository layer |
| 7.6 — Frontend CSS | Fixed header width and container box-sizing |
| 8 — Integration Tests | `AuthFlowIntegrationTest` + `BlogWorkflowIntegrationTest` with real H2 database (26 tests) |
| 9 — Playwright Browser E2E | 27 tests across `auth.spec.ts`, `posts.spec.ts`, `social.spec.ts`; API fixtures for setup |
| 11 — Cursor Pagination | `postsConnection(first, after)` via Viaduct `@connection`/`@edge`; `findPage` in repository; `ConnectionBuilder.fromList` in resolver |
| 13 — Migrate Resolver Tests | Migrated to new `FieldResolverTester`/`MutationResolverTester` API where possible; `@Suppress("DEPRECATION")` for resolvers returning List/Boolean/Int (new API only supports single GRT returns); zero deprecation warnings in build |
| 14 — Batch Author Resolver | `PostAuthorResolver` overrides `batchResolve`; `getAuthorsByPostIds` fetches all authors in one `inList` query; eliminates N+1 on posts list |
| E2E fixes | Fixed `e2e.sh` spurious `cd ..` bug; fixed Playwright strict-mode selector failures (`main h1`, `.first()`); all 81 browser tests now passing |
| CI fixes | Disabled `gradle-actions` proprietary caching component (`cache-disabled: true`) to suppress licensing warning |

## Next Steps

- **Phase 10**: Create Dockerfile for containerized deployment
- **Phase 12**: Frontend pagination UI — "Load More" button consuming `postsConnection` in `HomePage.tsx`
- **Phase 15**: DB-level cursor pagination for `postsConnection`
- **Phase 16**: Production database support — PostgreSQL/RDS, connection pooling, migrations

---

## Phase 10: Docker Deployment ⏳ TODO

**Goal**: Multi-stage Dockerfile for easy deployment.

#### Tasks:
- [ ] Multi-stage Dockerfile (build with Gradle, runtime with JRE-alpine)
- [ ] Make `DATABASE_PATH`, `SERVER_PORT`, `JWT_SECRET` configurable via env vars
- [ ] `.dockerignore` to exclude `build/`, `node_modules/`, etc.
- [ ] Optional `docker-compose.yml` with SQLite volume mount
- [ ] Update README with Docker instructions

**Example Dockerfile:**
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
RUN mkdir -p /app/data
ENV DATABASE_PATH=/app/data/blog.db
ENV SERVER_PORT=8080
ENV JWT_SECRET=change-me-in-production
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Success Criteria**: Image builds, container serves on port 8080, database persists in volume, image <200MB.

---

## Phase 12: Frontend Pagination UI ⏳ TODO

**Goal**: Consume `postsConnection` in `HomePage.tsx` to replace the current "load all posts" approach.

#### Tasks:
- [ ] Update `HomePage.tsx` to query `postsConnection(first: 10, after: cursor)` instead of `posts`
- [ ] Show "Load More" button when `pageInfo.hasNextPage = true`; append results using Apollo `fetchMore`
- [ ] Show "Showing X of Y posts" using `totalCount`
- [ ] Add Playwright tests: first page loads N posts, "Load More" appears, click appends posts, button hides when exhausted

**Success Criteria**: HomePage no longer fetches all posts at once; "Load More" works end-to-end; Playwright tests pass.

---

## Phase 15: DB-Level Cursor Pagination for `postsConnection` ⏳ TODO

**Goal**: Replace the current in-memory slicing in `PostsConnectionResolver` with a true database-level query so only the requested page of rows is fetched.

**Current problem**: `PostsConnectionResolver.resolve` calls `postRepository.findAll()`, loading every post into memory, then passes the full list to `ConnectionBuilder.fromList` which discards everything outside the requested window. This is a full table scan on every paginated request.

**Approach**: Viaduct's `ConnectionBuilder.fromList` generates opaque base64 cursors encoding the item's position (0-based index) in the list. Decoding a cursor gives an integer offset. We can use that offset directly with `findPage(limit, offset)` to push the slicing into the database.

#### Tasks:
- [ ] Decode the `after` cursor in `PostsConnectionResolver`: base64-decode → parse integer offset
- [ ] Call `postRepository.findPage(limit = first ?: DEFAULT_PAGE_SIZE, offset = decodedOffset + 1)` instead of `findAll()`
- [ ] Build the `PostsConnection` response manually (edges + cursors + `pageInfo`) using `postRepository.count()` for `totalCount` and `hasNextPage`
- [ ] Keep `ConnectionBuilder.fromList` as a fallback for the no-cursor first-page case, or replace entirely with manual construction for consistency
- [ ] Add/update repository integration tests for `findPage` edge cases
- [ ] Verify existing `query-tests.sh` pagination tests still pass end-to-end

**Key files**:
- `src/main/kotlin/org/tuchscherer/resolvers/PostQueryResolvers.kt` — `PostsConnectionResolver`
- `src/main/kotlin/org/tuchscherer/database/repositories/PostRepository.kt` — `findPage`, `count` already exist
- `src/main/kotlin/org/tuchscherer/database/repositories/ExposedPostRepository.kt` — implementation

**Success Criteria**: `postsConnection(first: N, after: cursor)` issues exactly one `SELECT … LIMIT N OFFSET M` query to the database; `findAll()` is no longer called from the connection resolver; all pagination `query-tests.sh` and Playwright tests pass.

---

## Phase 16: Production Database Support (PostgreSQL/RDS) ⏳ TODO

**Goal**: Make the app deployable against AWS RDS PostgreSQL in production while keeping SQLite for local dev and H2 for unit tests.

**Current state**: `AppConfig` already has `TEST`/`DEV`/`PROD` environments and `prodConfig()` reads `DATABASE_URL`/`DATABASE_DRIVER` from env vars. The config scaffolding exists — but several gaps remain before it actually works against RDS.

**Gaps to close:**

#### 1. Add PostgreSQL JDBC driver dependency
- Add `implementation("org.postgresql:postgresql:42.7.x")` to `build.gradle.kts`
- Keep SQLite and H2 drivers for dev/test

#### 2. Add HikariCP connection pooling for prod
- `DatabaseFactory.initialize()` currently calls `Database.connect(url, driver)` which creates bare connections — no pooling, no timeout handling
- For RDS, use `Database.connect(HikariDataSource(hikariConfig))` in prod
- Configure pool size, connection timeout, keepalive for RDS latency characteristics
- Dev/test can continue with simple `Database.connect`

#### 3. Replace `SchemaUtils.create` with proper migrations (Flyway)
- `SchemaUtils.create(Users, Posts, Comments, Likes)` is dev-only — it's a no-op if tables exist but cannot evolve the schema safely
- Add Flyway dependency; write initial migration `V1__create_tables.sql` matching current schema
- `DatabaseFactory.initialize()` runs `Flyway.configure().dataSource(...).load().migrate()` before any queries
- Test config can keep `SchemaUtils.create` for speed, or run Flyway against H2 (`MODE=PostgreSQL`)

#### 4. Fix H2 test mode to match prod dialect
- Test config currently uses `MODE=MySQL`; if prod is PostgreSQL, change to `MODE=PostgreSQL` so tests catch dialect-specific bugs

#### 5. Add RDS SSL configuration
- RDS requires SSL by default; JDBC URL needs `?sslmode=require` (or configure via HikariCP `addDataSourceProperty`)
- Make SSL mode configurable (`DATABASE_SSL_MODE` env var, default `require` in prod, `disable` in dev)

#### 6. Add database credentials env vars to `prodConfig()`
- RDS uses separate username + password (not embedded in URL)
- Add `DATABASE_USERNAME` and `DATABASE_PASSWORD` env vars; pass to HikariCP config
- Dev SQLite doesn't need them; keep optional with null defaults

#### 7. Update Docker / deployment config (ties into Phase 10)
- Add `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_SSL_MODE` to Dockerfile ENV documentation
- Document RDS security group requirements (port 5432 open to ECS/EC2 task)

**Key files**:
- `src/main/kotlin/org/tuchscherer/config/AppConfig.kt` — `prodConfig()`, `testConfig()`
- `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt` — connection + schema init
- `build.gradle.kts` — driver + HikariCP + Flyway dependencies
- `src/main/resources/db/migration/` — Flyway SQL migration files (new)

**Environment variables summary**:
| Variable | Dev | Test | Prod |
|---|---|---|---|
| `APP_ENV` | `DEV` (default) | `TEST` | `PROD` |
| `DATABASE_URL` | optional (defaults to `blog.db`) | n/a (hardcoded H2) | required (`jdbc:postgresql://rds-host:5432/blog`) |
| `DATABASE_USERNAME` | n/a | n/a | required |
| `DATABASE_PASSWORD` | n/a | n/a | required |
| `DATABASE_SSL_MODE` | `disable` | n/a | `require` |
| `JWT_SECRET` | optional | optional | required |

**Success Criteria**: `APP_ENV=PROD` with valid `DATABASE_*` vars connects to a real PostgreSQL/RDS instance, Flyway runs migrations on first boot, app serves traffic; dev still works with `blog.db` SQLite; all H2 tests pass with `MODE=PostgreSQL`.

---

## Testing Strategy

```
        /\
       /  \     Browser E2E (27 tests × 3 browsers via ./e2e.sh)
      /____\    ↑ Real UI flows through Chromium, Firefox, WebKit
     /      \
    /        \   API E2E (38 tests via ./query-tests.sh)
   /__________\  ↑ GraphQL + REST contracts via curl
  /            \
 /  Integration \ 26 integration tests (H2 in-memory)
/________________\↑ Real services + real DB, no HTTP overhead
/                  \
/    Unit Tests     \ 156 unit tests (MockK)
/____________________\↑ Isolated, fast, no DB
```

**Run commands:**
- `./gradlew test` — all unit + integration tests
- `./query-tests.sh` — API e2e tests (backend must be running)
- `./e2e.sh` — starts servers + runs Playwright browser tests
- `cd frontend && npm run test:e2e` — Playwright only (servers must already be running)

---

**Document Status**: ✅ Actively Maintained
**Author**: Claude Code (with human review)
