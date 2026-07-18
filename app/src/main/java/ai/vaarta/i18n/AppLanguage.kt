package ai.vaarta.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The Tier-1 UI languages (redesign spec §3B.1). Hinglish is a first-class peer, not a hack — a
 * romanized BCP-47 variant (`hi-Latn`), resolved via the `values-b+hi+Latn` resource qualifier.
 * Backed by [AppCompatDelegate]'s per-app language preference (`autoStoreLocales` in the manifest
 * persists the choice across restarts on API < 33 without any app-side storage).
 */
enum class AppLanguage(val tag: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    HINGLISH("hi-Latn", "Hinglish"),
    ;

    companion object {
        /** The language actually applied right now; falls back to English before any choice is made. */
        fun current(): AppLanguage {
            val locale = AppCompatDelegate.getApplicationLocales().takeUnless { it.isEmpty }?.get(0)
                ?: return ENGLISH
            val script = locale.script
            return when {
                locale.language == "hi" && script.equals("Latn", ignoreCase = true) -> HINGLISH
                locale.language == "hi" -> HINDI
                else -> ENGLISH
            }
        }

        /**
         * `setApplicationLocales` recreates the calling Activity itself (as long as it's an
         * `AppCompatActivity` — see [ai.vaarta.MainActivity] — and doesn't declare
         * `android:configChanges="locale"`); calling `.recreate()` ourselves on top of that causes
         * a double-recreate race where the SECOND (manual) recreation can win with the activity's
         * still-old base-context locale, so the screen silently keeps showing English. Do not add
         * a manual recreate() here — this was caught live on the emulator.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }

        /** False only before the user has ever made an explicit choice — gates the first-run picker. */
        fun hasBeenChosen(): Boolean = !AppCompatDelegate.getApplicationLocales().isEmpty
    }

    /**
     * The [RecognizerIntent.EXTRA_LANGUAGE][android.speech.RecognizerIntent.EXTRA_LANGUAGE] tag to
     * request for voice input (spec §3B.3, edge case 5) — voice follows the UI language. There is no
     * "hi-Latn" speech locale, so Hinglish requests hi-IN like Hindi does; the recognizer will return
     * Devanagari text even for a Hinglish speaker, which is an accepted quirk — the chat's own mirror
     * rule then replies in whatever script the transcript actually came back in.
     */
    fun speechLocaleTag(): String = when (this) {
        ENGLISH -> "en-IN"
        HINDI, HINGLISH -> "hi-IN"
    }
}
