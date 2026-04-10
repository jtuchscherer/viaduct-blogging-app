# Chores: Code Quality Review Findings

**Last Updated**: 2026-04-10

Findings from a Clean Code / SOLID / security review of the codebase. These are tech-debt items and quality improvements — not feature work.

---

## Bugs (High Priority)

- [x] **Duplicate username check + layer violation in `/auth/register`** (`GraphQLServer.kt:175-195`): Removed raw Exposed DAO query; endpoint now delegates entirely to `authService.createUser()`.
- [x] **Crash on deleted user in `/auth/me`** (`GraphQLServer.kt:244-249`): Replaced `principal!!` + `.first()` with safe access + `firstOrNull()`, returning 401/404 instead of crashing.
- [x] **MyPostsPage shows raw HTML tags in excerpts** (`MyPostsPage.tsx:80-82`): Replaced `post.content.substring()` with `getHtmlPreview()` from `utils/content.ts` to render rich-text previews with proper formatting.

---

## SOLID & Clean Code (Medium Priority)

- [x] **SRP: Extract auth routes from `GraphQLServer.kt`** — Moved all 3 auth endpoints + data classes (`RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`) into `AuthRoutes.kt` as a `Route.authRoutes()` extension. `GraphQLServer.kt` now calls `authRoutes(jwtService, authService)`.
- [x] **DRY: Extract auth extraction helper for resolvers** — Created `fun requireAuth(requestContext: Any?): User` in `auth/ResolverAuth.kt`. Replaced the 4-line cast-and-null-check pattern in all 7 mutation resolvers (`CreatePost`, `UpdatePost`, `DeletePost`, `CreateComment`, `DeleteComment`, `LikePost`, `UnlikePost`) and `MyPostsResolver`.
- [x] **Use domain exceptions instead of generic `RuntimeException`** — Added `NotFoundException` and `AuthorizationException` to `AuthContext.kt` alongside existing `AuthenticationException`. All resolvers and field resolvers now throw the appropriate domain exception; resolver tests updated to assert the specific type.
- [x] **Extract shared `Post` TypeScript interface** — Created `frontend/src/types.ts` with `Author`, `Comment`, and `Post` interfaces. `HomePage.tsx` imports `Post` from shared types; `PostDetailPage.tsx` imports `Author` and `Comment` from shared types.

---

## Test Quality (Medium Priority)

- [x] **Resolver tests test mock wiring, not behavior** — Refactored all resolver tests to assert on return values and observable outcomes instead of `verify { repo.method() }`.
- [x] **`relaxed = true` masks silent failures** — Removed `relaxed = true` from repository mocks; entity mocks (e.g. `mockk<Post>(relaxed = true)`) retained where stubs for every property would add noise without benefit.
- [x] **`KoinModulesTest` tests DI framework internals** — Removed `assertSame` singleton-scope assertions that tested Koin internals rather than application behaviour.
- [x] **Fix or remove `@Disabled` tests** — All disabled tests removed.
- [x] **Add missing edge case tests**: blank title/content on `CreatePost`, `UpdatePost`, `CreateComment`; authorization failures; not-found paths. (Cascading delete and duplicate-like coverage deferred to repository integration tests.)

---

## Security (Medium Priority)

- [x] **No input validation on mutations (blank check)** — `CreatePost`, `UpdatePost`, and `CreateComment` now reject blank title/content with a descriptive error. Maximum-length enforcement is still outstanding (see open item below).
- [x] **No maximum-length validation on mutations** — Title and content have no upper-bound check. A user could submit a multi-MB post body. Add server-side length limits.
- [ ] **No rate limiting on auth endpoints** — `/auth/login` and `/auth/register` have no brute-force protection. Add rate limiting middleware or per-IP throttling.
- [x] **Hardcoded CORS origin and frontend API URLs** — CORS now reads `CORS_ORIGIN` env var (default `localhost:5173`) in `ServerConfig`/`AppConfig`. Frontend uses `VITE_API_URL` from `frontend/.env`; `apollo-client.ts`, `LoginPage.tsx`, `RegisterPage.tsx` all use `import.meta.env.VITE_API_URL`. Playwright fixtures use `process.env.API_URL`; `playwright.config.ts` uses `process.env.FRONTEND_URL`.
