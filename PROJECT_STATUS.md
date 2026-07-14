# VAARTA — Project Status (READ THIS FIRST)

**Last updated:** 2026-07-14 · **Updated by:** implementation session (AI-assisted) · **Branch:** `vaarta-v2-ux`
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
| Risk UI — verification questions | **Closed.** `QuestionSelector` (core:reasoning, 5 tests) picks the highest-relevance question for the current stage; app shows one at a time with tap-to-cycle. Live-verified on the emulator: demo call correctly surfaced the ISOLATION-stage question first, tap cycled to the AUTHORITY-stage one. |
| Complaint export — PDF | **Closed.** `PdfExporter` (app, Android `PdfDocument` — can't be pure-Kotlin-tested, no unit tests for this one, noted honestly) paints `ComplaintRenderers.toText`'s output onto paginated A4 pages. Live-verified on the emulator: tapped Export PDF → Android share sheet opened offering a real `vaarta_complaint.pdf` with a **Print** option (OS only offers that for content it successfully parsed as a valid document) → pulled the file via `adb run-as` and confirmed `%PDF-1.4` header + `%%EOF` trailer, 39,244 bytes. Page-by-page visual render was not separately confirmed (no `pdftoppm` in this environment) — noted as the one unverified edge of this check. |
| Live AI suggestion, text-mode (`GeminiClient`, ADR-0002 Phase B) | **Closed.** Specialized system prompt + structured-output schema + `SuggestionSafetyFilter`, fails closed. Live-verified on the emulator: ran demo call with AI opted in → real Gemini reply appeared, contextual to the scammer's last line, filter-passed. |
| Live audio capture (`AudioCapture`) | **Closed, PC-verified.** 16kHz mono PCM16 via `AudioRecord`, VOICE_RECOGNITION→MIC fallback. Verified on the `vaarta_test` emulator booted with `-allow-host-audio`: `dumpsys audio` showed an active un-silenced recording session, and temporary diagnostic peak-logging confirmed real, dynamic, non-zero PCM reaching the app (cross-checked independently against an `ffmpeg` host recording of the same acoustic signal). |
| Live audio → AI suggestion streaming (`GeminiLiveClient`, ADR-0002 Phase B) | **Closed, PC-verified for this half.** OkHttp WebSocket, the protocol proven in `tools:demo:liveProbe`. Live-verified end-to-end on PC: mic audio streamed to Gemini Live produced real, safe, contextual suggestions rendered in `AiSuggestionCard` (e.g. correctly referenced India's 1930 cybercrime helpline, unprompted, in response to a synthetic scam script). One real bug found live-testing and fixed (per-fragment `.trim()` was jamming streamed words together — see 2026-07-07 PC-test changelog entry). |
| Recorded-audio scam analyzer (`GeminiClient.analyzeAudio` + `AudioScamAnalyzer`, ADR-0003 Phase 4D) | **Closed, verified end-to-end on the emulator (2026-07-09).** Pick any audio clip → `generateContent` transcribes + classifies it → transcript replayed through the deterministic `RiskEngine` (score ownership unchanged) → `HybridAlert` + reused web-grounding → shared `StatusBanner`/`ChatThread` verdict → optional save as `SessionSource.RECORDING`. Gate A proved the free key does inline-audio understanding (HTTP 200, accurate transcript). Live emulator run: a synthetic digital-arrest clip scored **100/100 SCAM_PATTERN** (deterministic, not AI), web-grounded as "Digital Arrest Scam" with 3 real cited sources, saved + replayed from encrypted history with sources intact. Fails closed on any error. |

**Total: 24 automated tests, 0 failures** (counted directly from fresh JUnit XML output, not the
build banner — see §7's evidence rule; re-verified 2026-07-07 after the live-audio fixes). Plus
manual end-to-end verification on a real Android environment (emulator) for Manual Mode/demo-call,
question-cycling, PDF export, text-mode AI suggestion, and (2026-07-07) the live-audio pipeline.

**Correction (2026-07-07):** earlier notes in this file's history and in conversation said "14"
then implied "15", then "18" total tests — all were stale counts as tests were added along the way
(`SuggestionSafetyFilterTest` alone added 6). The true count, verified by parsing
`build/test-results/test/*.xml` directly after each build, is now 24. Fixed here rather than
propagated — always re-count from fresh XML, never trust a remembered number.

### 🟡 Partially built (real gaps, not hidden)

| Component | What's missing |
|---|---|
| Intel pack breadth | Only a ~14-signal seed. Docs call for full per-scam-code (SC-01..SC-05) pattern lists per language. Current pack leans digital-arrest-generic. |
| Guardian/family alert | Share-intent mechanism works, but the message is **hardcoded/canned** — no real guardian contact picker or per-contact consent flow. |
| Live audio → deterministic engine (`inputTranscription` path) | Coded and wired (matches the same proven protocol as the working suggestion half), but **unverified**: PC acoustic-loopback testing (speaker→air→laptop mic) couldn't deliver clean enough audio for Gemini's `inputTranscription` to reliably transcribe English scam speech — got Tamil and noise instead of the real content in testing. Needs a real-phone speakerphone test (electrical audio path, no acoustic loopback) to fairly judge whether the risk score updates live from real caller speech. This is the #1 item in §5. |

### ❌ Not built (correctly deferred per ADR-0001, or genuinely not started)

- Real call detection (`CallScreeningService`) — app has no idea a call is happening; only runs via manual taps/demo button. **Deliberately deferred** (Android-15 FGS-start + Play-policy, ADR-0003 Phase 4C addendum).
- On-device ASR — not used; ASR happens server-side via Gemini Live's transcription (see the partial-build row above for its current verification status).
- ~~Overlay bubble over the dialer (`SYSTEM_ALERT_WINDOW`)~~ **BUILT (Phase 4C, 2026-07-09)** — `OverlayService` (FGS type=microphone) draws a draggable overlay bubble → ~45% panel with the shared `ChatThread`, verified on the emulator. Auto-appear-on-call is the deferred part (above).
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

**MAJOR REPRIORITIZATION (2026-07-07, ADR-0002):** the owner made clear the *core value* is
**live, in-call AI help** — hear the scammer live, understand words + tone, instantly suggest what
to say back. Research settled the platform reality (can't tap the call stream; mic+speakerphone is
the only path; Gemini Live free tier delivers live audio+tone understanding) and amended NEVER #3
to allow rail-guarded live LLM suggestions. This live-AI capability is now **THE headline feature**
and jumps to the top. The previously-planned polish items drop below it. Full plan: ADR-0002.

**Live AI voice-assist — build in phases (each independently verifiable):**
1. ~~Phase A — Audio foundation~~ **DONE** — `AudioCapture` built, live-verified on PC (real,
   dynamic, non-zero PCM confirmed via diagnostic peak logging + independent `ffmpeg` cross-check).
2. ~~Phase B — Live AI assist~~ **DONE and PROVEN on PC for the AI-suggestion half** — `GeminiLiveClient`
   + all ADR-0002 rails live-verified end-to-end (see 2026-07-07 PC-test changelog entry): real WS
   streaming, safety-filtered contextual suggestions rendered in-app. The inputTranscription→score
   half is coded and matches the proven protocol, but is **unverified** — PC acoustic-loopback audio
   quality wasn't clean enough to judge it fairly (see below).
3. **→ current focus: Real device test** — sideload `app-debug.apk` on the owner's physical Android
   phone and repeat the live-listening test on an actual call (or a second phone dialing in) with
   speakerphone. This is now priority #1, specifically to validate the one thing PC testing
   structurally can't: does the caller's real speech (electrical path via speakerphone mic, not
   speaker→air→laptop-mic acoustic loopback) reach `inputTranscription` cleanly enough to move the
   deterministic risk score live? Everything else in Phase B already has PC evidence.
4. ~~**Phase C — Floating bubble**~~ **DONE (overlay half), verified on the emulator (2026-07-09).**
   `OverlayService` (FGS type=microphone) hosts the extracted `CopilotSession` and draws a draggable
   `TYPE_APPLICATION_OVERLAY` bubble → ~45% panel with the shared `ChatThread`. User-initiated flow
   (grant overlay + mic → Start → bubble over dialer → Stop). **Auto-call-detection
   (`CallScreeningService`) remains DEFERRED** by Android-15 FGS/Play-policy design (ADR-0003 Phase 4C
   addendum). Not yet verified on physical hardware (OEM variance, R-05).
5. ~~**Phase 4D — recorded-audio scam analyzer**~~ **DONE, verified on the emulator (2026-07-09).**
   Pick a recording → transcribe + classify (Gate A proved the free key does inline-audio understanding)
   → deterministic re-score of the transcript + web grounding + shared verdict UI → optional save as a
   RECORDING. See the §8 changelog entry and ADR-0003 Phase 4D addendum.
6. **Phase D — Hardening** — 🟡 *in progress.* **Prompt-injection red-team of the AI safety filter is
   DONE** (2026-07-09, see §8 + ADR-0003 Phase D addendum): found + closed 6 real false-accept gaps in
   `SuggestionSafetyFilter`, now pinned by a 9-case `SuggestionSafetyRedTeamTest`. Remaining under this
   heading: broader fallback drills + latency-vs-budget eval. The one thing the emulator structurally
   can't prove remains the **physical-phone live-call test** (caller speech through speakerphone →
   transcription → score; OEM overlay/FGS variance R-05) — worth doing before any public demo.

**Then the smaller items (unchanged, just lower priority than live AI):**
7. **Real guardian contact picker** — system contact picker + stored preference (share-intent only).
8. **Intel pack breadth** — grow coverage per `SCAM_INTELLIGENCE.md` §5, EN/HI/Hinglish for MVP.
9. **Deferred to the very end (do once):** Presentation Deck, Demo Video — describe the final state.

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

- **2026-07-14 — v2 UX reshape, Phase 1: navigation + Home + Help; Manual Mode removed.** First
  phase of the "intelligence-everywhere" redesign (spec:
  `docs/superpowers/specs/2026-07-14-vaarta-v2-intelligence-ux-design.md`, plan:
  `docs/superpowers/plans/2026-07-14-vaarta-v2-phase1-nav-home.md`; branch `vaarta-v2-ux`). Built +
  **verified on the `vaarta_test` emulator** (screenshots):
  - **Manual Mode UI deleted** (owner: it gave the same canned answer to everyone — no intelligence).
    Removed `ui/ManualModeGrid.kt`, `signalVisualForCue`, and `CopilotSession`'s `cues`/`tapped`/
    `tapCue`. **The deterministic `RiskEngine` is untouched** — it stays as the invisible anti-
    hallucination safety floor (`HybridAlert` can raise but never lower it). Demo call still reaches
    **SCAM_PATTERN** live, confirming the floor survived the removal.
  - **3-tab shell** (`ui/VaartaNav.kt`): MD3 `Scaffold` + `NavigationBar` → Home / History / Help,
    no `navigation-compose` dependency (stays $0/lean). Live copilot, recording analyzer, and saved-
    call detail render as full-screen **sub-screens** (bottom bar hidden, own Back).
  - **Clean Home** (`ui/HomeScreen.kt`): one dominant **panic action** ("I'm on a scam call right
    now" → a "Do this now" sheet: don't pay / never share OTP / hang up / call 1930), two calm action
    cards (live · recording), and a trending-scams placeholder (real AI feed lands in Phase 4).
  - **Help tab** (`ui/HelpScreen.kt`): Call 1930, open cybercrime.gov.in, complaint draft (moved off
    the live screen, reuses `core:complaint` + `PdfExporter`), and warn-family share.
  - `assembleDebug` green; core tests unaffected (no `core:*` logic touched this phase).
  - **Next:** Phase 2 — the reusable "Understand this call" screen (verdict → clean transcript →
    download → multimodal chat). See the plan's build order (§10 of the spec).

- **2026-07-09 (later same day)** — **Phase D: AI safety hardening — prompt-injection red-team
  (ADR-0003 Phase D addendum).** Red-teamed `SuggestionSafetyFilter`, the last line before an AI reply
  reaches the user. Wrote an adversarial battery (`SuggestionSafetyRedTeamTest`) and ran it against the
  *current* filter first — **6 of 9 attack categories were genuine false-accepts** (bare imperative
  payment "Transfer the money…", "make the transfer" synonyms, "you must/need to…" obligation framing,
  OTP/verification-code synonyms, "do as they say / listen to the officer", bare imperative app-install).
  Closed all six **polarity-safely** — imperatives fire only sentence-initially or after an affirmative
  lead-in, so refusals ("I will not transfer…") and questions ("why would they need me to transfer
  money?") stay accepted; disclosure-code synonyms stay gated on an affirmative lead-in. Re-ran:
  **all 9 attacks now rejected, every "must stay accepted" guard still passes.** Core tests **76 → 85**;
  the battery is now a permanent regression suite. Honest scope: it's a deny list (defense-in-depth),
  deliberately not attempting adversarial-unicode/letter-spacing defeat — the real backstops for a total
  miss remain HybridAlert (alert can only be raised, never lowered) + fail-closed to the deterministic
  question. `assembleDebug` green.
- **2026-07-09 (later same day)** — **Phase 4D: recorded-audio scam analyzer (ADR-0003 Phase 4D
  addendum).** Added the after-the-fact case to the live copilot: analyze a *recording* of a call.
  Built + **verified end-to-end on the `vaarta_test` emulator**, no crash at any step:
  - **Gate A first (de-risk at $0):** a headless probe (`tools:demo:audioProbe`, key from git-ignored
    `secrets.properties`) sent a synthetic digital-arrest clip (Windows-TTS WAV, 1.59 MB) inline as
    base64 to `gemini-2.5-flash` → **HTTP 200 in ~8.3 s, accurate transcript, correct classification.**
    Folded one finding into the real prompt (enum-constrain `concern`, else the model returns free text).
  - **Score ownership unchanged (ADR-0002 D1):** the AI transcribes + gives an *advisory* concern; the
    authoritative score comes from replaying the transcribed CALLER turns through the deterministic
    `RiskEngine`, and `HybridAlert` lets the AI raise the alert but never lower the deterministic floor.
  - **New code:** `GeminiClient.analyzeAudio` (inline base64, `responseSchema`, thinking off, 60 s
    timeout, fails closed; `MAX_INLINE_AUDIO_BYTES = 14 MB`), `AudioAnalyzePrompt` (untrusted-audio
    framing), `CoachingWireParser.parseAudioAnalysis` + `Speaker.fromWire` + `AudioAnalysis`/`AudioTurn`
    models (core:reasoning, pure/unit-tested), `AudioScamAnalyzer` (Context-free pipeline; `assemble()`
    split from the network for testability; CALLER+UNKNOWN turns scored so ambiguity fails toward
    catching a scam), `recording/AudioAnalyzerViewModel` (reads the picked `Uri`, Idle/Running/Done/Error,
    friendly error copy). UI: home "🎧 Analyze a recorded call" (AI-configured only) → `GetContent`
    picker (no storage permission) → `AnalyzeScreen` (shared `StatusBanner` + `ChatThread`, "Share this
    warning" for HIGH_RISK+, "Save to history" as `SessionSource.RECORDING`). Shared `CoachBubble` now
    shows the "SAY THIS" header/chips only when there are replies (a recording verdict has none).
  - **Verified (adb + screenshots):** home button → SAF picker → picked the pushed clip → Running →
    verdict **"This matches a known scam" Risk 100/100** (deterministic engine scored the transcript),
    **web-grounded "Digital Arrest Scam" + 3 real cited sources** (thehindu.com ×2, livemint.com),
    plain-language summary → Save → the RECORDING appears in history with its scam-type and its Detail
    screen **replays the full thread including the sources** (SQLCipher JSON round-trip intact).
  - **Tests: 76 core tests green** (from 70) — +6 `parseAudioAnalysis` cases (well-formed, tolerant
    speaker mapping, blank-turn drop, fail-closed on empty/malformed, unknown-concern → OBSERVING).
  - **Consent (ADR-0004 kept):** analysis is RAM-only until the user taps Save; the clip is never
    stored (only the transcript/verdict the user chooses to keep).
  - **Honest gaps:** `AudioScamAnalyzer.assemble` has no standalone JVM test (combines already-tested
    `RiskEngine` + `HybridAlert`; covered by the on-device run); inline-only (clips >14 MB rejected with
    a message, Files API deferred); diarization is the model's guess (why scoring fails toward "caller");
    unusual container mimes may fail-closed as "unsupported format" — verified for WAV so far.
- **2026-07-09 (later same day)** — **Phase 4C: floating overlay + session-in-service (ADR-0003 Phase
  C addendum).** The copilot now runs as a floating in-call window, not just a full-screen activity.
  Built + **verified end-to-end on the `vaarta_test` emulator**:
  - **4C-1 — extracted `CopilotSession`** (pure refactor, zero behaviour change): the whole pipeline
    (engine ownership, `OwnWordsGate`, live reconnect, hybrid ratchet, grounding cap, demo) moved out of
    `SessionViewModel` into a plain `CopilotSession(scope: CoroutineScope)` holder so the *same* logic
    runs behind the in-app UI (ViewModel wraps it on `viewModelScope`) **or** inside the service (own
    scope). `SessionViewModel` is now a thin wrapper; `MainActivity` observes `vm.session.<flow>`.
    Verified: compiles + **70 core tests still green** + emulator demo path unchanged.
  - **Shared `ChatView.kt`** — moved `StatusBanner`/`ScamIdCard`/`ChatThread`/bubbles into one file
    (`internal`) so the app screen, history detail, AND the overlay panel render the identical thread.
  - **`OverlayService`** — foreground service, `foregroundServiceType="microphone"`, is itself the
    `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner` a non-Activity `ComposeView` needs.
    Draggable `TYPE_APPLICATION_OVERLAY` bubble (steady risk-colour ring) ↔ ~45% panel hosting the
    shared `ChatThread`. Manifest: `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE(_MICROPHONE)` +
    `POST_NOTIFICATIONS` + `<service … type=microphone>`. App wiring: "🪟 Use as a floating window"
    → overlay-permission handoff → mic → `OverlayService.start()` + `moveTaskToBack`.
  - **Verified (adb + dumpsys + screenshots), no crash at any step:** FGS runs with `types=0x00000080`
    (microphone) and platform-approved `Background started FGS: Allowed … uidState: TOP`; the bubble
    draws over the launcher (`TYPE_APPLICATION_OVERLAY`); tap **expands** to the panel showing the
    shared `StatusBanner` + live `LISTENING` status + calm empty-state; **▾ Hide collapses** back to the
    bubble; **Stop tears everything down** (service gone, overlay windows gone).
  - **Real bug found + fixed while verifying:** a `View.OnTouchListener` on an overlay `ComposeView`
    never received the tap (expand didn't fire) — switched tap+drag to Compose `pointerInput`
    (`detectTapGestures`/`detectDragGestures`), which dispatches reliably. Fixed + re-verified.
  - **Consent (ADR-0004 kept):** stopping does NOT auto-persist; the live session is published via
    `OverlayService.activeSession` for the app's explicit "Save this call" action.
  - **Honest gaps:** overlay thread verified with empty-state/banner/live-status but not *populated with
    live caller turns in the overlay* (emulator booted `-no-audio`; `ChatThread` rendering is the same
    composable proven in 4A, live-audio→thread is the PC-verified path); auto-call-detect DEFERRED by
    design; physical-hardware OEM overlay/FGS variance (R-05) unverified; no JVM unit test for the
    Android-framework `OverlayService`/`CopilotSession` (covered by the on-device run).
- **2026-07-09** — **Phase 4B: encrypted saved history (ADR-0004).** Added opt-in, on-device,
  encrypted-at-rest call history — the first deliberate, scoped reversal of the RAM-only stance (P2),
  gated by explicit "Save this call" consent. Built + **verified end-to-end on the emulator**:
  - **New module `core:data`** (Android library; Room + SQLCipher + KSP): `CallSessionEntity` /
    `TurnEntity` (+ `Converters`), `HistoryDao`, `VaartaDatabase`, `HistoryRepository`. Turns written
    live (crash-safe), session finalized on stop. Depends only on `core:common` (acyclic).
  - **`DatabaseKeyManager`** — SQLCipher passphrase is a random 32-byte value minted first-launch and
    sealed by a non-exportable **Android Keystore** AES-256/GCM key (wrapped blob in SharedPreferences).
    No hardcoded key; deprecated `security-crypto` deliberately avoided.
  - **App:** `HistoryViewModel` (AndroidViewModel; `SessionViewModel` stays RAM-only + Context-free),
    `history/ChatHistoryMapping.kt` (ChatItem↔Turn, coach replies/sources as boundary JSON so
    `core:data` never sees `core:reasoning` types). New screens in `MainActivity`: Home "🕘 History" +
    "💾 Save this call", **Saved-calls list** (retention Keep/7d/30d, per-row + delete-all), **read-only
    Detail** replay (reuses `ChatThread`/`VerdictHeader`).
  - **Verified:** `libsqlcipher.so` loads; Keystore key created (`vaarta_db_key.xml`); DB file is
    **encrypted** (no plaintext `SQLite format 3` header); a saved session **survived app restart AND
    reinstall**, loaded from the list, and the detail screen replayed the full thread (JSON round-trip
    intact). 70 core unit tests still green.
  - **Real bug found + fixed while testing:** the header drew *under* the status bar (top taps hit the
    system bar, not the app) — added `statusBarsPadding()` to all three screens.
  - **Honest gap:** `core:data` crypto/Room round-trip has no JVM unit test (needs instrumentation);
    covered by the on-device end-to-end verification above. ADR-0004 written; PRIVACY/RISK doc-table
    edits still pending (task #24, pre-commit).
- **2026-07-08** — **Live conversation copilot + hybrid web-grounded intelligence (ADR-0003).** Turned
  the single-suggestion layer into a turn-by-turn copilot and added live web-search scam
  identification, under a new hybrid "safety ratchet". Built + verified this session:
  - **Phase 1 (safety/intelligence core, headless):** `CoachingModels` (Reply/ReplyKind/CoachingResponse/
    CoachTurn/Source/GroundedAssessment); **overhauled `SuggestionSafetyFilter`** — now catches
    OTP/Aadhaar/PAN/bank-detail disclosure, isolation compliance, payment synonyms, compliance verbs,
    remote-access, in **English + Hindi/Hinglish**, polarity-aware ("I won't transfer" stays allowed),
    and filters the `why` field; `OwnWordsGate` (self-echo defense — stops the user's read-aloud reply
    from pinning the score, a real pre-existing bug); `nextStage`; `HybridAlert` (safety ratchet);
    `CoachPrompt` + `GroundedClassifyPrompt`; `GeminiClient.coach()` + `classify()`; `CoachingWireParser`.
  - **Phase 2 (copilot UI + live wiring):** real-ms clock (fixed the synthetic-clock bug that froze
    decay/hysteresis), fragment coalescing into turns, feed pipeline, live-lifecycle bug fixes
    (ERROR-restart-wedge, reset-doesn't-stop-live, late-fragment-after-stop), auto-reconnect for the
    ~15-min session cap. **Live coaching verified on the physical phone** — correct per-turn warnings,
    correct next-stage prediction, graded VERIFY/REFUSE/EXIT replies, all safety-filtered.
  - **Phase 3 (hybrid + grounding + redesign):** `HybridAlert` combines the deterministic floor with
    the AI's advisory concern by `max()` — the AI can RAISE the alert (novel scams) but never lower it;
    reassurance only on cited consensus; scam-variant labels require a cited source. Web grounding is a
    **two-call design** forced by probed API reality (2.5 grounding is free but can't combine with
    structured output; Gemini-3 grounding is paid) — grounded classification + structured coach, both
    free on `gemini-2.5-flash`, merged in the ViewModel; sources parsed from Gemini grounding metadata
    (prose-first prompt + 1024 tokens confirmed to return real cited sources: livemint, niti.gov.in…).
    Language-consistency instruction added. **UI redesigned** (all-ages, uncluttered): hybrid risk
    banner, web-grounded scam-ID card with tappable sources, "say this" hero + graded replies, demoted
    "earlier in this call" history, Manual Mode/complaint kept reachable but secondary — verified
    rendering on the emulator demo path.
  - **Tests:** 68 core:reasoning/complaint tests green (from 24), incl. `HybridAlertTest` (AI can raise
    never lower; "scammer talks AI to safe" cannot drop the alert; reassurance needs cited consensus),
    expanded safety-filter + grounded-parser + self-echo suites. Deterministic eval gates unchanged.
  - **Docs:** ADR-0003 written. **Still to land in the copilot commit:** PRIVACY_SECURITY.md C6 (note
    the web-search query) + data-inventory row + `docs/data-safety.json`; RISK_REGISTER row (grounding
    manipulation/hallucination). **Still open:** live end-to-end verification of the grounded scam-ID
    card on a real call (needs the phone reconnected); Gate 0 (real caller's voice surviving AEC).
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
- **2026-07-07 (same session, back to app work)** — Closed Next Up #1: verification-question UI.
  Added `QuestionSelector` to `core:reasoning` (pure Kotlin, 5 new tests) rather than putting
  selection logic directly in the Android ViewModel, so it stays unit-testable. Wired into
  `SessionViewModel`/`MainActivity` — one question shown at a time, tap cycles, matches
  `MOBILE_UX_SPEC.md` §3.2. Verified via `gradle clean test` (18/18 pass, fresh XML) AND live on
  the `vaarta_test` emulator: demo call correctly surfaced the ISOLATION-stage question first
  ("I am adding my son/daughter..."), tapping cycled to the AUTHORITY-stage one ("Which police
  station..."), matching `QuestionSelectorTest`'s predicted order exactly. Both unit-level and
  device-level evidence agree — no daylight between "tests pass" and "the feature actually works."
- **2026-07-07 (same session, PDF export)** — Closed Next Up #2. Added `PdfExporter` (app-level,
  `android.graphics.pdf.PdfDocument` — Android-only, so this can't live in `core:complaint`;
  honestly has no unit tests, noted rather than hidden) plus a `FileProvider` + `file_paths.xml`
  so the generated PDF can be shared via `content://` on modern Android without any new dangerous
  permission. Verified live on the emulator, methodically: (1) hit a coordinate bug mid-check —
  used displayed-screenshot pixel coordinates for `adb shell input tap` instead of converting to
  the device's actual 1080x2400 resolution, taps landed on the wrong element; caught it, switched
  to `uiautomator dump` for exact element bounds instead of eyeballing screenshots, which is the
  more reliable method going forward; (2) with correct coordinates, tapped Export PDF — Android's
  share sheet opened with a real `vaarta_complaint.pdf` and a **Print** option (the OS only offers
  that for content it successfully parsed); (3) pulled the file via `adb shell run-as` and
  confirmed `%PDF-1.4` header + `%%EOF` trailer, 39,244 bytes. Four independent signals the PDF is
  real and valid; page-by-page visual render specifically was not confirmed (no `pdftoppm` in this
  environment) — that's the one honestly-open edge of this verification.
- **2026-07-07 (same session, live-AI pivot)** — Owner clarified the core requirement: live in-call
  AI help (hear scammer live → understand words+tone → instantly suggest the reply), not a bare
  score or post-call analysis. Did proper web research on the two crux questions: (1) live call-audio
  access — confirmed HARD limit, no app can tap the call stream (Play ban since 2022), only mic+
  speakerphone works, Twilio rejected ($0/backend/legal/latency); (2) free live-AI tech — found
  Gemini Live API (free tier, streams audio, natively understands tone/emotion). Wrote ADR-0002
  capturing the architecture + a scoped amendment to GUARDRAILS NEVER #3 (LLM may now make live
  rail-guarded SUGGESTIONS, never the score) + mandatory safety rails, and reflected the amendment
  in the guardrail doc itself. Reprioritized §5: live-AI voice-assist (Phases A–D) is now the
  headline build; polish items dropped below it. No code yet this step — research + decision +
  planning, deliberately, before building the biggest/riskiest part of the project.
- **2026-07-07 (same session, Phase B core + AI proven)** — Owner provided a Gemini API key (stored
  in git-ignored `secrets.properties`; flagged for rotation since it was pasted in chat). Verified
  it: authenticates (HTTP 200), exposes `gemini-2.5-flash-native-audio-latest` (the real Live API
  model — supports `bidiGenerateContent`) + text models. Built + tested the safety core of Phase B
  first (no key needed): `LiveSuggestion` schema + `SuggestionSafetyFilter` (6 tests, blocks
  advise-to-pay/legal-advice/accusation, allows good questions). Then PROVED the specialized
  intelligence end-to-end via a plain REST call before any Android code: specialized prompt +
  grounding + structured-output schema turned a real scam line into a safe isolation-breaker ("I
  will confirm this with the 1930 cyber helpline first.", confidence 0.9) in 1.77s, passing the
  filter. Key impl finding recorded in ADR-0002: thinking must be OFF (`thinkingBudget=0`) for
  latency + to not blow the token budget. **The whole AI approach is now de-risked at $0 before the
  Android/streaming build.** Next: wire this proven `generateContent` call into the app as an
  "AI-suggested" card (text-mode first, emulator-testable), then the live-audio streaming layer.
- **2026-07-07 (same session, LIVE AI WORKING in-app)** — Wired the proven Gemini call into the app
  end-to-end: `GeminiClient` (app, HttpURLConnection + structured output, thinking off, fails-closed
  on any error), opt-in consent toggle (OFF by default), async request via viewModelScope/IO, result
  passed through `SuggestionSafetyFilter`, shown as a distinct "AI-SUGGESTED REPLY" card ALONGSIDE
  (never replacing) the deterministic ASK-THEM card. **Verified LIVE on the emulator:** enabled AI,
  ran the demo scam call → real Gemini reply appeared — "I will not transfer any money. I will verify
  this with the RBI directly." — contextual to the scammer's last line, safe, filter-passed. Two
  test-method bugs hit and fixed along the way (both mine, not the app's): (1) tapped adb coords from
  a taller layout onto a shorter fresh-launch layout → missed the toggle; fix = run demo first, then
  dump UI for real bounds; (2) added temporary content-free diagnostic logging to GeminiClient (HTTP
  code + error type only, no key/PII) to diagnose the silent fail-closed — kept, aligns with
  DEBUGGING_PLAYBOOK's content-free logging. Phase B (text-mode AI) is functionally DONE and proven
  in-app. Remaining for full vision: live-audio streaming (Gemini Live WebSocket + mic) — needs the
  physical phone. Standing reminders: rotate the API key; key is embedded in the debug APK (ADR-0002).
- **2026-07-07 (same session, Live WebSocket protocol PROVEN)** — Before building Android mic/stream
  code, de-risked the Gemini Live BidiGenerateContent protocol with a headless OkHttp probe
  (`tools:demo:liveProbe`, run via git-ignored key). Key finding it caught early: the native-audio
  model REJECTS `responseModalities:["TEXT"]` (close 1007) — it's the only Live model on this key, so
  Live must run in AUDIO mode. Solution proven working: AUDIO mode + `outputAudioTranscription`
  (AI suggestion as text, displayed; audio never played) + `inputAudioTranscription` (scammer words
  → deterministic engine). Full protocol recipe recorded in ADR-0002. Verified turn returned a real
  text suggestion. Added OkHttp dep. **Entire live-audio AI path now de-risked at $0 before touching
  the device.** Next (big, needs the physical phone to test): Android mic capture (AudioRecord 16kHz
  PCM16) + foreground service + `GeminiLiveClient` (OkHttp WebSocket streaming) + a "live listening"
  UI mode. Reminder unchanged: rotate the API key.
- **2026-07-07 (same session, live-audio layer BUILT)** — Wrote `AudioCapture` (AudioRecord, 16kHz
  mono PCM16, VOICE_RECOGNITION→MIC fallback, dedicated thread, no disk writes) and `GeminiLiveClient`
  (OkHttp WebSocket, the proven `liveProbe` protocol, regex-extracted in/out transcription, fails
  closed on any WS error). Wired into `SessionViewModel`/`MainActivity`: mic-permission launcher,
  "Start live listening" → "● Live: STATUS" UI, caller transcript → deterministic engine (score stays
  deterministic, ADR-0002), AI transcript → `SuggestionSafetyFilter` → `AiSuggestionCard`. Compiled
  clean (`assembleDebug` BUILD SUCCESSFUL). **Correction to the note directly above:** it said this
  needed the physical phone — checking the actual PC hardware first (this machine has a real mic) and
  the emulator's `-help-all` output showed `-allow-host-audio` (routes the PC's real mic into the
  emulator's virtual one), so the entire pipeline turned out to be PC-testable after all. Don't repeat
  that "needs the phone" assumption without checking — see next entry for the actual PC test.
- **2026-07-07 (same session, live-audio PC TEST — proven + one real bug found and fixed)** — Booted
  `vaarta_test` with `-allow-host-audio` (boot log confirmed: "Warning: Allowing host microphone
  input."), installed, granted `RECORD_AUDIO` via `adb`, started live listening. Verified with actual
  evidence, layer by layer, not assumptions:
  - **Mic bridge real, not zeroed:** `dumpsys audio` showed an active, un-silenced `VOICE_RECOGNITION`
    recording session (`src:VOICE_RECOGNITION not silenced`). Independently confirmed the PC's
    acoustic loopback (speaker→air→built-in mic) actually carries signal: recorded 7s with `ffmpeg`
    from "Microphone Array (Realtek Audio)" while a synthetic scam line played via Windows TTS
    (`System.Speech.Synthesis`) — `volumedetect` showed max -14.1 dB (real signal, not silence floor).
    Then added temporary bounded diagnostic logging (`AudioCapture`, first 40 chunks, peak amplitude
    only) and confirmed the app's own `AudioRecord` reads real, dynamic, non-zero PCM (peaks
    swinging ~2–3700 out of 32767) — the emulator↔host audio bridge genuinely works.
  - **WebSocket + AI suggestion pipeline: proven live, end-to-end.** Playing the synthetic scam script
    ("officer Sharma... CBI... arrest warrant... transfer the money") produced a real, safe,
    contextual AI reply rendered in the `AiSuggestionCard` — e.g. "I will confirm this with the 1930
    cyber helpline first." (India's real cybercrime helpline, from the system prompt's domain
    knowledge, unprompted). 300+ WS messages exchanged in one session; setup/turn handling solid.
  - **Real bug found and fixed:** `GeminiLiveClient.unescape()` called `.trim()` on every streamed
    text *fragment* before appending to the suggestion buffer, stripping the leading space Gemini's
    streaming API puts at fragment boundaries — words jammed together ("IamVAARTA,yourspecialized...").
    Fix: only trim once, on the fully assembled buffer at turn-end; per-fragment spacing is now
    preserved. Verified fixed: a rebuilt/reinstalled run rendered a clean "I am not clear, could you
    please state the name again?" with correct spacing.
  - **Real (test-environment) limitation found, not a code bug:** `inputTranscription` — the path that
    feeds the deterministic engine's score from the caller's words — was unreliable over acoustic
    loopback. Raw WS logs showed it mis-transcribing the English TTS speech as Tamil in one case and
    as noise ("70") in another, and the model itself said "I can't directly understand that language.
    Please switch to English" on one turn. This traces to real audio degradation inherent to the test
    method (speaker→open air→laptop mic, plus audible fan noise — three lossy hops that a real phone
    call on speakerphone doesn't have), not a parsing or wiring defect: the regex extraction correctly
    handled every message shape it was given, `AudioCapture` is proven feeding real signal, and the
    model's own reasoning (not reliant on the transcription side-channel) tracked context well enough
    to reference the correct scam pattern and helpline. Added a language-robustness instruction to
    `SharedScamPrompt` (audio is Indian-accented/Hindi-English code-switched; don't refuse on unclear
    audio) — reduced outright refusals but did not fix the underlying ASR-quality problem, as expected.
  - **Honest bottom line:** mic capture, host-audio bridge, WS streaming, and the AI-suggestion half of
    ADR-0002 are PROVEN on PC with direct evidence. The inputTranscription→risk-score half is UNVERIFIED
    on PC — not because the code is wrong (nothing found on inspection contradicts the design) but
    because this PC's acoustic-loopback test method can't deliver clean enough audio to judge it. The
    real device test (speakerphone → phone mic, electrical path, one hop, no loopback) is now the only
    way to actually validate that path, and moves to the top of §5 for that specific reason — not
    because PC testing was skipped, but because it was carried as far as it can honestly go.
  - Removed the raw-WS-message diagnostic log added mid-investigation (too verbose to leave — logged
    full audio payloads) after it served its purpose; kept the bounded 40-chunk `AudioCapture` peak
    log (matches the project's existing "DIAGNOSTIC (temporary)" convention in `GeminiClient`).
  - Re-ran `gradle clean test` after all fixes: **24/24 tests green**, fresh XML, no regressions.
