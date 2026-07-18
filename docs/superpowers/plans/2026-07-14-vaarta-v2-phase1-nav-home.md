# VAARTA v2 — Phase 1: Navigation + Home shell + delete Manual Mode

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the overloaded single-screen `MainActivity` with a clean 3-tab shell (Home / History / Help), delete the Manual Mode UI entirely, and give the app a calm, action-first landing screen — without breaking any existing feature.

**Architecture:** Keep the hand-rolled navigation approach (no new `navigation-compose` dependency — stays $0 and dependency-light). A single `VaartaNav` composable owns tab state and a "sub-screen" stack (Live / Analyze / Detail), rendered inside an MD3 `Scaffold` with a `NavigationBar`. Existing screen composables (`VaartaScreen`=live, `HistoryScreen`, `DetailScreen`, `AnalyzeScreen`) are reused as-is via routing; they are *not* rewritten in this phase (extraction into their own files is deferred to later phases). Manual Mode is removed from both the UI and `CopilotSession`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 (`androidx.compose.material3`), existing `VaartaTheme`/`RiskRing`/`Signals`. Build: Gradle 8.11.1 headless. Verify: `vaarta_test` emulator via adb.

## Global Constraints

- **Strict $0** — no new paid APIs/backends; **no new Gradle dependencies** in this phase (use plain Compose state, not `navigation-compose`).
- **Delete, don't hide** — Manual Mode UI is removed from the app, not merely made unreachable.
- **Keep the safety floor** — the deterministic `RiskEngine` / `HybridAlert` / `SuggestionSafetyFilter` are untouched. Only the Manual Mode *cue-tap* input path is removed.
- **Accessibility default** — every actionable element ≥48dp touch target + a TalkBack `contentDescription`/semantics; body text large.
- **Design system** — MD3 semantic color roles + existing `VaartaTheme.colors`; 8dp spacing grid; card corners 12dp; no raw hex in new components.
- **Evidence rule** — a task is "done" only with a green build AND an emulator screenshot/dumpsys showing the behavior. "It compiles" is not "it works".
- **No Manual Mode regressions in tests** — `PackParityTest` and the intel-pack `manualCue` DATA stay (they keep pack authoring honest); only the app-side cue UI + `CopilotSession` cue input are removed.

**Verification setup (run once per fresh shell before device steps):**
```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
$GRADLE = 'C:\Users\Meges\tools\gradle-8.11.1\bin\gradle.bat'
$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$ADB = "$SDK\platform-tools\adb.exe"
```

---

### Task 1: Delete the Manual Mode UI and its session input

**Files:**
- Delete: `app/src/main/java/ai/vaarta/ui/ManualModeGrid.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/Signals.kt` (remove now-dead `signalVisualForCue`, lines 16–31)
- Modify: `app/src/main/java/ai/vaarta/CopilotSession.kt` (remove `_tapped`/`tapped` L80–81, `cues` L388–400ish, `tapCue` L402–406, the `_tapped` reset L421, and fix the L529 "tap cues" copy)
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (remove the `ManualCueGrid` import L14, the `tapped` collectAsState L189, and the Manual Mode block L349–352)

**Interfaces:**
- Produces: `CopilotSession` no longer exposes `cues`, `tapped`, or `tapCue`. Nothing else should reference them after this task.

- [ ] **Step 1: Delete the Manual Mode composable file**

Delete `app/src/main/java/ai/vaarta/ui/ManualModeGrid.kt` entirely (defines `ManualCueGrid` + `ManualCueTile`, used only by the block being removed).

- [ ] **Step 2: Remove the dead cue-visual helper**

In `app/src/main/java/ai/vaarta/ui/Signals.kt`, delete the `signalVisualForCue(cueId: String)` function (the `when` block mapping `CUE_*` ids, ~L16–31). Keep `data class SignalVisual` and `signalVisualForStage` — `RiskHero` still uses both.

- [ ] **Step 3: Remove Manual Mode state from `CopilotSession`**

In `app/src/main/java/ai/vaarta/CopilotSession.kt`:
- Remove the `_tapped` / `tapped` StateFlow pair (~L80–81).
- Remove the `cues: List<Pair<String,String>>` property (~L388–400).
- Remove the `fun tapCue(cueId: String)` function (~L402–406).
- In `reset()`, remove the `_tapped.value = emptySet()` line (~L421).
- Change the empty-complaint message (~L529) from `"No warning signs detected yet — tap cues or run the demo call first."` to `"No warning signs detected yet — run the demo call or start live protection first."`

