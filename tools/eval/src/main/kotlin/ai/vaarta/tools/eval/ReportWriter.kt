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

    fun toMarkdownStratified(reports: List<StratifiedReport>): String = buildString {
        appendLine("# VAARTA Brain Benchmark — Stratified Report Card")
        appendLine()
        appendLine("## Overall")
        appendLine(toMarkdown(reports.map { it.overall }))

        appendLine("## By stratum")
        appendLine()
        appendLine("| Judge | Stratum | Cases | False-safe | FSR | False-alarm | FAR | Recall | Variant acc |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        for (r in reports) {
            for (stratum in Stratum.entries) {
                val s = r.byStratum[stratum] ?: continue
                appendLine(
                    "| ${r.judgeId} | $stratum | ${s.total} | ${s.falseSafe} | ${pct(s.falseSafeRate)} | " +
                        "${s.falseAlarm} | ${pct(s.falseAlarmRate)} | ${pct(s.recall)} | ${pct(s.variantAccuracy)} |"
                )
            }
        }
        appendLine()

        val anyMisses = reports.any { it.blatantScamMisses.isNotEmpty() }
        if (anyMisses) {
            appendLine("## ⚠️ NOT READY — blatant scam(s) missed")
            appendLine()
            for (r in reports) {
                if (r.blatantScamMisses.isNotEmpty()) {
                    appendLine("- **${r.judgeId}** missed: ${r.blatantScamMisses.joinToString(", ")}")
                }
            }
            appendLine()
            appendLine("> A missed BLATANT scam is never acceptable, no matter how good the aggregate rate looks.")
        }
    }

    fun toCsvStratified(reports: List<StratifiedReport>): String = buildString {
        appendLine("judgeId,stratum,total,scored,errors,falseSafe,falseSafeRate,falseAlarm,falseAlarmRate,recall,variantAccuracy,p50LatencyMs,p95LatencyMs")
        for (r in reports) {
            for (stratum in Stratum.entries) {
                val s = r.byStratum[stratum] ?: continue
                appendLine("${r.judgeId},$stratum,${s.total},${s.scored},${s.errors},${s.falseSafe},${s.falseSafeRate},${s.falseAlarm},${s.falseAlarmRate},${s.recall},${s.variantAccuracy},${s.p50LatencyMs},${s.p95LatencyMs}")
            }
        }
    }
}
