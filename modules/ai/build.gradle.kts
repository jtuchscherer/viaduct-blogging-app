plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

dependencies {
    // tracy-core (AI observability) pulls a full OpenTelemetry 1.51.0 set transitively; this BOM
    // forces the whole set to a patched version together to avoid version skew (CVE fix, #36).
    implementation(platform(libs.opentelemetry.bom))
    testImplementation(platform(libs.opentelemetry.bom))

    // langchain4j-core pins its own com.fasterxml.jackson:jackson-bom:2.21.4, which has a CVE
    // fixed in 2.21.5. Declaring jackson-databind directly at the version catalog's patched
    // version wins normal Gradle conflict resolution (highest requested version) over that bom.
    implementation(libs.jackson.databind)

    implementation(libs.koin.core)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)
    implementation(libs.tracy.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
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
