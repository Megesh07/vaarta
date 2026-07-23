import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:reasoning"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("ai.vaarta.tools.eval.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

val geminiKey: String = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("GEMINI_API_KEY", "")

tasks.register<JavaExec>("runBench") {
    group = "verification"
    description = "Run the scam-detection benchmark over the case set and write report.md/report.csv."
    mainClass.set("ai.vaarta.tools.eval.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // JavaExec's default workingDir is this subproject's dir, not repo root; Main.kt's default
    // paths ("tools/eval/.cache", "tools/eval/out") are repo-root-relative, so pin it explicitly
    // — otherwise output lands nested at tools/eval/tools/eval/out.
    workingDir = rootProject.projectDir
    systemProperty("gemini.key", geminiKey)
    project.findProperty("casesFile")?.let { systemProperty("cases.file", it) }
}
