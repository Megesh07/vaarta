package ai.vaarta.tools.eval

object ReportWriter {
    private fun pct(d: Double) = "%.2f".format(d)

    fun toMarkdown(reports: List<Report>): String = buildString {
        appendLine("# VAARTA Brain Benchmark — Report Card")
        appendLine()
        appendLine("| Judge | Cases | Errors | False-safe | FSR | False-alarm | FAR | Recall | Variant acc | p50 ms | p95 ms |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        for (r in reports) {
            appendLine(
                "| ${r.judgeId} | ${r.total} | ${r.errors} | ${r.falseSafe} | ${pct(r.falseSafeRate)} | " +
                    "${r.falseAlarm} | ${pct(r.falseAlarmRate)} | ${pct(r.recall)} | ${pct(r.variantAccuracy)} | " +
                    "${r.p50LatencyMs} | ${r.p95LatencyMs} |"
            )
        }
        appendLine()
        appendLine("> False-safe rate (FSR) is the catastrophic metric — a real scam scored safe. Lower is better.")
    }

    fun toCsv(reports: List<Report>): String = buildString {
        appendLine("judgeId,total,scored,errors,falseSafe,falseSafeRate,falseAlarm,falseAlarmRate,recall,variantAccuracy,p50LatencyMs,p95LatencyMs")
        for (r in reports) {
            appendLine("${r.judgeId},${r.total},${r.scored},${r.errors},${r.falseSafe},${r.falseSafeRate},${r.falseAlarm},${r.falseAlarmRate},${r.recall},${r.variantAccuracy},${r.p50LatencyMs},${r.p95LatencyMs}")
        }
    }
}