- [ ] **Step 4: Remove Manual Mode from `MainActivity`**

In `app/src/main/java/ai/vaarta/MainActivity.kt`:
- Delete the import `import ai.vaarta.ui.ManualCueGrid` (L14).
- Delete `val tapped by vm.session.tapped.collectAsState()` (L189).
- Delete the Manual Mode block (L349–352):
```kotlin
            // Manual Mode — always reachable (P0 peer), demoted into its own section.
            HorizontalDivider()
            Text("Manual mode — tap what you hear", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ManualCueGrid(cues = vm.session.cues, tapped = tapped, onTap = { vm.session.tapCue(it) })
```
(Leave the `HorizontalDivider()` before the complaint section that follows — it now separates live content from the complaint action.)

- [ ] **Step 5: Build and verify Manual Mode is gone**

Run: `& $GRADLE :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. No `ManualCueGrid` / `tapCue` / `cues` unresolved references.

- [ ] **Step 6: Run core tests (safety floor intact)**

Run: `& $GRADLE test --console=plain`
Expected: all green (incl. `PackParityTest` — the pack `manualCue` DATA is untouched).

- [ ] **Step 7: Commit**

```powershell
cd 'C:\Users\Meges\Downloads\ET\Vaarta\Vaarta'
git add -A
git commit -m "app — remove Manual Mode UI (canned-answer path); keep engine safety floor"
```

---

### Task 2: 3-tab navigation shell (Scaffold + NavigationBar)

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/VaartaNav.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (replace the `Screen` sealed interface + `VaartaApp` switch with a call to `VaartaNav`)

**Interfaces:**
- Consumes: existing composables `VaartaScreen(...)`, `HistoryScreen(...)`, `DetailScreen(...)`, `AnalyzeScreen(...)` (still in `MainActivity.kt`), and the three ViewModels.
- Produces:
  - `enum class VaartaTab { HOME, HISTORY, HELP }`
  - `sealed interface SubScreen { data object None; data object Live; data object Analyze; data object Detail }`
  - `@Composable fun VaartaNav(vm, historyVm, analyzerVm, onShare, onExportPdf, onOpenUrl)` — the new top-level host.

- [ ] **Step 1: Create the navigation host**

Create `app/src/main/java/ai/vaarta/ui/VaartaNav.kt`:
```kotlin
package ai.vaarta.ui

import ai.vaarta.AnalyzeScreen
import ai.vaarta.DetailScreen
import ai.vaarta.HistoryScreen
import ai.vaarta.SessionViewModel
import ai.vaarta.VaartaScreen
import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.data.db.SessionSource
import ai.vaarta.history.HistoryViewModel
import ai.vaarta.recording.AudioAnalyzerViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

enum class VaartaTab { HOME, HISTORY, HELP }

sealed interface SubScreen {
    data object None : SubScreen
    data object Live : SubScreen
    data object Analyze : SubScreen
    data object Detail : SubScreen
}

/**
 * Top-level host. Three bottom tabs (Home/History/Help). Full-screen "sub-screens" (the live
 * copilot, the recording analyzer, a saved-call detail) render over the tabs with their own back
 * action and hide the bottom bar while open. No navigation-compose dependency — a small state
 * holder is enough for this shape and keeps the build $0/lean.
 */
