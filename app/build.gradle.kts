import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read the Gemini key from the git-ignored secrets.properties (never hardcoded, never committed).
// Absent file / absent key -> empty string, and the AI layer fails closed to deterministic mode.
val secretsFile = rootProject.file("secrets.properties")
val geminiApiKey: String = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}.getProperty("GEMINI_API_KEY", "")

android {
    namespace = "ai.vaarta"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.vaarta"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(project(":core:reasoning"))
    implementation(project(":core:complaint"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Parse the Gemini JSON response into LiveSuggestion. HTTP uses the JDK's HttpURLConnection
    // for the text-mode call; OkHttp provides the WebSocket for the Gemini Live streaming path.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
