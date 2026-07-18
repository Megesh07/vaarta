package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.core.reasoning.coverKeyForScamType
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bundled cover-illustration system (redesign spec §5): every scam category gets a
 * distinctive, offline, $0 duotone cover. The caller sizes it — 56dp square for feed/list
 * thumbnails, a 16:7 `fillMaxWidth` box for the featured card and article banner. Decorative:
 * `contentDescription = null` (the row/card text carries the meaning for TalkBack, spec §11).
 */
@Composable
fun ScamCover(scamType: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val res = when (coverKeyForScamType(scamType)) {
        "digital_arrest" -> R.drawable.cover_digital_arrest
        "upi" -> R.drawable.cover_upi
        "parcel" -> R.drawable.cover_parcel
        "kyc_bank" -> R.drawable.cover_kyc_bank
        "investment" -> R.drawable.cover_investment
        "job" -> R.drawable.cover_job
        "loan_app" -> R.drawable.cover_loan_app
        "lottery" -> R.drawable.cover_lottery
        "romance" -> R.drawable.cover_romance
        "utility" -> R.drawable.cover_utility
        else -> R.drawable.cover_generic
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(corner)),
    )
}
