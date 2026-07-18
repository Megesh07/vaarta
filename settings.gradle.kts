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
        // Lets core:voice reference the vendored sherpa-onnx-1.13.4.aar as a flatDir module dependency
        // (name/ext notation) instead of implementation(files(...)), which AGP rejects for a module that
        // itself produces an AAR. Declared here (not in core/voice/build.gradle.kts) because
        // repositoriesMode = FAIL_ON_PROJECT_REPOS forbids project-level repositories{} blocks.
        flatDir { dirs("core/voice/libs") }
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
include(":core:voice")
include(":tools:demo")
