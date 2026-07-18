plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// core:voice — on-device, zero-enrollment speaker attribution (redesign spec Part D, 2026-07-18).
// Wraps sherpa-onnx's speaker-embedding native library (vendored locally, no Maven dependency — see
// libs/sherpa-onnx-1.13.4.aar, downloaded from the project's own GitHub release asset). Depends only
// on core:common per the established module rule (app -> core:* -> core:common); it does NOT depend
// on core:data — Room persistence of harvested embeddings lives there, wired together by `app`.
android {
    namespace = "ai.vaarta.core.voice"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    // Referenced via the flatDir repo declared in settings.gradle.kts, not implementation(files(...)),
    // because AGP refuses a direct local .aar `files()` dependency on a module that itself produces an
    // AAR ("Direct local .aar file dependencies are not supported when building an AAR" — bundleDebugAar
    // fails otherwise). The flatDir + group/name/ext module notation resolves it as a normal dependency
    // instead of a raw file blob, which AGP allows, and it still flows transitively into any consumer
    // (verified: :app depending on :core:voice correctly pulls in libsherpa-onnx-*.so + libonnxruntime.so).
    implementation(group = "", name = "sherpa-onnx-1.13.4", ext = "aar")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
