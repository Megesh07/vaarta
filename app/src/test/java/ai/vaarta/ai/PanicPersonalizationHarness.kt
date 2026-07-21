package ai.vaarta.ai

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * A REAL-NETWORK harness for [GeminiClient.personalizePanic] (redesign spec §A2) — same shape and
 * gating as [GeminiCoachLatencyHarness]: opt-in only, skips without a compiled key, so `./gradlew
 * test`/CI never run it. Confirms the request/response actually round-trips against the live model
 * (unit tests already cover the pure context-selection and wire-parsing logic with hand-crafted
 * JSON; this is the one piece that needs a real call).
 *
 * Run it manually:
 *   ./gradlew :app:testDebugUnitTest --tests "ai.vaarta.ai.PanicPersonalizationHarness" -Dvaarta.latency=1 --info
 */
class PanicPersonalizationHarness {

    private val scenarios = listOf(
        "an active live call" to "The user has a live call in progress right now. Detected pattern: Digital arrest. Current risk level: SCAM_PATTERN.",
        "a recent recording" to "The user's most recently analyzed call or recording showed: Loan app harassment. Its risk level was HIGH_RISK.",
        "a vague signal" to "The user has a live call in progress right now. Detected pattern: a suspicious pattern, not yet identified. Current risk level: CAUTION.",
    )

    @Test
    fun `measure latency and inspect the personalization for each context shape`() {
        assumeTrue(System.getProperty("vaarta.latency") != null,
            "latency harness is opt-in: pass -Dvaarta.latency=1 to run it")
        assumeTrue(GeminiClient.isConfigured(),
            "no GEMINI_API_KEY compiled in — add it to secrets and rebuild to run the harness")

        println("\n=== VAARTA personalizePanic() live latency + shape harness ===")
        for ((label, context) in scenarios) {
            val started = System.nanoTime()
            val result = GeminiClient.personalizePanic(context)
            val ms = (System.nanoTime() - started) / 1_000_000

            println("--- $label")
            println("    context=$context")
            println("    latency=${ms}ms")
            if (result == null) {
                println("    RESULT: null (fell closed — bad key / network / quota / model declined).\n")
                continue
            }
            println("    heading: ${result.heading}")
            println("    steps (${result.steps.size}):")
            result.steps.forEach { println("      - $it") }
            println()
        }
        println("=== done ===\n")
    }
}
