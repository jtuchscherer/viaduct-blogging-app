pluginManagement {
    val viaductVersion: String by settings

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.airbnb.viaduct.settings-gradle-plugin") version viaductVersion
    }
}

plugins {
    id("com.airbnb.viaduct.settings-gradle-plugin")
}

rootProject.name = "viaduct-blogging-app"

// Plain Gradle modules — they carry no Viaduct resolvers, so they stay outside the topology.
include(":modules:ai")
include(":modules:resolverkit")

// The root project is both the application and a Viaduct module, so it appears twice below.
includeViaductApplication {
    project(":")
    modulePackagePrefix("org.tuchscherer.viadapp")

    includeModule {
        project(":")
        modulePackageSuffix("resolvers")
    }
    includeModule {
        project(":modules:analytics")
        modulePackageSuffix("analytics")
    }
    includeModule {
        project(":modules:checkedlist")
        modulePackageSuffix("checkedlist")
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
