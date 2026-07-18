package ai.vaarta.ui

import ai.vaarta.R
import ai.vaarta.i18n.AppLanguage
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * The Tier-1 language list (redesign spec §3B.1). Each label renders in its OWN script/style, never
 * translated into the current UI language — "English · हिन्दी · Hinglish" reads the same regardless
 * of what's currently selected, so a user who can't read the current language can still find their own.
 */
@Composable
fun LanguageOptionsList(onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VSpace.sm)) {
        for (language in AppLanguage.entries) {
            LanguageRow(language, onClick = { onSelect(language) })
        }
    }
}

@Composable
private fun LanguageRow(language: AppLanguage, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Surface(
        color = c.panel, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(VSpace.lg)) {
            Text(language.nativeLabel, style = MaterialTheme.typography.titleMedium, color = c.ink)
            if (language == AppLanguage.HINGLISH) {
                Text(
                    stringResource(R.string.language_hinglish_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
        }
    }
}

/**
 * One-time, full-screen, not skippable (spec §3B.1 — elder-friendly, an explicit choice rather than
 * a silent default). The title itself renders in English AND Hindi stacked, since a reader who can't
 * read either language yet still needs to recognise which row is theirs.
 */
@Composable
fun FirstRunLanguagePicker(onChosen: () -> Unit) {
    val c = VaartaTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(VSpace.xxl),
            verticalArrangement = Arrangement.Center,
        ) {
            VaartaIcon(R.drawable.ic_nav_shield, contentDescription = null, tint = c.indigo, size = 40.dp)
            Spacer(Modifier.height(VSpace.lg))
            Text(stringResource(R.string.language_picker_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text(stringResource(R.string.language_picker_title_hi), style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Spacer(Modifier.height(VSpace.xs))
            Text(stringResource(R.string.language_picker_first_run_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(VSpace.xxl))
            LanguageOptionsList(onSelect = { language ->
                onChosen()
                AppLanguage.apply(language) // AppCompatActivity recreates itself — no manual recreate()
            })
        }
    }
}

/** Compact "current language" row + label, shown once in Help; opens [LanguageOptionsList] in a sheet. */
@Composable
fun HelpLanguageRow(current: AppLanguage, onClick: () -> Unit) {
    val c = VaartaTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(stringResource(R.string.help_language_title), style = MaterialTheme.typography.titleMedium, color = c.ink)
        Text(
            stringResource(R.string.help_language_current, current.nativeLabel),
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
    }
}
