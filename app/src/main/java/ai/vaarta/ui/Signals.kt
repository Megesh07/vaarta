package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.common.Stage
import androidx.annotation.DrawableRes

/**
 * The scam-signal icon vocabulary (design system §2) — how VAARTA explains itself with almost no text.
 * The SAME glyph means the same thing wherever it appears (live detector by [Stage], verdict headers),
 * so a user learns it once and recognises it everywhere, in any language.
 */
data class SignalVisual(@param:DrawableRes val icon: Int, val label: String)

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
