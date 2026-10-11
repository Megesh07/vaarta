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
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("ai.vaarta.tools.eval.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

// One or more free-tier keys (GEMINI_API_KEY, GEMINI_API_KEY_2, GEMINI_API_KEY_3, ...) — each is a
// separate AI Studio project with its own independent 20-requests/day/model quota, round-robined by
// GeminiClassifyJudge so the benchmark isn't bottlenecked by a single key's daily cap.
val geminiKeys: List<String> = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.let { props ->
    props.stringPropertyNames()
        .filter { it == "GEMINI_API_KEY" || it.matches(Regex("GEMINI_API_KEY_\\d+")) }
        .sorted()
        .map { props.getProperty(it) }
        .filter { it.isNotBlank() }
}

tasks.register<JavaExec>("runBench") {
    group = "verification"
    description = "Run the scam-detection benchmark over the case set and write report.md/report.csv."
    mainClass.set("ai.vaarta.tools.eval.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // JavaExec's default workingDir is this subproject's dir, not repo root; Main.kt's default
    // paths ("tools/eval/.cache", "tools/eval/out") are repo-root-relative, so pin it explicitly
    // — otherwise output lands nested at tools/eval/tools/eval/out.
    workingDir = rootProject.projectDir
    systemProperty("gemini.keys", geminiKeys.joinToString(","))
    project.findProperty("casesFile")?.let { systemProperty("cases.file", it) }
}
