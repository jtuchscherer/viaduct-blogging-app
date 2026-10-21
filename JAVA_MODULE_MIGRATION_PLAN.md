# Java analytics module migration

Updated October 19, 2026. Status: implemented.

## Why analytics

`:modules:analytics` is the Java Viaduct tenant. The application, core post resolvers,
and `:modules:checkedlist` remain Kotlin. Analytics provides a small but substantive
language boundary: four batched fields extend Kotlin-owned `BlogPost` and
`CheckedListPost`, `trending` returns both types, and `recordPostView` records public
engagement. One GraphQL operation exercises both languages.

Checkedlist would require migrating its CRUD, authorization, nested items, and social
behavior together. The AI module has no tenant schema, while converting shared
utilities alone would not demonstrate Java/Kotlin resolver composition.

## Implemented architecture

```text
Kotlin application and core tenant
  ├── Java analytics tenant (:modules:analytics)
  │     ├── six resolvers and read-time helper
  │     └── repository and post-lookup interfaces
  ├── Kotlin persistence adapter (:modules:analytics-persistence)
  │     └── Exposed table/repository ──> Java analytics interfaces
  ├── Kotlin checkedlist tenant (:modules:checkedlist)
  └── Kotlin shared and AI libraries
```

Tenant production code lives entirely in `modules/analytics/src/main/java`. Its existing
SDL, package names, and repository/lookup method signatures are preserved. The
ordinary Kotlin persistence library contains `AnalyticsTables.kt` and
`ExposedPostViewRepository.kt`; it is registered outside the Viaduct tenant topology.
The `post_views` table, UUID/count columns, and existing view data need no migration.

The root application's `AnalyticsKoinModule.kt` composes Java resolver constructors
and the Kotlin repository. The root also implements `PostTypeLookupPort` and
`PostStatusLookupPort`, keeping post storage and publication checks behind its
repository layer. Analytics and its persistence adapter have no source dependency on
the application. Java calls the existing Kotlin `GlobalIdsKt.decodeGlobalId` utility
rather than duplicating global-ID validation.

## Build and execution

The tenant applies `com.airbnb.viaduct.module-java-gradle-plugin` **3.0.0** and
exports `com.airbnb.viaduct:javaapi-api` at the same version. The application includes
`javaapi-runtime` alongside the Kotlin runtime. The Java plugin wires its annotation
processor, resolver bases, generated types, and resolver registry; there is no manual
annotation-processor dependency. Kotlin and KSP plugins are removed from analytics.

Java analytics uses `viaduct.java.grts`; Kotlin tenants use `viaduct.api.grts`.
Viaduct passes engine data across the boundary. No casts convert one generated type
family into the other. Java `trending` builds references with `ctx.ref(...)` and the
concrete Java post reflection types, then Kotlin node/field resolvers supply the
selected post data. Checklist read time declares `items { text }` as its object
fragment so Kotlin checklist fields supply the nested dependency.

Java batch resolvers return `CompletableFuture<Map<Context, T>>`. Results use the
original contexts as keys in an `IdentityHashMap`; aliases and repeated parents
retain their independent results. Each view-count batch makes one bulk repository
lookup, with missing counts returning zero. Generated bases wrap successful values
in Java field results. Dependency and repository failures preserve error behavior.

Read time retains the 200-word-per-minute heuristic, the 0.5-minute minimum, and
Kotlin's Unicode whitespace trimming semantics. Public view recording still accepts
anonymous users, rejects drafts and missing posts, and preserves malformed-ID errors.
Trending preserves ranking, candidate over-fetching, publication filtering, and
omission of deleted post types. Public/admin schema scopes and frontend behavior
are unchanged by the language migration.

Analytics configures its Java **25** toolchain explicitly. Kotlin projects retain
the shared Java 25 toolchain, Kotlin **2.3.21**, and Gradle **9.3.1**. The new execution
engine and incremental execution remain disabled.

rc.1 Java code generation rejects selective resolver declarations across the central
schema, including Kotlin-owned types. The mixed application therefore keeps BlogPost
nodes non-selective and projects a fixed set of metadata columns. Feed identities and
batched on-demand content still avoid loading bodies for metadata-only requests.
Selection-aware metadata projections require an upstream code-generation fix.

