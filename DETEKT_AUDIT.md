# Detekt findings audit

Audit date: 2026-10-07. Analyzer: Detekt 1.23.8, unmodified default rules. This records the initial report and its implemented resolution; the inventory is an audit trail, not a baseline.

The initial run reported **229 findings: 121 in production and 108 in tests**. Every finding is classified below. A classification of **Tune** means the complaint conflicts with a useful convention; it does not mean turning the rule off globally. **Fix** means a straightforward cleanup with no intended behavior change. **Review** means the code needs a focused design or behavior decision before changing it.

Recommended dispositions: **158 convention exceptions, 61 straightforward fixes, 10 findings needing closer review**. Counts refer to findings, not unique defects: for example, the server's one setup method triggers both size and complexity checks. Guard-clause settings may not recognize every syntax shape, so these are audit dispositions rather than a predicted post-configuration count.

## Implementation decisions

The recommendations are implemented in the shared `detekt.yml`, loaded with `buildUponDefaultConfig = true` by all five Kotlin projects. Default failure enforcement remains enabled; no baseline was created. The initial report below is retained as an audit trail.

- Scoped path/package exceptions cover tests, Ktor imports, repository method counts, schema widths, and the four intentional grouping/launcher filenames. Required-argument limits and guard-clause settings are adjusted without raising global thresholds.
- Resolver files now live under `src/main/kotlin/org/tuchscherer/viadapp/resolvers/`; the AI file is named `RephraseContentResolver.kt`. Kotlin namespaces and the JVM launcher remain unchanged.
- Business limits/timeouts/dimensions have names, unused test code and the commented-out configuration stub are removed, and idiomatic checks preserve existing messages.
- Server setup is split into plugin installation, focused route methods and operation execution/logging. Serialization reuses one mapper and one result specification. Its final HTTP boundary still logs unexpected failures, but rethrows cancellation. A method-local exception documents the catch-all boundary.
- Schema creation and health queries now live in an injected `DatabaseMaintenanceRepository`, keeping transactions inside repositories. Health checks catch `SQLException`, log the cause and return `false`; programming failures propagate. Integration tests cover schema initialization/reinitialization, healthy, unavailable and unexpected-failure cases.
- The complexity guard validates the document before scoring and delegates invalid documents to Viaduct. Only client-facing GraphQL exceptions delegate during scoring; internal assertions, unexpected exceptions and cancellation propagate. Checking the GraphQL error interface needs a documented local `InstanceOfCheckForException` exception because Kotlin cannot catch interfaces.
- AI operations share one tracing/error adapter that retains causes, propagates cancellation and restores the interrupt flag, including SDK-wrapped control-flow exceptions. Factories preserve lazy model creation and allow failure-path tests. Reachability catches transport I/O failures and always disconnects the HTTP connection.
- Three interleaved-guard methods need local `ReturnCount` exceptions (`QueryComplexityGuard.check`, `QueryFieldComplexityCalculator.calculate`, and `TrendingQueryResolver.resolve`). `PostVisibility` and publishing checks work with the configured guard-clause allowance. Cause-chain control-flow handling is a separate helper, so the AI adapter needs no `ThrowsCount` exception.

The test audit added backend failure-path and HTTP tests for changed behavior, plus repository/startup integration coverage. Frontend behavior and GraphQL operations are unchanged, so existing frontend, query and E2E suites cover those layers. The refactoring audit removed repeated tracing/request-fixture setup, moved SQL out of the factory, reused serialization work, removed dead imports, and handled wrapped control flow. Final validation passed after refactoring:

| Check | Result |
|---|---|
| Full Gradle build | Passed |
| Detekt (five projects) | Zero findings |
| Backend unit/integration tests | 590 passed, none skipped |
| Frontend type-check/lint/unit tests | Passed; 131 unit tests |
| GraphQL API suite | 156 passed |
| Chromium/Firefox/WebKit E2E suite | 414 passed |

The complete suite order was run before the refactoring audit and repeated afterward. A temporary production `MagicNumber` violation caused Detekt to fail as expected and was removed. The build task graph confirms Detekt participates in `check` for all five projects. Resolver namespace checks and whitespace checks passed.

## Recommended policy

