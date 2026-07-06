package ai.vaarta.core.complaint

import kotlinx.serialization.json.Json

/**
 * JSON and plain-text renderings of a [ComplaintDraft] — one source of truth, many renderers
 * (DATABASE_DESIGN.md §5). PDF (Android PdfDocument) and DOCX renderers live in Android modules.
 */
object ComplaintRenderers {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(draft: ComplaintDraft): String = json.encodeToString(ComplaintDraft.serializer(), draft)

    fun toText(draft: ComplaintDraft): String = buildString {
        appendLine("CYBER CRIME COMPLAINT (DRAFT)")
        appendLine("=".repeat(44))
        appendLine("Generated: ${draft.generatedAtIso}")
        appendLine()
        appendLine("INCIDENT")
        appendLine("  Start:     ${draft.incident.startIso}")
        appendLine("  End:       ${draft.incident.endIso}")
        appendLine("  Duration:  ${draft.incident.durationMinutes} min")
        appendLine("  Number:    ${draft.incident.callerNumbers.joinToString(", ").ifEmpty { "(withheld)" }}")
        if (draft.incident.platforms.isNotEmpty()) {
            appendLine("  Platforms: ${draft.incident.platforms.joinToString("; ")}")
        }
        appendLine()
        appendLine("CLASSIFICATION")
        appendLine("  Scam:       ${draft.classification.scamName ?: "Unclassified"} (${draft.classification.scamCode ?: "-"})")
        appendLine("  Confidence: ${draft.classification.confidenceBucket}")
        appendLine()
        appendLine("NARRATIVE (${draft.narrative.lang})")
        appendLine(draft.narrative.text)
        appendLine()
        draft.complainant?.let {
            appendLine("COMPLAINANT")
            appendLine("  Name:    ${it.name ?: "-"}")
            appendLine("  Address: ${it.address ?: "-"}")
            appendLine()
        }
        draft.loss?.let {
            appendLine("FINANCIAL LOSS")
            appendLine("  Amount: Rs. ${it.amountInr ?: 0}")
            if (it.transactionRefs.isNotEmpty()) appendLine("  Refs:   ${it.transactionRefs.joinToString(", ")}")
            appendLine()
        }
        appendLine("EVIDENCE (detected signals)")
        for (e in draft.evidence) {
            val ts = "%02d:%02d".format(e.atOffsetMs / 60_000, e.atOffsetMs / 1000 % 60)
            appendLine("  [$ts] ${e.description}${e.quote?.let { " — \"$it\"" } ?: ""}")
        }
        appendLine()
        appendLine("-".repeat(44))
        appendLine(draft.disclaimer)
    }
}
