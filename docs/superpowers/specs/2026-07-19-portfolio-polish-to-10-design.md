# Portfolio Polish — Closing the Gap to a Genuine 10/10 MVP

**Date:** 2026-07-19
**Status:** Approved direction (this doc is the written spec for user review)
**Owner branch:** `vaarta-v2-ux`

## 1. Goal

A fresh, evidence-based audit (this session, clean `gradlew clean test assembleDebug lintDebug`,
counts parsed directly from JUnit XML — not from `PROJECT_STATUS.md`'s remembered numbers) found
VAARTA in strong but not flawless shape: **159 tests / 0 failures, 0 lint issues, `assembleDebug`
green** — and seven confirmed, source-verified gaps between that state and "a senior engineer would
ship this as-is" for the **portfolio/hackathon-MVP** bar (not the harder "real-world automatic
shield" bar — that one is structurally capped by platform/scope decisions this project has
deliberately kept, and is out of scope here).

This spec closes every gap that is actually closable with code, within the existing $0/sideload/MVP
constraints, and sets up (without performing) the two gaps that aren't code problems: native-speaker
language review and the demo video/deck.

## 2. Non-goals (explicitly out of scope — do not reopen)

- `CallScreeningService` real call auto-detection (Android-15 FGS/Play-policy — ADR-0003 Phase 4C
  addendum stays correct).
- Play Store publishing, DOCX export, Elder Mode, Tier-2 cloud LLM polish — all correctly out per
  ADR-0001.
- Voice-clone audio detection for SC-09 (family-emergency impersonation) — different modality,
  genuinely unsolved, stays a tracked open-research item (`SCAM_INTELLIGENCE.md` R1).
- Regional script variants (Tamil Nadu cyber-police flavor, R3) — separate future research.
- Performing the native-speaker Hindi/Hinglish review, or producing the demo video/deck — both are
  real, necessary steps toward "10," but neither is a code task; they're called out in §10 as the
  work that remains after this plan, not silently dropped.

## 3. Safety invariants (unchanged, carried over from prior specs — verbatim requirements)

1. `RiskEngine` remains the sole owner of the risk score. No LLM output ever writes to the score.
2. `HybridAlert` ratchet: AI concern can only **raise** displayed level, never lower it.
3. `SuggestionSafetyFilter` remains the runtime enforcement of the HARD RULES deny-list.
4. All LLM calls fail closed: network/parse failure → null → deterministic-only behavior.
5. Any change to `core-scam-v1.json` must keep `PackParityTest` green (every signal needs a
   `manualCue`) and must not regress `EvalTest`'s zero-tolerance false-positive gate.

## 4. Baseline evidence (this session, not carried from memory)

| Check | Result |
|---|---|
| Full test suite, clean rebuild | **159 tests, 0 failures, 0 errors, 0 skipped** (22 XML files: `core:reasoning`, `core:complaint`, `app`) |
| `assembleDebug` | Green |
| `lintDebug` | **0 issues** (not just 0 errors) across `app`, `core:data`, `core:voice` |
| `core:voice`/`core:data` instrumented tests | Not counted above — need a device, unchanged caveat from prior sessions |

## 5. Increment A — Fix the `"fir"` false-positive (correctness bug)

**Problem:** `core-scam-v1.json`'s `SIG_LEGAL_THREAT` signal (`hi_latn`, line 76) includes the
3-character pattern `"fir"` under `FUZZY1` matching. `TextMatcher.kt`'s fuzzy matcher does
whitespace-stripped substring + Levenshtein-distance-≤1 matching, so "for", "from", and "Sir" all
false-fire this signal (each is edit-distance 1 from "fir").

**Fix:** `"fir"` on its own is too short and too common a substring for fuzzy matching to be safe.
Replace it with a longer, distinctive form (`"fir registered"`, `"fir file"`, `"fir number"` — mirrors
the already-safe `en` pattern `"fir registered"` at line 75) and/or switch the `hi_latn` variant to
`MatchMode.EXACT` if a bare 3-letter token must stay. The bug and fix are proven with a new
`TextMatcherTest.kt` reproducing the false positive first (red), then fixed (green).