- Keep the default configuration as the foundation (`buildUponDefaultConfig = true`) and enforce failures through `check` in every Kotlin project. Use one shared root configuration. Do not create a blanket baseline or set `ignoreFailures`.
- Allow wildcard imports in tests and `io.ktor.*` DSL packages. Keep explicit imports for application-specific production code. Existing Koin wiring has two imports to clean up.
- Keep the 120-character production line limit; exclude tests from it. Long test lines are mainly generated GraphQL builders, mock stubs and descriptive test names. Production calls/prompts can be wrapped without changing content.
- Keep `MagicNumber` for business logic. Exclude the specific database table-definition files, where column names and widths are already meaningful schema declarations. Name the remaining validation, paging, cryptographic and transport constants.
- Exclude entity repository files from `TooManyFunctions`, preserving the default rule elsewhere. The interfaces and implementations group related persistence operations for one entity. Splitting them only to reach an arbitrary method count would add indirection. Reassess this exception if repositories acquire unrelated responsibilities.
- Set `LongParameterList.ignoreDefaultParameters: true`; optional timestamps/status do not make the common call sites hard to use. Keep the default threshold for required arguments.
- Set `ReturnCount.excludeGuardClauses: true` and `ThrowsCount.excludeGuardClauses: true`. Prefer early exits for empty results, auth and not-found checks. If interleaved guards or Elvis throws still trigger the rule, use a documented annotation on the specific method rather than disabling either rule globally.
- Keep package/directory alignment. Move the 17 root resolver files to the directory matching their existing `org.tuchscherer.viadapp.resolvers` package. Viaduct requires the namespace, not the current directory mismatch.
- Keep filename matching, with specific exceptions for `ViaductApplication.kt`, the two table-group files, and `CheckedListFragments.kt`. The launcher filename determines `ViaductApplicationKt`; the fragment object is registered by KSP and must remain. Rename the single AI resolver file.
- Keep exception, dead-code, length and complexity rules. An invalid JWT intentionally returns `null`; rename the catch binding to `expected` or `_`, both already allowed by Detekt. Database health failures need diagnostics. GraphQL HTTP and third-party AI adapters can justify a local catch-all boundary when it preserves the cause and cancellation, but not a project-wide rule exception.

## Behavior-sensitive review

`GraphQLServer.start` is 144 lines and has complexity 19 (defaults: 60 and 15). It installs server plugins and handles GraphQL, GraphiQL, health, AI health, metrics and auth. Extract plugin setup and route responsibilities while preserving existing endpoint behavior; do not raise global thresholds to fit this one method.

`DatabaseFactory.healthCheck` intentionally turns a failed probe into `false`, but drops the exception. Keep that contract, add appropriate diagnostics, and narrow the catch to expected database failures if possible. This needs failure-path coverage if behavior changes.

`QueryComplexityGuard.check` delegates calculator failures to Viaduct validation. A generic catch is not automatically safe: unexpected failures also bypass complexity scoring. Preserve the original cause in diagnostics and identify expected validation exceptions before narrowing it. The parser's separate `catch (_: Exception)` is already allowed by the default rule and is not among the 229 findings; that exemption is not proof the catch is semantically correct.

The three AI operations already log, trace and wrap their causes in `AIServiceException`. These are adapter boundaries, so the findings are not evidence that errors are silently lost. The reachability probe intentionally returns `false`. Before keeping a documented broad catch, check SDK exception types and cancellation/interrupt behavior; the HTTP probe also deserves guaranteed connection cleanup.

`JwtService.verifyToken` returning `null` for `JWTVerificationException` is an expected authentication rejection. Logging every invalid token would add noise and potentially sensitive information; no global exception-rule change is warranted.

## Validation plan

This audit itself changes no runtime behavior. No behavior-mirroring unit tests are needed for rule preferences or file layout. Verify configuration loading, coverage of all five Kotlin projects, `check` task wiring and failure on a representative retained rule. Then run the repository's backend, frontend, query and browser suites in the prescribed order; repeat after refactoring. Add meaningful failure-path tests for any exception-handling behavior changes. At audit time, backend tests passed after plugin addition; full build and Detekt remain red on the initial findings, and the other suites have not yet run for this task.

## Findings by rule

