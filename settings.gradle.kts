pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Meret"

include(":app")

// Pure Kotlin — no Android imports, fast unit tests.
include(":core:model")
include(":core:common")
include(":domain")

// Platform-facing core.
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:location")
include(":core:maps")

// Screens. A feature never depends on another feature.
include(":feature:map")
include(":feature:tracking")
include(":feature:waypoint")
include(":feature:measure")
include(":feature:tiles")
include(":feature:sync")
include(":feature:export")
include(":feature:settings")

// Background work.
include(":sync")
include(":peer")
