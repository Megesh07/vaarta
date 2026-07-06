package ai.vaarta.tools.demo

import ai.vaarta.core.common.RiskEvent
import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.ComplaintRenderers
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.core.reasoning.PackLoader
import ai.vaarta.core.reasoning.RiskEngine

/**
 * Text-mode rig (TESTING_STRATEGY.md §6): feeds a scripted digital-arrest call through the real
 * Tier-0 engine, prints the live risk trace, then emits the auto-generated complaint. No audio,
 * no device — this is the CLI preview of what the Android app does in-call.
 */
fun main() {
    val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    val engine = RiskEngine(pack, listOf("en", "hi_latn", "hi"))

    val script = listOf(
        5_000L to "Hello, this is about a parcel in your name that customs has seized with illegal items",
        20_000L to "I am transferring you to the CBI crime branch cyber cell now",
        45_000L to "Officer Sharma badge number 4471, an arrest warrant and FIR are registered against you for money laundering",
        75_000L to "You are now under digital arrest, do not disconnect and do not tell anyone in your family",
        100_000L to "Install WhatsApp and join the video call, keep your camera on at all times",
        125_000L to "A non bailable arrest warrant has been issued and your account will be frozen within two hours",
        155_000L to "To verify your funds transfer the money to this RBI supervised account immediately",
    )

    println("VAARTA - live risk trace (text-mode rig)")
    println("=".repeat(72))
    var finalScore = 0
    for ((t, text) in script) {
        val s = engine.ingest(RiskEvent.Transcript(text, t, t + 3_000, isFinal = true, confidence = 0.9f))
        val bar = "#".repeat(s.score / 5).padEnd(20, '.')
        println("[%02d:%02d] %-12s %3d |%s| %s".format(t / 60_000, t / 1000 % 60, s.level, s.score, bar, s.topSignals.firstOrNull()?.explain ?: ""))
        finalScore = s.score
    }

    val fired = engine.sessionSignals()
    val start = 1_720_000_000_000L
    val input = ComplaintInput(
        callerNumber = "+91 92XXXXXX21",
        callStartEpochMs = start,
        callEndEpochMs = start + 175_000L,
        languages = listOf("en", "hi"),
        matchedScamCode = "SC-01",
        matchedScamName = "Digital Arrest - police/CBI impersonation",
        finalScore = finalScore,
        detectedSignals = fired.map { DetectedSignal(it.signalId, it.category, it.stage, it.atMs, it.explain) },
        complainantName = "R. Kumar",
    )

    println()
    println(ComplaintRenderers.toText(ComplaintBuilder.assemble(input, generatedAtEpochMs = start + 200_000L)))
}
