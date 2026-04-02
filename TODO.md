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
