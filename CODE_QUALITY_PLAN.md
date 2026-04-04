# Chores: Code Quality Review Findings

**Last Updated**: 2026-04-03

Findings from a Clean Code / SOLID / security review of the codebase. These are tech-debt items and quality improvements — not feature work.

---

## Bugs (High Priority)

- [x] **Duplicate username check + layer violation in `/auth/register`** (`GraphQLServer.kt:175-195`): Removed raw Exposed DAO query; endpoint now delegates entirely to `authService.createUser()`.
- [x] **Crash on deleted user in `/auth/me`** (`GraphQLServer.kt:244-249`): Replaced `principal!!` + `.first()` with safe access + `firstOrNull()`, returning 401/404 instead of crashing.
- [x] **MyPostsPage shows raw HTML tags in excerpts** (`MyPostsPage.tsx:80-82`): Replaced `post.content.substring()` with `getExcerpt()` from `utils/content.ts`; moved helper out of `HomePage.tsx` into the shared util.

---

## SOLID & Clean Code (Medium Priority)

- [ ] **SRP: Extract auth routes from `GraphQLServer.kt`** — The class handles Ktor server setup, CORS, JWT validation, GraphQL execution, 3 auth endpoints, and health check (~260 lines, 5+ responsibilities). Extract auth routes into a dedicated `AuthRoutes.kt` Ktor routing module.
- [ ] **DRY: Extract auth extraction helper for resolvers** — Every mutation resolver repeats 4 lines of `ctx.requestContext as? RequestContext` / `requestContext.user` casting and null-checking. Create a `fun Context.requireAuth(): User` extension function and use it in `CreatePostResolver`, `UpdatePostResolver`, `DeletePostResolver`, `CreateCommentResolver`, `DeleteCommentResolver`, `LikePostResolver`, `UnlikePostResolver`.
- [ ] **Use domain exceptions instead of generic `RuntimeException`** — Resolvers throw `RuntimeException` for auth, not-found, and authorization errors. `AuthenticationException` exists in `AuthContext.kt` but isn't used by resolvers. Create/reuse specific exception types (`AuthenticationException`, `NotFoundException`, `AuthorizationException`) so callers can distinguish errors.
- [ ] **Extract shared `Post` TypeScript interface** — `Post` is redefined with slightly different shapes in `HomePage.tsx`, `MyPostsPage.tsx`, `PostDetailPage.tsx`, `EditPostPage.tsx`. Extract into a shared `types.ts` and use GraphQL fragments for the query fields.

---

## Test Quality (Medium Priority)

- [ ] **Resolver tests test mock wiring, not behavior** — Most resolver unit tests mock repositories with `mockk(relaxed = true)`, then `verify { repo.method() }` was called. They don't assert the resolver returns correct results. Refactor to assert on return values and actual behavior instead of verifying which mock methods were called.
- [ ] **`relaxed = true` masks silent failures** — Relaxed mocks return defaults silently, so tests can pass even when the resolver returns garbage data. Remove `relaxed = true` where possible and explicitly stub only what's needed.
- [ ] **`KoinModulesTest` tests DI framework internals** — `assertSame(instance1, instance2)` verifies Koin singleton scope, not application behavior. Consider removing or replacing with a smoke test that starts the app and verifies a request works.
- [ ] **Fix or remove `@Disabled` tests** — Disabled tests in repository layer with comments like "Update pattern needs refactoring" are test debt. Either fix the underlying issue or delete the tests.
- [ ] **Add missing edge case tests**: empty/blank title and content on mutations, cascading deletes (post → comments/likes), invalid cursor format in pagination, duplicate like by same user.

---

## Security (Medium Priority)

- [ ] **No input validation on mutations** — `CreatePostResolver` and `UpdatePostResolver` accept any title/content without length checks. A user could submit a multi-MB post body. Add server-side validation for maximum lengths.
- [ ] **No rate limiting on auth endpoints** — `/auth/login` and `/auth/register` have no brute-force protection. Add rate limiting middleware or per-IP throttling.
- [ ] **Hardcoded CORS origin and frontend API URLs** — CORS is hardcoded to `localhost:5173` in `GraphQLServer.kt:94`. `LoginPage.tsx` and `RegisterPage.tsx` hardcode `http://localhost:8080`. Make these configurable via environment variables / Vite env vars.
