plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.viaduct.module)
    jacoco
}

viaductModule {
    modulePackageSuffix.set("checkedlist")
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
    compileOnly(libs.viaduct.tenant.api)
    testCompileOnly(libs.viaduct.tenant.api)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.koin.core)

    testImplementation("com.airbnb.viaduct.service:wiring:${libs.versions.viaduct.get()}")
    testImplementation("com.airbnb.viaduct.engine:wiring:${libs.versions.viaduct.get()}")
    testImplementation("com.airbnb.viaduct.tenant:wiring:${libs.versions.viaduct.get()}")
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
