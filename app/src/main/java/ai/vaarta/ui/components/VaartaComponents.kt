package ai.vaarta.ui.components

import ai.vaarta.R
import ai.vaarta.ui.VaartaIcon
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Calm Guardian shared component set. Built once so the look cannot drift screen-to-screen:
 * one button, one tappable card, one source link, one back bar, one eyebrow, one empty state.
 * All colour flows from [VaartaTheme]; all text from [MaterialTheme.typography]. No emoji, no
 * hard-coded sizes.
 */

/** Primary filled action. Indigo (brand) by default; [destructive] switches to the risk-red for panic/scam. */
@Composable
fun VaartaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val c = VaartaTheme.colors
    val container = if (destructive) c.scam else c.indigo
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
    ) {
        if (leadingIcon != null) {
            VaartaIcon(leadingIcon, contentDescription = null, tint = Color.White, size = 20.dp)
            Spacer(Modifier.width(VSpace.sm))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/** Secondary action — outlined/tonal, quieter than the primary. */
@Composable
fun VaartaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
) {
    val c = VaartaTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
    ) {
        if (leadingIcon != null) {
            VaartaIcon(leadingIcon, contentDescription = null, tint = c.indigo, size = 20.dp)
            Spacer(Modifier.width(VSpace.sm))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.ink)
    }
}

enum class ChipTone { BRAND, NEUTRAL }

/**
 * A tappable row card with a leading icon chip — the premium replacement for the old bare-emoji card.
 * [ChipTone.BRAND] uses the indigo chrome tint (action cards); [ChipTone.NEUTRAL] stays quiet (content
 * rows). Risk-red is never used here — it is reserved for the panic/scam context only.
 */
@Composable
fun IconChipCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.BRAND,
) {
    val c = VaartaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "cardPress")
    val chipBg = if (tone == ChipTone.BRAND) c.indigoTint else c.track
    val chipTint = if (tone == ChipTone.BRAND) c.indigo else c.muted

    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) BorderStroke(1.dp, c.line) else null,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(VSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VSpace.md),
        ) {
            Surface(color = chipBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(icon, contentDescription = null, tint = chipTint, size = 22.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 20.dp)
        }
    }
}

/**
 * A half-width action tile (redesign spec §6.1): icon chip over a short title, no subtitle.
 * Two of these share a row under the wide primary card — the compact end of the tile grammar.
 */
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VaartaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "tilePress")

    Card(
        colors = CardDefaults.cardColors(containerColor = c.panel),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (c.isDark) 0.dp else 1.dp),
        border = if (c.isDark) BorderStroke(1.dp, c.line) else null,
        modifier = modifier
            .heightIn(min = 104.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(VSpace.lg), verticalArrangement = Arrangement.spacedBy(VSpace.md)) {
            Surface(color = c.indigoTint, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    VaartaIcon(icon, contentDescription = null, tint = c.indigo, size = 20.dp)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2)
        }
    }
}

/**
 * A quiet, centered text link — the replacement for a secondary-button clone of another screen's
 * primary action (redesign spec §6.2, e.g. "Get live help from VAARTA →"). No card, no border —
 * reads as a lightweight way out, not a competing call to action.
 */
@Composable
fun TextLinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VaartaTheme.colors
    Box(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = VSpace.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.indigo)
    }
}

/**
 * A compact tappable link row — leading icon + title (+ optional subtitle) + trailing chevron, no
 * card/border (redesign spec §6.5: "compact link-rows, not full-width buttons"). For entries that
 * are a destination or reference action rather than a screen's primary call to action.
 */
@Composable
fun LinkRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = VaartaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = VSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.md),
    ) {
        VaartaIcon(icon, contentDescription = null, tint = c.indigo, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        VaartaIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = c.faint, size = 18.dp)
    }
}

/** One cited web source: link glyph + title, tappable. Replaces the old copy-pasted "🔗 title" rows. */
@Composable
fun SourceLink(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VaartaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
    ) {
        VaartaIcon(R.drawable.ic_link_external, contentDescription = null, tint = c.verify, size = 16.dp)
        Text(title, style = MaterialTheme.typography.bodySmall, color = c.verify)
    }
}

/** One back affordance for every sub-screen; [trailing] hosts an optional end action (e.g. share). */
@Composable
fun VaartaBackBar(title: String?, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = VaartaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = VSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VSpace.sm),
    ) {
        Surface(
            color = Color.Transparent,
            shape = CircleShape,
            modifier = Modifier.size(44.dp).clickable(onClick = onBack),
        ) {
            Box(contentAlignment = Alignment.Center) {
                VaartaIcon(R.drawable.ic_arrow_left, contentDescription = "Back", tint = c.ink, size = 24.dp)
            }
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Small caps section/category label. */
@Composable
fun Eyebrow(text: String, color: Color = VaartaTheme.colors.muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** Centered empty-state — icon + one quiet line, replacing "text in a box". */
@Composable
fun EmptyState(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    val c = VaartaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(VSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VSpace.md),
    ) {
        VaartaIcon(icon, contentDescription = null, tint = c.faint, size = 32.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}
