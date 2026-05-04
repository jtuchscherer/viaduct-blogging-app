# TODO: Viaduct Blogging App — Implementation Plan

**Status**: 🚀 In Progress — Core complete; Flyway migrations + test refactor remaining

**Last Updated**: 2026-05-03

## Test Statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration tests (`./gradlew test`) | 394 | ✅ All passing |
| API E2E tests (`./query-tests.sh`) | 38 | ✅ All passing |
| Browser E2E tests (Playwright, 40 tests × 3 browsers) | 120 runs | ✅ All passing |

## Completed Phases

| Phase | Summary |
|---|---|
| 1–6 | Foundation: test infra, repository pattern, service layer, Koin DI, server consolidation, resolver refactor |
| 7–9 | Testing: 133 unit tests (MockK), H2 integration tests, Playwright E2E suite |
| 10 | Docker: runtime Dockerfile, `docker-compose.yml`, env-var config (`DATABASE_URL`, `JWT_SECRET`, `CORS_ORIGIN`) |
| 11 | Cursor pagination: `postsConnection(first, after)` via Viaduct `@connection`/`@edge` |
| 12 | Frontend pagination UI: "Load More" button, `fetchMore`, "Showing X of Y posts" |
| 13 | Resolver test migration to `FieldResolverTester`/`MutationResolverTester` API |
| 14 | Batch author resolver: `batchResolve` eliminates N+1 on post lists |
| 15 | DB-level cursor pagination: `findPage(limit, offset)` replaces full table scan in `PostsConnectionResolver` |
| 16 (partial) | PostgreSQL + HikariCP: driver, connection pooling, Micrometer pool metrics; Flyway migrations outstanding |
| 17 | Production telemetry: structured JSON logging, `CallId`/`CallLogging`, Micrometer + Prometheus `/metrics`, enhanced `/health` |
| 18 | Rich text editor: Lexical on Create/Edit pages; DOMPurify rendering in PostDetailPage |
| 19 | Frontend unit tests: Vitest + jsdom + Testing Library (26 tests, `npm test`) |
| Admin | Full CRUD over users/posts/comments; dashboard stats; `requireAdmin()` guard; cascading user delete |
| CheckedList | `CheckedListPost` type with toggleable ordered items; full resolver/repo/test/frontend coverage |
| Analytics | View counts, read-time estimates, `trending(limit)` query; `recordPostView` mutation |
| Code quality | Domain exceptions, `requireAuth()` helper, input validation, structured logging, env-var config |

## Next Steps

- **Phase 16**: Flyway migrations — write `V1__create_tables.sql`, replace `SchemaUtils.create` in `DatabaseFactory`, test against H2 `MODE=PostgreSQL`
- **Tech Debt**: Drop `DefaultAbstractResolverTestBase` from root-project resolver tests (see section below)

---

## Phase 16: Remaining — Flyway Migrations

**Goal**: Replace `SchemaUtils.create` (dev-only, no schema evolution) with Flyway so the prod database schema can be evolved safely.

#### Tasks:
- [ ] Add Flyway dependency to `build.gradle.kts`
- [ ] Write `src/main/resources/db/migration/V1__create_tables.sql` matching current schema (Users, Posts, Comments, Likes, CheckedListItems, PostViews)
- [ ] `DatabaseFactory.initialize()` runs `Flyway.migrate()` before any queries in prod; keep `SchemaUtils.create` for H2 test config
- [ ] Change H2 test mode from `MODE=MySQL` to `MODE=PostgreSQL` to catch dialect bugs early
- [ ] Add `DATABASE_USERNAME` / `DATABASE_PASSWORD` env vars to `prodConfig()` for RDS (currently embedded in URL)
- [ ] Document RDS SSL config (`DATABASE_SSL_MODE`, default `require` in prod)

**Success Criteria**: `APP_ENV=PROD` boots against a real PostgreSQL instance via Flyway; dev still works with `blog.db`; all H2 tests pass.

---

## Tech Debt: Drop `DefaultAbstractResolverTestBase` from root-project resolver tests

**Goal**: Remove `viaduct-tenant-runtime`, `viaduct-engine-runtime`, and `viaduct-engine-wiring` as test dependencies from the root `build.gradle.kts`, and delete lines 33–35 from `gradle/libs.versions.toml`.

**Why**: The analytics and checkedlist modules already had this refactor applied. These three deps exist solely because every test extends `DefaultAbstractResolverTestBase` and implements:

```kotlin
override fun getSchema(): ViaductSchema = SchemaFactory(DefaultCoroutineInterop).fromResources()
```

**Approach**: Same pattern applied to analytics and checkedlist modules:
- Replace `DefaultAbstractResolverTestBase` with plain JUnit classes
- Mock `GlobalID` directly: `mockk<GlobalID<ViaductBlogPost>>()` with `every { id.internalID } returns "..."`
- For batch resolvers that call `ctx.nodeRef(...)`, explicitly stub `ctx.nodeRef(any<GlobalID<T>>())` to return `mockk<T>(relaxed = true)`
- For mutation resolvers that return GRT objects via builders, keep error/auth tests and leave success-path coverage to `query-tests.sh`

**Affected test files** (all in `src/test/kotlin/org/tuchscherer/resolvers/`):
`AdminMutationResolversTest.kt`, `AdminQueryResolversTest.kt`, `CommentFieldResolversTest.kt`, `CreateCommentResolverTest.kt`, `CreatePostResolverTest.kt`, `DeleteCommentResolverTest.kt`, `DeletePostResolverTest.kt`, `LikeFieldResolversTest.kt`, `LikeObjectFieldResolversTest.kt`, `LikePostResolverTest.kt`, `MyPostsResolverTest.kt`, `NodeResolversTest.kt`, `PostCommentsResolverTest.kt`, `PostFieldResolversTest.kt`, `PostResolverTest.kt`, `PostsResolverTest.kt`, `UnlikePostResolverTest.kt`, `UpdatePostResolverTest.kt`, `UserResolversTest.kt`

**What stays**: `testFixtures(libs.viaduct.tenant.api)` must remain — `MockConnectionFieldExecutionContext` used in `PostsResolverTest` lives there.

**Definition of done**: Lines 33–35 removed from `gradle/libs.versions.toml`; matching `testImplementation` lines removed from root `build.gradle.kts`; `./gradlew test` passes.

---

## Testing Strategy

```
        /\
       /  \     Browser E2E (40 tests × 3 browsers via ./e2e.sh)
      /____\    ↑ Real UI flows through Chromium, Firefox, WebKit
     /      \
    /        \   API E2E (38 tests via ./query-tests.sh)
   /__________\  ↑ GraphQL + REST contracts via curl
  /            \
 /  Integration \ H2 in-memory integration tests
/________________\↑ Real services + real DB, no HTTP overhead
/                  \
/    Unit Tests     \ MockK unit tests
/____________________\↑ Isolated, fast, no DB
```

**Run commands:**
- `./gradlew test` — all unit + integration tests
- `./query-tests.sh` — API e2e tests (backend must be running)
- `./e2e.sh` — starts servers + runs Playwright browser tests
- `cd frontend && npm test` — frontend Vitest unit tests
