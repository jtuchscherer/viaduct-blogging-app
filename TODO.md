# TODO: Viaduct Blogging App — Implementation Plan

**Status**: 🚀 In Progress — Phases 1–9 + 11 Complete, Phase 10 Next

**Last Updated**: 2026-04-01

## Test Statistics

| Suite | Count | Status |
|---|---|---|
| Unit + Integration tests (`./gradlew test`) | 176 (4 skipped) | ✅ All passing |
| API E2E tests (`./query-tests.sh`) | 38 | ✅ All passing |
| Browser E2E tests (Playwright, 27 tests × 3 browsers) | 81 runs | ✅ Configured |

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

## Next Steps

- **Phase 10**: Create Dockerfile for containerized deployment
- **Phase 12**: Frontend pagination UI — "Load More" button consuming `postsConnection` in `HomePage.tsx`
- **Phase 13**: Migrate resolver tests from deprecated `DefaultAbstractResolverTestBase` to new Viaduct testing API
- **Phase 14**: Migrate `PostAuthorResolver` to a Viaduct batch resolver to eliminate N+1 queries on the posts list

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

## Phase 13: Migrate Resolver Tests to New Viaduct Testing API ⏳ TODO

**Goal**: Eliminate deprecation warnings in `./gradlew build` output.

**Warning:**
```
'class DefaultAbstractResolverTestBase' is deprecated.
Use FieldResolverTester, MutationResolverTester, or NodeResolverTester in viaduct.api.testing.
```

**Affected files** (`src/test/kotlin/org/tuchscherer/resolvers/`):
`CreatePostResolverTest`, `UpdatePostResolverTest`, `DeletePostResolverTest`, `PostsResolverTest`, `PostResolverTest`, `MyPostsResolverTest`, `PostFieldResolversTest`, `PostCommentsResolverTest`, `LikePostResolverTest`, `UnlikePostResolverTest`, `LikeFieldResolversTest`, `LikeObjectFieldResolversTest`, `CreateCommentResolverTest`, `DeleteCommentResolverTest`, `CommentFieldResolversTest`

#### Tasks:
- [ ] Read `viaduct.api.testing` package to understand new `FieldResolverTester`, `MutationResolverTester`, `NodeResolverTester` APIs
- [ ] Migrate mutation resolver tests to `MutationResolverTester`
- [ ] Migrate query resolver tests to `NodeResolverTester`
- [ ] Migrate field resolver tests to `FieldResolverTester`
- [ ] Verify `./gradlew build` has zero deprecation warnings for resolver tests

**Success Criteria**: No `DefaultAbstractResolverTestBase is deprecated` warnings; all 176 tests still passing.

---

## Phase 14: Batch Resolver for PostAuthorResolver ⏳ TODO

**Goal**: Eliminate the N+1 query problem when loading the posts list. Currently each post in the list fires a separate `getAuthorForPost` query; with a batch resolver Viaduct collects all post IDs and issues a single `SELECT` for all authors at once.

**Background**: Any field resolver that extends a standard `resolve(ctx: Context)` runs once per parent object. When `posts` returns 20 results and the client requests `author` on each, that's 20 individual author lookups. A batch resolver changes the signature to `resolve(keys: List<K>): Map<K, V>` — Viaduct groups all keys and calls it once.

#### Tasks:
- [ ] Read the Viaduct batch resolver docs and identify the correct base class / annotation for a batched field resolver
- [ ] Add `getAuthorsForPosts(postIds: List<UUID>): Map<UUID, User>` to `PostRepository` interface and `ExposedPostRepository` (single `SELECT … WHERE id IN (…)` via Exposed)
- [ ] Rewrite `PostAuthorResolver` to extend the batch resolver base and implement `resolve(keys)` returning `Map<postId, ViaductUser>`
- [ ] Update `PostAuthorResolverTest` to test the batched behaviour (multiple keys in one call)
- [ ] Verify `./gradlew test` still passes

**Success Criteria**: Loading N posts triggers exactly 1 author query instead of N; all existing tests pass.

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
/    Unit Tests     \ 150 unit tests (MockK)
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
