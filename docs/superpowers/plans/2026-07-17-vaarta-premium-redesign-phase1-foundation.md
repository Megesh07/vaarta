# VAARTA Premium Redesign — Phase 1: Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the structural foundation of the premium redesign: India-anchored prompts (tested), correct edge-to-edge/system-bar behavior, a shared sub-screen scaffold that makes inset/back bugs impossible, and an India-locale relative-time formatter wired into the worst visible misalignment.

**Architecture:** Spec `docs/superpowers/specs/2026-07-17-vaarta-premium-redesign-design.md` §3A.1, §8, §12 phase 1. Pure logic goes in `core:reasoning` (JVM-tested); prompt constants and UI in `app`. The app module gains a JVM unit-test source set (prompts are pure Kotlin objects).

**Tech Stack:** Kotlin, Jetpack Compose (M3), JUnit4, Gradle wrapper with JDK 17 (`JAVA_HOME=C:\Users\Meges\AppData\Local\Programs\jdk17\jdk-17.0.19+10`).

## Global Constraints

- Strict $0 — no new paid deps; JUnit for app tests is free and already in the version catalog.
- No engine/AI/data behavior changes beyond appending the India block to prompts.
- `LANGUAGE_REMINDER` must remain the LAST element wherever prompts are assembled (2026-07-15 recency lesson) — the India block goes into the system INSTRUCTION constants, never appended after the reminder.
- All commands from repo root in Git Bash: `export JAVA_HOME='C:\Users\Meges\AppData\Local\Programs\jdk17\jdk-17.0.19+10'` first.
- Commit style: `area — what changed; why` (match repo history). No AI co-author trailers.
- **String convention (for phases 2–7, established here):** new/rewritten screen copy goes to `app/src/main/res/values/strings.xml` with `stringResource(R.string.…)`; names `screen_element` (e.g. `home_panic_title`). Phase 8 enforces zero hardcoded UI strings repo-wide.

---

### Task 1: India anchor block in every user-facing prompt, with tests

**Files:**
- Create: `app/src/main/java/ai/vaarta/ai/IndiaContext.kt`
- Create: `app/src/test/java/ai/vaarta/ai/IndiaContextTest.kt`
- Modify: `app/build.gradle.kts` (add `testImplementation(libs.junit)`)
- Modify: `app/src/main/java/ai/vaarta/ai/ChatPrompt.kt`, `AwarenessPrompt.kt`, `CoachPrompt.kt`, `AudioAnalyzePrompt.kt`, `SharedScamPrompt.kt` (append block to each system-instruction constant)

**Interfaces:**
- Produces: `IndiaContext.BLOCK: String` — the single India-anchor constant every prompt must contain.

- [ ] **Step 1: Add the test dependency**

In `app/build.gradle.kts` `dependencies { }`, after `implementation(libs.okhttp)` add:

```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/ai/vaarta/ai/IndiaContextTest.kt`:

```kotlin
package ai.vaarta.ai

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec §3A.1: every user-facing prompt carries the one shared India context block, so
 * India-first can never silently drift out of an AI surface.
 */
class IndiaContextTest {

    private val userFacingPrompts = mapOf(
        "ChatPrompt" to ChatPrompt.INSTRUCTION,
        "AwarenessPrompt.FEED" to AwarenessPrompt.FEED,
        "AwarenessPrompt.SUMMARY_SYSTEM" to AwarenessPrompt.SUMMARY_SYSTEM,
        "CoachPrompt" to CoachPrompt.INSTRUCTION,
        "AudioAnalyzePrompt" to AudioAnalyzePrompt.INSTRUCTION,
        "SharedScamPrompt" to SharedScamPrompt.INSTRUCTION,
    )

    @Test
    fun `every user-facing prompt contains the India anchor block`() {
        for ((name, prompt) in userFacingPrompts) {
            assertTrue("$name is missing IndiaContext.BLOCK", prompt.contains(IndiaContext.BLOCK))
        }
    }

    @Test
    fun `the anchor block pins the Indian help rail and forbids foreign resources`() {
        for (required in listOf("1930", "cybercrime.gov.in", "Sanchar Saathi", "₹", "911", "UPI")) {
            assertTrue("anchor block must mention $required", IndiaContext.BLOCK.contains(required))
        }
    }
}
```