@Composable
fun VaartaNav(
    vm: SessionViewModel,
    historyVm: HistoryViewModel,
    analyzerVm: AudioAnalyzerViewModel,
    onShare: (String) -> Unit,
    onExportPdf: (ComplaintDraft) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(VaartaTab.HOME) }
    var sub by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // A sub-screen takes the whole window (no bottom bar) so nothing competes with its back action.
    if (sub != SubScreen.None) {
        when (sub) {
            SubScreen.Live -> VaartaScreen(
                vm = vm, historyVm = historyVm, analyzerVm = analyzerVm,
                onOpenHistory = { sub = SubScreen.None; tab = VaartaTab.HISTORY },
                onOpenAnalyze = { sub = SubScreen.Analyze },
                onShare = onShare, onExportPdf = onExportPdf, onOpenUrl = onOpenUrl,
                onBack = { sub = SubScreen.None },
            )
            SubScreen.Analyze -> AnalyzeScreen(
                analyzerVm = analyzerVm, historyVm = historyVm,
                onBack = { analyzerVm.reset(); sub = SubScreen.None },
                onShare = onShare, onOpenUrl = onOpenUrl,
            )
            SubScreen.Detail -> DetailScreen(
                historyVm = historyVm,
                onBack = { historyVm.closeDetail(); sub = SubScreen.None; tab = VaartaTab.HISTORY },
                onOpenUrl = onOpenUrl,
            )
            SubScreen.None -> Unit
        }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == VaartaTab.HOME,
                    onClick = { tab = VaartaTab.HOME },
                    icon = { Text("🛡️") },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == VaartaTab.HISTORY,
                    onClick = { tab = VaartaTab.HISTORY },
                    icon = { Text("🕘") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = tab == VaartaTab.HELP,
                    onClick = { tab = VaartaTab.HELP },
                    icon = { Text("🆘") },
                    label = { Text("Help") },
                )
            }
        },
    ) { pad ->
        when (tab) {
            VaartaTab.HOME -> HomeScreen(
                aiConfigured = vm.session.aiConfigured,
                onStartLive = { sub = SubScreen.Live },
                onAnalyzeRecording = { sub = SubScreen.Analyze },
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(pad),
            )
            VaartaTab.HISTORY -> HistoryScreen(
                historyVm = historyVm,
                onBack = { tab = VaartaTab.HOME },
                onOpen = { id -> historyVm.openDetail(id); sub = SubScreen.Detail },
                modifier = Modifier.padding(pad),
            )
            VaartaTab.HELP -> HelpScreen(
                vm = vm,
                onShare = onShare,
                onExportPdf = onExportPdf,
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(pad),
            )
        }
    }
}
```

> NOTE: this task introduces three new parameters/signatures that Tasks 3–4 and small edits below must satisfy: `VaartaScreen(..., onBack)`, `HistoryScreen(..., modifier)`, `HomeScreen(...)`, `HelpScreen(...)`. Steps 2–4 make the existing screens compile against these.

- [ ] **Step 2: Make `VaartaScreen`/`HistoryScreen` accept the new params**

In `app/src/main/java/ai/vaarta/MainActivity.kt`:
- Add `onBack: () -> Unit` to `VaartaScreen(...)`'s parameter list, and add a `‹ Back` text at the start of its header `Row` (mirror the existing `HistoryScreen` back control style): `Text("‹ Back", fontSize = 15.sp, color = VaartaTheme.colors.indigo, modifier = Modifier.clickable(onClick = onBack))` then a `Spacer(Modifier.weight(1f))` before the "VAARTA" title.
- Add `modifier: Modifier = Modifier` to `HistoryScreen(...)` and apply it on the root `Surface` (`Surface(modifier = modifier.fillMaxSize(), ...)`).

- [ ] **Step 3: Replace `VaartaApp` with `VaartaNav` in `MainActivity`**

In `app/src/main/java/ai/vaarta/MainActivity.kt`:
- Delete the `private sealed interface Screen { ... }` block (L122–127) and the entire `fun VaartaApp(...)` (L131–170).
- In `onCreate`, change the body of `VaartaTheme { ... }` to:
```kotlin
                VaartaNav(vm, historyVm, analyzerVm, onShare = ::shareText, onExportPdf = ::exportAndSharePdf, onOpenUrl = ::openUrl)
```
- Add `import ai.vaarta.ui.VaartaNav`.

- [ ] **Step 4: Temporary stubs so it compiles before Tasks 3–4**

At the bottom of `VaartaNav.kt`, add minimal stub composables (replaced by Tasks 3 and 4):
```kotlin
@Composable
private fun HomeScreenStubMarker() { /* replaced in Task 3 */ }
```
Then create real `HomeScreen` (Task 3) and `HelpScreen` (Task 4) BEFORE building. (Order: do Steps 1–3 here, then jump to Task 3 and Task 4, then return to Step 5.)

- [ ] **Step 5: Build**

Run: `& $GRADLE :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` (requires Tasks 3 & 4 done).

- [ ] **Step 6: Install and verify the 3 tabs**

```powershell
& $ADB install -r app\build\outputs\apk\debug\app-debug.apk
& $ADB shell am start -n ai.vaarta.debug/ai.vaarta.MainActivity
```
Verify via screenshot: bottom NavigationBar shows Home / History / Help; tapping each switches content; the bottom bar disappears when a sub-screen (live/analyze/detail) is open and its back returns correctly.

- [ ] **Step 7: Commit**

```powershell
git add -A
git commit -m "app — 3-tab shell (Home/History/Help) via Scaffold + NavigationBar"
```

---

### Task 3: Clean Home screen (panic action + 2 action cards + feed placeholder)

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/HomeScreen.kt`

