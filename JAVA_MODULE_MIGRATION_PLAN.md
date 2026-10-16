# Java module migration plan

Reviewed October 16, 2026. Status: proposal; no Java migration implemented.

## Recommendation

Migrate **`:modules:analytics`** to a Java Viaduct tenant. Keep the application,
core post resolvers, and `:modules:checkedlist` in Kotlin.

Analytics has six resolvers and a bounded concern. Its four batched fields extend
Kotlin-owned `BlogPost` and `CheckedListPost`; `trending` returns both post types;
`recordPostView` adds a mutation. A single GraphQL operation can therefore demonstrate
Java analytics and Kotlin post resolution working together. Existing repository and
lookup interfaces provide useful language boundaries.

| Candidate | Assessment |
| --- | --- |
| **analytics** | Best first migration: real Viaduct schema ownership, batching, cross-module field dependencies, a query, and a mutation. About 435 lines across 13 production Kotlin files. |
| checkedlist | A useful later candidate, but about 996 production lines with CRUD, authorization, nested items, node construction, and social behavior. More contracts to preserve. |
| ai | Natural Java library dependencies, but currently no tenant schema or resolvers. Converting it would not demonstrate Java Viaduct modules. |
| shared | Small, but its node helpers expose Kotlin Viaduct types. Limited showcase value and broader coupling. |
| Root application | Hosting, authentication, persistence composition, and the core tenant make this too broad for the first migration. |

## Proposed boundary

Make analytics' production sources Java: the six resolvers, read-time helper,
`PostViewRepository`, `PostStatusLookupPort`, and `PostTypeLookupPort`/`PostKind`.
Preserve package names and interface signatures where practical. Keep its SDL unchanged.

Retain Exposed persistence in Kotlin. Prefer a small, ordinary
**`:modules:analytics-persistence`** library containing `AnalyticsTables.kt`,
`ExposedPostViewRepository.kt`, and their H2 integration tests. This preserves the
existing analytics boundary and makes the language split explicit. Register this
library with ordinary Gradle `include`, outside the Viaduct tenant topology.

```text
Kotlin application
  ├── Java analytics tenant (schema, resolvers, repository/lookup contracts)
  ├── Kotlin analytics-persistence ──> Java analytics contracts
  ├── Kotlin checkedlist tenant
  └── Kotlin core tenant and application adapters
```

Keep Koin composition in the root Kotlin application. Move `analyticsKoinModule`
there and register the Kotlin repository plus Java resolver constructors explicitly.
The root already supplies both lookup ports and consumes `PostViewRepository` for
admin statistics. Neither analytics nor its persistence adapter may depend on root.

Moving the two persistence classes/tests into the root repository layer is a smaller
alternative if avoiding another Gradle module is preferred. It has the same dependency
direction, but weakens the current analytics packaging. Replacing Exposed with JDBC
would add a separate persistence change and is unnecessary for this showcase.

The existing Kotlin `shared` library can remain a dependency: Java can call the
existing `GlobalIdsKt.decodeGlobalId` static method. Preserve its decoding/error
behavior rather than duplicating it or migrating unrelated shared helpers.

## Evidence and compatibility gates

The published **3.0.0-rc.1** Java API/runtime binaries were inspected with `javap`.
Their source JARs are empty. The binaries contain object selection fragments,
batch resolution, `ref(GlobalID<T>)`, `globalIDFor(Type<T>, String)`, `Type.ofClass`,
Java `FieldValue` success/error factories, and a Java executor using the shared
service `CodeInjector`. These establish the API building blocks; application
interoperability still needs an executable proof.

| Area | Required proof before completing the migration |
| --- | --- |
| Generated types | Java resolvers use `viaduct.java.grts`; Kotlin resolvers continue using `viaduct.api.grts`. They communicate through Viaduct engine data, not casts between generated classes. |
| Cross-language node references | Java `trending` returns both concrete post types; Kotlin node/field resolvers and Java analytics fields resolve in the same operation. |
| Nested dependencies | Java checklist read time reads `items { text }` supplied through Kotlin resolvers. Check generated getters, missing-field errors, and nullability behavior. |
| Batching | Generated Java examples use `CompletableFuture<Map<Context, T>>`, rather than Kotlin's `List<FieldValue<T>>`. Key results by original context, preferably using `IdentityHashMap`; preserve one bulk count lookup and independent duplicate-parent results. Verify field-error behavior against the generated rc.1 base classes. |
| DI | Existing `KoinTenantCodeInjector` resolves Java constructor dependencies through real Viaduct startup and execution. A separate DI framework is unnecessary. |
| Plugin/runtime discovery | Exact rc.1 Java plugin generates Java bases, GRT dependencies, annotation-processor metadata, and the execution registry alongside the Kotlin modules. Each analytics coordinate has one owner, with no stale Kotlin registry. |

Upstream's inspected release commit includes a mixed Kotlin/Java execution smoke
test and a Java batching resolver with an object fragment. The exact published
Java plugin binary has not been inspected or exercised in this repository.

## Implementation sequence

1. **Prove the runtime boundary first.** On an implementation branch, use a
   temporary Java probe tenant with distinct test field names so it cannot duplicate
   analytics' existing coordinates. Exercise a batch on Kotlin-owned posts, nested
   checklist item text, mixed node references, and Koin injection through real
   GraphQL execution. Check clean code generation and registry discovery. Remove
   the probe when the production conversion replaces it. If a required gate fails,
   investigate before translating the full module.