(`SharedScamPrompt.INSTRUCTION` — check the actual constant name in `SharedScamPrompt.kt` first; if it differs (e.g. `PROMPT`), use the real name in both test and step 4.)

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ai.vaarta.ai.IndiaContextTest' --console=plain`
Expected: FAIL — `Unresolved reference: IndiaContext` (compile error counts as the failing state).

- [ ] **Step 4: Implement**

`app/src/main/java/ai/vaarta/ai/IndiaContext.kt`:

```kotlin
package ai.vaarta.ai

/**
 * The one shared India anchor (spec §3A.1). Appended to every user-facing system instruction so
 * India-first is a tested contract, not a habit. Language-independent: names, numbers, and rails
 * stay as-is in every reply language. [IndiaContextTest] asserts every prompt contains it.
 */
object IndiaContext {
    val BLOCK =
        """
        INDIA CONTEXT (always applies):
        - The user is in India. All advice, examples, institutions, and resources must be Indian:
          police/CBI/ED/RBI/TRAI/I4C/SEBI, Indian banks, UPI/IMPS/NEFT, OTPs, Aadhaar/PAN/KYC/SIM.
        - The help rail is: call 1930 (national cyber-crime helpline, free, 24x7), file at
          cybercrime.gov.in, report the fraud number/SMS at Sanchar Saathi (sancharsaathi.gov.in).
        - Money is in rupees (₹); lakh/crore phrasing is natural. Phone numbers read as +91.
        - NEVER suggest non-Indian resources (911, FTC, Action Fraud, IC3, or any foreign
          helpline/agency) — they are wrong for this user.
        - Keep these untranslated in any language: 1930, cybercrime.gov.in, Sanchar Saathi, UPI,
          OTP, ₹.
        """.trimIndent()
}
```

Then append to each system-instruction constant. Pattern (same for all five files) — for string templates built with `"""…""".trimIndent()`, change:

```kotlin
    val INSTRUCTION =
        """
        …existing text…
        """.trimIndent()
```

to:

```kotlin
    val INSTRUCTION =
        """
        …existing text…
        """.trimIndent() + "\n\n" + IndiaContext.BLOCK
```

Apply to: `ChatPrompt.INSTRUCTION`, `AwarenessPrompt.FEED`, `AwarenessPrompt.SUMMARY_SYSTEM`, `CoachPrompt.INSTRUCTION`, `AudioAnalyzePrompt.INSTRUCTION`, `SharedScamPrompt` (its instruction constant). Do NOT touch `ChatPrompt.LANGUAGE_REMINDER` or `AwarenessPrompt.summaryQuery`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ai.vaarta.ai.IndiaContextTest' --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 6: Full build + commit**

Run: `./gradlew :app:assembleDebug --console=plain` → BUILD SUCCESSFUL

```bash
git add app/build.gradle.kts app/src/main/java/ai/vaarta/ai/ app/src/test/
git commit -m "app:ai — India anchor block appended to every user-facing prompt; tested contract (spec §3A.1); app module gains JVM unit tests"
```

---

### Task 2: Edge-to-edge with correct system-bar icon appearance

**Files:**
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (`onCreate`)

**Interfaces:**
- Consumes: `androidx.activity.enableEdgeToEdge` (already on classpath via activity-compose).
- Produces: correct dark-icons-on-light / light-icons-on-dark status + nav bars app-wide.

- [ ] **Step 1: Enable edge-to-edge**

In `MainActivity.onCreate`, before `setContent`:

```kotlin
import androidx.activity.enableEdgeToEdge
…
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // system-bar icons follow light/dark correctly (spec §8.1)
        setContent {
```

