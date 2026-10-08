// Force patched versions in the Gradle plugin classpath.
// The Viaduct plugin pulls in Netty 4.1.x and older Logback as build-time
// dependencies; these show up in GitHub's dependency graph (attributed to
// settings.gradle.kts) and trigger Dependabot alerts even though they are
// only used during the build, not at runtime.
//
// Viaduct also brings Jackson 2.17.3 into both the settings and project plugin classpaths.
// Patch both graphs: a project-only override leaves the settings-plugin copy vulnerable.
// Use the Jackson BOM to keep core, databind, annotations, and the Kotlin module aligned.
buildscript {
    val buildToolJacksonVersion: String by project
    configurations.all {
        resolutionStrategy.force(
            "com.fasterxml.jackson:jackson-bom:$buildToolJacksonVersion",
            "io.netty:netty-codec-http:4.1.132.Final",
            "io.netty:netty-codec-http2:4.1.132.Final",
            "io.netty:netty-codec:4.1.132.Final",
            "io.netty:netty-handler:4.1.132.Final",
            "io.netty:netty-common:4.1.132.Final",
            "io.netty:netty-buffer:4.1.132.Final",
            "io.netty:netty-transport:4.1.132.Final",
            "io.netty:netty-resolver:4.1.132.Final",
            "io.netty:netty-transport-native-epoll:4.1.132.Final",
            "io.netty:netty-transport-native-kqueue:4.1.132.Final",
            "ch.qos.logback:logback-classic:1.5.37",
            "ch.qos.logback:logback-core:1.5.37"
        )
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.viaduct.application)
    alias(libs.plugins.viaduct.module)
    application
    jacoco
}

dependencies {
    implementation(project(":modules:analytics"))
    implementation(project(":modules:checkedlist"))
    implementation(project(":modules:ai"))
    implementation(project(":modules:resolverkit"))

    implementation(libs.viaduct.api)
    implementation(libs.viaduct.runtime)
    implementation("javax.inject:javax.inject:1")
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    // logstash-logback-encoder uses Jackson 3; align its core and databind patches together.
    implementation(platform(libs.jackson3.bom))

    // Database dependencies
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    // HTTP server for auth endpoints
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.cors)

    // JWT for authentication
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)
    // JSON structured logging
    implementation(libs.logstash.logback.encoder)
    // Logback conditional config support
    implementation(libs.janino)

    // Flyway for production schema migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Koin for Dependency Injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Testing
    testImplementation(libs.viaduct.test.fixtures)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotest.property)

    constraints {
        // Ktor's test host brings the Apache client; patch HTTP/1 and HTTP/2 together.
        testImplementation(libs.httpcore5) {
            because("Reject oversized HTTP/1 headers (GHSA-hf6x-8p5f-cgmf)")
        }
        testImplementation(libs.httpcore5.h2) {
            because("Enforce HTTP/2 header limits before SETTINGS ACK (GHSA-v3jc-474w-2wm6)")
        }
    }
}

application {
    mainClass.set("org.tuchscherer.viadapp.ViaductApplicationKt")
}

// Force patched versions across every project. The Kotlin Gradle plugin creates several
// internal tooling configurations on every project regardless of whether we ever exercise
// them, each pulling its own dependencies independent of anything we declare:
//   - swiftExportClasspathResolvable (Kotlin/Native Swift Export worker) transitively
//     depends on opentelemetry-api/-context 1.41.0, CVE fixed in 1.62.0 — unrelated to the
//     :modules:ai/tracy-core occurrence already forced via the opentelemetry-bom platform
//     dependency there (#36).
//   - kotlinBouncyCastleConfiguration ("Bouncy Castle dependencies used internally for
//     library publishing validation tasks. Not used during compilation.", per its own
//     description) transitively depends on bouncycastle 1.80/1.80.2 — the same CVEs
//     patched to 1.85 for the ASN.1 depth and certificate Name Constraints fixes.
// Both are per-project configurations the Kotlin plugin creates on every project, so this
// needs allprojects rather than the root-only configurations.all block below.
val opentelemetryVersion: String = libs.versions.opentelemetry.get()
val bouncyCastleVersion: String = libs.versions.bouncycastle.get()
allprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("detekt.yml"))
        }
    }
    configurations.all {
        resolutionStrategy.force(
            "io.opentelemetry:opentelemetry-api:$opentelemetryVersion",
            "io.opentelemetry:opentelemetry-context:$opentelemetryVersion",
            "org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion",
            "org.bouncycastle:bcpg-jdk18on:$bouncyCastleVersion",
            "org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion",
            "org.bouncycastle:bcutil-jdk18on:$bouncyCastleVersion",
        )
    }
}

// Force patched dependency versions to address CVEs.
val nettyVersion: String = libs.versions.netty.get()
configurations.all {
    resolutionStrategy.force(
        "io.netty:netty-codec-http:$nettyVersion",
        "io.netty:netty-codec-http2:$nettyVersion",
        "io.netty:netty-codec-compression:$nettyVersion",
        "io.netty:netty-codec-base:$nettyVersion",
        "io.netty:netty-codec:$nettyVersion",
        "io.netty:netty-handler:$nettyVersion",
        "io.netty:netty-common:$nettyVersion",
        "io.netty:netty-buffer:$nettyVersion",
        "io.netty:netty-transport:$nettyVersion",
        "io.netty:netty-resolver:$nettyVersion",
        "io.netty:netty-transport-classes-epoll:$nettyVersion",
        "io.netty:netty-transport-classes-kqueue:$nettyVersion",
        "io.netty:netty-transport-native-epoll:$nettyVersion",
        "io.netty:netty-transport-native-kqueue:$nettyVersion"
    )
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    // Exclude generated resolver bases, app entry point, and Viaduct/Koin wiring
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/viadapp/ViaductApplication*",
                    "**/viadapp/resolvers/resolverbases/**",
                    "**/config/KoinTenantCodeInjector*",
                )
            }
        })
    )
}
