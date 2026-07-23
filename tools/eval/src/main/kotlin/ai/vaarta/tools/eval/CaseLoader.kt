package ai.vaarta.tools.eval

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object CaseLoader {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class WireGroundTruth(val isScam: Boolean, val variant: String, val concern: String)

    @Serializable
    private data class WireCase(
        val id: String,
        val transcript: String,
        val language: String,
        val source: String,
        val groundTruth: WireGroundTruth,
    )

    fun load(text: String): List<TranscriptCase> =
        json.decodeFromString<List<WireCase>>(text).map { w ->
            TranscriptCase(
                id = w.id,
                transcript = w.transcript,
                language = w.language,
                source = w.source,
                groundTruth = GroundTruth(
                    isScam = w.groundTruth.isScam,
                    variant = w.groundTruth.variant,
                    concern = Concern.valueOf(w.groundTruth.concern),
                ),
            )
        }
}
