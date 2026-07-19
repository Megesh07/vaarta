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
- Regional script variants (Tamil Nadu cyber-police flavor, R3) — separate future research.
- **Caller-ID card in the overlay ("Truecaller-lite") — considered and REJECTED by the owner
  (2026-07-19):** for saved contacts the native dialer already shows the name (zero intelligence
  added), and Truecaller-style names for *unknown* numbers require a crowdsourced contact-harvesting
  directory that is closed/paid (Truecaller API) or ToS-violating (scraper APIs) — and is the exact
  privacy posture VAARTA stands against. Owner's filter, applied project-wide: **intelligence must
  actually benefit the user, never exist for name's sake.**
- Truecaller-style name lookup for unknown numbers — see above; permanently out.
- Real call-audio recording — researched and confirmed impossible for third-party apps
  (OS-level block since Android 10, not just Play policy — sideloading does not help); the
  accessibility-service hack is fragile/OEM-dependent and Shizuku needs ADB re-arming every reboot,
  unusable for VAARTA's audience. Speakerphone stays, now as a **proven** dead-end. Recorded as
  ADR-0005 (§15) so it is never re-litigated from scratch.
- Performing the native-speaker Hindi/Hinglish review, or producing the demo video/deck — both are
  real, necessary steps toward "10," but neither is a code task; they're called out in §16 as the
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

## 13. Increment K — Scam-link checker (approved 2026-07-19)

**Why:** scam calls and messages constantly push links ("click to update KYC", loan-app APKs,
fake bank portals). Checking a URL against real threat intelligence is genuine, $0,
trust-building intelligence — it benefits the user directly, passing the owner's
"intelligence must actually benefit" filter.

**Sources (all verified free as of 2026-07-19; re-verify exact API shapes against official docs at
implementation time, never from memory):**
- **URLhaus (abuse.ch)** — free REST API, no auth required.
- **Google Safe Browsing v4** — free API key for **non-commercial** use; VAARTA is non-commercial
  by its own scope lock (no monetization, sideload-only), so this qualifies. If VAARTA ever
  commercializes, this must move to the paid Web Risk API — noted so the constraint is visible.
- PhishTank (free key) — optional third source, only if the first two prove insufficient.

