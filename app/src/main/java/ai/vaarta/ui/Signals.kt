package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.common.Stage
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The scam-signal icon vocabulary (design system §2) — how VAARTA explains itself with almost no text.
 * The SAME glyph means the same thing wherever it appears (live detector by [Stage], verdict headers),
 * so a user learns it once and recognises it everywhere, in any language.
 */
data class SignalVisual(@param:DrawableRes val icon: Int, val label: String)

/** Live-detected tokens are grouped by the deterministic [Stage] the fired signal belongs to — the
 *  scam-script grammar the engine already scores against (HOOK→AUTHORITY→ISOLATION→ESCALATION→EXTRACTION). */
@Composable
fun signalVisualForStage(stage: Stage): SignalVisual = when (stage) {
    Stage.HOOK -> SignalVisual(R.drawable.ic_sig_parcel, stringResource(R.string.signal_hook))
    Stage.AUTHORITY -> SignalVisual(R.drawable.ic_sig_badge, stringResource(R.string.signal_authority))
    Stage.ISOLATION -> SignalVisual(R.drawable.ic_sig_secret, stringResource(R.string.signal_secrecy))
    Stage.ESCALATION -> SignalVisual(R.drawable.ic_sig_arrest, stringResource(R.string.signal_threat))
    Stage.EXTRACTION -> SignalVisual(R.drawable.ic_sig_money, stringResource(R.string.signal_money))
    Stage.NONE -> SignalVisual(R.drawable.ic_wave, stringResource(R.string.signal_listening))
}
