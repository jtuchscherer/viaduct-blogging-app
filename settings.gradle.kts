rootProject.name = "viaduct-blogging-app"

include(":modules:analytics")

val viaductVersion: String by settings

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs")
    }
}