| Rule | Findings | Tune | Fix | Review |
|---|---:|---:|---:|---:|
| WildcardImport | 102 | 100 | 2 | 0 |
| MagicNumber | 37 | 12 | 25 | 0 |
| MaxLineLength | 28 | 25 | 3 | 0 |
| InvalidPackageDeclaration | 17 | 0 | 17 | 0 |
| TooManyFunctions | 9 | 9 | 0 | 0 |
| TooGenericExceptionCaught | 7 | 0 | 0 | 7 |
| MatchingDeclarationName | 5 | 4 | 1 | 0 |
| NewLineAtEndOfFile | 5 | 0 | 5 | 0 |
| ReturnCount | 4 | 4 | 0 | 0 |
| LongParameterList | 2 | 2 | 0 | 0 |
| SwallowedException | 2 | 1 | 0 | 1 |
| UseRequire | 2 | 0 | 2 | 0 |
| UseCheckOrError | 2 | 0 | 2 | 0 |
| UnusedPrivateMember | 2 | 0 | 2 | 0 |
| LongMethod | 1 | 0 | 0 | 1 |
| CyclomaticComplexMethod | 1 | 0 | 0 | 1 |
| ThrowsCount | 1 | 1 | 0 | 0 |
| UnusedPrivateProperty | 1 | 0 | 1 | 0 |
| UnusedParameter | 1 | 0 | 1 | 0 |

## Complete inventory

Paths and line numbers identify the original report, before any cleanup or moves.

### WildcardImport (102)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:7` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:11` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:15` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:16` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:17` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:18` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:19` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:20` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:21` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/config/KoinModules.kt:30` | Fix | Replace the two application-specific Koin wildcard imports with explicit imports. |
| `src/main/kotlin/org/tuchscherer/config/KoinModules.kt:31` | Fix | Replace the two application-specific Koin wildcard imports with explicit imports. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:4` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:6` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:7` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/main/kotlin/org/tuchscherer/web/AuthRoutes.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/CommentRepositoryTest.kt:6` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/CommentRepositoryTest.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/UserRepositoryTest.kt:4` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/UserRepositoryTest.kt:6` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/PostRepositoryTest.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/PostRepositoryTest.kt:7` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/LikeRepositoryTest.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/database/repositories/LikeRepositoryTest.kt:7` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/config/AppConfigTest.kt:4` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/config/KoinModulesTest.kt:6` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/config/KoinModulesTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/auth/PasswordServiceTest.kt:4` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/auth/AuthenticationServiceTest.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/auth/AuthenticationServiceTest.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/auth/JwtServiceTest.kt:11` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/integration/AuthFlowIntegrationTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/integration/BlogWorkflowIntegrationTest.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikePostResolverTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UserResolversTest.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UserResolversTest.kt:15` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminMutationResolversTest.kt:20` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminMutationResolversTest.kt:23` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostsResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostsResolverTest.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostsResolverTest.kt:16` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostsResolverTest.kt:21` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/MyPostsResolverTest.kt:11` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/MyPostsResolverTest.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/MyPostsResolverTest.kt:16` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/MyPostsResolverTest.kt:21` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UnlikePostResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UnlikePostResolverTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UnlikePostResolverTest.kt:18` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/UnlikePostResolverTest.kt:23` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminStatsAnalyticsResolversTest.kt:12` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminQueryResolversTest.kt:21` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminQueryResolversTest.kt:25` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminQueryResolversTest.kt:30` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreatePostResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeleteCommentResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeleteCommentResolverTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeleteCommentResolverTest.kt:18` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeleteCommentResolverTest.kt:23` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeletePostResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeletePostResolverTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeletePostResolverTest.kt:18` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/DeletePostResolverTest.kt:23` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostCommentsResolverTest.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostCommentsResolverTest.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostCommentsResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostCommentsResolverTest.kt:17` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikeFieldResolversTest.kt:14` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikeFieldResolversTest.kt:17` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikeFieldResolversTest.kt:23` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostFieldResolversTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostFieldResolversTest.kt:16` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostFieldResolversTest.kt:21` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostResolverTest.kt:8` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostResolverTest.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostResolverTest.kt:17` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/checkedlist/ViaductPostCreationPortTest.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/checkedlist/ViaductPostSocialAccessTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `src/test/kotlin/org/tuchscherer/analytics/ViaductPostTypeLookupPortTest.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/repositories/CheckedListItemRepositoryTest.kt:6` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/resolvers/CheckedListPostBatchResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/resolvers/CheckedListMutationResolverTest.kt:19` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/resolvers/CheckedListItemBatchResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/resolvers/CheckedListPostFieldResolversTest.kt:15` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/AnalyticsUtilsTest.kt:3` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/repositories/PostViewRepositoryTest.kt:4` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/repositories/PostViewRepositoryTest.kt:5` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/RecordPostViewMutationResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/TrendingQueryResolverTest.kt:13` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/BlogPostViewCountBatchResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/CheckedListPostReadTimeMinutesBatchResolverTest.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/BlogPostReadTimeMinutesBatchResolverTest.kt:9` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/CheckedListPostViewCountBatchResolverTest.kt:10` | Tune | Allow wildcard imports in tests and the Ktor DSL; keep the rule elsewhere. |

