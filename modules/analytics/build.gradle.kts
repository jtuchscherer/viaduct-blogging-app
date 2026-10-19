plugins {
    `java-library`
    alias(libs.plugins.viaduct.module.java)
    jacoco
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    api(libs.viaduct.java.api)
    implementation(project(":modules:shared"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    // Kotlin-hosted unit and interoperability tests execute these Java classes in :test.
    val applicationTests = rootProject.tasks.named<Test>("test")
    dependsOn(applicationTests)
    executionData(applicationTests.get())
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching {
        exclude("**/resolverbases/**")
    })
    reports {
        xml.required = true
        html.required = true
    }
}

// Replaces Detekt for the Java tenant. Compile handwritten sources against the generated
// classes so upstream codegen warnings cannot hide a new warning in application code.
val lintJava by tasks.registering(JavaCompile::class) {
    dependsOn(tasks.compileJava)
    source(fileTree("src/main/java") { include("**/*.java") })
    classpath = sourceSets.main.get().compileClasspath + sourceSets.main.get().output.classesDirs
    destinationDirectory.set(layout.buildDirectory.dir("lint/java"))
    javaCompiler.set(javaToolchains.compilerFor(java.toolchain))
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-proc:none"))
    options.release.set(25)
}

tasks.check {
    dependsOn(lintJava)
}