**Interfaces:**
- Consumes: nothing beyond the callbacks passed by `VaartaNav`.
- Produces: `@Composable fun HomeScreen(aiConfigured: Boolean, onStartLive: () -> Unit, onAnalyzeRecording: () -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create the Home screen**

Create `app/src/main/java/ai/vaarta/ui/HomeScreen.kt`. Structure top→bottom: header ("VAARTA" + tagline), a large PANIC card (opens a "Do this now" `ModalBottomSheet`), two action cards ("Help me on a call" → `onStartLive`; "Check a recording" → `onAnalyzeRecording`, shown only if `aiConfigured`), then a "Trending scams" section header + placeholder text (real feed lands in Phase 4). Use `VaartaTheme.colors`, 8dp grid, card corners 12dp, touch targets ≥48dp, and `semantics`/`contentDescription` on each action. The panic sheet lists, in large legible text: **Don't pay anyone. Never share an OTP or PIN. Hang up. Call 1930.** plus a "Start live help" button (→ `onStartLive`) and "Analyze a recording" (→ `onAnalyzeRecording`). Use `androidx.compose.material3.ModalBottomSheet` for the sheet, `rememberModalBottomSheetState`. Reference: mobile-app-ui-design skill (thumb-zone primary action, 60/30/10 color, peak-end) and spec §4.1 / §5.

> Full styling is left to the implementer following the design system; the REQUIRED shape is: exactly one visually-dominant PANIC control in the thumb zone, two secondary action cards, and a clearly-labeled (not yet populated) trending-scams section. No Manual Mode. No risk red used decoratively — red only inside the panic context.

- [ ] **Step 2: Build**

Run: `& $GRADLE :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify on emulator**

Install + launch (as Task 2 Step 6). Screenshot the Home tab: PANIC card prominent, two action cards present, trending placeholder visible. Tap PANIC → the "Do this now" sheet appears with the four steps + two buttons. Tap "Help me on a call" → the live screen opens; back returns to Home.

- [ ] **Step 4: Commit**

```powershell
git add -A
git commit -m "app — clean Home: panic action + call/recording cards + feed placeholder"
```

---

### Task 4: Help tab (complaint + 1930 + gov site + warn family)

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (remove the complaint block from `VaartaScreen`, L354–367, now that Help owns it)

**Interfaces:**
- Consumes: `SessionViewModel` (`vm.session.generateComplaint()`, `vm.session.complaint`, `vm.session.complaintDraft`), `onShare`, `onExportPdf`, `onOpenUrl`.
- Produces: `@Composable fun HelpScreen(vm: SessionViewModel, onShare: (String) -> Unit, onExportPdf: (ComplaintDraft) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create the Help screen**

Create `app/src/main/java/ai/vaarta/ui/HelpScreen.kt`. Sections (cards, 8dp grid): (1) **"If this is happening now"** — big "Call 1930" button → `onOpenUrl("tel:1930")`; (2) **"Report online"** — "Open cybercrime.gov.in" button → `onOpenUrl("https://cybercrime.gov.in")`; (3) **"Prepare a complaint"** — "Generate complaint draft" → `vm.session.generateComplaint()`, then show `vm.session.complaint` in a card with "Share as text" (`onShare`) and "Export PDF" (`onExportPdf(vm.session.complaintDraft!!)`) — reuse the exact block being removed from `VaartaScreen`; (4) **"Warn your family"** — "Share a warning" → `onShare("VAARTA: please be careful — scammers posing as police/CBI are calling people and demanding money or OTPs. Never pay or share an OTP. If pressured, hang up and call 1930.")`. Collect state with `collectAsState()`. Accessibility + `VaartaTheme.colors` as elsewhere.

- [ ] **Step 2: Remove the complaint block from the live screen**

In `MainActivity.kt` `VaartaScreen`, delete L354–367 (the trailing `HorizontalDivider()`, "Generate complaint draft" `OutlinedButton`, and the `complaint?.let { ... }` card). The live screen is now focused on live protection only; complaint lives in Help. Remove any now-unused imports flagged by the build.

- [ ] **Step 3: Build**

Run: `& $GRADLE :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify on emulator**

