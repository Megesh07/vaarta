import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:reasoning"))
    implementation(project(":core:complaint"))
    implementation(libs.okhttp)
}

application {
    mainClass.set("ai.vaarta.tools.demo.DemoKt")
}

// Headless probe of the Gemini Live WebSocket (de-risks the protocol before the Android build).
// Reads the key from the git-ignored secrets.properties; never hardcoded, never committed.
val geminiKey: String = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("GEMINI_API_KEY", "")

tasks.register<JavaExec>("liveProbe") {
    group = "verification"
    description = "Connect to Gemini Live over WebSocket and try a text turn."
    mainClass.set("ai.vaarta.tools.demo.LiveProbeKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("gemini.key", geminiKey)
}