2. **Extract persistence without changing behavior.** Move the Exposed table,
   repository implementation, H2 helper, and repository tests into the ordinary
   Kotlin adapter library. Preserve `post_views`, column types, initialization,
   transactions, increment behavior, and ordering. Establish root Koin wiring.
3. **Convert analytics as one tenant.** Change its contracts and pure estimation
   helper to Java, then all six resolver implementations. Switch the tenant plugin
   and remove its Kotlin/KSP configuration together; a partial plugin switch must
   not strand the remaining Kotlin resolvers. Preserve object fragments selecting
   `id`, `content`, and `items { text }`. Use constructor injection and generated
   Java types; adapt futures and batching explicitly.
   Before removing the Kotlin plugin, port `src/test/kotlin` tests to Java or move
   retained Kotlin tests into a Kotlin project so they continue to compile and run.
4. **Finish build and documentation.** Add the Java plugin/API aliases at
   `3.0.0-rc.1`, with `api("com.airbnb.viaduct:javaapi-api:3.0.0-rc.1")` in
   analytics and `implementation("com.airbnb.viaduct:javaapi-runtime:3.0.0-rc.1")`
   in the application. The plugin is
   `com.airbnb.viaduct.module-java-gradle-plugin`; it wires Viaduct's annotation
   processor itself. Keep analytics' existing topology/package registration and
   Kotlin runtime for the other tenants. Retain Java 25, Kotlin 2.3.21, and Gradle
   9.3.1. Configure the Java tenant's Java 25 toolchain explicitly after removing
   Kotlin; the current shared convention is registered through the Kotlin plugin.
   Remove analytics-only unused plugins/dependencies, check Java compilation
   and analysis coverage, and document an example mixed-language query.
5. **Audit and verify.** Complete the acceptance checks below and the repository's
   full test/refactoring sequence. Keep the language migration separate from
   optional scopes, engine flags, new features, and persistence redesign.

## Acceptance checks

Preserve existing GraphQL signatures, public/admin scopes, stored data, and frontend
behavior. Port existing analytics resolver/helper tests to the Java implementation;
Kotlin tests may call Java classes during transition. Do not assume Kotlin Viaduct
test fixtures accept Java execution contexts. Use explicit stubs for unit behavior
and real engine integration tests for the language boundary.

- **View counts:** both post types, multiple parents, repeated parents/aliases,
  absent rows returning zero, empty batches, and failure propagation. Maintain a
  dedicated batch integration check so accidental per-parent database reads are detected.
- **Read time:** blog content and checklist text; empty/whitespace content retains
  the 0.5-minute minimum, and 400 words yield 2.0 minutes. Exercise nested Kotlin
  fields through the Java object fragment, including dependency errors.
- **View mutation:** anonymous published-post recording succeeds, drafts and
  missing posts return false, malformed IDs retain their existing error contract,
  and admin totals reflect successful increments.
- **Trending:** default/explicit limits, no candidates, ordering, mixed post types,
  candidate over-fetching, draft filtering, and omission of deleted/unknown types.
  Resolve ordinary post fields and Java analytics fields in the same request.
- **Persistence:** retain H2 repository integration tests and verify existing
  SQLite data remains usable without a database migration.
- **Contract/browser coverage:** public/admin introspection stays equivalent;
  existing analytics queries, draft visibility, trending, and browser workflows pass.
  Audit backend unit/integration, frontend unit, E2E, and query coverage and add
  missing cases. A language-only migration needs no frontend product changes.

Run `./gradlew test`, `cd frontend && bash test.sh`, `./query-tests.sh`, and
`./e2e.sh` in that order. After the required refactoring audit, repeat all four.
Also build from clean generated output to catch stale Kotlin metadata or classes.

Keep this as a focused, reversible change after the rc.1 upgrade. With SDL and
database format preserved, reverting the Java conversion restores the Kotlin
tenant without a data migration.

## References and planning validation

- [Java reference application](https://github.com/airbnb/viaduct/tree/release/v3.0.0/demoapps/starwars-java)
- [Mixed-language execution smoke test](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/gradle-plugins/gradletestapps/multi-project/src/test/kotlin/com/example/execution/multiproject/PluginExecutionSmokeTest.kt)
- [Java batching/object-fragment example](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/demoapps/starwars-java/modules/filmography/src/main/java/com/example/starwars/modules/filmography/characters/resolvers/CharacterFilmCountResolver.java)
- [Java module build](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/demoapps/starwars-java/modules/filmography/build.gradle.kts)
- [Java plugin: processor and generated-type wiring](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/gradle-plugins/module-java/src/main/kotlin/viaduct/gradle/ViaductJavaModulePlugin.kt)
- [Published rc.1 Java API](https://repo.maven.apache.org/maven2/com/airbnb/viaduct/javaapi-api/3.0.0-rc.1/)
- [Published rc.1 Java runtime](https://repo.maven.apache.org/maven2/com/airbnb/viaduct/javaapi-runtime/3.0.0-rc.1/)

Source links pin the inspected release-branch commit; they are not a verified rc.1
tag. Planning compared local module boundaries and existing tests with these
sources and exact published API/runtime signatures. No Java migration has been
compiled or tested. The completed Kotlin rc.1 upgrade and its validation results
are recorded in [VIADUCT_3_UPGRADE.md](VIADUCT_3_UPGRADE.md).
