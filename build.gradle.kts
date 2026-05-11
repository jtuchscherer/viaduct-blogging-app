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
    alias(libs.plugins.ksp)
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
    implementation(project(":modules:analytics"))
    implementation(project(":modules:checkedlist"))

    implementation(libs.viaduct.api)
    implementation(libs.viaduct.runtime)
    implementation(libs.graphql.java)
    // Viaduct's fat runtime jar uses graphql.scalars.ExtendedScalars internally but
    // does not bundle or declare it as a transitive dep, so we must add it explicitly.
    implementation(libs.graphql.java.extended.scalars)
    implementation("javax.inject:javax.inject:1")
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

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
    testImplementation(testFixtures(libs.viaduct.tenant.api))
    testImplementation(testFixtures(libs.viaduct.tenant.runtime))
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

// Force patched dependency versions to address CVEs.
val viaductVersion: String = libs.versions.viaduct.get()
val nettyVersion: String = libs.versions.netty.get()
val jacksonCore3Version: String = libs.versions.jackson3.get()
val bouncyCastleVersion: String = libs.versions.bouncycastle.get()
configurations.all {
    resolutionStrategy.force(
        "tools.jackson.core:jackson-core:$jacksonCore3Version",
        "org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion",
        "org.bouncycastle:bcpg-jdk18on:$bouncyCastleVersion",
        "org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion",
        "org.bouncycastle:bcutil-jdk18on:$bouncyCastleVersion",
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
                    "**/config/ViaductConfig*",
                )
            }
        })
    )
}
