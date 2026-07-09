package ai.vaarta.core.reasoning

/**
 * The hybrid "safety ratchet" (ADR-0003) — how the deterministic engine and the web-grounded AI
 * combine into the single alert the user sees.
 *
 * Principle: in scam defense the two mistakes are NOT equal. Missing a real scam is catastrophic;
 * over-warning on a genuine call is a minor annoyance. So intelligence is unleashed in the helpful
 * direction and bounded only in the one dangerous direction:
 *
 * - The deterministic engine ([RiskEngine]) owns the numeric score AND the safety FLOOR. It is
 *   instant, explainable, and impossible to socially-engineer.
 * - The AI's [CoachingResponse.concern] is ADVISORY and may only ever RAISE the displayed alert
 *   above that floor — catching novel/advanced scams the static pack never knew — NEVER lower it.
 * - Reassurance ("this looks like a genuine call") is allowed only by CITED CONSENSUS: both tracks
 *   read low AND the AI backs it with a real web source. A scammer who manipulates the model into
 *   "tell them it's safe" therefore cannot switch off a rule-detected scam, and cannot manufacture
 *   reassurance without a source.
 *
 * Pure functions, no state — fully unit-testable, and the safety guarantees are pinned by tests.
 */
object HybridAlert {

    /**
     * The alert level shown to the user = the HIGHER of the deterministic level and the AI's
     * advisory concern. Monotonic in both inputs; the AI can only ratchet it up.
     */
    fun displayedLevel(engineLevel: RiskLevel, aiConcern: RiskLevel): RiskLevel =
        if (aiConcern.ordinal > engineLevel.ordinal) aiConcern else engineLevel

    /** True when the AI *raised* the alert above what the rules alone detected — i.e. the extra
     *  intelligence is doing work this turn (used to label the banner "identified from the live web"). */
    fun aiRaisedAlarm(engineLevel: RiskLevel, aiConcern: RiskLevel): Boolean =
        aiConcern.ordinal > engineLevel.ordinal

    /**
     * Whether the app may show a calming "this looks like a genuine call" reassurance. Requires
     * CITED CONSENSUS: the deterministic floor is low (≤ CAUTION), the AI also reads low (≤ CAUTION),
     * the AI explicitly flagged benign, AND it cited at least one real source. Any elevated signal
     * from either track, or an unsourced benign claim, blocks reassurance — it fails toward caution.
     */
    fun mayReassure(engineLevel: RiskLevel, assessment: GroundedAssessment): Boolean =
        assessment.benign &&
            assessment.sources.isNotEmpty() &&
            engineLevel.ordinal <= RiskLevel.CAUTION.ordinal &&
            assessment.concern.ordinal <= RiskLevel.CAUTION.ordinal

    /**
     * Whether a grounded scam-variant label may be shown. Guards against hallucinated variants: the
     * AI must cite at least one source for the named type. No source → no claim.
     */
    fun mayShowScamType(assessment: GroundedAssessment): Boolean =
        !assessment.scamType.isNullOrBlank() && assessment.sources.isNotEmpty()
}
