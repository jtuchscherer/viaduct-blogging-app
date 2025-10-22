plugins {
    kotlin("jvm") version "2.2.20"
    alias(libs.plugins.viaduct.application)
    alias(libs.plugins.viaduct.module)
    application
}

viaductApplication {
    modulePackagePrefix.set("com.example.viadapp")
}

viaductModule {
    modulePackageSuffix.set("resolvers")
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.10")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")

    // Database dependencies
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.44.1")

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
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.9.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

application {
    mainClass.set("com.example.viadapp.ViaductApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}
