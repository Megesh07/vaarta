# VAARTA "Calm Guardian" Premium UI + Consistency Pass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing VAARTA v2 surface clean, consistent, sleek, and genuinely premium — replace all emoji-as-icons with a coherent hand-authored line-icon set, route every screen through the existing type scale, fix the raw-markdown-in-AI-text bug, unify duplicated micro-components, and consolidate three overlapping surfaces.

**Architecture:** Foundation-first. Build the primitives (glyphs, spacing tokens, `VaartaIcon`, `MarkdownText` + a pure parser, shared components) once, then apply them screen-by-screen. Pure logic (markdown parser) lives in `core:reasoning` and is TDD-tested; Compose visuals are verified by build + emulator screenshots (light + dark) since they have no meaningful unit test.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android vector drawables. No new dependencies.

## Global Constraints

- **Strictly $0** — no new paid APIs, no new libraries, no dependency additions (esp. NOT `material-icons-extended`). Offline. (ADR-0001)
- **Never add a Claude/AI Co-Authored-By trailer** to commits on this repo.
- **Color = risk only.** Chrome uses indigo + neutrals; the risk ramp (`observing/caution/high/scam/safe`) is the only loud set. `scam` red is reserved for panic/scam context and is never a chip/decoration.
- **Icon house style (verbatim):** `android:width/height="24dp"`, `viewportWidth/Height="24"`, `strokeWidth="1.75"`, `strokeLineCap="round"`, `strokeLineJoin="round"`, `fillColor="#00000000"`, stroke color a placeholder that is overridden at the call site by `Icon(painter, tint = …)`.
- **Elder-first:** ≥48dp tap targets, body text ≥15sp, no thin low-contrast text, no flashing.
- **Emoji allowed ONLY** in user-authored shared strings: `WARN_FAMILY_MESSAGE`, `warnFamilyText()`, and attachment content markers ("📷 Photo" / "🎧 Audio clip"). Everywhere else in the interface: real icons.
- **All text** flows through `MaterialTheme.typography.*` (defined in `app/src/main/java/ai/vaarta/ui/theme/Type.kt`). No inline `fontSize`/`fontWeight` left in screen files.
- **Verification bar:** `gradlew :app:assembleDebug` green + `gradlew test` green + emulator screenshots (light AND dark) before any task claiming a visible surface is "done".

