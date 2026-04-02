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

### Known Viaduct test infrastructure limitation
`ConnectionFieldExecutionContext` (used by `PostsConnectionResolver`) is not compatible with `DefaultAbstractResolverTestBase`. Test pagination via the repository layer (`findPage`) and `query-tests.sh` instead.

## Definition of Done

Before declaring any task complete, always run the full test suite and confirm it passes:
```bash
./gradlew test   # must show BUILD SUCCESSFUL with 0 failures
```
Do not mark work as done until this passes locally.

## Maintaining TODO.md and DEVELOPMENT_PLAN.md

Whenever you modify `TODO.md` or `DEVELOPMENT_PLAN.md`, update the `**Last Updated**` date at the top of the respective file to today's date.

## Key decisions

- **Single server on port 8080**: Auth routes and GraphQL share one Ktor server. No CORS complexity.
- **`fromList` for pagination**: `PostsConnectionResolver` uses `ConnectionBuilder.fromList` for in-memory slicing. Fine for this scale; DB-level cursor pagination is a future optimisation.
- **Relay-style pagination**: `postsConnection(first, after)` is implemented; the existing `posts` query is kept for backwards compatibility.
- **JWT in localStorage**: Stored as `authToken` + `authUser` keys.
