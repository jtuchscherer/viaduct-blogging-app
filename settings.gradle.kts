rootProject.name = "viaduct-blogging-app"

include(":modules:analytics")
include(":modules:checkedlist")
include(":modules:ai")

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
