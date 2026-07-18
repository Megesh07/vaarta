package ai.vaarta.ui

import ai.vaarta.core.reasoning.MdBlock
import ai.vaarta.core.reasoning.MdSpan
import ai.vaarta.core.reasoning.parseMarkdown
import ai.vaarta.ui.theme.VSpace
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private fun List<MdSpan>.annotated(): AnnotatedString = buildAnnotatedString {
    this@annotated.forEach { s ->
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.SemiBold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
            ),
        ) { append(s.text) }
    }
}

/**
 * Renders AI prose from markdown into clean, type-scaled Compose text — no raw markers ever shown.
 * The parser ([parseMarkdown]) lives in core:reasoning (pure, unit-tested); this is only the render.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = VaartaTheme.colors.ink) {
    val blocks = parseMarkdown(text)
    Column(modifier) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) Spacer(Modifier.height(VSpace.sm))
            when (block) {
                is MdBlock.Heading -> Text(block.spans.annotated(), style = MaterialTheme.typography.titleMedium, color = color)
                is MdBlock.Paragraph -> Text(block.spans.annotated(), style = MaterialTheme.typography.bodyLarge, color = color)
                is MdBlock.Bullets -> block.items.forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge, color = color)
                        Text(item.annotated(), style = MaterialTheme.typography.bodyLarge, color = color)
                    }
                }
                is MdBlock.Numbered -> block.items.forEachIndexed { n, item ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${n + 1}.  ", style = MaterialTheme.typography.bodyLarge, color = color)
                        Text(item.annotated(), style = MaterialTheme.typography.bodyLarge, color = color)
                    }
                }
            }
        }
    }
}
