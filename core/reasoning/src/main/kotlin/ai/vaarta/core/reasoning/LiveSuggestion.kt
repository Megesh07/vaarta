package ai.vaarta.core.reasoning

import kotlinx.serialization.Serializable

/**
 * A single live in-call reply suggestion produced by the AI layer (ADR-0002). The AI is forced to
 * return exactly this shape (structured output) — it cannot free-associate. Every instance must
 * pass [SuggestionSafetyFilter] before it is ever shown to the user.
 *
 * `category` is a free-string label from the model (e.g. "verification", "anti-isolation") kept as
 * text, not our enum, because the model's labels are advisory and must not be trusted to match our
 * taxonomy exactly. `confidence` is the model's own 0..1 self-estimate — advisory only; it never
 * feeds the deterministic risk score (ADR-0002: the LLM never sets the score).
 */
@Serializable
data class LiveSuggestion(
    val suggestedReply: String,
    val why: String = "",
    val category: String = "",
    val confidence: Double = 0.0,
)