**Design:**
- New `core:reasoning` pure function: extract URLs from a text (unit-testable, no Android deps).
- New app-side `LinkChecker` (OkHttp, same client pattern as `GeminiClient`): query URLhaus first
  (no key needed), Safe Browsing second. Aggregate verdict: `MALICIOUS` (either source flags) /
  `CLEAN_SO_FAR` (both checked, neither flags — never displayed as "safe", phrased as "not on known
  threat lists") / `UNKNOWN` (network failure → fail closed, say nothing).
- Surfaces: chat messages (both directions) and analyzed-recording transcripts. A flagged link
  renders a red inline warning row; the warning can only **add** concern (consistent with the
  ratchet — a clean lookup never reassures).
- API key (Safe Browsing) rides the same local-properties/BuildConfig mechanism as the existing
  Gemini key; URLhaus needs none.

**Safety invariants:** a link verdict never touches the risk score (`RiskEngine` untouched);
lookup failure = silent, deterministic-only behavior — identical failure posture to every other
network call in the app.

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/UrlExtractor.kt` (+ TDD test)
- Create: `app/src/main/java/ai/vaarta/ai/LinkChecker.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/ConversationScreen.kt` (inline warning row)
- Modify: `strings.xml` (+ hi / hi+Latn) — warning strings
- Live verification: Google's own Safe Browsing test URLs (`testsafebrowsing.appspot.com`) give a
  deterministic known-bad target without touching a real malicious site.

## 14. Increment J — Voice-anomaly detection SPIKE (spike-gated, runs last)

**Owner's question this answers:** "the other side's voice is deepfake/AI or real — since we record
through the speaker, can we distinguish it?"

**Honest position (research verdict, 2026-07-19):** unknown — and unknowable without measuring.
Anti-spoofing models (AASIST-class, ~340K params, ONNX-portable — and the app already ships
`libonnxruntime.so` via the sherpa-onnx AAR, so zero new native dependencies) rely on high-frequency
synthesis artifacts that the telephony codec largely destroys, and the speakerphone→air→mic second
hop is untested in published research. ASVspoof 2021's codec-transmission task showed detection
survives telephony channels *degraded but above chance*; in-the-wild data often collapses models to
near-chance. Identical epistemic shape to R-01 — so it gets the same treatment: **a spike, never a
promise.**

**Spike protocol (pass/fail gate, before ANY product integration):**
1. Acquire an open AASIST-class ONNX anti-spoofing model; run it via the already-bundled
   onnxruntime.
2. Build a tiny eval corpus on the REAL path: (a) genuine human speech and (b) TTS/voice-clone
   speech, each played through an actual phone speaker into the actual device mic (matching the
   live-call audio path exactly).
3. Score both sets. **Gate:** clear score separation between real and synthetic on this path →
   proceed. Overlapping distributions → record the negative result in the ADR and stop; the
   behavioral layer below is the defense.
4. Either outcome is a WIN for the portfolio: "we measured it" beats "we assumed it."

**If the spike passes, integration design (and only then):**
- Raise-only advisory through the existing `HybridAlert` ratchet — phrased "voice characteristics
  unusual — verify who this is," NEVER "this is a deepfake" (an accusation the model cannot
  support; also ethical red line §9 of SCAM_INTELLIGENCE.md).
- Wired to H2: kinship-claim signal + voice-anomaly advisory → coach prompts a verification
  question ("ask something only your real son would know").
- **The channel-immune defense is H2's behavioral signals, always.** A flawless voice clone still
  has to say scam things; behavior survives any audio path. The audio detector is a second
  opinion, never load-bearing.

**Ordering:** runs LAST (after A–H, K, F, G) — it is research-grade and must not delay the
known-value increments.

## 15. ADR-0005 — call-audio access is closed (write it, one page)

Record the researched dead-end as a decision doc in `docs/decisions/0005-call-audio-access.md`:
Android has blocked the `VOICE_CALL` stream for non-system apps since Android 10 at the **OS
level** (not merely Play policy — sideloading does not bypass it); the accessibility-service hack
is fragile, OEM-dependent, and was the specific technique Play banned in 2022; Shizuku-based
recording requires ADB re-arming after every reboot — unusable for VAARTA's elderly-inclusive
audience. **Speakerphone + mic remains the only sanctioned path, now as a verified conclusion with
citations, so no future session burns time re-investigating it.**

## 16. What's still needed after this plan (not part of it, tracked honestly)

- **Native-speaker review** of all machine-drafted Hindi/Hinglish strings, now including H's two new
  signals' patterns — a human task, gates calling those languages "shipped" per the existing
  checklist in `PROJECT_STATUS.md`.
- **Demo video + deck** — deliberately done once, at the very end, describing the final state (prior
  decision, unchanged).
- Whatever Increment F's physical-phone test reveals about the live-call flagship's real-world
  reliability — an outcome, not a task, that this plan produces evidence for but cannot predetermine.

## 17. Testing strategy summary

- **A, H:** TDD — failing test first (`TextMatcherTest`, new eval fixtures), then the pack/code
  change, then green. `PackParityTest` and existing `EvalTest` cases are regression gates.
- **B, C, D:** Compose UI changes — verified live on the emulator (screenshot + tap-through), same
  method already established throughout this project; no meaningful pure-logic unit to TDD beyond
  `GuardianStore`'s read/write (which does get a JVM unit test).
- **E:** Documentation-only, no test — verified by re-reading the corrected file.
- **K:** TDD for `UrlExtractor` (pure JVM); `LinkChecker` verified live against Google's own
  Safe Browsing test URLs (deterministic known-bad, no real malicious site touched); network-failure
  path verified with airplane-mode emulator run (must stay silent).
- **F:** Manual verification, emulator then physical device — not automatable, exact commands
  provided at execution time.
- **G:** Review pass, not a test — verified by a clean diff and green full-suite re-run afterward.
- **J:** the spike IS the test — measured score separation on the real audio path is the gate;
  no product code ships without it.

## 18. Execution order

**A → H → K → B → C → D → E → F → G → J** — correctness bugs and detection intelligence first
(A/H/K are the "actually benefits the user" core), then UX-trust items (B/C/D), then docs (E),
then the verification pass (F: emulator, then physical phone over wireless adb), the hardening
review (G), and the research spike (J) last so it can never delay known value.
