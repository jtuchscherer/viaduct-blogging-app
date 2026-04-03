# Viaduct Blogging App — Claude Instructions

## Project Overview

A blogging application with a Kotlin/Viaduct GraphQL backend and React/TypeScript frontend.

- **Backend**: Kotlin, Viaduct (GraphQL framework), Ktor, Exposed ORM, SQLite, Koin DI
- **Frontend**: React 19, TypeScript, Apollo Client, React Router, Vite
- **Backend port**: 8080 (GraphQL at `/graphql`, auth at `/auth/*`, health at `/health`)
- **Frontend port**: 5173

## Common Commands

### Backend
```bash
./gradlew build          # compile + test
./gradlew build -x test  # compile only
./gradlew run            # start the server
./gradlew test           # run unit + integration tests
```

### Frontend
```bash
cd frontend
npm run dev              # start Vite dev server
npm run build            # production build
npm run lint             # ESLint
```

### Tests
```bash
./gradlew test           # backend unit + integration tests (182 tests)
./query-tests.sh         # curl-based GraphQL API tests (requires server running)
./e2e.sh                 # start servers + run Playwright browser tests
cd frontend && npm run test:e2e        # Playwright tests only (servers must already be running)
cd frontend && npm run test:e2e:ui     # Playwright interactive UI mode
```

### Start everything locally
```bash
./start.sh   # builds backend, starts backend + frontend
```

## Architecture

### Backend structure
```
src/main/kotlin/org/tuchscherer/
├── auth/           AuthenticationService, JwtService, PasswordService, RequestContext
├── config/         AppConfig, KoinModules, KoinTenantCodeInjector, DatabaseFactory
├── database/
│   ├── Models.kt   Exposed DAO entities (User, Post, Comment, Like)
│   ├── Tables.kt   Exposed table definitions
│   └── repositories/  Interfaces + ExposedXxxRepository implementations
├── resolvers/      GraphQL resolvers (one file per concern)
└── web/            GraphQLServer (Ktor, hosts both GraphQL and auth routes)
src/main/viaduct/schema/schema.graphqls   — GraphQL schema (source of truth)
```

### Dependency injection
All dependencies are wired via Koin. When adding a new resolver:
1. Create the resolver class with constructor injection
2. Add `singleOf(::MyResolver)` to `resolverModule` in `KoinModules.kt`

Viaduct instantiates resolvers via `KoinTenantCodeInjector`, which delegates to Koin.

### Adding a GraphQL field or resolver
1. Add the field/type to `schema.graphqls`
2. Run `./gradlew build` — Viaduct generates base classes under `src/main/kotlin/org/tuchscherer/viadapp/resolvers/resolverbases/`
3. Create a resolver class extending the generated base
4. Register it in `KoinModules.kt`

### Resolver conventions
- Mutation resolvers extend `MutationResolvers.XxxYyy()`
- Query resolvers extend `QueryResolvers.XxxYyy()`
- Field resolvers extend `XxxResolvers.FieldName()` (e.g. `PostResolvers.Author()`)
- Auth: cast `ctx.requestContext as? RequestContext` to get the authenticated user
- All database access goes through repositories, never raw `transaction {}` in resolvers

### Repository conventions
- Interfaces live in `database/repositories/XxxRepository.kt`
- Implementations are `ExposedXxxRepository` using Exposed DAO
- All `transaction {}` blocks belong in the repository layer only
- **No raw Exposed queries outside repositories** — this applies everywhere: resolvers, route handlers, services. Even Ktor auth routes in `GraphQLServer.kt` must go through repositories or services, never `User.find { ... }` directly.

## Coding Standards

### Exception handling
- Use domain-specific exceptions, never generic `RuntimeException`, in resolvers and route handlers
- `AuthenticationException` — user is not authenticated (maps to 401)
- `NotFoundException` — requested resource does not exist (maps to 404)
- `AuthorizationException` — user lacks permission for the operation (maps to 403)
- Exception types live in `auth/` package; create new types there as needed

### Auth context in resolvers
- Use a shared `requireAuth()` helper to extract the authenticated user from `ctx.requestContext`
- Do not copy-paste the `ctx.requestContext as? RequestContext` / `requestContext.user` null-check pattern into each resolver
- The helper should throw `AuthenticationException` on failure

