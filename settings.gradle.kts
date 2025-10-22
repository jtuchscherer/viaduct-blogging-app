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
        create("libs") {
            // This injects a dynamic value that your TOML can reference.
            version("viaduct", viaductVersion)
        }
    }
}
