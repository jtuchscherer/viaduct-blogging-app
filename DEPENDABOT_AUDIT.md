# Dependabot audit

**Last Updated**: 2026-10-07

Audited all 55 open alerts after PR #65 merged: **2 critical, 24 high, 23 medium,
6 low**. These changes remove the affected dependency versions for **all 26
critical/high alerts**, plus 17 medium and 3 low alerts, without application code
changes or dependency major-version upgrades. These are local fixes; GitHub must
receive the updated default-branch dependency submission after merge before it
can close the Maven alerts. No alerts were dismissed.

## Dependency paths and fixes

GitHub attributes all Maven dependencies to `settings.gradle.kts`; this does not
mean they are build-only. Scope below comes from resolved Gradle configurations,
the submitted SBOM, and the frontend lockfile.

| Dependency | Actual use | Before → after | Fix |
|---|---|---|---|
| Netty | Ktor server runtime | 4.2.16.Final → 4.2.17.Final | Update the existing coordinated Netty overrides. |
| Bouncy Castle | Kotlin publishing-validation tooling on all five Kotlin projects; absent from app runtime | 1.84 → 1.85 | Update bcprov, bcpg, bcpkix, and bcutil together. |
| Jackson 2, application | Ktor JSON/auth, JWT libraries, Flyway, LangChain4j, OpenTelemetry | 2.22.1 → 2.22.3 | Update databind and the Kotlin module; their BOM aligns core. |
| Jackson 2, tooling | Viaduct settings and project plugins → gradle:common → jackson-module-kotlin | 2.17.3 → 2.18.11 | Override the Jackson BOM separately in settings and project buildscript classpaths. Share the version in gradle.properties because the catalog is unavailable during settings-plugin resolution. |
| Jackson 3 | logstash-logback-encoder at runtime | 3.2.1 → 3.2.3 | Replace the single-core force with a BOM aligning core and databind. |
| Apache HttpCore 5 | Ktor test host → Apache client; absent from app runtime | 5.3.6 → 5.4.3 | Constrain httpcore5 and httpcore5-h2 in testImplementation. |
| source-map-js | jsdom/css-tree and Vite/PostCSS development tools | 1.2.1 → 1.2.2 | Update within existing transitive ranges. |
| undici | jsdom development dependency | 7.29.0 → 7.29.1 | Update the existing npm override. |
| browserslist | ESLint React hooks → Babel compilation targets | 4.28.5 → 4.29.3 | Update within the existing range; browser-data dependencies update together. |
| brace-expansion | minimatch in development tools | 5.0.9 → 5.0.12 | Update the existing override; npm audit found two additional high advisories not in GitHub's 55-alert snapshot. Also fixes medium alert #189. |

The Netty critical advisory requires TLS SNI routing with a permissive fallback
SSL context and per-SNI mTLS enforcement. The application's server currently uses
an HTTP connector without that configuration. It is still patched because the
vulnerable library is shipped. Bouncy Castle's critical advisory concerns
certificate Name Constraints validation, and its high advisory concerns ASN.1
depth handling; those packages are confined to Kotlin publishing tooling here.

The Jackson alerts include CPU/memory denial of service and polymorphic type
validation bypasses. Several require special parser entry points, XML types,
identity-enabled collections, or configured polymorphism. Runtime presence alone
does not establish that every exploit is reachable. Upgrading removes the
affected versions regardless of those prerequisites. The logging encoder normally
writes JSON, but its Jackson 3 dependencies are patched as well.

The HttpCore alerts concern excessive HTTP/1 and HTTP/2 headers in the test HTTP
client. The npm high alerts concern malformed source maps, untrusted browser
statistics, dropped TLS callbacks in Undici BalancedPool, and uncontrolled brace
recursion. They are development dependencies, and are patched rather than
suppressed.

## Critical and high alert inventory

“Patched locally” means the final resolved graph/lockfile has no version in the
advisory's affected range. It does not mean the alert is already closed on GitHub.

