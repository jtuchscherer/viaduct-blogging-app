# Viaduct 3.0 release-candidate upgrade

Initial upgrade reviewed October 16, 2026; follow-ups started October 19, 2026.
Current dependencies: **3.0.0-rc.2**, upgraded October 21, 2026.

## rc.2 compatibility update

All Viaduct plugins, APIs, runtimes, and fixtures now use `3.0.0-rc.2`.
Java 25, Kotlin 2.3.21, KSP 2.3.12, and Gradle 9.3.1 are unchanged.

rc.2 fixes the upstream GraphiQL template: it loads Babel and no longer imports
the nonexistent introspection patch. `GraphiQLRoutes.kt` now returns the configured
upstream HTML directly, removing every local HTML rewrite and CDN substitution.
The application still serves the bundled JavaScript, JSX, and favicon resources
referenced by that HTML. CDN dependencies still require Internet access.

Schema and execution-registry loading now require explicit resource indexes;
rc.2 removes the ClassGraph fallback for these resources. Our existing settings
topology and application plugin already generate both indexes, covering core,
Java analytics, and Kotlin checklist tenants. Keep the application plugin and all
three tenants in `includeViaductApplication`; manually assembling a runtime
classpath without these generated indexes now fails at startup. No application
bootstrap API changes are needed.

An installed-distribution smoke test exposed an existing source-tree dependency
in `QueryComplexityGuard`: it read the generated built-in SDL and module source
files from the working directory. The guard now uses `SchemaFactory.fromResources()`
to load the same indexed schema resources as the engine, including built-ins.
Its integration tests use that loader too. A regression runs the guard in a fresh
JVM from an empty temporary directory, and mixed-language field tests ensure
complexity limits cover both Java analytics and Kotlin checklist fields.

The installed-distribution check verifies exactly one schema index and one
execution-registry index, with all three tenant resources present exactly once.
It starts the launcher outside the checkout with a temporary SQLite database and
exercises registration, blog/checklist creation, Java read-time and view-count
fields, view recording, trending, and the bundled GraphiQL assets.

The test-fixtures JAR no longer bundles JUnit classes. The root no longer needs
the rc.1 workaround that put an explicit JUnit API dependency before the fixtures.
All projects with tests use `testImplementation(libs.junit.jupiter)` for the API,
parameterized-test support, and engine, plus
`testRuntimeOnly(libs.junit.platform.launcher)` for Gradle's launcher. Separate API
and engine declarations were redundant because the Jupiter aggregate provides
them transitively. One catalog version aligns Jupiter and Platform on JUnit 6.1.3.

Both full rc.2 verification passes on October 21, 2026 passed in the required
order: 677 backend tests with no skips, frontend type-check/lint and 131 unit
tests, 158 query checks, and 420 browser tests with retries disabled. The code
audit removed the old filesystem loader and its hardcoded module list; no further
refactoring was needed. Gradle build/static analysis also passed. The public
schema matches all 159 snapshot entries. After launching `start.sh`, GraphiQL
executed its default query and decoded a BlogPost ID at the normal port 8080 URL.
The installed-distribution smoke test passed; a container image was not run.

The sections below record the initial rc.1 migration and its verification history.

## Initial rc.1 migration

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

The analytics tenant now uses Java, alongside Kotlin core and checklist tenants. It applies
the Java module plugin and `javaapi-api`; the application adds `javaapi-runtime`, all at rc.1.
Kotlin persistence remains in a separate ordinary library. The implemented boundaries and
mixed-language query are described in [JAVA_MODULE_MIGRATION_PLAN.md](JAVA_MODULE_MIGRATION_PLAN.md).

## Java 25 toolchain

All six backend projects target Java 25 for compilation, tests, and application execution.
The five Kotlin projects share `jvmToolchain(25)`; Java analytics declares its toolchain explicitly. Kotlin 2.3 supplies the JVM 25 bytecode target;
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
| Implemented | Resolver output diagnostics | `withResolverErrorReporter(...)` reports incomplete output by schema coordinates. Client-visible missing-field errors stay disabled. Real-engine tests exercise both default behavior and the strict test flag. |
| Partially implemented; rc.1 limitation | Selective loading | Feed roots return identities, node queries project metadata without bodies, and a separate batched content field loads bodies on demand for clients and analytics. Selection-aware metadata via `isSelective` is blocked by Java code generation in rc.1. Draft visibility, stable ordering, and batching are covered. |
| Implemented | Java analytics tenant | Six Java resolvers compose with Kotlin post nodes and nested checklist items. Existing analytics data stays in the Kotlin persistence adapter; real-engine tests cover both language directions and production Koin wiring. |
| Implemented | Root-field references | `admin.post` delegates to the existing `Query.post` factory through `ctx.ref(Query.post { ... })`. The opt-in is local to that resolver; the default engine and feature flags are unchanged. The API remains experimental. |

The scope follow-up removes the separate Kotlin scope map and regex-based vocabulary test.
Three manifest tests pin the served views, generated routing ids, and rejection of unknown
schema ids. In an isolated build, a probe using `@scope(to: ["publicc"])` failed schema
assembly with an invalid-scope diagnostic; the unmodified schema assembled successfully.

The diagnostic reporter logs only the missing field's parent type and name. It does not
log request context, returned data, or error details. Five tests verify omitted nullable
output, strict test-mode errors and paths, explicit nulls, unselected fields, and safe logging.
An omitted selected nullable field continues to resolve to null without a new client error
under the production flags. The experimental execution engine remains disabled.

