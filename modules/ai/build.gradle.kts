plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)
    implementation(libs.tracy.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing:1.51.0")
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
