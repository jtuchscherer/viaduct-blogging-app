# Viaduct 3.0.0-rc.1 upgrade

Upgrade reviewed October 16, 2026; follow-ups started October 19, 2026.

## Applied migration

- Align the settings plugin, application/module plugins, API, runtime, and test fixtures on
  `3.0.0-rc.1` through `gradle.properties` and `gradle/libs.versions.toml`.
- Target Java 25 with Kotlin 2.3.21, KSP 2.3.12, and Gradle 9.3.1. The initial
  compatibility upgrade retained Java 21; the subsequent Java 25 change is described below.
- Replace removed `ctx.nodeRef(id)` calls with `ctx.ref(id)`.
- Use generated `getXOrThrow()` accessors where the application depends on a field being
  available. In 3.0, `getX()` returns null for unavailable or failed fields; `getXOrThrow()`
  retains the previous access/error behavior, including genuine null values on nullable fields.
- Update mutation test contexts for the removed `queryValue` argument. Prefer the app's
  explicit JUnit 6 API dependency before Viaduct's fixtures, whose rc.1 JAR also bundles
  JUnit 5 classes; this prevents mixed JUnit nullability metadata during Kotlin compilation.
- Load the public/admin views through `SchemaScopeInfo.fromResources()` from the manifest
  generated from `src/main/viaduct/scopes.yaml`. The initial compatibility upgrade retained
  manual declarations; the scope follow-up below adds build-time validation. Runtime query
  tests verify both public/admin introspection and admin operation rejection.

The application remains Kotlin. The Java plugin and `javaapi-*` dependencies mentioned in
the announcement apply when adding Java tenant modules. The proposed analytics migration
for a mixed Java/Kotlin showcase is described in [JAVA_MODULE_MIGRATION_PLAN.md](JAVA_MODULE_MIGRATION_PLAN.md).

## Java 25 toolchain

All five backend projects use a shared `jvmToolchain(25)` for Kotlin/Java compilation,
tests, and application execution. Kotlin 2.3 supplies the JVM 25 bytecode target;
Gradle 9.3.1 supports running on Java 25. The wrapper distribution is pinned with its
official SHA-256, and the wrapper scripts/JAR are regenerated together.

JaCoCo 0.8.14 is pinned across projects for Java 25 instrumentation/reporting.
Detekt moves to `dev.detekt` 2.0.0-alpha.3, whose tested toolchain matches Kotlin
2.3.21, Gradle 9.3.1, and JDK 25. There is no stable Detekt 2 release; the previous
1.23.8 analyzer embeds a compiler that rejects Java 25. Existing production/test
source coverage and the shared configuration remain enabled.

GitHub Actions provisions Temurin 25 in both backend jobs, and the runtime image
uses `eclipse-temurin:25-jre-alpine`. The published tag supports Linux amd64 and
arm64. Local verification uses checksum-verified Temurin 25.0.4.1; select your JDK
25 through `JAVA_HOME` as described in the README.

The test audit found no new API behavior requiring new product workflows: existing
backend, frontend, query, and browser suites exercise the runtime change. The new
analyzer identified duplicated post-author checks; extracting the shared guard
preserves authorization and error messages. A regression test also covers a post
disappearing during update, using strict mocks. Clean code generation, application
class-file versions, coverage reporting, and static analysis are additional
compatibility checks.

Review also found six existing exception tests in `UpdatePostResolverTest` whose
inferred return types prevented JUnit discovery. Explicit `runBlocking<Unit>`
return types make all eight tests in that class execute. Removed setup consisted
of unused mock property stubs and unused global Koin initialization; the resolver
receives its repository directly, and its successful result uses a separate
updated-post mock.

The Java 25 browser rerun exposed an intermittent WebKit failure during unnecessary
UI setup in the home-feed test. That test now creates its post through the API
fixture, following the repository's test convention. The dedicated create-post UI
test continues to exercise form submission and navigation.

## Recommended follow-ups

The compatibility upgrade retained existing behavior. Follow-up status is recorded below.

