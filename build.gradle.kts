plugins {
    kotlin("jvm") version "1.9.24"
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
    implementation("io.ktor:ktor-server-core:2.3.6")
    implementation("io.ktor:ktor-server-netty:2.3.6")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.6")
    implementation("io.ktor:ktor-serialization-jackson:2.3.6")
    implementation("io.ktor:ktor-server-cors:2.3.6")

    // JWT for authentication
    implementation("io.ktor:ktor-server-auth:2.3.6")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.6")

    // Koin for Dependency Injection
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-ktor:3.5.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.9.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.2")
    testImplementation("io.insert-koin:koin-test:3.5.0")
    testImplementation("io.insert-koin:koin-test-junit5:3.5.0")
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
