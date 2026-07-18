package ai.vaarta.core.reasoning

/**
 * The one line of advisory context the coach call gets about the grounded classifier's finding
 * (Part B, redesign spec §4). [scamType] must already be source-backed — callers pass
 * [HybridAlert.mayShowScamType]-gated values only, the same gate the UI banner uses; this function
 * does not re-check sourcing, it only formats. Explicitly labelled "advisory only" so the coach
 * prompt's instruction to reason from the transcript, not this line, is reinforced at the data layer
 * too, not just in prose.
 */
fun groundedContextLine(scamType: String?): String {
    val trimmed = scamType?.trim()
    return if (trimmed.isNullOrEmpty()) {
        "[CONTEXT] Grounded classification so far: none yet."
    } else {
        """[CONTEXT] Grounded classification so far: "$trimmed" (source-backed, advisory only — reason from the transcript itself)."""
    }
}
