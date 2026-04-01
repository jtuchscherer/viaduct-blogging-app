plugins {
    kotlin("jvm") version "2.2.20"
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
    implementation("com.airbnb.viaduct:tenant-api:0.25.0")
    implementation("com.airbnb.viaduct:service-api:0.25.0")
    implementation("com.airbnb.viaduct:service-wiring:0.25.0")
    implementation("javax.inject:javax.inject:1")
    implementation("ch.qos.logback:logback-classic:1.5.25")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")

    // Database dependencies
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")

    // HTTP server for auth endpoints
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.cors)

    // JWT for authentication
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Koin for Dependency Injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // Testing
    testImplementation(testFixtures("com.airbnb.viaduct:tenant-runtime:0.25.0"))
    testImplementation("com.airbnb.viaduct:engine-wiring:0.25.0")
    testImplementation("com.airbnb.viaduct:engine-runtime:0.25.0")
    testImplementation("com.airbnb.viaduct:engine-api:0.25.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.12.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation("io.mockk:mockk:1.14.3")
    testImplementation("com.h2database:h2:2.3.232")
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
        "io.netty:netty-resolver:4.2.12.Final"
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