### MagicNumber (37)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt:34` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt:35` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt:36` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/CommentResolvers.kt:25` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminQueryResolvers.kt:44` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminQueryResolvers.kt:86` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminQueryResolvers.kt:111` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminMutationResolvers.kt:23` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminMutationResolvers.kt:24` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/resolvers/AiResolvers.kt:24` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:8` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:9` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:10` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:11` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:12` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:38` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:41` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:43` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `src/main/kotlin/org/tuchscherer/auth/PasswordService.kt:18` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/auth/AuthenticationService.kt:20` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/auth/AuthenticationService.kt:22` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `src/main/kotlin/org/tuchscherer/auth/AuthenticationService.kt:24` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/database/CheckedListTables.kt:14` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/database/CheckedListTables.kt:15` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/database/CheckedListTables.kt:19` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListMutationResolvers.kt:35` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListMutationResolvers.kt:144` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListMutationResolvers.kt:206` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListMutationResolvers.kt:211` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:120` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:121` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:125` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:125` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/NoOpAIService.kt:16` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/NoOpAIService.kt:16` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |
| `modules/analytics/src/main/kotlin/org/tuchscherer/analytics/AnalyticsTables.kt:17` | Tune | Exclude database table declarations: the column name and width together are the schema definition. |
| `modules/analytics/src/main/kotlin/org/tuchscherer/viadapp/analytics/resolvers/TrendingQueryResolver.kt:43` | Fix | Name the validation limit, pagination default, salt size, timeout, status range, or embedding dimension; preserve its value. |

### MaxLineLength (28)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:166` | Fix | Wrap the call or concatenate prompt strings without changing their contents. |
| `src/test/kotlin/org/tuchscherer/auth/AuthenticationServiceTest.kt:163` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikePostResolverTest.kt:77` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikePostResolverTest.kt:98` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikePostResolverTest.kt:117` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/LikePostResolverTest.kt:133` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UserResolversTest.kt:146` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/AdminMutationResolversTest.kt:237` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:99` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:116` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:134` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:149` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:164` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/UpdatePostResolverTest.kt:179` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/PostsResolverTest.kt:109` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/NodeResolversTest.kt:57` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/NodeResolversTest.kt:87` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:79` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:106` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:122` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:140` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/CreateCommentResolverTest.kt:157` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `src/test/kotlin/org/tuchscherer/resolvers/RephraseContentResolverTest.kt:129` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:47` | Fix | Wrap the call or concatenate prompt strings without changing their contents. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:70` | Fix | Wrap the call or concatenate prompt strings without changing their contents. |
| `modules/ai/src/test/kotlin/org/tuchscherer/ai/OllamaAIServiceTest.kt:127` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `modules/ai/src/test/kotlin/org/tuchscherer/ai/OllamaAIServiceTest.kt:161` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |
| `modules/analytics/src/test/kotlin/org/tuchscherer/analytics/resolvers/TrendingQueryResolverTest.kt:53` | Tune | Exclude tests from this presentation-only rule; generated GraphQL builder chains and descriptive test names remain readable. |

