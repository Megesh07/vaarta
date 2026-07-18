package ai.vaarta.share

import ai.vaarta.i18n.AppLanguage

/**
 * A family broadcast can't assume the recipients' language (redesign spec §3B.3, edge case 2) — when
 * the UI is not English, every "warn/alert my family" share appends one fixed English safety line
 * after the localized message, so a recipient who doesn't read the sender's chosen language still
 * gets the core fact and the numbers to call. English UI just shares the message as-is.
 */
object BilingualShare {
    private const val ENGLISH_SAFETY_LINE =
        "(English) No officer or bank ever arrests anyone or asks for money over a call — hang up and report to 1930 or cybercrime.gov.in."

    fun compose(localizedText: String, language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) localizedText else "$localizedText\n\n$ENGLISH_SAFETY_LINE"
}