- [ ] **Step 2: Build, install, verify on the emulator**

Run: `./gradlew :app:assembleDebug --console=plain`, then boot `vaarta_test` (headless script in PROJECT_STATUS §3), `adb install -r`, launch, `adb shell screencap`.
Expected: on the light theme the clock/wifi/battery icons are **dark and legible** (they were white-on-light before). Bottom gesture bar area shows no opaque scrim regression.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ai/vaarta/MainActivity.kt
git commit -m "app — enableEdgeToEdge: status-bar icons legible on light theme (spec §8.1; was white-on-light)"
```

---

### Task 3: `VaartaSubScreen` scaffold + system back everywhere

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/components/VaartaSubScreen.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/ArticleScreen.kt` (adopt scaffold — fixes status-bar overlap)
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (`AnalyzeScreen` adopts scaffold; `VaartaScreen` gets `BackHandler`)
- Modify: `app/src/main/java/ai/vaarta/ui/ConversationScreen.kt` (gets `BackHandler`)

**Interfaces:**
- Produces:
  `@Composable fun VaartaSubScreen(title: String?, onBack: () -> Unit, modifier: Modifier = Modifier, scrollable: Boolean = true, bottomContent: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`
  — owns `BackHandler`, background `Surface`, `statusBarsPadding`, `VaartaBackBar`, screen padding (`VSpace.xl` horizontal), and (when `scrollable`) the scroll column. Later phases build every sub-screen on this.

- [ ] **Step 1: Create the scaffold**

`app/src/main/java/ai/vaarta/ui/components/VaartaSubScreen.kt`:

```kotlin
package ai.vaarta.ui.components

import ai.vaarta.ui.theme.VSpace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one sub-screen frame (spec §8.1–§8.3). Every full-screen sub-surface renders inside this, so
 * a screen *cannot* forget the status bar, the back affordance, or system-back handling — the
 * Article-under-the-clock and back-exits-the-app bugs become impossible by construction.
 */
@Composable
fun VaartaSubScreen(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack) // system back == the back arrow, never app exit (spec §8.2)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            VaartaBackBar(title = title, onBack = onBack)
            val body: @Composable ColumnScope.() -> Unit = content
            if (scrollable) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VSpace.xl),
                ) { body() }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) { body() }
            }
            if (bottomContent != null) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = VSpace.xl, vertical = VSpace.md),
                ) { bottomContent() }
            }
        }
    }
}
```

- [ ] **Step 2: Adopt in `ArticleScreen`**

Replace the outer structure (`Surface { Column { Column(scroll…) { VaartaBackBar…` and the bottom actions `Column`) with:

```kotlin
    VaartaSubScreen(
        title = null,
        onBack = onBack,
        modifier = modifier,
        bottomContent = {
            VaartaButton(
                text = "Ask VAARTA about this",
                onClick = { onAskAbout(seedContext) },
                leadingIcon = R.drawable.ic_sparkle,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(VSpace.sm))
            VaartaSecondaryButton(
                text = "Warn my family",
                onClick = { onShare(warnFamilyText(card, summary)) },
                leadingIcon = R.drawable.ic_bell,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        // existing banner Card + loading/summary/sources content, unchanged, with the previous
        // Arrangement.spacedBy(VSpace.md) recreated via Spacer(Modifier.height(VSpace.md)) between
        // the banner and the summary block (content order otherwise identical; the inner
        // VaartaBackBar call is REMOVED — the scaffold owns it now)
    }
```

