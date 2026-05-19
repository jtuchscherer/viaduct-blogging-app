plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
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
