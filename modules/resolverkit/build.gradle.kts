plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

// Plain Gradle module (like :modules:ai): it holds a generic helper shared by resolvers
// in other modules, not resolvers itself, so it stays outside the Viaduct topology.
dependencies {
    api(libs.viaduct.api)

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