The root-reference follow-up shares post lookup and object construction between admin and
public queries. Admin request identity reaches the referenced lookup, preserving access to
other authors' drafts. Six real-engine tests cover scalar/nested selections, missing posts,
aliased draft access, public-schema rejection, and errors at the original admin field path.
They replace two unit cases whose fixture cannot materialize lazy references. Explicit Unit
return types also activate ten previously undiscovered exception/overflow tests in the
touched admin test class; all twenty remaining admin unit tests pass.

Post projections keep large bodies out of feed and metadata node reads. Author lookup
selects only identities; feed ordering includes an id tie-breaker. Content has a separate
batched field resolver that serves both client selections and analytics required selections.
SQL and real-engine tests cover pagination, drafts, analytics, and content returned after
create, author update, and admin update. Direct object tests assert node-owned fields;
content assertions run through its field resolver and persisted responses. A feed that
requests content performs identity, metadata, and content reads in batches; this reduces
body I/O for metadata-only requests but adds round trips for content-heavy previews.

The initial Kotlin selective implementation passed focused tests, but combining it with Java
analytics exposed an rc.1 code-generation limitation: the Java generator validates every
schema type and rejects `BlogPost @resolver(isSelective: true)` even when Kotlin owns it.
Its CLI does not expose an owning-module filter or a way to skip this resolver validation.
The mixed-language app therefore uses a fixed metadata projection excluding content.
Selection-aware metadata loading must wait for an upstream fix; the application does not
replace plugin internals or alter generated schema metadata to bypass validation.

The upgrade also inherits resource indexing/startup improvements, tenant metadata/topology
validation, materialization and duplicate-selection fixes, polymorphic-query heap protections,
and DataLoader cache-key fixes described in the changelog. Those benefits require no new
application feature flags.

Keep the new execution engine and `@defer` disabled: the changelog describes them as
incomplete and unsuitable for production use. Remote JVM execution has no immediate
application requirement. Java analytics now demonstrates mixed tenants as described in
the linked migration document. No new application query/parser cache is announced.

## Verification scope

### Initial rc.1 GraphiQL compatibility repair

The rc.1 GraphiQL HTML requires `/js/jsx-loader.js`, `/js/global-id-plugin.jsx`,
and favicons from the runtime JAR; the application now serves those resources.
The template also imports `/js/introspection-patch.js`, which is absent from the
published JAR, and its JSX loader calls Babel without loading it. The same template
defects are present in the previous 2.0 runtime; they are not new engine behavior.
The [Star Wars rc.1 controller](https://github.com/viaduct-dev/starwars/blob/rc/v3.0.0-rc.1/src/main/kotlin/com/example/starwars/service/graphiql/StaticJsController.kt)
serves the two bundled JavaScript files but does not address those template defects.

The rc.1 version of `GraphiQLRoutes.kt` kept the configured upstream HTML, loaded a
pinned Babel build, and replaced the missing introspection-patch dependency with
the standard fetcher.
Our real introspection response builds a valid GraphQL client schema without the patch.
GraphiQL, its React components, toolkit, and explorer use the CDN versions verified
together. The rc.2 update above removes these compatibility substitutions.
GraphiQL still requires Internet access for its CDN dependencies.

Route tests check the configured HTML, every referenced local asset, JavaScript MIME
types, and missing-resource responses. Browser tests load the real schema, execute
the default blogging query, and round-trip a BlogPost ID through the Global ID utility
in Chromium, Firefox, and WebKit. CI runs the Chromium GraphiQL smoke tests against
its existing backend server. No business schema or frontend application behavior
changes in this repair.

Both complete GraphiQL-repair verification passes on October 19, 2026 passed in
the required order: 675 backend tests, frontend type-check/lint and 131 unit tests,
158 query checks, and 420 browser tests with retries disabled. The code audit found
no further refactoring needed after extracting the GraphiQL routes from the server.
The final page was also checked separately after launching `start.sh` on port 8080.

### Upgrade and module follow-ups

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
verification pass. Both combined follow-up passes completed on Java 25 on October 19, 2026.
The first regenerated Kotlin and Java tenants from clean output; the second followed the
classpath, unused-dependency, and projection-mapping cleanup. All suites were green in order.

| Check | Final result |
| --- | --- |
| Backend unit/integration tests, including all modules | 673 passed on each pass; no failures, errors, or skips |
| Frontend type-check and ESLint | Passed twice |
| Frontend unit tests | 131 passed on each pass |
| GraphQL query checks | 158 passed on each pass |
| Playwright: Chromium, Firefox, WebKit | 414 passed on each pass with retries disabled |
| Public GraphQL schema snapshot | Matches all 159 entries |
| Java analytics coverage | 80/80 handwritten lines; 36/36 branches |
| Application bytecode | All six backend projects emit Java 25 class files (major version 69) |
| Gradle build/static analysis, shell syntax, diff whitespace | Passed |

The analytics migration retains the existing helper/resolver tests, adding Java batch,
Unicode, and mixed-language execution cases. Kotlin persistence tests include H2 and an
existing SQLite analytics table. No frontend API or workflow changed; the existing frontend,
query, and browser suites exercise both languages through production routing and injection.
Java compiler lint treats handwritten-source warnings as errors; generated resolver bases
are excluded from Java coverage and that separate lint pass.

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