## Mixed-language example

After publishing posts and recording views, run this against `/graphql`:

```graphql
query MixedLanguageShowcase {
  trending(limit: 5) {
    __typename
    id
    title
    ... on BlogPost {
      viewCount
      readTimeMinutes
    }
    ... on CheckedListPost {
      items { text }
      viewCount
      readTimeMinutes
    }
  }
}
```

Java resolves `trending`, `viewCount`, and `readTimeMinutes`. Kotlin resolves ordinary
post fields and checklist items, including item text needed by Java read-time
calculation even when clients omit `items`.

## Testing, analysis, and coverage

The existing analytics resolver/helper tests remain Kotlin and now live in the root
`src/test/kotlin/org/tuchscherer/analytics` tree, where they exercise Java contexts
and future/map results using explicit mocks. The root test classpath includes the
Java plugin's generated-type artifact. Persistence integration tests live in
`modules/analytics-persistence/src/test/kotlin`, including H2 behavior and reopening
an existing SQLite database.

`MixedLanguageAnalyticsIntegrationTest` runs the real Viaduct engine with the
production Koin injector and registry. It covers mixed post references, nested
checklist dependencies and failures, aliases, bulk view-count reads, anonymous
mutations, draft/deleted-post filtering, malformed IDs, and Kotlin admin totals.
Its strict missing-output flag catches incomplete materialization without enabling
the new execution engine or changing production error responses.

```bash
# Focused Java/Kotlin resolver behavior and real-engine composition
./gradlew :test --tests 'org.tuchscherer.analytics.*' \
  --tests org.tuchscherer.integration.MixedLanguageAnalyticsIntegrationTest

# Kotlin persistence behavior, including existing SQLite data
./gradlew :modules:analytics-persistence:test

# Strict Java compiler analysis of handwritten production sources
./gradlew :modules:analytics:lintJava

# Java coverage from Kotlin-hosted tests; generated resolver bases are excluded
./gradlew :modules:analytics:jacocoTestReport

# Clean generation, all backend tests, and Kotlin/Java static analysis
./gradlew clean build
```

`lintJava` uses `-Xlint:all -Werror -proc:none` against already compiled generated
classes, keeping warnings in handwritten code actionable. It is part of analytics'
`check` task. Detekt remains enabled for every Kotlin project, including the
persistence adapter and relocated tests. Java coverage uses the root test execution
data; its report is `modules/analytics/build/reports/jacoco/test/html/index.html`.

The migration's isolated Java 25 validation passed Java generation/compilation,
application and test compilation, strict Java lint, Kotlin analysis, persistence
tests, and real-engine mixed-language tests. Final application acceptance follows
[the upgrade verification notes](VIADUCT_3_UPGRADE.md): run backend tests, frontend
checks, query tests, and browser tests in order, then repeat after the refactoring
audit. The language migration needs no frontend product changes or database migration.

## References

- [Java reference application](https://github.com/airbnb/viaduct/tree/release/v3.0.0/demoapps/starwars-java)
- [Mixed-language execution smoke test](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/gradle-plugins/gradletestapps/multi-project/src/test/kotlin/com/example/execution/multiproject/PluginExecutionSmokeTest.kt)
- [Java batching/object-fragment example](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/demoapps/starwars-java/modules/filmography/src/main/java/com/example/starwars/modules/filmography/characters/resolvers/CharacterFilmCountResolver.java)
- [Java plugin wiring](https://github.com/airbnb/viaduct/blob/3b1a3d0054f27c7d2abf01fbc35946f48e0d327b/gradle-plugins/module-java/src/main/kotlin/viaduct/gradle/ViaductJavaModulePlugin.kt)
- [Published rc.1 Java API](https://repo.maven.apache.org/maven2/com/airbnb/viaduct/javaapi-api/3.0.0-rc.1/)
- [Published rc.1 Java runtime](https://repo.maven.apache.org/maven2/com/airbnb/viaduct/javaapi-runtime/3.0.0-rc.1/)

Source links pin the inspected release-branch commit. The initial implementation
was compiled and executed against the published `3.0.0-rc.1` plugin, API, and runtime.
The app now uses 3.0.0; see [the upgrade notes](VIADUCT_3_UPGRADE.md) for current
compatibility changes and verification.
