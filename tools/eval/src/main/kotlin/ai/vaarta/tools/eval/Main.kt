package ai.vaarta.tools.eval

import java.io.File

/** Runs every configured judge over the case set, writes report.md + report.csv.
 *  Key + paths come from system properties (set by the runBench Gradle task). */
fun main() {
    val apiKey = System.getProperty("gemini.key").orEmpty()
    require(apiKey.isNotBlank()) { "gemini.key is blank — set GEMINI_API_KEY in secrets.properties" }

    val casesText = System.getProperty("cases.file")
        ?.let { File(it).readText() }
        ?: object {}.javaClass.getResource("/foundation-cases.json")!!.readText()
    val cases = CaseLoader.load(casesText)

    val cacheDir = File(System.getProperty("cache.dir") ?: "tools/eval/.cache")
    val outDir = File(System.getProperty("out.dir") ?: "tools/eval/out").apply { mkdirs() }

    // 15 RPM free tier -> 4000 ms min interval (comfortably under the ceiling). (spec §10)
    val judge = GeminiClassifyJudge(
        apiKey = apiKey,
        throttle = Throttle(minIntervalMs = 4000),
        cache = ResponseCache(cacheDir),
    )

    println("Running ${judge.id} over ${cases.size} cases...")
    val pairs = cases.map { c ->
        val v = judge.assess(c)
        println("  ${c.id}: scam=${v.isScam} concern=${v.concern} variant='${v.variant}' ${v.latencyMs}ms ${v.error ?: ""}")
        c to v
    }
    val report = Scorer.score(judge.id, pairs)

    File(outDir, "report.md").writeText(ReportWriter.toMarkdown(listOf(report)))
    File(outDir, "report.csv").writeText(ReportWriter.toCsv(listOf(report)))
    println("\n" + ReportWriter.toMarkdown(listOf(report)))
    println("Wrote ${outDir}/report.md and report.csv")
}
