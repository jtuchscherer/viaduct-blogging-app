// Force patched versions in the Gradle plugin classpath.
// The Viaduct plugin pulls in Netty 4.1.x and older Logback as build-time
// dependencies; these show up in GitHub's dependency graph (attributed to
// settings.gradle.kts) and trigger Dependabot alerts even though they are
// only used during the build, not at runtime.
buildscript {
    configurations.all {
        resolutionStrategy.force(
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
            "ch.qos.logback:logback-classic:1.5.32",
            "ch.qos.logback:logback-core:1.5.32"
        )
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.viaduct.application)
    alias(libs.plugins.viaduct.module)
    application
    jacoco
}

viaductApplication {
    modulePackagePrefix.set("org.tuchscherer.viadapp")
}

viaductModule {
    modulePackageSuffix.set("resolvers")
}

dependencies {
    implementation(libs.viaduct.tenant.api)
    implementation(libs.viaduct.service.api)
    implementation(libs.viaduct.service.wiring)
    implementation("javax.inject:javax.inject:1")
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    // Database dependencies
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
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

    // Koin for Dependency Injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Testing
    testImplementation(testFixtures(libs.viaduct.tenant.runtime))
    testImplementation(testFixtures(libs.viaduct.tenant.api))
    testImplementation(libs.viaduct.engine.wiring)
    testImplementation(libs.viaduct.engine.runtime)
    testImplementation(libs.viaduct.engine.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.h2)
    testImplementation(libs.assertj.core)
}

application {
    mainClass.set("org.tuchscherer.viadapp.ViaductApplicationKt")
}

// Force Netty to a patched version to address CVEs (CRLF injection, HTTP request smuggling).
// Also force the four internal Viaduct artifacts that tenant-api:0.29.0's testFixturesApiElements
// variant incorrectly declares as version "INCLUDED" (published module metadata bug). These four
// lines can be removed once Viaduct ships a fix.
val viaductVersion: String = libs.versions.viaduct.get()
val nettyVersion: String = libs.versions.netty.get()
configurations.all {
    resolutionStrategy.force(
        "com.airbnb.viaduct:service-runtime:$viaductVersion",
        "com.airbnb.viaduct:service-wiring:$viaductVersion",
        "com.airbnb.viaduct:tenant-runtime:$viaductVersion",
        "com.airbnb.viaduct:tenant-wiring:$viaductVersion",
        "io.netty:netty-codec-http:$nettyVersion",
        "io.netty:netty-codec-http2:$nettyVersion",
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

// The Viaduct plugin unconditionally adds -Xcontext-receivers, which Kotlin 2.3+ rejects.
// Strip it out after the plugin has configured the task since our code doesn't use that feature.
afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.set(freeCompilerArgs.get().filter { it != "-Xcontext-receivers" })
        }
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
                    "**/config/ViaductConfig*",
                )
            }
        })
    )
}
