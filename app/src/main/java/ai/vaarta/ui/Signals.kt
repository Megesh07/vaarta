package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.common.Stage
import androidx.annotation.DrawableRes

/**
 * The scam-signal icon vocabulary (design system §2) — how VAARTA explains itself with almost no text.
 * The SAME glyph means the same thing whether it comes from the live detector (by [Stage]) or a Manual
 * Mode tile (by cue id), so a user learns it once and recognises it everywhere, in any language.
 */
data class SignalVisual(@param:DrawableRes val icon: Int, val label: String)

/** Manual Mode tiles — one per cue in [ai.vaarta.CopilotSession.cues]. Short labels (≤2 words) that
 *  survive translation; the icon carries the meaning. */
fun signalVisualForCue(cueId: String): SignalVisual = when (cueId) {
    "CUE_CLAIMS_AUTHORITY" -> SignalVisual(R.drawable.ic_sig_badge, "Police / CBI")
    "CUE_THREATENS_ARREST" -> SignalVisual(R.drawable.ic_sig_arrest, "Arrest")
    "CUE_PARCEL" -> SignalVisual(R.drawable.ic_sig_parcel, "Parcel")
    "CUE_BADGE_CASE_ID" -> SignalVisual(R.drawable.ic_sig_badge, "Badge / case")
    "CUE_ASKS_AADHAAR_OTP" -> SignalVisual(R.drawable.ic_sig_secret, "Aadhaar / OTP")
    "CUE_DONT_TELL_ANYONE" -> SignalVisual(R.drawable.ic_sig_secret, "Keep secret")
    "CUE_STAY_ON_LINE" -> SignalVisual(R.drawable.ic_sig_clock, "Stay on line")
    "CUE_MOVE_TO_WHATSAPP" -> SignalVisual(R.drawable.ic_sig_screen, "Move to video")
    "CUE_SENT_FAKE_DOCS" -> SignalVisual(R.drawable.ic_sig_badge, "Fake warrant")
    "CUE_PRESSURE_STAY" -> SignalVisual(R.drawable.ic_sig_clock, "Urgency")
    "CUE_DEMANDS_MONEY" -> SignalVisual(R.drawable.ic_sig_money, "Money / UPI")
    else -> SignalVisual(R.drawable.ic_wave, "Signal")
}

/** Live-detected tokens are grouped by the deterministic [Stage] the fired signal belongs to — the
 *  scam-script grammar the engine already scores against (HOOK→AUTHORITY→ISOLATION→ESCALATION→EXTRACTION). */
fun signalVisualForStage(stage: Stage): SignalVisual = when (stage) {
    Stage.HOOK -> SignalVisual(R.drawable.ic_sig_parcel, "Hook")
    Stage.AUTHORITY -> SignalVisual(R.drawable.ic_sig_badge, "Authority")
    Stage.ISOLATION -> SignalVisual(R.drawable.ic_sig_secret, "Secrecy")
    Stage.ESCALATION -> SignalVisual(R.drawable.ic_sig_arrest, "Threat")
    Stage.EXTRACTION -> SignalVisual(R.drawable.ic_sig_money, "Money")
    Stage.NONE -> SignalVisual(R.drawable.ic_wave, "Listening")
}