### InvalidPackageDeclaration (17)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/resolvers/ChecklistAiResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/CommentResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/CommentFieldResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminQueryResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/PostMutationResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/NodeResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/PostValidation.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/PostFieldResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/UserResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/LikeFieldResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/LikeResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/LikeObjectFieldResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/AdminMutationResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/PublishResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/PostQueryResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/AiResolvers.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |
| `src/main/kotlin/org/tuchscherer/resolvers/ResolverUtils.kt:1` | Fix | Move the resolver file into org/tuchscherer/viadapp/resolvers. Keep its Kotlin package unchanged for Viaduct discovery. |

### TooManyFunctions (9)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/database/repositories/CommentRepository.kt:10` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/ExposedCommentRepository.kt:17` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/ExposedLikeRepository.kt:18` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/UserRepository.kt:12` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/PostRepository.kt:11` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/ExposedPostRepository.kt:24` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/ExposedUserRepository.kt:16` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `src/main/kotlin/org/tuchscherer/database/repositories/LikeRepository.kt:10` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/repositories/ExposedCheckedListItemRepository.kt:23` | Tune | Entity-specific repository methods are cohesive CRUD, batching, pagination and administration; scope the exception to repositories, not all classes. |

### TooGenericExceptionCaught (7)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:168` | Review | A final HTTP error boundary is legitimate. Preserve logging and rethrow cancellation; justify a local exception rather than weakening the rule globally. |
| `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt:58` | Review | Narrow to expected database failures where possible; preserve the false-on-unhealthy contract and add diagnostics. |
| `src/main/kotlin/org/tuchscherer/complexity/QueryComplexityGuard.kt:78` | Review | Schema/validation failures may delegate to Viaduct, but broad catches also hide unexpected failures. Distinguish those cases and preserve full diagnostics. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:51` | Review | The AI adapter translates third-party failures with cause and tracing. Narrow known transport/SDK errors or document a local boundary exception; preserve cancellation and interrupt semantics. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:74` | Review | The AI adapter translates third-party failures with cause and tracing. Narrow known transport/SDK errors or document a local boundary exception; preserve cancellation and interrupt semantics. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:95` | Review | The AI adapter translates third-party failures with cause and tracing. Narrow known transport/SDK errors or document a local boundary exception; preserve cancellation and interrupt semantics. |
| `modules/ai/src/main/kotlin/org/tuchscherer/ai/OllamaAIService.kt:126` | Review | The AI adapter translates third-party failures with cause and tracing. Narrow known transport/SDK errors or document a local boundary exception; preserve cancellation and interrupt semantics. |

### MatchingDeclarationName (5)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/resolvers/AiResolvers.kt:12` | Fix | Rename this single-resolver file to RephraseContentResolver.kt. |
| `src/main/kotlin/org/tuchscherer/viadapp/ViaductApplication.kt:15` | Tune | Keep intentional table/fragment grouping or the stable ViaductApplicationKt launcher filename; use scoped file exceptions. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/viadapp/checkedlist/resolvers/CheckedListFragments.kt:32` | Tune | Keep intentional table/fragment grouping or the stable ViaductApplicationKt launcher filename; use scoped file exceptions. |
| `modules/checkedlist/src/main/kotlin/org/tuchscherer/checkedlist/database/CheckedListTables.kt:13` | Tune | Keep intentional table/fragment grouping or the stable ViaductApplicationKt launcher filename; use scoped file exceptions. |
| `modules/analytics/src/main/kotlin/org/tuchscherer/analytics/AnalyticsTables.kt:16` | Tune | Keep intentional table/fragment grouping or the stable ViaductApplicationKt launcher filename; use scoped file exceptions. |

### NewLineAtEndOfFile (5)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/database/Tables.kt:66` | Fix | Add the final newline; remove the entirely commented-out ViaductConfig stub instead of preserving dead code. |
| `src/main/kotlin/org/tuchscherer/database/Models.kt:60` | Fix | Add the final newline; remove the entirely commented-out ViaductConfig stub instead of preserving dead code. |
| `src/main/kotlin/org/tuchscherer/config/ViaductConfig.kt:13` | Fix | Add the final newline; remove the entirely commented-out ViaductConfig stub instead of preserving dead code. |
| `src/main/kotlin/org/tuchscherer/auth/PasswordService.kt:40` | Fix | Add the final newline; remove the entirely commented-out ViaductConfig stub instead of preserving dead code. |
| `src/main/kotlin/org/tuchscherer/auth/AuthContext.kt:5` | Fix | Add the final newline; remove the entirely commented-out ViaductConfig stub instead of preserving dead code. |

