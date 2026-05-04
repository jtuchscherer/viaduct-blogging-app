# TODO: Viaduct Blogging App — Implementation Plan

**Status**: 🚀 In Progress — Core complete; Analytics (Phases 20–23) + Flyway migrations + test refactor remaining

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

---

## Phase 20: Analytics Backend — Schema, Storage, and Resolvers ⏳ TODO

**Goal**: Implement the analytics tenant module on the backend: track post views, expose `viewCount`, `readTimeMinutes`, and `trending` via GraphQL.

**Current state**: `ViaductPostTypeLookupPort.kt` exists as a stub; `post_type` column is in the `Posts` table. There is no `PostViews` table, no analytics schema extension, and no resolvers. The `QueryFieldComplexityCalculator` already accounts for `trending` as an unbounded list — the resolver just needs to be built.

### Tasks

#### 1. Add `PostViews` table and repository
- New `PostViews` table: `id`, `post_id` (FK → Posts), `viewed_at` (datetime)
- `PostViewRepository` interface: `recordView(postId: UUID)`, `countViews(postId: UUID): Int`, `topByViews(limit: Int): List<PostView>`
- `ExposedPostViewRepository` implementation; all queries inside `transaction {}` blocks
- Register in `KoinModules.kt`
- Add a Flyway migration `V2__create_post_views.sql`
- `DatabaseFactory`: add `PostViews` to `createMissingTablesAndColumns` call (already switched from `create` in the container fix)

#### 2. Add analytics GraphQL schema extension
- New file `src/main/viaduct/schema/PostAnalytics.graphqls` (Viaduct supports split schema files):
  ```graphql
  # Extends BlogPost and CheckedListPost with analytics fields
  extend type BlogPost {
    viewCount: Int! @resolver
    readTimeMinutes: Int! @resolver
  }

  extend type CheckedListPost {
    viewCount: Int! @resolver
    readTimeMinutes: Int! @resolver
  }

  extend type Mutation {
    recordPostView(postId: ID! @idOf(type: "BlogPost")): Boolean! @resolver
  }

  extend type Query {
    trending(limit: Int = 10): [Post!]! @resolver
  }
  ```
- Run `./gradlew build` to generate resolver base classes

#### 3. Implement resolvers
- `BlogPostViewCountResolver` — extends generated `BlogPostResolvers.ViewCount()`; calls `postViewRepository.countViews(post.id)`
- `BlogPostReadTimeMinutesResolver` — extends `BlogPostResolvers.ReadTimeMinutes()`; computes `max(1, wordCount / 200)` from `post.content` (strips HTML tags first)
- Same two resolvers for `CheckedListPost`
- `RecordPostViewResolver` — extends `MutationResolvers.RecordPostView()`; calls `postViewRepository.recordView(postId)`; no auth required (public)
- `TrendingResolver` — extends `QueryResolvers.Trending()`; calls `postViewRepository.topByViews(limit)`, fetches the corresponding posts via `postRepository.findByIds(ids)`, returns as `List<Post>`
- Register all five in `KoinModules.kt`

#### 4. Unit tests
- `ViewCountResolverTest`, `ReadTimeMinutesResolverTest` — mock `PostViewRepository`; assert correct values
- `RecordPostViewResolverTest` — verify `recordView` is called with correct id; unauthenticated calls succeed
- `TrendingResolverTest` — mock `topByViews` returning N ids; assert resolver returns the right posts in order
- Repository integration tests for `PostViewRepository` using `DatabaseTestHelper` (H2)

**Key files**:
- `src/main/kotlin/org/tuchscherer/database/Tables.kt` — add `PostViews`
- `src/main/kotlin/org/tuchscherer/database/repositories/PostViewRepository.kt` — new
- `src/main/kotlin/org/tuchscherer/database/repositories/ExposedPostViewRepository.kt` — new
- `src/main/viaduct/schema/PostAnalytics.graphqls` — new
- `src/main/kotlin/org/tuchscherer/resolvers/AnalyticsResolvers.kt` — new (all five resolvers)
- `src/main/resources/db/migration/V2__create_post_views.sql` — new
- `src/main/kotlin/org/tuchscherer/config/KoinModules.kt` — register new beans

**Success Criteria**: `trending(limit: 5) { id title }` returns the 5 most-viewed posts; `recordPostView` inserts a row in `post_views`; `viewCount` and `readTimeMinutes` return correct values; `./gradlew test` passes.

---

## Phase 21: Analytics Frontend — View Tracking and Post Detail Page ⏳ TODO

