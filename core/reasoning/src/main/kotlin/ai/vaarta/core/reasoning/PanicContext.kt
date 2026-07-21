package ai.vaarta.core.reasoning

/**
 * The AI's personalization of the panic-sheet safety steps (redesign spec §A2) — a short heading
 * plus 2-4 tailored steps, layered ON TOP of the always-shown base [RightNowSteps]. Never the only
 * source of guidance: the base steps render instantly and stay even if this never arrives.
 */
data class PanicPersonalization(val heading: String, val steps: List<String>)

/**
 * Picks what real situation (if any) should personalize the panic sheet (redesign spec §A2): an
 * active live call's detected signal takes priority, then the most recently analyzed
 * recording/chat, else no context at all — in which case the caller must skip the AI call entirely
 * and show only the base safety steps (never invent a generic personalization with nothing to go on).
 */
object PanicContextSelector {

    /**
     * @param liveScamType the current live session's grounded scam-type read, if any
     * @param liveRiskLevel the current live session's displayed [RiskLevel] name (e.g. "OBSERVING")
     * @param recentScamType the most recently saved call/recording/chat's scam-type, if any
     * @param recentRiskLevel the most recently saved call/recording/chat's final risk-level name, if any
     * @return a one-line situation description for the AI prompt, or null when nothing real is known
     */
    fun select(
        liveScamType: String?,
        liveRiskLevel: String,
        recentScamType: String?,
        recentRiskLevel: String?,
    ): String? {
        val liveActive = liveRiskLevel != RiskLevel.OBSERVING.name || !liveScamType.isNullOrBlank()
        if (liveActive) {
            val pattern = liveScamType?.trim().takeUnless { it.isNullOrBlank() }
                ?: "a suspicious pattern, not yet identified"
            return "The user has a live call in progress right now. Detected pattern: $pattern. " +
                "Current risk level: $liveRiskLevel."
        }
        val recentActive = !recentScamType.isNullOrBlank() ||
            (recentRiskLevel != null && recentRiskLevel != RiskLevel.OBSERVING.name)
        if (recentActive) {
            val pattern = recentScamType?.trim().takeUnless { it.isNullOrBlank() } ?: "a suspicious pattern"
            return "The user's most recently analyzed call or recording showed: $pattern. " +
                "Its risk level was ${recentRiskLevel ?: "unknown"}."
        }
        return null
    }
}