### ReturnCount (4)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/complexity/QueryComplexityGuard.kt:54` | Tune | Prefer readable early exits. Try excludeGuardClauses; use a documented local exception if the analyzer does not recognize interleaved guards. |
| `src/main/kotlin/org/tuchscherer/auth/PostVisibility.kt:26` | Tune | Prefer readable early exits. Try excludeGuardClauses; use a documented local exception if the analyzer does not recognize interleaved guards. |
| `src/main/kotlin/org/tuchscherer/complexity/QueryFieldComplexityCalculator.kt:19` | Tune | Prefer readable early exits. Try excludeGuardClauses; use a documented local exception if the analyzer does not recognize interleaved guards. |
| `modules/analytics/src/main/kotlin/org/tuchscherer/viadapp/analytics/resolvers/TrendingQueryResolver.kt:42` | Tune | Prefer readable early exits. Try excludeGuardClauses; use a documented local exception if the analyzer does not recognize interleaved guards. |

### LongParameterList (2)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/database/repositories/UserRepository.kt:41` | Tune | Enable ignoreDefaultParameters: User.create has five required parameters and Post.create has three. |
| `src/main/kotlin/org/tuchscherer/database/repositories/PostRepository.kt:48` | Tune | Enable ignoreDefaultParameters: User.create has five required parameters and Post.create has three. |

### SwallowedException (2)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/database/DatabaseFactory.kt:58` | Review | The health probe should report false, but preserve diagnostic information and decide which failures it should catch. |
| `src/main/kotlin/org/tuchscherer/auth/JwtService.kt:49` | Tune | Invalid JWTs intentionally return null. Name the exception expected or use _: JWTVerificationException; no global suppression. |

### UseRequire (2)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/resolvers/AiResolvers.kt:22` | Fix | Use require or error with the same condition, exception type and message. |
| `src/main/kotlin/org/tuchscherer/resolvers/AiResolvers.kt:25` | Fix | Use require or error with the same condition, exception type and message. |

### UseCheckOrError (2)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/config/AppConfig.kt:74` | Fix | Use require or error with the same condition, exception type and message. |
| `src/main/kotlin/org/tuchscherer/config/AppConfig.kt:92` | Fix | Use require or error with the same condition, exception type and message. |

### UnusedPrivateMember (2)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/test/kotlin/org/tuchscherer/resolvers/UserResolversTest.kt:36` | Fix | Remove the unused test helper, variable, or parameter; retain the tested behavior. |
| `modules/checkedlist/src/test/kotlin/org/tuchscherer/checkedlist/resolvers/CheckedListMutationResolverTest.kt:46` | Fix | Remove the unused test helper, variable, or parameter; retain the tested behavior. |

### LongMethod (1)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:79` | Review | Extract Ktor plugin installation and focused route handlers; keep size and complexity defaults. DSL nesting alone does not explain all mixed responsibilities. |

### CyclomaticComplexMethod (1)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/web/GraphQLServer.kt:79` | Review | Extract Ktor plugin installation and focused route handlers; keep size and complexity defaults. DSL nesting alone does not explain all mixed responsibilities. |

### ThrowsCount (1)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/main/kotlin/org/tuchscherer/resolvers/PublishResolvers.kt:31` | Tune | Not-found and authorization checks have distinct domain outcomes. Try excludeGuardClauses; retain a local exception if Elvis expressions are still counted. |

### UnusedPrivateProperty (1)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/test/kotlin/org/tuchscherer/auth/AuthenticationServiceTest.kt:129` | Fix | Remove the unused test helper, variable, or parameter; retain the tested behavior. |

### UnusedParameter (1)

| Location | Disposition | Rationale / action |
|---|---|---|
| `src/test/kotlin/org/tuchscherer/analytics/ViaductPostTypeLookupPortTest.kt:26` | Fix | Remove the unused test helper, variable, or parameter; retain the tested behavior. |
