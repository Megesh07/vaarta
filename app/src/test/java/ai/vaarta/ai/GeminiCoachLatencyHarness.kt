package ai.vaarta.ai

import ai.vaarta.core.reasoning.ConversationTurn
import ai.vaarta.core.reasoning.Speaker
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * A REAL-NETWORK, no-phone-call harness for the live copilot's brain — [GeminiClient.coach].
 *
 * Purpose (2026-07-21): answer two questions without placing a real scam call —
 *   1. HOW FAST is the live coach turn round-trip? (with reasoning now ON, thinkingBudget=512)
 *   2. Does the reply actually ADAPT — light/short on a merely-suspicious call, grounding + firmer
 *      with an exit on a confirmed scam — and does it read the emotion?
 *
 * It runs three transcripts that escalate from "just a bit sus" to "full digital arrest", each with
 * the risk level the live engine would have reached, and prints the wall-clock latency plus the
 * warning + graded replies so you can eyeball tone, empathy, and length side by side.
 *
 * NOT a normal unit test — it hits the real Gemini API (spends free-tier quota) and needs a key
 * compiled in. It is therefore DOUBLE-GATED so `./gradlew test` and CI never run it:
 *   - skips unless `-Dvaarta.latency=1` is passed, AND
 *   - skips unless a GEMINI_API_KEY is compiled in (BuildConfig).
 *
 * Run it manually:
 *   ./gradlew :app:testDebugUnitTest --tests "ai.vaarta.ai.GeminiCoachLatencyHarness" -Dvaarta.latency=1 --info
 * (the printed lines appear in the test's stdout; `--info` guarantees they surface).
 *
 * NOTE: on the happy path [GeminiClient.coach] never touches android.util.Log, so it runs fine on
 * the plain JVM test JVM. If you see "Method w in android.util.Log not mocked", the call FAILED and
 * hit the diagnostic log line — that means a bad/missing key, no network, or quota (429), not a bug
 * in the harness.
 */
class GeminiCoachLatencyHarness {

    private data class Scenario(
        val label: String,
        val riskLevel: String,
        val stage: String,
        val nextStage: String,
        val history: List<ConversationTurn>,
    )

    private fun turn(speaker: Speaker, text: String, atMs: Long) = ConversationTurn(speaker, text, atMs)

    private val scenarios = listOf(
        Scenario(
            label = "MILDLY SUSPICIOUS — should be light, ~1 short verify line, no exit",
            riskLevel = "CAUTION",
            stage = "HOOK",
            nextStage = "AUTHORITY",
            history = listOf(
                turn(Speaker.CALLER, "Hello, I am calling about a parcel in your name, there is an issue with the customs clearance.", 3_000),
            ),
        ),
        Scenario(
            label = "ESCALATING — authority + urgency, should firm up, add a refuse",
            riskLevel = "HIGH_RISK",
            stage = "AUTHORITY",
            nextStage = "ISOLATION",
            history = listOf(
                turn(Speaker.CALLER, "Hello, this is about a parcel in your name that customs has seized with illegal items.", 3_000),
                turn(Speaker.CALLER, "I am transferring you to the CBI crime branch now, Officer Sharma badge 4471, an FIR is registered against you.", 25_000),
            ),
        ),
        Scenario(
            label = "CONFIRMED SCAM (EXTRACTION) — user likely scared: should reassure-then-firm, include an exit",
            riskLevel = "SCAM_PATTERN",
            stage = "EXTRACTION",
            nextStage = "EXTRACTION",
            history = listOf(
                turn(Speaker.CALLER, "You are now under digital arrest, do not disconnect and do not tell anyone in your family.", 3_000),
                turn(Speaker.CALLER, "A non-bailable arrest warrant is issued and your account will be frozen in two hours.", 25_000),
                turn(Speaker.CALLER, "Transfer the money to this RBI supervised account right now to verify your funds and clear your name.", 50_000),
            ),
        ),
    )

    @Test
    fun `measure coach latency and inspect adaptive tone across sus-to-scam scenarios`() {
        assumeTrue(System.getProperty("vaarta.latency") != null,
            "latency harness is opt-in: pass -Dvaarta.latency=1 to run it")
        assumeTrue(GeminiClient.isConfigured(),
            "no GEMINI_API_KEY compiled in — add it to secrets and rebuild to run the harness")

        println("\n=== VAARTA coach() live latency + adaptivity harness ===")
        println("model=gemini-2.5-flash  thinkingBudget=512 (reasoning ON)  non-blocking in the app\n")

        for (s in scenarios) {
            val started = System.nanoTime()
            val result = GeminiClient.coach(s.history, s.stage, s.nextStage, s.riskLevel)
            val ms = (System.nanoTime() - started) / 1_000_000

            println("--- ${s.label}")
            println("    riskLevel=${s.riskLevel}  stage=${s.stage}->${s.nextStage}  latency=${ms}ms")
            if (result == null) {
                println("    RESULT: null (fell closed — bad key / network / quota). See class note.\n")
                continue
            }
            println("    warning (${result.warning.length} chars): ${result.warning}")
            println("    replies (${result.replies.size}):")
            result.replies.forEach { println("      [${it.kind}] ${it.text}") }
            println()
        }
        println("=== done — read the latency numbers and check that tone/length grew with the risk level ===\n")
    }
}