**Goal**: Wire `recordPostView` into the post detail page so views are tracked automatically on open, and display `viewCount` and `readTimeMinutes` on the post detail page.

**Depends on**: Phase 20 complete (backend resolvers live).

### Tasks

#### 1. Fire `recordPostView` on post open
- In `PostDetailPage.tsx`, add a `useMutation` for `recordPostView`
- Call it inside a `useEffect` that fires once when the `postId` is known (on mount)
- No loading state or error UI needed — fire-and-forget; log to console on failure only
- Mutation should be skipped if the viewer is the post's author (optional, nice-to-have)

#### 2. Add `viewCount` and `readTimeMinutes` to the post detail query
- Extend the `GET_POST` query in `PostDetailPage.tsx` to also fetch `viewCount` and `readTimeMinutes`
- Display them below the post title / metadata line, e.g.:
  - `👁 142 views  ·  ⏱ 4 min read`
- Match the existing metadata style (author, date)

#### 3. Show `readTimeMinutes` on post cards (HomePage)
- Extend the `postsConnection` query to include `readTimeMinutes` on each post
- Add it to `PostCard` / the post list items on `HomePage`: `⏱ 3 min read`
- Keep it subtle — secondary text colour, small font

#### 4. Unit and E2E tests
- Vitest unit test: `PostDetailPage` fires `recordPostView` mutation on mount (use `MockedProvider`)
- Playwright test: open a post, reload, assert view count incremented (query `viewCount` via API after page load)
- Playwright test: post card shows `readTimeMinutes`; post detail page shows both `viewCount` and `readTimeMinutes`

**Key files**:
- `frontend/src/pages/PostDetailPage.tsx` — mutation + extended query + metadata display
- `frontend/src/pages/HomePage.tsx` — add `readTimeMinutes` to connection query
- `frontend/src/components/PostCard.tsx` (or equivalent list item) — render read time
- `frontend/src/graphql/` (or inline) — extend/add query/mutation fragments
- `frontend/e2e/posts.spec.ts` — add view count + read time E2E tests
- `frontend/test/pages/PostDetailPage.test.tsx` — unit test for mutation on mount

**Success Criteria**: Opening a post increments its `viewCount` in the database; the detail page shows views and read time; post cards show read time; Playwright and Vitest tests pass.

---

## Phase 22: Analytics Frontend — Admin Stats and Homepage Sorting ⏳ TODO

**Goal**: Surface analytics data in the admin dashboard and let users sort the homepage post list by trending or creation date.

**Depends on**: Phases 20–21 complete.

### Tasks

#### 1. Add analytics to the Admin dashboard
- Extend the `AdminStats` GraphQL type (or add a separate query) to include:
  - `totalViews: Int!` — sum of all post view counts
  - Top 5 trending posts (id, title, viewCount) for an "Most Viewed Posts" table
- Add a new "Analytics" card/section to the admin stats page showing:
  - Total views across all posts
  - "Most Viewed Posts" table: rank, title (linked to post), view count
- Keep it on the existing admin stats page, not a new route

#### 2. Sort posts on the Homepage
- Add a sort control to `HomePage` — a segmented button or dropdown with two options:
  - **New** (default) — sorted by `createdAt` descending (current behaviour)
  - **Trending** — fetches `trending(limit: 10)` instead of `postsConnection`
- When "Trending" is selected, switch the Apollo query to `trending`; when "New", use `postsConnection` as today
- "Load More" pagination applies only to "New" mode; trending shows a fixed list (no pagination needed)
- Persist the selected sort in component state (no URL param needed)

#### 3. Unit and E2E tests
- Playwright test: admin stats page shows "Most Viewed Posts" table after viewing a post
- Playwright test: homepage sort — default shows newest first; switch to trending, verify the most-viewed post appears at the top
- Vitest unit test: sort control renders both options; clicking "Trending" switches the active label

**Key files**:
- `src/main/viaduct/schema/schema.graphqls` — extend `AdminStats` with `totalViews` and `topPosts`
- `src/main/kotlin/org/tuchscherer/resolvers/AdminResolvers.kt` — update `AdminStatsResolver`
- `frontend/src/pages/AdminStatsPage.tsx` (or equivalent) — add analytics section
- `frontend/src/pages/HomePage.tsx` — sort control + conditional query
- `frontend/e2e/posts.spec.ts` — sorting E2E tests
- `frontend/test/pages/HomePage.test.tsx` — sort control unit tests

**Success Criteria**: Admin dashboard shows total views and a top-5 most-viewed table; homepage sort control switches between newest and trending; all new Playwright and Vitest tests pass; `./gradlew test` still green.

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
