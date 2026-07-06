package ai.vaarta.core.reasoning

import ai.vaarta.core.common.IntelPack
import kotlinx.serialization.json.Json

/** Loads intel packs from JSON (MVP format — ADR-0001). Production compiles signed YAML. */
object PackLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromJson(text: String): IntelPack = json.decodeFromString(IntelPack.serializer(), text)

    /** Loads a bundled pack resource, e.g. "/packs/core-scam-v1.json". */
    fun fromResource(path: String): IntelPack {
        val stream = PackLoader::class.java.getResourceAsStream(path)
            ?: error("Intel pack resource not found: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { fromJson(it.readText()) }
    }
}