**Build/verify commands (this machine):**
- Compile: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :core:reasoning:test`
- JDK 17 required for compile; SDK at `C:/Users/Meges/AppData/Local/Android/Sdk`. Emulator AVD `vaarta_test`. See PROJECT_STATUS.md for the exact env.

---

## Phase 1 — Foundation

### Task 1: Markdown parser (pure, TDD)

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/Markdown.kt`
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/MarkdownTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class MdSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false)
  sealed interface MdBlock {
      data class Paragraph(val spans: List<MdSpan>) : MdBlock
      data class Heading(val spans: List<MdSpan>) : MdBlock          // any #-level → one bold line
      data class Bullets(val items: List<List<MdSpan>>) : MdBlock    // "- " / "* " lines
      data class Numbered(val items: List<List<MdSpan>>) : MdBlock   // "1." lines
  }
  fun parseMarkdown(src: String): List<MdBlock>
  ```
- Consumes: nothing.

- [ ] **Step 1: Write failing tests**

```kotlin
package ai.vaarta.core.reasoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTest {
    @Test fun plainTextIsOneParagraph() {
        val b = parseMarkdown("Just plain text.")
        assertEquals(listOf(MdBlock.Paragraph(listOf(MdSpan("Just plain text.")))), b)
    }

    @Test fun boldInlineIsSplitIntoSpans() {
        val b = parseMarkdown("Be **very** careful")
        assertEquals(
            MdBlock.Paragraph(listOf(MdSpan("Be "), MdSpan("very", bold = true), MdSpan(" careful"))),
            b.single(),
        )
    }

    @Test fun italicWithSingleAsterisk() {
        val b = parseMarkdown("this is *important* ok")
        assertEquals(
            MdBlock.Paragraph(listOf(MdSpan("this is "), MdSpan("important", italic = true), MdSpan(" ok"))),
            b.single(),
        )
    }

    @Test fun headingBecomesHeadingBlockWithoutHashes() {
        val b = parseMarkdown("## What to do")
        assertEquals(MdBlock.Heading(listOf(MdSpan("What to do"))), b.single())
    }

    @Test fun dashBulletsAreCollected() {
        val b = parseMarkdown("- first\n- second")
        assertEquals(
            MdBlock.Bullets(listOf(listOf(MdSpan("first")), listOf(MdSpan("second")))),
            b.single(),
        )
    }

    @Test fun numberedListIsCollected() {
        val b = parseMarkdown("1. one\n2. two")
        assertEquals(
            MdBlock.Numbered(listOf(listOf(MdSpan("one")), listOf(MdSpan("two")))),
            b.single(),
        )
    }

    @Test fun noRawMarkersSurviveAnywhere() {
        val src = "# Title\n\nText with **bold**, *em*, and a `code` bit.\n- item **x**\n> quote"
        val flat = parseMarkdown(src).joinToString(" ") { block ->
            when (block) {
                is MdBlock.Paragraph -> block.spans.joinToString("") { it.text }
                is MdBlock.Heading -> block.spans.joinToString("") { it.text }
                is MdBlock.Bullets -> block.items.joinToString(" ") { it.joinToString("") { s -> s.text } }
                is MdBlock.Numbered -> block.items.joinToString(" ") { it.joinToString("") { s -> s.text } }
            }
        }
        assertTrue("no ** left") { !flat.contains("**") }
        assertTrue("no leading # left") { !flat.contains("#") }
        assertTrue("no backticks left") { !flat.contains("`") }
        assertTrue("no blockquote marker left") { !flat.trimStart().startsWith(">") }
    }

    @Test fun blankInputIsEmpty() = assertEquals(emptyList(), parseMarkdown("   "))
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.MarkdownTest"`
Expected: FAIL (unresolved reference `parseMarkdown` / `MdBlock`).

- [ ] **Step 3: Implement the parser**

```kotlin
package ai.vaarta.core.reasoning

/** One run of inline text with optional emphasis. */
data class MdSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/** A rendered block. Deliberately tiny — covers exactly the markdown Gemini emits in prose. */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val spans: List<MdSpan>) : MdBlock
    data class Bullets(val items: List<List<MdSpan>>) : MdBlock
    data class Numbered(val items: List<List<MdSpan>>) : MdBlock
}

private val BULLET = Regex("""^\s*[-*]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*\d+[.)]\s+(.*)$""")
private val HEADING = Regex("""^\s*#{1,6}\s+(.*)$""")

/**
 * A deliberately small, dependency-free markdown reader for AI prose. Handles what Gemini actually
 * produces — bold/italic inline, dash/asterisk bullets, numbered lists, ATX headings — and strips
 * anything else (backticks, blockquote `>`, stray markers) so no raw markup ever reaches the UI.
 */
fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = ArrayList<MdBlock>()
    val para = ArrayList<String>()
    var bullets: ArrayList<List<MdSpan>>? = null
    var numbered: ArrayList<List<MdSpan>>? = null

    fun flushPara() {
        if (para.isNotEmpty()) {
            out += MdBlock.Paragraph(parseInline(para.joinToString(" ")))
            para.clear()
        }
    }
    fun flushLists() {
        bullets?.let { if (it.isNotEmpty()) out += MdBlock.Bullets(it) }; bullets = null
        numbered?.let { if (it.isNotEmpty()) out += MdBlock.Numbered(it) }; numbered = null
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> { flushPara(); flushLists() }
            HEADING.matches(line) -> {
                flushPara(); flushLists()
                out += MdBlock.Heading(parseInline(HEADING.find(line)!!.groupValues[1]))
            }
            BULLET.matches(line) -> {
                flushPara(); numbered?.let { flushLists() }
                (bullets ?: ArrayList<List<MdSpan>>().also { bullets = it })
                    .add(parseInline(BULLET.find(line)!!.groupValues[1]))
            }
            NUMBERED.matches(line) -> {
                flushPara(); bullets?.let { flushLists() }
                (numbered ?: ArrayList<List<MdSpan>>().also { numbered = it })
                    .add(parseInline(NUMBERED.find(line)!!.groupValues[1]))
            }
            else -> { flushLists(); para += line }
        }
    }
    flushPara(); flushLists()
    return out
}

/** Inline pass: **bold**, *italic*, drop stray backticks and blockquote markers. */
private fun parseInline(textIn: String): List<MdSpan> {
    val text = textIn.replace("`", "").removePrefix(">").trimStart()
    val spans = ArrayList<MdSpan>()
    var i = 0
    val sb = StringBuilder()
    fun push(bold: Boolean = false, italic: Boolean = false, s: String) {
        if (s.isNotEmpty()) spans += MdSpan(s, bold, italic)
    }
    fun flushPlain() { if (sb.isNotEmpty()) { push(s = sb.toString()); sb.clear() } }

    while (i < text.length) {
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 1) { flushPlain(); push(bold = true, s = text.substring(i + 2, end)); i = end + 2; continue }
        }
        if (text[i] == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > i) { flushPlain(); push(italic = true, s = text.substring(i + 1, end)); i = end + 1; continue }
        }
        sb.append(text[i]); i++
    }
    flushPlain()
    return if (spans.isEmpty()) listOf(MdSpan(text)) else spans
}
```

- [ ] **Step 4: Run tests, verify they pass**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.MarkdownTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/Markdown.kt core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/MarkdownTest.kt
git commit -m "core:reasoning — add dependency-free markdown parser for AI prose (TDD)"
```