| Priority | Feature | Application benefit and prerequisites |
| --- | --- | --- |
| Implemented | `scopes.yaml` and `SchemaScopeInfo.fromResources()` | The manifest is the single source of truth for vocabulary and served views. Viaduct validates it at build time; registration and routing load the generated resource. Public = `[public]`, admin = `[public, admin]`. |
| Small development improvement | Resolver output diagnostics | Configure `withResolverErrorReporter(...)` to report `ResolverOutputMissingFieldException`. Consider `ENABLE_RESOLVER_OUTPUT_MISSING_FIELD_ERRORS` in tests to expose incomplete generated-object construction in mapper helpers and module boundaries. Test this explicitly before changing client-visible error behavior. |
| Larger performance project | Selective Kotlin resolvers | Use `@resolver(isSelective: true)`, `ctx.ownedSelections()`, and selected field coordinates to request only needed data. Add repository projections first: current Exposed DAO queries load full rows, and feed previews request content, limiting immediate savings. Preserve draft visibility, ordering, and batching. |
| As module composition grows | Root-field references | Generated calls through `ctx.ref(...)` can compose future cross-module factories. Current node references already cover this app's use cases. Root-field references remain experimental. |

The scope follow-up removes the separate Kotlin scope map and regex-based vocabulary test.
Three manifest tests pin the served views, generated routing ids, and rejection of unknown
schema ids. In an isolated build, a probe using `@scope(to: ["publicc"])` failed schema
assembly with an invalid-scope diagnostic; the unmodified schema assembled successfully.

The upgrade also inherits resource indexing/startup improvements, tenant metadata/topology
validation, materialization and duplicate-selection fixes, polymorphic-query heap protections,
and DataLoader cache-key fixes described in the changelog. Those benefits require no new
application feature flags.

Keep the new execution engine and `@defer` disabled: the changelog describes them as
incomplete and unsuitable for production use. Remote JVM execution has no immediate
application requirement. Java tenants are an optional showcase follow-up in the
linked migration plan. No new application query/parser cache is announced.

## Verification scope

The test audit covers backend resolver/auth units, repository and workflow integrations,
frontend units, real GraphQL queries, and browser workflows. Existing coverage exercises
node references, batching, polymorphic posts, analytics, pagination, draft visibility, and
authentication. Public/admin schema introspection checks were added for the engine upgrade.
The author-reference unit test now asserts the returned author and missing-post error instead
of swallowing exceptions and checking repository calls. Empty-page coverage now rejects a
null edge list. Publish/unpublish browser checks wait for the completed status transition
before navigating, avoiding cancellation of an in-flight mutation.

The required verification sequence is `./gradlew test`, `cd frontend && bash test.sh`,
`./query-tests.sh`, and `./e2e.sh`, followed by a code/refactoring audit and a second complete
verification pass. All suites passed on Java 25 before and after the audit. After correcting
the home-feed test setup, the final complete browser run passed with retries disabled:

| Check | Final result |
| --- | --- |
| Backend unit/integration tests, including all modules | 597 passed; no failures, errors, or skips |
| Frontend type-check and ESLint | Passed |
| Frontend unit tests | 131 passed |
| GraphQL query checks | 158 passed |
| Playwright: Chromium, Firefox, WebKit | 414 passed with retries disabled |
| Isolated home-feed test, repeated three times per browser | 9 passed with retries disabled |
| Application bytecode | All five backend projects emit Java 25 class files (major version 69) |
| Gradle build/static analysis, shell syntax, diff whitespace | Passed |

Validation included regenerating the backend and all tenant modules from clean build output.
The runtime container tag was verified as published; a container build/start was not part
of this local verification.

## Sources

- [Release changelog](https://slate.airbnb.tools/Hmw308IHKU/Changelog+for+v3.0.0+release).
- [Kotlin JVM/Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html).
- [Gradle Java compatibility](https://docs.gradle.org/9.3.1/userguide/compatibility.html).
- [Detekt 2.0.0-alpha.3 compatibility](https://detekt.dev/docs/2.0.0-alpha.3/introduction/compatibility/).
- [JaCoCo Java version support](https://www.jacoco.org/jacoco/trunk/doc/changes.html).
- [Scope manifest example](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/demoapps/starwars/src/main/viaduct/scopes.yaml).
- [Selective resolvers](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/docs/docs/docs/developers/resolvers/selective_resolution.md).
- [Root-field references](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/docs/docs/docs/developers/resolvers/root_field_references.md).
- [Feature flags](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/core/service/api/src/main/kotlin/viaduct/service/api/spi/FlagManager.kt).

Relevant API signatures were also checked against the published rc.1 artifacts. The source
links pin the inspected release-branch commit; they are not a public rc.1 tag.
