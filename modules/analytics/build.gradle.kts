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
    api(libs.viaduct.api)
    implementation(libs.viaduct.runtime)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.koin.core)

    testImplementation(testFixtures(libs.viaduct.tenant.api))
    testImplementation(testFixtures(libs.viaduct.tenant.runtime))
    testImplementation(libs.viaduct.engine.runtime)
    testImplementation(libs.viaduct.engine.wiring)
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
    // viaduct:runtime is a fat jar that embeds JUnit 5.11 without relocation. Moving it to the
    // end of the classpath ensures our declared JUnit 5.12.2 jars are loaded first.
    doFirst {
        val runtimeJar = classpath.filter { "runtime-0.31" in it.name }
        classpath = classpath.minus(runtimeJar).plus(runtimeJar)
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
