rootProject.name = "viaduct-blogging-app"

val viaductVersion: String by settings

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs")
    }
}