---

### Task 2: `MarkdownText` composable

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/MarkdownText.kt`

**Interfaces:**
- Consumes: `parseMarkdown`, `MdBlock`, `MdSpan` (Task 1); `VaartaTheme`, `MaterialTheme.typography`.
- Produces: `@Composable fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = VaartaTheme.colors.ink)`.

- [ ] **Step 1: Implement**

```kotlin
package ai.vaarta.ui

import ai.vaarta.core.reasoning.MdBlock
import ai.vaarta.core.reasoning.MdSpan
import ai.vaarta.core.reasoning.parseMarkdown
import ai.vaarta.ui.theme.VaartaTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
        withStyle(SpanStyle(
            fontWeight = if (s.bold) FontWeight.SemiBold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
        )) { append(s.text) }
    }
}

/** Renders AI prose from markdown into clean, type-scaled Compose text — no raw markers ever shown. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = VaartaTheme.colors.ink) {
    val blocks = parseMarkdown(text)
    Column(modifier) {
        blocks.forEachIndexed { i, block ->
            if (i > 0) Text("", style = MaterialTheme.typography.bodyMedium) // spacer line
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
```

- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add app/src/main/java/ai/vaarta/ui/MarkdownText.kt && git commit -m "app — MarkdownText composable renders AI prose via the type scale"`

---

### Task 3: Spacing tokens + `VaartaIcon` helper

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/theme/Spacing.kt`
- Create: `app/src/main/java/ai/vaarta/ui/VaartaIcon.kt`

**Interfaces:**
- Produces: `object VSpace { val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp; val xl=20.dp; val xxl=24.dp; val xxxl=32.dp }`
- Produces: `@Composable fun VaartaIcon(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = VaartaTheme.colors.ink, size: Dp = 24.dp)`

- [ ] **Step 1: Write `Spacing.kt`**

```kotlin
package ai.vaarta.ui.theme

import androidx.compose.ui.unit.dp

/** The one spacing scale (design system §6). Screens use these, never ad-hoc dp values. */
object VSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}
```

- [ ] **Step 2: Write `VaartaIcon.kt`**

```kotlin
package ai.vaarta.ui