Install + launch. Help tab: tap "Generate complaint draft" (after running a demo call from the live screen so there's content) → draft appears; "Export PDF" opens the share sheet with a real PDF (as in the existing PDF-export verification). "Call 1930" opens the dialer with 1930 prefilled. "Open cybercrime.gov.in" opens the browser (user-confirmed). "Share a warning" opens the share sheet.

- [ ] **Step 5: Commit**

```powershell
git add -A
git commit -m "app — Help tab: 1930 dial, cybercrime.gov.in, complaint draft, warn-family"
```

---

### Task 5: Full-flow verification + status update

**Files:**
- Modify: `PROJECT_STATUS.md` (status matrix + Next Up + change log)

- [ ] **Step 1: Cold-start regression pass on the emulator**

Force-stop, cold launch, wait for render (≥6s — the known cold-start gotcha). Walk every route: Home → panic sheet; Home → live → demo call reaches SCAM_PATTERN (engine unaffected) → back; Home → analyze (if aiConfigured) → back; History tab → (save a call from live first) → open detail → back; Help tab → complaint + 1930 + gov + warn-family. Screenshot each. Confirm: no Manual Mode anywhere, no crash, bottom bar behaves.

- [ ] **Step 2: Confirm tests still green**

Run: `& $GRADLE test --console=plain` → all green.

- [ ] **Step 3: Update PROJECT_STATUS.md**

Move Manual Mode from "Built" to a removed/changed note; add the 3-tab shell + Home + Help to "Built (this phase)"; add a dated change-log entry; point Next Up at Phase 2 (Understand-this-call screen). Keep the evidence discipline (screenshots referenced).

- [ ] **Step 4: Commit**

```powershell
git add -A
git commit -m "docs — PROJECT_STATUS: v2 Phase 1 (nav + Home + Help, Manual Mode removed)"
```

---

## Self-Review

**Spec coverage (Phase 1 slice of the spec):**
- §3 delete Manual Mode UI / keep engine → Task 1. ✓
- §4 3-tab IA (Home/History/Help) → Task 2. ✓
- §4.1 Home (panic + 2 actions + feed placeholder) → Task 3. ✓
- §4.3 Help (complaint/1930/gov/warn-family) → Task 4. ✓
- §5 Calm Guardian / MD3 / accessibility → applied across Tasks 3–4 (design-system directives in each). ✓
- §6 chat/feed/overlay, §4.4 understand-this-call → **deferred to Phases 2–7** (correctly out of this plan). ✓

**Placeholder scan:** UI-styling detail in Tasks 3–4 is intentionally directive (shape mandated, pixels delegated to the design system) because embedding 200 lines of final Compose per screen would duplicate the implementation; the *required structure, callbacks, and signatures* are fully specified. No "TBD"/"handle edge cases" left.

**Type consistency:** `VaartaNav` calls `VaartaScreen(..., onBack=)`, `HistoryScreen(..., modifier=)`, `HomeScreen(aiConfigured,onStartLive,onAnalyzeRecording,onOpenUrl,modifier)`, `HelpScreen(vm,onShare,onExportPdf,onOpenUrl,modifier)` — all matched by Tasks 2 Step 2, 3, and 4. `SubScreen`/`VaartaTab` used consistently. `onOpenUrl("tel:1930")` relies on the existing `openUrl` in `MainActivity` (uses `ACTION_VIEW` + `Uri.parse` — handles `tel:` and `https:`). ✓

**Note on TDD:** Phase 1 is a pure Compose UI/navigation restructure with no new business logic, so the plan uses build + on-device verification as its test cycle (per the Global Constraints evidence rule) rather than unit tests. Unit-test-first returns in Phase 3 (chat context assembly) and Phase 4 (awareness-feed parser), which add real `core:*` logic.