**Files:**
- Modify: `core/reasoning/src/main/resources/packs/core-scam-v1.json` (line 76)
- Create: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/TextMatcherTest.kt`

## 6. Increment B — Confirmation dialogs on destructive rows

**Problem:** "Delete all conversations" (`MainActivity.kt:603`) and "Clear voice data"
(`HelpScreen.kt:219`) both fire immediately on tap — no confirmation. The code even has a comment
admitting it ("no confirmation dialog").

**Fix:** One reusable `ConfirmDialog` composable (Material3 `AlertDialog`), wired into both call
sites. Destructive action only proceeds on explicit confirm.

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/components/ConfirmDialog.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt:603` (delete-all onClick)
- Modify: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt:219` (clear-voice-data onClick)
- Modify: `app/src/main/res/values/strings.xml` (+ `values-hi`, `values-b+hi+Latn`) — 2 new
  confirm-dialog strings per destructive action (title + body), following the existing
  `settings_*`/`conv_*` naming convention.

## 7. Increment C — Real guardian contact picker

**Problem:** "Warn my family" (Help, Article, Live, Analyze) always opens Android's generic
`ACTION_SEND` chooser — no stored guardian, no direct-to-contact send, a contact must be picked from
scratch every single time.

**Fix:** Add a one-time (changeable) guardian contact picker using Android's contact-picker intent
(`Intent.ACTION_PICK` on the contacts content URI — no runtime permission needed for this specific
picker pattern; verify the exact current API against `developer.android.com` at implementation time,
not from memory). Store the chosen contact's display name + phone number in `SharedPreferences`
(same mechanism `AwarenessStore.kt` already uses — no new persistence dependency). Add a "Guardian
contact" settings row in Help (view current / change / clear). When a guardian is set, "Warn my
family" sends directly to them (`ACTION_SENDTO`, SMS); when unset, falls back to **exactly today's**
generic-chooser behavior — fail-closed, consistent with the rest of the app.

**Files:**
- Create: `app/src/main/java/ai/vaarta/guardian/GuardianStore.kt` (SharedPreferences read/write,
  mirrors `AwarenessStore.kt`'s pattern)
- Create: `app/src/main/java/ai/vaarta/guardian/GuardianPickerContract.kt` (thin wrapper over the
  contact-picker `ActivityResultContract`)
- Modify: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt` (new settings row)
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (share-flow branch: guardian set → direct
  send, else → existing chooser)
- Modify: `strings.xml` (+ hi / hi+Latn) — new guardian-row strings

## 8. Increment D — Overlay speaker-off nudge parity

**Problem:** The speaker-off nudge (reusing the existing `live_active_caption` string) is wired into
the in-app Live screen (`MainActivity.kt`, `CopilotSession.kt`) but **not** into the floating overlay
panel (`OverlayService.kt`) — someone using only the overlay bubble never sees it.

**Fix:** Surface the same nudge condition/string inside `OverlayService.kt`'s panel composable,
reusing the existing logic rather than duplicating it (extract if needed so both call sites share
one source of truth).

**Files:**
- Modify: `app/src/main/java/ai/vaarta/OverlayService.kt`
- Modify (if extraction needed): `app/src/main/java/ai/vaarta/CopilotSession.kt`

## 9. Increment E — Documentation correction + real follow-up tracking

**Problem 1:** `PROJECT_STATUS.md`'s 2026-07-19 changelog entry describes the absent Manual Mode UI
as an accidental gap needing a follow-up. It is not — `docs/superpowers/specs/2026-07-14-vaarta-v2-intelligence-ux-design.md`
already documents its **deliberate, approved deletion** ("Manual Mode is dead weight... DELETED"),
for the exact reason the v2 redesign exists. The status doc mischaracterizes its own project's
history.

**Fix:** Correct that entry — state plainly that Manual Mode UI's absence is intentional and
resolved, not open. Confirmed by this session's user decision: no code change, doc-only.

