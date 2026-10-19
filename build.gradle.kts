// Viaduct brings older Jackson and Guava into separate settings and project plugin graphs.
// Patch both graphs: a project-only override leaves the settings-plugin copy vulnerable.
// Use the Jackson BOM to keep core, databind, annotations, and the Kotlin module aligned.
buildscript {
    val buildToolJacksonVersion: String by project
    val buildToolGuavaVersion: String by project
    configurations.all {
        resolutionStrategy.force(
            "com.fasterxml.jackson:jackson-bom:$buildToolJacksonVersion",
            "com.google.guava:guava:$buildToolGuavaVersion",
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
    implementation(project(":modules:analytics-persistence"))
    implementation(project(":modules:checkedlist"))
    implementation(project(":modules:ai"))
    implementation(project(":modules:shared"))

    implementation(libs.viaduct.api)
    implementation(libs.viaduct.runtime)
    implementation(libs.viaduct.java.runtime)
    // Koin constructs Java resolvers whose generic supertypes refer to Java-generated types.
    // The application plugin supplies those types separately at runtime.
    compileOnly(project(path = ":", configuration = "viaductJavaGRTClasses"))
    // Align every Netty module, including Ktor's HTTP/3 and QUIC dependencies.
    implementation(enforcedPlatform(libs.netty.bom))
    // Koin and AI tracing also bring Ktor modules; keep them on the server's version.
    implementation(platform(libs.ktor.bom))
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
    // Viaduct's fixtures bundle JUnit 5 API classes. Prefer our matching JUnit 6 API,
    // including its nullability annotations, on both compiler and runtime classpaths.
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.viaduct.test.fixtures)
    // Kotlin tests exercise Java resolver contexts as well as Kotlin-generated types.
    testImplementation(project(path = ":", configuration = "viaductJavaGRTClasses"))
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
val jacocoVersion: String = libs.versions.jacoco.get()
allprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
        }
    }
    plugins.withId("jacoco") {
        extensions.configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
            // JaCoCo 0.8.14 supports Java 25 class files for both tests and reports.
            toolVersion = jacocoVersion
        }
    }
    plugins.withId("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
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
