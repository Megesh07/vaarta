plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// core:data — opt-in encrypted saved history (Phase 4B, ADR-0004). Android library because it needs
// the Android Keystore (to wrap the DB key) and Room/SQLCipher (encrypted-at-rest storage). Nothing
// here reaches the network; it only persists on-device what the live/recorded pipelines already held
// in RAM, gated by explicit user consent (PRIVACY_SECURITY.md P2 addendum).
android {
    namespace = "ai.vaarta.core.data"
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

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SQLCipher provides the encrypted SQLite; androidx.sqlite is the SupportSQLiteOpenHelper API
    // Room binds to. The passphrase is generated on device and wrapped by the Keystore (never here).
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    implementation(libs.kotlinx.serialization.json)
}