**Problem 2:** The four `task_*` follow-up IDs (`task_ecd0ce74`, `task_0682d091`, `task_517a16be`,
`task_6a52885f`) exist only as prose in `PROJECT_STATUS.md`'s changelog — no code marker, no issue
tracker. "Tracked" in name only. Three of the four are closed by this plan (A closes
`task_ecd0ce74`'s "fir" bug; B closes `task_517a16be`'s destructive-confirm gap; D closes
`task_0682d091`'s overlay-nudge gap); `task_6a52885f` (Manual Mode) is closed by the correction above,
not by building anything.

**Fix:** Add a small **"Open follow-ups" table** to `PROJECT_STATUS.md` (§5 area) as the single place
these are tracked, and stop leaving them buried in changelog prose going forward.

**Files:**
- Modify: `PROJECT_STATUS.md` (correct the Manual Mode entry; add/close the follow-ups table)

## 10. Increment F — Verification pass (the flagship proof)

Two stages, in order:

1. **Emulator-level verification of A–D** — same method already used throughout this project
   (screenshots, `dumpsys window`/`dumpsys activity services`, manual tap-through on the
   `vaarta_test` AVD). Confirms the "fir" fix doesn't false-fire/miss-fire on the existing eval
   scripts, both confirm dialogs block-then-proceed correctly, guardian picker stores/sends/falls
   back correctly, overlay nudge appears.
2. **Physical-phone live-call test over wireless `adb` debugging** — the flagship, previously
   unproven on any hardware: does real caller speech (electrical path via speakerphone, not
   PC acoustic loopback) reach `inputTranscription` cleanly enough to move the deterministic risk
   score live? Exact `adb pair`/`adb connect` + build/install steps provided at execution time; the
   user runs it on their device and reports observed behavior back — this is not something drivable
   without physical hardware. Two honest outcomes: it works (flagship demo unlocked) or it doesn't
   transcribe cleanly (pivot the demo narrative to the already-proven recorded-call analyzer +
   demo-call path, and note R-01 as confirmed-hard rather than unproven).

## 11. Increment G — Definition-of-done hardening pass

Per the project's own global standard: a `security-and-hardening` + `code-simplification` review
pass over the full diff from A–D + H, then `git-cleanup`. Applied once, after the code increments
land, before considering this plan done.

## 12. Increment H — Intel-pack breadth (SC-08 and SC-09 gaps)

Grounded against `docs/SCAM_INTELLIGENCE.md`'s own SC-01..SC-10 taxonomy vs. the current 21-signal
pack (`core-scam@2026.07.2`). Two concrete, real gaps — not vague "grow coverage":

**H1 — SC-08's missing half (bank KYC-expiry / link-push).** New signal:

| id | category | stage | pattern shape |
|---|---|---|---|
| `SIG_HOOK_KYC_EXPIRY` | `SERVICE_THREAT` | HOOK | "your KYC will expire" / "account will be blocked" / "click this link to update KYC" — same "lose access unless you act now" shape as the existing electricity-disconnection signal |

**H2 — SC-09 (family-emergency impersonation), currently a total gap.** `SCAM_INTELLIGENCE.md §10`
lists this as open research ("R1: what's detectable from text alone? — M2"). Scoped honestly here to
**text-detectable behavioral signals only** — voice-clone audio detection stays explicitly out
(different modality, not solved by this pass). New category `KINSHIP_IMPERSONATION` + two new
signals:

| id | category | stage | pattern shape |
|---|---|---|---|
| `SIG_HOOK_FAMILY_EMERGENCY` | `KINSHIP_IMPERSONATION` | HOOK | caller claims to be a family member in sudden trouble ("it's me, mom" / "mera accident ho gaya" / "I'm in police custody") |
| `SIG_ISOLATION_NEW_NUMBER` | `KINSHIP_IMPERSONATION` | ISOLATION | "my phone broke, this isn't my number, don't call the old one back" |

Money-extraction reuses the existing generic `SIG_EXTRACTION_TRANSFER`/`SIG_EXTRACTION_UPI_QR`
signals — no new EXTRACTION signal needed.

**Critical safety requirement (non-negotiable, same discipline as the existing eval suite):** a new
**benign fixture** — a real family member genuinely calling about a real emergency, who never
demands secretive urgent money to an unfamiliar account — must **not** reach `SCAM_PATTERN`. This is
the same stage-grammar asymmetry that already protects genuine police callbacks (HOOK alone is
never enough; ISOLATION + EXTRACTION together is the tell). Ship H2 only alongside this test, never
before it.

Both new signals get a `manualCue` (enforced by `PackParityTest`) and follow the existing
`en`/`hi_latn`/`hi` schema. Like every other Hindi/Hinglish string in this app, the new patterns are
machine-drafted — add them to the existing native-review checklist in `PROJECT_STATUS.md`, don't
present them as reviewed.

**Files:**
- Modify: `core/reasoning/src/main/resources/packs/core-scam-v1.json` → bump `packId` to
  `core-scam@2026.07.3`
- Modify: `core/common/src/main/kotlin/ai/vaarta/core/common/IntelPack.kt` (add
  `KINSHIP_IMPERSONATION` to `SignalCategory`)
- Modify: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/EvalTest.kt` (or a new
  `KinshipEvalTest.kt` — new scam-script + new benign-family-emergency-script fixtures)
- `PackParityTest` and existing `EvalTest` cases must stay green (regression, unchanged pack ID for
  prior signals means prior tests keep passing).

## 13. What's still needed after this plan (not part of it, tracked honestly)

- **Native-speaker review** of all machine-drafted Hindi/Hinglish strings, now including H's two new
  signals' patterns — a human task, gates calling those languages "shipped" per the existing
  checklist in `PROJECT_STATUS.md`.
- **Demo video + deck** — deliberately done once, at the very end, describing the final state (prior
  decision, unchanged).
- Whatever Increment F's physical-phone test reveals about the live-call flagship's real-world
  reliability — an outcome, not a task, that this plan produces evidence for but cannot predetermine.

## 14. Testing strategy summary

- **A, H:** TDD — failing test first (`TextMatcherTest`, new eval fixtures), then the pack/code
  change, then green. `PackParityTest` and existing `EvalTest` cases are regression gates.
- **B, C, D:** Compose UI changes — verified live on the emulator (screenshot + tap-through), same
  method already established throughout this project; no meaningful pure-logic unit to TDD beyond
  `GuardianStore`'s read/write (which does get a JVM unit test).
- **E:** Documentation-only, no test — verified by re-reading the corrected file.
- **F:** Manual verification, emulator then physical device — not automatable, exact commands
  provided at execution time.
- **G:** Review pass, not a test — verified by a clean diff and green full-suite re-run afterward.