import ai.vaarta.ui.theme.VaartaTheme
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every VAARTA line icon is drawn through here so the tint always comes from a token, never a raw hex. */
@Composable
fun VaartaIcon(
    @DrawableRes res: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = VaartaTheme.colors.ink,
    size: Dp = 24.dp,
) {
    Icon(painterResource(res), contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}
```
(Add `import androidx.compose.foundation.layout.size`.)

- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug` → SUCCESS.
- [ ] **Step 4: Commit** — `git commit -am "app — spacing scale + VaartaIcon token-tinted icon helper"`

---

### Task 4: Author the ~20 vector glyphs

**Files (Create, all in `app/src/main/res/drawable/`):** `ic_nav_shield.xml`, `ic_nav_chat.xml`, `ic_nav_help.xml`, `ic_phone.xml`, `ic_globe.xml`, `ic_file_text.xml`, `ic_bell.xml`, `ic_headphones.xml`, `ic_image.xml`, `ic_download.xml`, `ic_alert_triangle.xml`, `ic_sparkle.xml`, `ic_check.xml`, `ic_chevron_right.xml`, `ic_arrow_left.xml`, `ic_close.xml`, `ic_link_external.xml`, `ic_siren.xml`.

- [ ] **Step 1:** Author each in the house style (Global Constraints). Template (this is `ic_arrow_left.xml`):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
  <path android:strokeColor="#1B1826" android:strokeWidth="1.75" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="M15,5 L8,12 L15,19"/>
</vector>
```
Draw the remaining glyphs as clean Lucide-equivalents on the 24-grid (chevron-right = `M9,5 L16,12 L9,19`; check = `M5,13 l4,4 l10,-11`; close = `M6,6 L18,18 M18,6 L6,18`; phone/globe/etc. modeled on Lucide paths). Keep each to 1–3 paths. `strokeColor` value is irrelevant (overridden by tint) but keep `#1B1826` for editor legibility.

- [ ] **Step 2: Verify they render** — build, then in a scratch preview or the running app confirm each glyph is centered and legible at 24dp. (During screen tasks each is seen in context.)
- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug` → SUCCESS (vector XML compiles).
- [ ] **Step 4: Commit** — `git add app/src/main/res/drawable/ic_*.xml && git commit -m "app — author line-icon glyphs (nav/actions) in the house style"`

---

### Task 5: Shared components — button, icon-chip card, SourceLink, BackBar, Eyebrow, EmptyState

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/components/VaartaComponents.kt`

**Interfaces (Produces):**
```kotlin
@Composable fun VaartaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes leadingIcon: Int? = null, enabled: Boolean = true, destructive: Boolean = false)
@Composable fun VaartaSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes leadingIcon: Int? = null, enabled: Boolean = true)
enum class ChipTone { BRAND, NEUTRAL }
@Composable fun IconChipCard(@DrawableRes icon: Int, title: String, subtitle: String?, onClick: () -> Unit, tone: ChipTone = ChipTone.BRAND, modifier: Modifier = Modifier)
@Composable fun SourceLink(title: String, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun VaartaBackBar(title: String?, onBack: () -> Unit)
@Composable fun Eyebrow(text: String, color: Color = VaartaTheme.colors.muted)
@Composable fun EmptyState(@DrawableRes icon: Int, text: String)
```

- [ ] **Step 1: Implement** all of the above. Key rules: buttons `heightIn(min = 52.dp)`, filled uses `c.indigo` (or `c.scam` when `destructive`), leading icon via `VaartaIcon(tint = onColor)`; `IconChipCard` chip is a 40dp `Surface(shape = RoundedCornerShape(12.dp))` with bg `c.indigoTint` (BRAND) or `c.line`/neutral (NEUTRAL) holding `VaartaIcon(tint = indigo/muted)`, trailing `ic_chevron_right` tinted `muted`; `SourceLink` = Row(`ic_link_external` 16dp tint `verify` + Text title `bodySmall` color `verify`) clickable; `VaartaBackBar` = Row(`ic_arrow_left` + optional title `titleLarge`) height 56dp; `Eyebrow` = Text `labelSmall` uppercase; `EmptyState` = centered Column(icon 32dp tint `faint` + Text `bodyMedium` `muted`). All text uses `MaterialTheme.typography.*`.
- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug` → SUCCESS.
- [ ] **Step 3: Commit** — `git add app/src/main/java/ai/vaarta/ui/components/VaartaComponents.kt && git commit -m "app — shared Calm Guardian components (button, icon-chip card, source link, back bar, eyebrow, empty state)"`

---

## Phase 2 — Apply to screens

> For every screen task: swap emoji → `VaartaIcon`/component; replace inline `fontSize`/`fontWeight` with `MaterialTheme.typography.*`; replace ad-hoc `Spacer(n.dp)` with `VSpace.*`; use shared components. Each task ends with: build green, then install + screenshot the screen in **light and dark** on the `vaarta_test` emulator, then commit.

### Task 6: Bottom nav (`VaartaNav.kt`)
- [ ] Replace the three `icon = { Text("🛡️") }` etc. with `VaartaIcon(R.drawable.ic_nav_shield, "Home", size = 24.dp)` (and `ic_nav_chat`, `ic_nav_help`). Let Material3 handle the selected pill/tint. Build → screenshot (light+dark) → commit.

### Task 7: Home (`HomeScreen.kt`)
- [ ] Header title → `headlineMedium`, subtitle → `bodyMedium`.
- [ ] Panic card: `🚨` → `VaartaIcon(R.drawable.ic_siren, tint = Color.White, size = 34.dp)`; titles → typography; keep `c.scam`.
- [ ] `ActionCard(emoji=…)` → `IconChipCard(icon = R.drawable.ic_mic/ic_nav_chat/ic_headphones, tone = BRAND)`; drop the `›` Text (chip card provides chevron).
- [ ] `AwarenessCardRow` → neutral-tone card row: eyebrow via `Eyebrow`, chevron icon; scam-type uses `Eyebrow`.
- [ ] Feed section title → `titleLarge`; helper → `bodySmall`; empty card → `EmptyState(R.drawable.ic_globe, …)`.
- [ ] Panic sheet buttons → `VaartaButton`/`VaartaSecondaryButton` with icons; step badges kept.
- [ ] Spacing → `VSpace`. Build → screenshot (light+dark) → commit.

### Task 8: Help (`HelpScreen.kt`)
- [ ] All four buttons → `VaartaButton`(Call 1930 `destructive=true`, icon `ic_phone`) / `VaartaSecondaryButton`(`ic_globe`, `ic_file_text`, `ic_bell`).
- [ ] `HelpSection` title → `titleLarge`; body copy → `bodyMedium`; `StepRow` keeps badge but text → `bodyLarge`.
- [ ] Complaint card share/export → `VaartaButton`/secondary. Spacing → `VSpace`. Build → screenshot (light+dark) → commit.

### Task 9: Article (`ArticleScreen.kt`) + markdown
- [ ] `"‹ Back"` → `VaartaBackBar(title = null, onBack)`.
- [ ] Summary body `Text(summary?.text…)` → `MarkdownText(summary?.text.orEmpty())` (fixes markdown bug on this screen).
- [ ] Source rows → `SourceLink`; "Sources" label → `Eyebrow`.
- [ ] Buttons → `VaartaButton`("Ask VAARTA about this", `ic_sparkle`) / `VaartaSecondaryButton`("Warn my family", `ic_bell`). Emoji stays only inside `warnFamilyText()`.
- [ ] Titles/eyebrows → typography; spacing → `VSpace`. Build → screenshot (light+dark) → commit.

### Task 10: Chat (`ConversationScreen.kt` + `ChatView.kt`) + markdown
- [ ] `ChatView.AssistantBubble`: `Text(item.text…)` → `MarkdownText(item.text)` (fixes markdown bug in chat). Label `🛡️ VAARTA` → `VaartaIcon(ic_sparkle,16dp,indigo)` + Text.
- [ ] `CoachBubble`: `🌐`/`⚠️` markers → `VaartaIcon(ic_globe/ic_alert_triangle)`; warning text via `MarkdownText`; source rows → `SourceLink`; "SAY THIS" → `Eyebrow`.
- [ ] `ScamIdCard` + `AssistantBubble`/`CoachBubble` source rows all use `SourceLink` (removes the 4× duplication). `ReplyLine` `❝ ❞` kept (typographic, not emoji).
- [ ] `ConversationScreen` composer: `🎤 🖼️ 🎧` → `VaartaIcon(ic_mic/ic_image/ic_headphones, tint=muted, 22dp)`; context header `🌐`/`⬇` → `VaartaIcon(ic_globe/ic_download)`; empty-state `🛡️` → `EmptyState(ic_nav_shield, …)`. Attachment chip `✕` → `ic_close`. Attachment *content* labels ("📷 Photo") stay (shared text).
- [ ] All text → typography; spacing → `VSpace`. Build → screenshot (light+dark) → commit.

### Task 11: Live + Analyze + Panic + permission screen + ChatView banner
- [ ] `RiskHero.kt`: `⚠ Flagged…` → `VaartaIcon(ic_alert_triangle,16dp,muted)` + Text `bodySmall`.
- [ ] `ChatView.StatusBanner`: `⚠` → `ic_alert_triangle` (white tint).
- [ ] `MainActivity.kt` live controls + permission screen: `🎙 🪟 🎧 🔊 🔔 🤖 ✕ ❝❞ ✓` → icons (`ic_mic`, `ic_history`/window, `ic_headphones`, speaker→`ic_alert_triangle` or drop, `ic_bell`, `ic_sparkle`, `ic_close`, `ic_check`); source-type glyphs (`💬 🎧 📞`) → `ic_nav_chat`/`ic_headphones`/`ic_phone`. Buttons → `VaartaButton`. Text → typography.
- [ ] `AnalyzeScreen`: icon/button/spacing pass.
- [ ] **§9 consolidation:** extract the "right-now emergency steps" used by Home's panic sheet and Help's "If this is happening now" into ONE composable in `components/` and call it from both. Extract the web-grounded scam-ID into ONE presentation (prefer `ScamIdCard`; `CoachBubble` reuses it). Build → screenshot (light+dark) → commit.

### Task 12: Overlay (`OverlayService.kt`)
- [ ] Any emoji in the floating panel → icons; buttons → `VaartaButton`; text → typography; spacing → `VSpace`. Keep the Phase-5 drag/resize/edge-snap behavior untouched. Build → install → screenshot the expanded overlay (light+dark) → commit.

---

## Phase 3 — Sweep & verify

### Task 13: Type-scale + emoji + markdown sweep
- [ ] **Emoji grep:** `grep -rEn "[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}]" app/src/main/java` → only the §4.3 whitelist (`ChatAttachment`, `ArticleScreen.warnFamilyText`, `HelpScreen.WARN_FAMILY_MESSAGE`, `ConversationScreen` attach labels) remains.
- [ ] **Hardcoded-size grep:** `grep -rn "fontSize =" app/src/main/java/ai/vaarta/ui app/src/main/java/ai/vaarta/*.kt` → none outside `theme/`. Fix stragglers.
- [ ] **Markdown on device:** open a chat, ask a question that yields a bold/bulleted answer; confirm no `**`/`#`/`-` characters appear. Screenshot.
- [ ] Fix anything found; commit.

### Task 14: Full verification + status + commit
- [ ] `./gradlew :core:reasoning:test` → all green (incl. `MarkdownTest`).
- [ ] `./gradlew :app:assembleDebug` → SUCCESS.
- [ ] Install; walk every screen in light AND dark; capture the before/after screenshot set as evidence.
- [ ] TalkBack spot-check: icon-only controls announce; decorative icons are `contentDescription = null`.
- [ ] Update `PROJECT_STATUS.md` (status matrix + change log, dated 2026-07-16) and `docs/superpowers/plans/2026-07-16-vaarta-ui-premium-pass.md` checkboxes.
- [ ] Commit (no Claude co-author).

---

## Self-Review (against the spec)

- **§4 icons** → Tasks 3–4, applied 6–12. ✓
- **§5 markdown** → Tasks 1–2, applied 9–11. ✓
- **§6 type/spacing** → Task 3 + every screen task + Task 13 sweep. ✓
- **§7 shared components** → Task 5, applied throughout. ✓
- **§8 motion** → folded into `VaartaButton`/`IconChipCard` (press state) in Task 5; sub-screen transitions are optional polish (revisit in the "make it perfect" pass, not blocking). ✓
- **§9 simplification** → Task 11 (emergency steps + scam-ID consolidation) and `VaartaBackBar` (Tasks 5/9). ✓
- **§10 inventory** → Tasks 6–12 cover every listed file. ✓
- **§11 verification** → Tasks 13–14. ✓
- Types consistent across tasks (`MdBlock`/`MdSpan`/`parseMarkdown`, `ChipTone`, component signatures). ✓
- No placeholders; every code step shows code. Icon `pathData` for the less-obvious glyphs is left to authoring in Task 4 with an explicit template + per-glyph hints (acceptable: they are trivial vector art, not logic).
