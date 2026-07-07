# VAARTA — Project Status (READ THIS FIRST)

**Last updated:** 2026-07-07 · **Updated by:** implementation session (AI-assisted)
**This file is the single source of truth for "what's built, what's not, what's next."**
Keep it current — every session/collaborator updates it before stopping (see "Rules for keeping
this file honest" at the bottom). If this file and someone's memory disagree, this file wins.

---

## 0. If you are a fresh agent/collaborator picking this up cold

1. Read this file fully. Do not re-derive project state from the conversation that isn't here —
   if it mattered, it's written down below.
2. Read [docs/README.md](docs/README.md) for the product/architecture design (foundation frozen,
   see `docs/ARCHITECTURE_FREEZE_REVIEW.md`).
3. Read [docs/decisions/0001-mvp-scope-lock.md](docs/decisions/0001-mvp-scope-lock.md) — **this
   is the locked scope for the current build.** It is deliberately narrower than the full `/docs`
   design (hackathon/portfolio MVP, strict $0, not production).
4. Jump to **§5 Next Up** below and continue from the top of that list unless told otherwise.
5. Before changing a locked decision, read `docs/IMPLEMENTATION_GUARDRAILS.md` — NEVER/ALWAYS
   rules are binding, human or AI.

No further context is needed. Do not ask the user to re-explain the project — it's all here.

---

## 1. What VAARTA is (one paragraph)

A native Android app that protects Indian citizens from **digital-arrest scams**: it scores a
suspicious call live using a deterministic rule engine (not an LLM — see design rationale in
`docs/AI_REASONING_ENGINE.md` §1), coaches the user with verification questions, and auto-drafts
a cyber-crime complaint. Current build target: a **hackathon/portfolio MVP**, not production —
see the scope lock in §2.

## 2. Scope lock (binding for this build)

Full detail: [docs/decisions/0001-mvp-scope-lock.md](docs/decisions/0001-mvp-scope-lock.md) (ADR-0001).

- **Hard constraint: $0 to build.** No paid APIs, no backend/server, no Play Store fee (sideload only).
- **Intent:** usable by a few real people, portfolio-worthy — not production-grade.
- **In scope:** digital-arrest detection engine, Manual Mode, complaint generation, citizen-facing UI.
- **Out of scope (deliberately):** cloud LLM polish, Play publishing, DOCX export, Elder Mode,
  P1/P2 languages, and the challenge's counterfeit-currency / fraud-graph / geospatial pillars.
- **Stretch, spike-gated (never a blocker):** live on-device ASR, overlay bubble + real call
  detection, encrypted persistence.

## 3. Toolchain (exact paths — this machine, Windows)

No Android Studio GUI is installed; everything builds headless via Gradle. These paths are real
and verified working as of this writing — don't rediscover them:

| Tool | Path |
|---|---|
| JDK 17 | `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` (set as `JAVA_HOME`) |
| Gradle 8.11.1 | `C:\Users\Meges\tools\gradle-8.11.1\bin\gradle.bat` (standalone, not on PATH) |
| Android SDK | `C:\Users\Meges\AppData\Local\Android\Sdk` |
| SDK packages installed | `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`/`34.0.0`, `emulator`, `system-images;android-35;google_apis;x86_64` |
| Emulator AVD | `vaarta_test` (Pixel 6 profile, x86_64, google_apis, API 35) |
| APK output | `app/build/outputs/apk/debug/app-debug.apk` |

### Commands (PowerShell; set `$env:JAVA_HOME` first in a fresh shell)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
$GRADLE = 'C:\Users\Meges\tools\gradle-8.11.1\bin\gradle.bat'

# Run all engine/complaint tests (pure JVM, no device needed):
& $GRADLE test --console=plain

# Run the text-mode demo (live risk trace + generated complaint, CLI):
& $GRADLE :tools:demo:run -q --console=plain

# Build the Android debug APK:
& $GRADLE :app:assembleDebug --console=plain

# Boot the emulator + install + launch VAARTA (script already exists, reusable):
# See: <session-scratchpad>/setup-and-run-emulator.ps1 — or manually:
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\emulator\emulator.exe" -avd vaarta_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
& "$sdk\platform-tools\adb.exe" wait-for-device
& "$sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$sdk\platform-tools\adb.exe" shell am start -n ai.vaarta.debug/ai.vaarta.MainActivity
```

### Known gotchas already hit and fixed (don't re-debug these)

1. **`sdkmanager --licenses` piped "y" via stdin can silently hang** waiting on a prompt that
   never resolves through a background job. Fix used: write the license hash files directly —
   `%SDK%\licenses\android-sdk-license` and `android-sdk-preview-license` — instead of piping input.
2. **Root `build.gradle.kts` must declare every plugin used by ANY subproject** with `apply false`
   (`android.application`, `kotlin.android`, `kotlin.compose` — not just `kotlin.jvm`), or you get
   `Error resolving plugin ... already on the classpath with an unknown version`.
3. **`gradle.properties` needs `android.useAndroidX=true` explicitly** or the build fails at
   `:app:checkDebugAarMetadata` once any AndroidX/Compose dependency is added.
4. **Background downloads on this machine are slow** (~1–2 MB/s observed). A file being written by
   `Invoke-WebRequest` will report "in use by another process" if you try to read/extract it early —
   that means it's still downloading, not stuck. Always run downloads with `run_in_background` and
   wait for the actual completion notification, don't guess from elapsed time.
5. **This repo has no `.git` until this commit.** If you're reading this in a fresh clone, ignore
   this note — it's a historical marker that version control started late in the build.

## 4. Status matrix — what's built vs. not (evidence-based, not vibes)

### ✅ Built and verified

| Component | Evidence |
|---|---|
| `core:common` — event model, intel-pack model, text normalization | Compiles; used by all other modules |
| `core:reasoning` — Tier-0 deterministic engine (signal matching, stage grammar, scoring, hysteresis) | **6 unit tests green** (`RiskEngineTest.kt`) |
| `core:reasoning` — text-mode eval harness | **2 tests green** (`EvalTest.kt`): scam script reaches SCAM_PATTERN by min 3; genuine police call never does |
| `core:reasoning` — pack data-invariant | **1 test green** (`PackParityTest.kt`): every signal has a Manual Mode cue |
| `core:complaint` — slot-based complaint builder + JSON/TXT renderers | **4 tests green** (`ComplaintBuilderTest.kt`) |
| `tools:demo` — CLI rig, real engine end-to-end | Runs; verified output (risk trace + generated complaint) |
| `app` — Manual Mode chips, risk card (4 states), demo-call button, family-alert share, complaint share | **Manually verified live** on the `vaarta_test` emulator: tapped "Run demo scam call" → card correctly went OBSERVING → SCAM_PATTERN (100/100), "Alert family" button appeared, counter-fact line appeared |
| Intel pack `core-scam-v1.json` | 14 signals + 3 questions, EN/HI/Hinglish, loaded and matched correctly in tests + live run |
| Manual Mode ↔ signal parity | **Closed.** All 14 signals now have a `manualCue`; enforced by `PackParityTest.kt` so it can't silently regress |

**Total: 13 automated tests, 0 failures** (counted directly from fresh JUnit XML output, not the
build banner — see §7's evidence rule). Plus one manual end-to-end verification on a real Android
environment (emulator).

**Correction (2026-07-07):** earlier notes in this file's history and in conversation said "14"
then implied "15" total tests — both were arithmetic slips (RiskEngineTest has always had 6 tests,
not 8; it was momentarily miscounted right after it was first written). The true count, verified
by parsing `build/test-results/test/*.xml` directly, is 13. Fixed here rather than propagated.

### 🟡 Partially built (real gaps, not hidden)

| Component | What's missing |
|---|---|
| Intel pack breadth | Only a ~14-signal seed. Docs call for full per-scam-code (SC-01..SC-05) pattern lists per language. Current pack leans digital-arrest-generic. |
| Risk UI — verification questions | The question bank exists in the pack (`Q_STATION`, `Q_VERIFY_1930`, `Q_ADD_FAMILY`) but **nothing in the UI surfaces or cycles them.** The bubble spec's "ASK THEM: ❝...❞" one-question-at-a-time feature is unbuilt. |
| Complaint export | JSON + TXT renderers done and tested. **PDF renderer not built** (needs Android `PdfDocument`, was deferred as Android-specific). DOCX correctly out of scope. |
| Guardian/family alert | Share-intent mechanism works, but the message is **hardcoded/canned** — no real guardian contact picker or per-contact consent flow. |

### ❌ Not built (correctly deferred per ADR-0001, or genuinely not started)

- Real call detection (`CallScreeningService`) — app has no idea a call is happening; only runs via manual taps/demo button.
- Live audio capture + VAD + on-device ASR — zero code. This is the biggest remaining piece and the product's core unproven risk (see `docs/RISK_REGISTER.md` R-01).
- Overlay bubble over the dialer (`SYSTEM_ALERT_WINDOW`) — current UI is a normal full-screen activity, not an in-call floating bubble.
- Persistence (Room/SQLCipher) — nothing saved between app opens; RAM-only.
- Tier-1 (on-device LLM) / Tier-2 (cloud LLM polish) — correctly out of scope (cost + design).
- Multi-language beyond EN/HI/Hinglish seed.
- Play publishing, DOCX, Elder Mode, pattern-pack signing — correctly out of MVP scope (ADR-0001).

## 5. Next Up (prioritized backlog — start at the top)

**Open decision from 2026-07-07 — RESOLVED, then CORRECTED same day.** First resolution: close the
Manual Mode parity gap, then pivot to required deliverables (Architecture Diagram, Deck, Video)
ahead of remaining feature work. That reasoning assumed hackathon-deadline urgency that was never
actually confirmed. When challenged, the user clarified: **there is no fixed deadline**, and making
a Presentation Deck / Demo Video *now*, describing a ~75%-done app, produces throwaway work — the
moment the verification-question UI, PDF export, or guardian picker land, that deck/video goes
stale and has to be redone. **Corrected decision: finish the product first. Deck and Demo Video are
deferred to the end, made once, describing the more complete app — not iterated alongside it.**

The Architecture Diagram (done, see below) is the one exception worth keeping: it's designed to
track status via its own color-coding, not describe a "finished" product, so it doesn't go stale
the same way — but no further deliverable work happens until the app itself is further along.

1. **Verification-question UI** — surface the pack's `questions` list in the risk card, one at a
   time, cycling on tap (per `MOBILE_UX_SPEC.md` §3.2). The data already exists; this is UI-only.
2. **PDF export** — Android `PdfDocument` renderer for `ComplaintDraft` (parallel to the existing
   TXT/JSON renderers in `core:complaint`).
3. **Real guardian contact picker** — replace the canned alert message with a system contact picker
   + stored preference (still share-intent only, per the locked `SEND_SMS` decision).
4. **Intel pack breadth** — grow signal/pattern coverage per `SCAM_INTELLIGENCE.md` §5, still EN/HI/Hinglish only for MVP.
5. **Real device test** — install `app-debug.apk` on the owner's physical Android phone via `adb install`, confirm parity with emulator behavior.
6. **Stretch spike: on-device ASR** — sherpa-onnx feasibility spike per `docs/AUDIO_PIPELINE.md` /
   `INDIAN_LANGUAGE_SUPPORT.md` §4. Gated, not a blocker — Manual Mode already carries the product.
7. **Stretch: real call detection + overlay bubble** — `CallScreeningService` + `SYSTEM_ALERT_WINDOW`, makes it feel like a real in-call product even before ASR lands.
8. **Deferred to the end (do once, not iteratively):** Presentation Deck, Demo Video — build these
   after the items above land, describing the final MVP state, not a mid-way snapshot.

## 6. Process rules to follow (do not skip)

- `docs/IMPLEMENTATION_GUARDRAILS.md` — binding NEVER/ALWAYS rules for every change.
- `docs/CLAUDE_CODE_RULES.md` — AI-assisted development rules (no placeholders, no invented
  constants, mark `// UNVERIFIED-ON-DEVICE:` where relevant).
- Reopening any LOCKED decision (`docs/FOUNDATION_AUDIT.md` §2) or this scope lock (ADR-0001)
  requires a new ADR in `docs/decisions/` with evidence — not a silent code change.
- This repo now has git (see commit history). Use normal commit hygiene going forward — small,
  described commits — even though the formal branch/PR workflow in `CONTRIBUTING.md` is relaxed
  for solo/small-team MVP speed.

## 7. Rules for keeping this file honest

- **Update this file before ending any work session** — move items between "Built"/"Partial"/
  "Not built", update §5 Next Up, add a dated entry to the change log below.
- Never mark something "✅ Built" without evidence (a test, a verified run, a screenshot) — match
  the doc set's own "NO VERIFIED EVIDENCE FOUND" discipline. "I wrote the code" is not evidence
  that it works.
- If scope changes, write a new ADR in `docs/decisions/` first, then reflect it here — not the
  other way around.

## 8. Change log

- **2026-07-07** — Initial MVP build. Toolchain provisioned from scratch (JDK, Gradle, Android SDK,
  emulator — all $0). `core:common`, `core:reasoning` (Tier-0 engine), `core:complaint` built and
  tested (14 tests green). Android `:app` built, installed, and manually verified live on the
  `vaarta_test` emulator (demo scam call correctly reached SCAM_PATTERN 100/100). This status file
  and git version control created.
- **2026-07-07 (later same day)** — Git hygiene pass: renamed `master` → `main` (matches
  `CONTRIBUTING.md`, was mismatched before); added `.gitattributes` (LF enforced regardless of a
  contributor's local `core.autocrlf`); hardened `.gitignore` (keystores, native build dirs,
  `/models/`); added local commit-message template matching the `module — what/why` convention.
  Re-verified the foundation from a clean state, not from memory: `gradle clean test` → 12/12 pass
  (counted from fresh XML results, not the build banner); deleted and rebuilt the APK from source
  (byte-identical, 24,997,022 bytes — confirms reproducibility); reinstalled fresh on the emulator
  and re-ran the demo tap end-to-end. One methodology note worth keeping: the first re-verify
  screenshot looked wrong (Android's default splash icon, not the app), which turned out to be a
  too-short wait after `force-stop` (cold start takes ~6s) — confirmed via logcat (no crash, process
  alive, `MainActivity` resumed) before concluding it was a test-script timing issue, not an app
  bug. Re-ran with a longer wait and got the correct SCAM_PATTERN 100/100 result again. No app code
  changed in this pass — process/tooling only.
- **2026-07-07 (new session)** — Resumed via `PROJECT_STATUS.md` cold-start (§0), confirmed repo
  state unchanged (clean, `main`, 5 commits). Resolved the open decision: closed the Manual Mode
  parity gap (added `manualCue` for `SIG_LEGITIMACY_THEATER`/`SIG_IDENTITY_PHISH`/
  `SIG_ESCALATION_DOCS` in the pack + matching chips in `SessionViewModel`), added
  `PackParityTest.kt` so this specific gap is now structurally regression-proof, verified via clean
  rebuild (13/13 tests, counted from fresh XML — see §4's correction note) and a fresh APK build.
  Also corrected a real test-count error propagated earlier in this file/conversation (claimed
  14-then-15 total tests at various points; true count via XML is 13). Produced the Architecture
  Diagram (`docs/diagrams/vaarta-architecture-v1.svg`) — validated as well-formed XML, deliberately
  color-coded by real build status rather than showing the full designed pipeline as done.
- **2026-07-07 (same session, correction)** — Started building the Presentation Deck next (per the
  prior entry's stated plan), got as far as invoking the pptx-creation skill before the user
  challenged the sequencing: there is no fixed deadline, so a deck/video describing a ~75%-done app
  is throwaway work — it goes stale the moment more app features land and would need redoing.
  Correct call, acted on immediately: aborted the deck attempt (nothing was written to disk,
  confirmed via `git status`), reverted the priority order in §5 back to finishing app features
  first. Deck and Demo Video are deferred to the very end, built once against the final MVP state.
  Net effect: same conclusion the *first* pause reached before deadline-urgency reasoning
  (unconfirmed) overrode it — worth remembering next time a "hackathons need deliverables early"
  instinct shows up without checking whether it's actually true for this project.
