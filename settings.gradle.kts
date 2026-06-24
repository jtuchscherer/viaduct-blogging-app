rootProject.name = "viaduct-blogging-app"

include(":modules:analytics")
include(":modules:checkedlist")
include(":modules:ai")

val viaductVersion: String by settings

pluginManagement {
    repositories {
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs")
    }
}
