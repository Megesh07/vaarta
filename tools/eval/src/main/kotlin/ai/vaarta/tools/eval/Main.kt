package ai.vaarta.tools.eval

import java.io.File

/** Runs every configured judge over the case set, writes report.md + report.csv.
 *  Key + paths come from system properties (set by the runBench Gradle task). */
fun main() {
    val apiKeys = System.getProperty("gemini.keys").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotBlank() }
    require(apiKeys.isNotEmpty()) { "gemini.keys is blank — set GEMINI_API_KEY in secrets.properties" }

    val casesText = System.getProperty("cases.file")
        ?.let { File(it).readText() }
        ?: object {}.javaClass.getResource("/vaarta-eval-cases.json")!!.readText()
    val cases = CaseLoader.load(casesText)

    val cacheDir = File(System.getProperty("cache.dir") ?: "tools/eval/.cache")
    val outDir = File(System.getProperty("out.dir") ?: "tools/eval/out").apply { mkdirs() }

    // 15 RPM free tier -> 4000 ms min interval (comfortably under the ceiling). (spec §10)
    val judge = GeminiClassifyJudge(
        apiKeys = apiKeys,
        throttle = Throttle(minIntervalMs = 4000),
        cache = ResponseCache(cacheDir),
    )

    println("Running ${judge.id} over ${cases.size} cases with ${apiKeys.size} key(s)...")
    val pairs = cases.map { c ->
        val v = judge.assess(c)
        println("  ${c.id}: scam=${v.isScam} concern=${v.concern} variant='${v.variant}' ${v.latencyMs}ms ${v.error ?: ""}")
        c to v
    }
    val report = Scorer.scoreStratified(judge.id, pairs)

    File(outDir, "report.md").writeText(ReportWriter.toMarkdownStratified(listOf(report)))
    File(outDir, "report.csv").writeText(ReportWriter.toCsvStratified(listOf(report)))
    println("\n" + ReportWriter.toMarkdownStratified(listOf(report)))
    println("Wrote ${outDir}/report.md and report.csv")
}
