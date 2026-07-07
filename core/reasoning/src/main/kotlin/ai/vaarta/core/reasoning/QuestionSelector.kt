package ai.vaarta.core.reasoning

import ai.vaarta.core.common.IntelPack
import ai.vaarta.core.common.Question
import ai.vaarta.core.common.Stage

/**
 * Picks the verification question to show in the bubble — AI_REASONING_ENGINE.md §5,
 * MOBILE_UX_SPEC.md §3.2 ("exactly one suggested question visible; tap cycles").
 *
 * Pure and stateless except for the cycle position, which the caller owns (RAM-only,
 * per-session — DATABASE_DESIGN.md §2). Kept in core:reasoning, not the app, so selection logic
 * is unit-testable without Android/Robolectric.
 */
class QuestionSelector(private val pack: IntelPack) {

    /** Questions relevant once the session has reached [highestStage], most relevant (highest stage) first. */
    fun candidatesFor(highestStage: Stage): List<Question> =
        pack.questions
            .filter { it.stage != Stage.NONE && it.stage.ordinal <= highestStage.ordinal }
            .sortedByDescending { it.stage.ordinal }

    /** The question at [index] into the wrapped candidate list, or null if none apply yet. */
    fun select(highestStage: Stage, index: Int): Question? {
        val candidates = candidatesFor(highestStage)
        if (candidates.isEmpty()) return null
        return candidates[index.mod(candidates.size)]
    }

    fun textFor(question: Question, language: String): String =
        question.text[language] ?: question.text.values.firstOrNull() ?: ""
}