### Input validation
- All mutations must validate inputs server-side: non-blank strings, maximum lengths, valid formats
- Do not rely on the frontend for validation — the GraphQL API is a public contract
- Reject invalid input early with a descriptive error message

### Error handling & null safety
- Never use `!!` on nullable database lookups — use `firstOrNull()` with a proper error/response
- Route handlers must return appropriate HTTP status codes (404, 401, 403), not generic 500s
- Catch specific exceptions, not blanket `Exception` where possible

### Frontend conventions
- **Shared types**: Define TypeScript interfaces used across pages in `frontend/src/types.ts`, not inline in each page
- **Shared utilities**: Reusable functions like `getExcerpt()` belong in `frontend/src/utils/`, not duplicated per page
- **Environment variables**: Use Vite env vars (`import.meta.env.VITE_API_URL`) for backend URLs; never hardcode `localhost:8080`
- **GraphQL fragments**: Use fragments for shared field sets (e.g., post fields) to avoid duplication across queries

## Testing

### Test structure
```
src/test/kotlin/org/tuchscherer/
├── auth/           Unit tests for services (MockK mocks)
├── config/         KoinModulesTest
├── database/repositories/  H2 in-memory integration tests
├── integration/    AuthFlowIntegrationTest, BlogWorkflowIntegrationTest
└── resolvers/      Unit tests per resolver file (MockK mocks)

frontend/e2e/
├── fixtures/auth.ts    registerUser(), registerAndLogin()
├── fixtures/posts.ts   createPostViaAPI()
├── auth.spec.ts
├── posts.spec.ts
└── social.spec.ts
```

### Test conventions
- Resolver unit tests use MockK; never touch the database
- Repository tests use H2 in-memory via `DatabaseTestHelper`
- Playwright fixtures use the real API for setup (register user, create post) — never the UI — so tests only exercise the thing they're actually testing
- Each Playwright test uses a unique `Date.now()` suffix on usernames/titles to avoid data collisions

### Test quality principles
- **Test behavior, not implementation**: Assert on return values and observable outcomes. Do not `verify { repo.method() }` — that tests mock wiring, not correctness. If the resolver is refactored to call a different method that produces the same result, the test should still pass.
- **Avoid `mockk(relaxed = true)`**: Relaxed mocks silently return defaults, so tests can pass even when the code returns garbage. Explicitly stub only the methods your test needs.
- **No `@Disabled` tests without a plan**: Every disabled test must have a comment linking to a chore or issue. Prefer fixing or deleting over disabling.
- **Cover edge cases**: Empty/blank inputs, not-found lookups, authorization failures, and boundary conditions (e.g., pagination with 0 results) should all have tests.

### Known Viaduct test infrastructure limitation
`ConnectionFieldExecutionContext` (used by `PostsConnectionResolver`) is not compatible with `DefaultAbstractResolverTestBase`. Test pagination via the repository layer (`findPage`) and `query-tests.sh` instead.

## Definition of Done

Before declaring any task complete, always run the full test suite and confirm it passes:
```bash
./gradlew test   # must show BUILD SUCCESSFUL with 0 failures
```
Do not mark work as done until this passes locally.

## Maintaining TODO.md, DEVELOPMENT_PLAN.md, and CODE_QUALITY_PLAN.md

Whenever you modify `TODO.md`, `DEVELOPMENT_PLAN.md`, or `CODE_QUALITY_PLAN.md`, update the `**Last Updated**` date at the top of the respective file to today's date.

- `TODO.md` — feature phases and implementation roadmap
- `DEVELOPMENT_PLAN.md` — architecture, schema, and high-level design
- `CODE_QUALITY_PLAN.md` — tech debt, code quality findings, and cleanup tasks

## Key decisions

- **Single server on port 8080**: Auth routes and GraphQL share one Ktor server. No CORS complexity.
- **`fromList` for pagination**: `PostsConnectionResolver` uses `ConnectionBuilder.fromList` for in-memory slicing. Fine for this scale; DB-level cursor pagination is a future optimisation.
- **Relay-style pagination**: `postsConnection(first, after)` is implemented; the existing `posts` query is kept for backwards compatibility.
- **JWT in localStorage**: Stored as `authToken` + `authUser` keys.