| Alert | Severity | Package | Advisory | Resolved versions | Result |
|---|---|---|---|---|---|
| [#163](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/163) | critical | `io.netty:netty-handler` | [GHSA-c4c3-7fpv-j4q5](https://github.com/advisories/GHSA-c4c3-7fpv-j4q5) | 4.2.17.Final | Patched locally |
| [#164](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/164) | critical | `org.bouncycastle:bcprov-jdk18on` | [GHSA-9pwp-9qqc-pr26](https://github.com/advisories/GHSA-9pwp-9qqc-pr26) | 1.85 | Patched locally |
| [#113](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/113) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-j3rv-43j4-c7qm](https://github.com/advisories/GHSA-j3rv-43j4-c7qm) | 2.18.11, 2.22.3 | Patched locally |
| [#116](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/116) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-rmj7-2vxq-3g9f](https://github.com/advisories/GHSA-rmj7-2vxq-3g9f) | 2.18.11, 2.22.3 | Patched locally |
| [#149](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/149) | high | `com.fasterxml.jackson.core:jackson-core` | [GHSA-r7wm-3cxj-wff9](https://github.com/advisories/GHSA-r7wm-3cxj-wff9) | 2.18.11, 2.22.3 | Patched locally |
| [#158](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/158) | high | `browserslist` | [GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g) | 4.29.3 | Patched locally |
| [#165](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/165) | high | `org.bouncycastle:bcprov-jdk18on` | [GHSA-qp49-qgx5-5m26](https://github.com/advisories/GHSA-qp49-qgx5-5m26) | 1.85 | Patched locally |
| [#170](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/170) | high | `tools.jackson.core:jackson-databind` | [GHSA-q4xh-88c3-wmh7](https://github.com/advisories/GHSA-q4xh-88c3-wmh7) | 3.2.3 | Patched locally |
| [#171](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/171) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-q4xh-88c3-wmh7](https://github.com/advisories/GHSA-q4xh-88c3-wmh7) | 2.18.11, 2.22.3 | Patched locally |
| [#172](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/172) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-q4xh-88c3-wmh7](https://github.com/advisories/GHSA-q4xh-88c3-wmh7) | 2.18.11, 2.22.3 | Patched locally |
| [#177](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/177) | high | `undici` | [GHSA-w293-vg96-wgc3](https://github.com/advisories/GHSA-w293-vg96-wgc3) | 7.29.1 | Patched locally |
| [#190](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/190) | high | `source-map-js` | [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q) | 1.2.2 | Patched locally |
| [#192](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/192) | high | `org.apache.httpcomponents.core5:httpcore5` | [GHSA-hf6x-8p5f-cgmf](https://github.com/advisories/GHSA-hf6x-8p5f-cgmf) | 5.4.3 | Patched locally |
| [#193](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/193) | high | `org.apache.httpcomponents.core5:httpcore5-h2` | [GHSA-v3jc-474w-2wm6](https://github.com/advisories/GHSA-v3jc-474w-2wm6) | 5.4.3 | Patched locally |
| [#195](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/195) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54) | 2.18.11, 2.22.3 | Patched locally |
| [#196](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/196) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54) | 2.18.11, 2.22.3 | Patched locally |
| [#197](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/197) | high | `tools.jackson.core:jackson-databind` | [GHSA-wv8q-qhhj-9h54](https://github.com/advisories/GHSA-wv8q-qhhj-9h54) | 3.2.3 | Patched locally |
| [#198](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/198) | high | `tools.jackson.core:jackson-databind` | [GHSA-cxp5-3px4-pw24](https://github.com/advisories/GHSA-cxp5-3px4-pw24) | 3.2.3 | Patched locally |
| [#199](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/199) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-cxp5-3px4-pw24](https://github.com/advisories/GHSA-cxp5-3px4-pw24) | 2.18.11, 2.22.3 | Patched locally |
| [#200](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/200) | high | `com.fasterxml.jackson.core:jackson-databind` | [GHSA-cxp5-3px4-pw24](https://github.com/advisories/GHSA-cxp5-3px4-pw24) | 2.18.11, 2.22.3 | Patched locally |
| [#201](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/201) | high | `com.fasterxml.jackson.core:jackson-core` | [GHSA-p6pp-m3f8-5c89](https://github.com/advisories/GHSA-p6pp-m3f8-5c89) | 2.18.11, 2.22.3 | Patched locally |
| [#202](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/202) | high | `com.fasterxml.jackson.core:jackson-core` | [GHSA-p6pp-m3f8-5c89](https://github.com/advisories/GHSA-p6pp-m3f8-5c89) | 2.18.11, 2.22.3 | Patched locally |
| [#203](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/203) | high | `tools.jackson.core:jackson-core` | [GHSA-p6pp-m3f8-5c89](https://github.com/advisories/GHSA-p6pp-m3f8-5c89) | 3.2.3 | Patched locally |
| [#204](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/204) | high | `com.fasterxml.jackson.core:jackson-core` | [GHSA-7hhh-6rmp-j9qf](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf) | 2.18.11, 2.22.3 | Patched locally |
| [#205](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/205) | high | `tools.jackson.core:jackson-core` | [GHSA-7hhh-6rmp-j9qf](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf) | 3.2.3 | Patched locally |
| [#206](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/206) | high | `com.fasterxml.jackson.core:jackson-core` | [GHSA-7hhh-6rmp-j9qf](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf) | 2.18.11, 2.22.3 | Patched locally |

## Medium and low alert inventory

These were reviewed but were not the requested remediation scope. Most are
removed by the same upgrades. Nine remain: six medium and three low alerts.

| Alert | Severity | Package | Summary | Result |
|---|---|---|---|---|
| [#194](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/194) | medium | `org.apache.httpcomponents.client5:httpclient5` | Apache HttpComponents Client: Connection Leak on Content-Encoding Decode Error Leads to Pool Exhaustion DoS | Remaining: 5.5.1 |
| [#191](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/191) | low | `dompurify` | DOMPurify: IN_PLACE returns a force-removed rawtext root whose text carries attacker markup — pure HTML reparse executes | Remaining: 3.4.13 |
| [#189](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/189) | medium | `brace-expansion` | brace-expansion: Quadratic-time expansion of the `{a},b}` rewrite causes CPU denial of service | Patched by these upgrades |
| [#186](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/186) | low | `dompurify` | DOMPurify: IN_PLACE: node-removing afterSanitize hook leaves detached subtree event handlers armed, causing DOM XSS | Remaining: 3.4.13 |
| [#185](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/185) | low | `undici` | undici vulnerable to caching and replay of unsafe HTTP method responses | Patched by these upgrades |
| [#184](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/184) | medium | `undici` | undici vulnerable to cross-user cookie disclosure via Set-Cookie caching in shared caches | Patched by these upgrades |
| [#183](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/183) | low | `undici` | undici vulnerable to downstream response splitting via retry interceptor | Patched by these upgrades |
| [#180](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/180) | low | `undici` | undici vulnerable to response truncation via oversized chunked responses in the dump interceptor | Patched by these upgrades |
| [#179](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/179) | medium | `undici` | undici vulnerable to Denial of Service via orphaned RetryHandler response body | Patched by these upgrades |
| [#175](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/175) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete PolymorphicTypeValidator denylist) | Patched by these upgrades |
| [#174](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/174) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete PolymorphicTypeValidator denylist) | Patched by these upgrades |
| [#173](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/173) | medium | `tools.jackson.core:jackson-databind` | jackson-databind: Comparable missing from DefaultBaseTypeLimitingValidator's unsafe base types (incomplete PolymorphicTypeValidator denylist) | Patched by these upgrades |
| [#169](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/169) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution | Patched by these upgrades |
| [#168](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/168) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution | Patched by these upgrades |
| [#167](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/167) | medium | `tools.jackson.core:jackson-databind` | jackson-databind: Path Deserialization Missing Scheme Allowlist for FileSystemProvider Resolution | Patched by these upgrades |
| [#166](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/166) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: Incomplete fix for CVE-2026-54514: eager DNS resolution (SSRF) still present in InetAddress deserialization | Patched by these upgrades |
| [#162](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/162) | medium | `io.netty:netty-handler` | Netty: Fragmented ClientHello records trigger quadratic pre-handshake reassembly in default SNI parsing | Patched by these upgrades |
| [#161](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/161) | medium | `baseline-browser-mapping` | baseline-browser-mapping process termination on invalid input causes denial of service | Patched by these upgrades |
| [#160](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/160) | medium | `vitest` | Vitest: Path Traversal / Arbitrary File Read via @vitest/mocker Redirect Mock | Remaining: 4.1.10 |
| [#159](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/159) | medium | `@vitest/mocker` | Vitest: Path Traversal / Arbitrary File Read via @vitest/mocker Redirect Mock | Remaining: 4.1.10 |
| [#155](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/155) | medium | `@humanfs/node` | humanfs: Recursive copy follows symlinked files and copies data from outside the source tree | Remaining: 0.16.7 |
| [#154](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/154) | medium | `io.netty:netty-codec-http` | Netty Vulnerable to Cache Poisoning and Information Disclosure via CORS Vary Header Overwrite | Patched by these upgrades |
| [#153](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/153) | medium | `org.jetbrains.kotlin:kotlin-gradle-plugin` | JetBrains Kotlin: Unsafe Deserialization in Kotlin Build Cache Enables Code Execution | Remaining: 2.2.21 |
| [#152](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/152) | medium | `com.fasterxml.jackson.core:jackson-core` | jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition | Patched by these upgrades |
| [#147](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/147) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: @JsonIgnore on a Record property is bypassed with a PropertyNamingStrategy | Patched by these upgrades |
| [#123](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/123) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind has case-insensitive deserialization bypasses per-property @JsonIgnoreProperties | Patched by these upgrades |
| [#119](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/119) | medium | `com.fasterxml.jackson.core:jackson-databind` | jackson-databind: InetSocketAddress deserialization triggers eager DNS resolution (SSRF) | Patched by these upgrades |
| [#4](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/4) | low | `com.google.guava:guava` | Information Disclosure in Guava | Remaining: 31.0.1-jre |
| [#3](https://github.com/jtuchscherer/viaduct-blogging-app/security/dependabot/3) | medium | `com.google.guava:guava` | Guava vulnerable to insecure use of temporary directory | Remaining: 31.0.1-jre |

Remaining alerts are in HttpClient's test dependency (#194), frontend development
tools (#155, #159, #160), DOMPurify (#186, #191), the Kotlin Gradle plugin (#153),
and Guava in Viaduct's settings-plugin/Guice dependency graph (#3, #4). The Kotlin
advisory's first published fix is **2.4.20-Beta1**; adopting it would require a
coordinated Kotlin/KSP upgrade and prerelease tooling, outside this focused change.
The other remaining medium/low dependencies can be handled separately.

Additional npm-audit advisories GHSA-qhr7-859c-m2p7 and GHSA-6j4f-fj2g-mc7p
(high, brace-expansion) are patched by 5.0.12. npm audit now reports **0 critical,
0 high, 5 moderate, 1 low** package findings. Those counts aggregate transitive
parents and multiple advisories, so they differ from GitHub's alert counts. npm
also reports a moderate fflate advisory not in the GitHub snapshot.

## Verification and test audit

The settings-plugin fix was verified with **GitHub Dependency Graph Gradle Plugin
1.4.1**, using the init scripts from the repository's pinned
[gradle/actions commit](https://github.com/gradle/actions/tree/39e147cb9de83bb9910b8ef8bd7fff0ee20fcd6f).
The action's `:ForceDependencyResolutionPlugin_resolveAllDependencies` task
successfully produced a local snapshot of 447 resolved Maven coordinates.
No snapshot was submitted from this branch. The snapshot includes Jackson
**2.18.11** in both plugin graphs, **2.22.3** in the app/AI module, **3.2.3** for
logging, Netty **4.2.17.Final**, Bouncy Castle **1.85**, and HttpCore **5.4.3**;
it contains none of the versions affected by the 26 targeted alerts. This checks
the same collector as CI, addressing the earlier ineffective project-only
Jackson override.

Inspected settings and project buildscript classpaths, runtimeClasspath and
testRuntimeClasspath on every Kotlin project, Kotlin's Bouncy Castle and Swift
Export tooling configurations, and Detekt's classpaths. npm dependency paths
were verified with `npm ls source-map-js browserslist undici brace-expansion`.
All 55 advisory ranges were compared against the collected Maven snapshot and
npm lockfile: 46 have no affected versions remaining; nine medium/low alerts do.

| Test layer | Audit conclusion |
|---|---|
| Backend unit | Existing JWT, resolver, input-validation, and JSON server tests cover owned behavior affected by the upgrades. No new application logic. |
| Backend integration | Existing auth/blog workflows, repositories, and Ktor test-host tests exercise cross-layer compatibility and the upgraded test HTTP client. |
| Frontend unit | Existing 131 tests exercise jsdom and updated development tools; type-check and lint exercise the remaining build tools. |
| API/query | Existing 156 cases exercise the real Netty server, auth JSON, GraphQL payloads, invalid inputs, and authorization failures. |
| Browser E2E | Existing 414 cases across Chromium, Firefox, and WebKit exercise the built frontend and real server. |

No application behavior coverage gaps were found. Dependency graphs and advisory
ranges are checked directly instead of adding tests that merely assert version
strings or copy third-party implementation details.

The Clean Code audit removed an unused Viaduct version local and updated the AI
module's outdated Jackson explanation. Shared plugin versions live in one
property, Jackson families use BOM alignment, and HttpCore constraints apply only
to tests. No application refactoring was needed.

| Verification | First round | After build-file cleanup |
|---|---|---|
| Backend unit/integration | 590 passed, 0 skipped | 590 passed, 0 skipped |
| Frontend type-check, lint, unit | All checks passed; 131 tests | All checks passed; 131 tests |
| API/query | 156 passed | 156 passed |
| Browser E2E | 413 passed first try, 1 passed on retry | 413 passed first try, 1 passed on retry |
| Gradle build and all five Detekt tasks | Not separately run | Passed |
| Frontend production build | Passed | Dependency versions unchanged after cleanup |

Both full suite invocations exited successfully. In each browser round, WebKit's
`checklist post appears on the My Posts page` timed out navigating to `/login`
before the test assertions and then stalled during browser-context teardown. The
fresh-context retry passed in under one second. This matches the browser-process
state issue already documented in `frontend/playwright.config.ts`; retries remain
visible here rather than presenting the runs as entirely free of flakiness.

The affected case also passed **five isolated repetitions with retries disabled**
using fresh browser contexts:

```bash
./e2e.sh --project=webkit --grep 'checklist post appears on the My Posts page' --repeat-each=5 --retries=0
```

No browser assertions failed after retry, and no tests were skipped.
