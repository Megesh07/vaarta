pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "vaarta"

// Module dependency rule (TECHNICAL_ARCHITECTURE.md §9): app -> core:* -> core:common.
// Pure-Kotlin domain modules first; Android modules (:app, :core:overlay, …) added in phase 3.
include(":app")
include(":core:common")
include(":core:reasoning")
include(":core:complaint")
include(":core:data")
include(":tools:demo")