(The two stacked bottom buttons become a single primary + share-icon in Phase 4 — this task only re-frames the screen; don't redesign it here.)

- [ ] **Step 3: Adopt in `AnalyzeScreen`; add `BackHandler` to `VaartaScreen` + `ConversationScreen`**

`AnalyzeScreen` (MainActivity.kt): replace `Surface { Column(…statusBarsPadding().verticalScroll…) { VaartaBackBar(…)` with `VaartaSubScreen(title = "Analyze a recording", onBack = onBack, ) { …existing state whens, minus the VaartaBackBar call… }` (keep the existing `Arrangement.spacedBy` by inserting `Spacer(Modifier.height(VSpace.md))` between state blocks as needed — visual parity, not redesign).

`VaartaScreen` (custom header row, keeps its structure) — first line of the composable body:

```kotlin
    androidx.activity.compose.BackHandler(onBack = onBack)
```

`ConversationScreen` — same one-liner before its `Surface`:

```kotlin
    androidx.activity.compose.BackHandler(onBack = onBack)
```

- [ ] **Step 4: Build + emulator verification**

Run: `./gradlew :app:assembleDebug --console=plain`, install, launch. With adb:
1. Home → trending card → Article: screenshot — back arrow now **below** the status bar (was overlapping the clock).
2. `adb shell input keyevent 4` on Article → returns to **Home** (app still foreground).
3. Home → Help me on a call → keyevent 4 → Home (was: app exited).
4. Home → Ask VAARTA → keyevent 4 → Conversations tab per its onBack (app still foreground).

Expected: app never leaves the foreground on system back from a sub-screen; `dumpsys activity activities | grep -c ai.vaarta` still shows the task.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ai/vaarta/ui/components/VaartaSubScreen.kt app/src/main/java/ai/vaarta/ui/ArticleScreen.kt app/src/main/java/ai/vaarta/MainActivity.kt app/src/main/java/ai/vaarta/ui/ConversationScreen.kt
git commit -m "app:ui — VaartaSubScreen scaffold (insets+back-bar+BackHandler by construction); fixes Article under status bar and system-back exiting the app (spec §8.1-8.3)"
```

---

### Task 4: India-locale relative time (core, TDD) + wire the Conversations date

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/RelativeTime.kt`
- Create: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/RelativeTimeTest.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (`HistoryRow` — replace `historyDateFmt`)

**Interfaces:**
- Produces: `fun relativeTimeLabel(thenMs: Long, nowMs: Long): String` (top-level, package `ai.vaarta.core.reasoning`) — "11:41 pm" (same day) · "Yesterday" (previous day) · "16 Jul" (same year) · "16 Jul 2025" (older). en-IN month/day forms.

- [ ] **Step 1: Write the failing tests**

`core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/RelativeTimeTest.kt`:

```kotlin
package ai.vaarta.core.reasoning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RelativeTimeTest {

    private fun ms(y: Int, mo: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.ENGLISH).apply {
            clear(); set(y, mo - 1, d, h, min)
        }.timeInMillis

    private val now = ms(2026, 7, 17, 14, 0) // 17 Jul 2026, 2:00 pm IST

    @Test fun `same day shows the time`() =
        assertEquals("11:41 am", relativeTimeLabel(ms(2026, 7, 17, 11, 41), now))

    @Test fun `previous calendar day shows Yesterday`() =
        assertEquals("Yesterday", relativeTimeLabel(ms(2026, 7, 16, 23, 30), now))

    @Test fun `same year shows day and month`() =
        assertEquals("2 Mar", relativeTimeLabel(ms(2026, 3, 2, 9, 0), now))

    @Test fun `older shows day month year`() =
        assertEquals("16 Jul 2025", relativeTimeLabel(ms(2025, 7, 16, 9, 0), now))

    @Test fun `future timestamps fall back to full date`() =
        assertEquals("18 Jul 2026", relativeTimeLabel(ms(2026, 7, 18, 9, 0), now))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:reasoning:test --tests 'ai.vaarta.core.reasoning.RelativeTimeTest' --console=plain`
Expected: FAIL — unresolved reference `relativeTimeLabel`.

- [ ] **Step 3: Implement**

`core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/RelativeTime.kt`:

```kotlin
package ai.vaarta.core.reasoning

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One relative-time label for list rows (spec §3A.5/§6.6): short, single-line, en-IN forms.
 * Pure JVM so it's unit-testable; the caller passes `now` (no hidden clock).
 */
fun relativeTimeLabel(thenMs: Long, nowMs: Long): String {
    val locale = Locale("en", "IN")
    val then = Calendar.getInstance().apply { timeInMillis = thenMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        thenMs <= nowMs && sameDay(then, now) ->
            SimpleDateFormat("h:mm a", locale).format(Date(thenMs)).lowercase(locale)
        thenMs <= nowMs && sameDay(then, yesterday) -> "Yesterday"
        thenMs <= nowMs && then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("d MMM", locale).format(Date(thenMs))
        else -> SimpleDateFormat("d MMM yyyy", locale).format(Date(thenMs))
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:reasoning:test --tests 'ai.vaarta.core.reasoning.RelativeTimeTest' --console=plain`
Expected: PASS (5 tests). Also run the module's full suite: `./gradlew :core:reasoning:test --console=plain` → all green.

- [ ] **Step 5: Wire into `HistoryRow`**

In `MainActivity.kt`: delete `private val historyDateFmt = SimpleDateFormat(…)` (and its now-unused imports if nothing else uses them); in `HistoryRow` replace

```kotlin
Text(historyDateFmt.format(Date(session.startedAtMs)), style = MaterialTheme.typography.labelMedium, color = c.muted)
```

with

```kotlin
Text(
    ai.vaarta.core.reasoning.relativeTimeLabel(session.startedAtMs, System.currentTimeMillis()),
    style = MaterialTheme.typography.labelMedium,
    color = c.muted,
    maxLines = 1,
)
```

- [ ] **Step 6: Build + emulator spot-check + commit**

Run: `./gradlew :app:assembleDebug --console=plain`, install, open Conversations.
Expected: each row's meta line fits on **one line** ("● This matches a known scam · Yesterday"), no 3-line date wrap.

```bash
git add core/reasoning/src app/src/main/java/ai/vaarta/MainActivity.kt
git commit -m "core:reasoning+app — relativeTimeLabel (en-IN, TDD) replaces wrapping absolute dates in Conversations rows (spec §3A.5, §6.6)"
```

---

### Task 5: Phase-1 wrap-up — full test sweep + status doc

**Files:**
- Modify: `PROJECT_STATUS.md` (§8 change log entry; §5 note that redesign phase 1 is done)

- [ ] **Step 1: Full verification sweep**

Run: `./gradlew test --console=plain` → all modules green (count from fresh JUnit XML, not the banner).
Run: `./gradlew :app:assembleDebug --console=plain` → green.
Emulator: one pass over Home → Article → back, Live → back, Conversations — screenshots for the status entry.

- [ ] **Step 2: Update PROJECT_STATUS.md + commit**

Add a dated §8 entry: phase 1 delivered (India prompt anchor + tests, edge-to-edge fix, VaartaSubScreen + BackHandler, relativeTimeLabel), with the verification evidence, and "Next: Phase 2 — covers".

```bash
git add PROJECT_STATUS.md
git commit -m "docs — PROJECT_STATUS: premium-redesign phase 1 (foundation) done + verified"
```

---

## Self-review notes

- **Spec coverage (phase 1 scope):** §3A.1 anchor → Task 1; §8.1 edge-to-edge/insets → Tasks 2–3; §8.2 back → Task 3; §3A.5 en-IN → Task 4; string convention → Global Constraints (deliberate resequencing: per-screen extraction happens in phases 2–7 to avoid translating copy that's about to be rewritten; phase 8 gates it).
- **Deferred within phase 1 (deliberate):** row/tile v2 components and shimmer move to the phases that first render them (Home/Conversations) — building them unused here would be YAGNI and unverifiable.
- **Type consistency:** `IndiaContext.BLOCK`, `VaartaSubScreen(title, onBack, modifier, scrollable, bottomContent, content)`, `relativeTimeLabel(thenMs, nowMs)` — names used identically across tasks.
