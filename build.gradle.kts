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
    kotlin("jvm") version "2.3.20"
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
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")

    // Database dependencies
    implementation("org.xerial:sqlite-jdbc:3.53.0.0")
    implementation("org.postgresql:postgresql:42.7.10")
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
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    // Logback conditional config support
    implementation("org.codehaus.janino:janino:3.1.12")

    // Koin for Dependency Injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Testing
    testImplementation(testFixtures(libs.viaduct.tenant.runtime))
    testImplementation(testFixtures(libs.viaduct.tenant.api))
    testImplementation(libs.viaduct.engine.wiring)
    testImplementation(libs.viaduct.engine.runtime)
    testImplementation(libs.viaduct.engine.api)
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.12.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass.set("org.tuchscherer.viadapp.ViaductApplicationKt")
}

// Force Netty to a patched version to address CVEs (CRLF injection, HTTP request smuggling)
configurations.all {
    resolutionStrategy.force(
        "io.netty:netty-codec-http:4.2.12.Final",
        "io.netty:netty-codec-http2:4.2.12.Final",
        "io.netty:netty-codec-base:4.2.12.Final",
        "io.netty:netty-codec:4.2.12.Final",
        "io.netty:netty-handler:4.2.12.Final",
        "io.netty:netty-common:4.2.12.Final",
        "io.netty:netty-buffer:4.2.12.Final",
        "io.netty:netty-transport:4.2.12.Final",
        "io.netty:netty-resolver:4.2.12.Final",
        "io.netty:netty-transport-classes-epoll:4.2.12.Final",
        "io.netty:netty-transport-classes-kqueue:4.2.12.Final",
        "io.netty:netty-transport-native-epoll:4.2.12.Final",
        "io.netty:netty-transport-native-kqueue:4.2.12.Final"
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
