package ai.vaarta.ai

/** Re-export: the canonical prompt now lives in core:reasoning (shared with tools:eval). */
object GroundedClassifyPrompt {
    val INSTRUCTION: String = ai.vaarta.core.reasoning.GroundedClassifyPrompt.INSTRUCTION
}
