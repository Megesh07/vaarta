import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read the app's keys from the git-ignored secrets.properties (never hardcoded, never committed).
// Absent file / absent key -> empty string, and the relevant feature fails closed.
val secretsFile = rootProject.file("secrets.properties")
val secretsProps = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val geminiApiKey: String = secretsProps.getProperty("GEMINI_API_KEY", "")

// Safe Browsing v4 (free, non-commercial tier) — same fail-closed contract as the Gemini key:
// absent file/property -> empty string, and LinkChecker's Safe Browsing lookup is skipped (UNKNOWN).
val safeBrowsingApiKey: String = secretsProps.getProperty("SAFE_BROWSING_API_KEY", "")

// URLhaus (abuse.ch) Auth-Key — same fail-closed contract: absent file/property -> empty string,
// and LinkChecker's URLhaus lookup is skipped (UNKNOWN).
val urlhausAuthKey: String = secretsProps.getProperty("URLHAUS_AUTH_KEY", "")

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
        buildConfigField("String", "SAFE_BROWSING_API_KEY", "\"$safeBrowsingApiKey\"")
        buildConfigField("String", "URLHAUS_AUTH_KEY", "\"$urlhausAuthKey\"")
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // Forward "vaarta.*" -D flags (e.g. -Dvaarta.latency=1) to the forked test JVM — Gradle
            // does NOT do this by default, and the opt-in real-network harnesses (GeminiCoachLatency
            // Harness, PanicPersonalizationHarness) gate themselves on exactly this property.
            System.getProperties().forEach { (k, v) ->
                if (k.toString().startsWith("vaarta.")) it.systemProperty(k.toString(), v.toString())
            }
        }
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
    implementation(project(":core:data"))
    implementation(project(":core:voice"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
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

    // Loads real article preview images (og:image) in the Trending-scams feed. Small, standard,
    // Compose-native; degrades to the category illustration when a card has no image.
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
