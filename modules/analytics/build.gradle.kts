plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.viaduct.module)
    jacoco
}

viaductModule {
    modulePackageSuffix.set("analytics")
}

// The Viaduct plugin unconditionally adds -Xcontext-receivers, which Kotlin 2.3+ rejects.
afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.set(freeCompilerArgs.get().filter { it != "-Xcontext-receivers" })
        }
    }
}

dependencies {
    api(libs.viaduct.tenant.api)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.koin.core)

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
}
