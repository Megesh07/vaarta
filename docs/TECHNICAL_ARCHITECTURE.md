# VAARTA — Technical Architecture

**Status:** FOUNDATION — Single Source of Truth · v1.0 · 2026-07-05
**Owner:** CTO / Principal Android Engineer

---

## 1. Governing principle: on-device first

VAARTA v1 is a **fully on-device Android application**. There is **no mandatory backend**. Cloud services (LLM polish, pattern-pack updates) are optional, opt-in, and degrade gracefully to on-device behavior.

**Why:** (a) Privacy/DPDPA — the strongest data-protection posture is not holding data; (b) latency — in-call assistance cannot tolerate mobile-network round trips on Tier-3 connectivity; (c) cost — no per-user inference bill at v1 scale; (d) trust — "your call never leaves your phone" is the core marketing and ethical claim.
**Alternatives considered:** cloud-streaming ASR (Google STT / Azure) — rejected for v1: sends live call content off-device by default, breaks the trust claim, adds ₹ cost and latency, and creates a DPDPA processing footprint. Kept as an architectural extension point only.
**Tradeoff accepted:** on-device ASR quality < cloud ASR quality, especially for low-resource Indic languages. Mitigated by the rule engine being robust to noisy transcripts (keyword/stem matching, fuzzy match) and by Manual Mode.

## 2. The binding platform constraint (read this first)

**Third-party Android apps cannot access the caller's voice stream.**

- `MediaRecorder.AudioSource.VOICE_CALL` / `VOICE_DOWNLINK` / `VOICE_UPLINK` require system privileges (`CAPTURE_AUDIO_OUTPUT`) — unavailable to Play Store apps since Android 9/10 hardening.
- The AccessibilityService call-recording workaround is **explicitly banned** by Google Play policy (since May 2022). Using it = removal.
- `CallScreeningService` gives call metadata and screening control, **not** audio.

**Therefore VAARTA's only compliant live-audio path is: the microphone, while the user has speakerphone enabled.** The mic picks up both the user's voice (near) and the caller's voice (played through the loudspeaker). This is the same approach used by mainstream "AI call scanner" features shipped on Play.

Consequences, binding on all specs:
1. Onboarding and the in-call bubble must **coach the user to enable speakerphone** (one tap; we can programmatically suggest but on modern Android cannot force it from a non-default-dialer app — we show a "Turn on speaker" instruction; if we hold the `CallScreeningService` role we still don't control in-call audio routing).
2. **Manual Mode (F4) is a P0 feature, not a fallback afterthought** — earpiece calls, Bluetooth headsets, and consent-withheld cases must still get risk scoring via user-tapped cues.
3. Audio quality planning assumes far-end speech at loudspeaker-through-air quality. AUDIO_PIPELINE.md owns this.

## 3. System overview

```
┌─────────────────────────────── ANDROID APP (Kotlin) ───────────────────────────────┐
│                                                                                     │
│  :app  (Compose UI, navigation, onboarding, settings, exports)                      │
│    │                                                                                │
│  ┌─┴──────────────┐  ┌───────────────┐  ┌──────────────────┐  ┌─────────────────┐  │
│  │ :core:call     │  │ :core:overlay │  │ :core:audio      │  │ :core:asr       │  │
│  │ CallScreening- │  │ Bubble window │  │ ForegroundService│  │ sherpa-onnx /   │  │
│  │ Service, call  │→ │ (SYSTEM_ALERT │← │ AudioRecord 16k  │→ │ whisper.cpp JNI │  │
│  │ state events   │  │ _WINDOW)      │  │ VAD, ring buffer │  │ streaming ASR   │  │
│  └────────────────┘  └───────┬───────┘  └──────────────────┘  └────────┬────────┘  │
│                              │                    ┌─────────────────────┘           │
│                              ▼                    ▼                                 │
│                      ┌──────────────────────────────────┐   ┌────────────────────┐ │
│                      │ :core:reasoning                  │   │ :core:intel        │ │
│                      │ Risk engine (Tier-0 rules),      │←──│ Scam pattern packs │ │
│                      │ question selector, session state │   │ (bundled, signed)  │ │
│                      └──────────────┬───────────────────┘   └────────────────────┘ │
│                                     ▼                                              │
│  ┌────────────────┐  ┌──────────────────────┐  ┌───────────────────────────────┐   │
│  │ :core:alerts   │  │ :core:complaint      │  │ :core:data                    │   │
│  │ Guardian SMS / │  │ Draft builder,       │  │ Room + SQLCipher, DataStore,  │   │
│  │ share intents  │  │ PDF/DOCX/TXT/JSON    │  │ session store (RAM-first)     │   │
│  └────────────────┘  └──────────────────────┘  └───────────────────────────────┘   │
│                                                                                     │
│  :core:cloud (OPTIONAL, opt-in): Claude API client (complaint polish, F14),         │
│                                  pattern-pack fetcher (F13). Feature-flagged.       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 4. Technology decisions (decision records)

### D1 — Language & UI: Kotlin + Jetpack Compose
- **Why:** platform default, best access to telephony/audio APIs, Compose for rapid, accessible UI; team of AI-assisted developers benefits from single-language codebase.
- **Alternatives:** Flutter/React Native — rejected: overlay windows, foreground-service audio, JNI ASR, and CallScreeningService are all friction points through a bridge; no cross-platform payoff (no iOS v1).
- **Failure/recovery:** none specific; standard Android.

### D2 — Call detection: `CallScreeningService` role + `TelephonyCallback` fallback
- **Why:** `RoleManager.ROLE_CALL_SCREENING` gives reliable incoming-call events + number without `READ_CALL_LOG` (which Play restricts heavily). Fallback `READ_PHONE_STATE` + `TelephonyCallback` when the user declines the role.
- **Alternatives:** default dialer replacement (`ROLE_DIALER`) — rejected v1: enormous scope (we'd own the entire in-call UI), though it is the *only* path to first-party in-call audio routing control; revisit at M3+ as "VAARTA Dialer" if speakerphone friction proves fatal. `READ_CALL_LOG` — rejected: Play sensitive-permission gauntlet, not needed.
- **Privacy:** number processed in RAM; stored only in a saved session/complaint on user action.
- **Failure:** role denied → fallback listener; both denied → app still works as a manually-launched assistant (user opens app during a call).
- **Testing:** Robolectric shadows + physical dual-SIM matrix (TESTING_STRATEGY.md §5).

### D3 — Overlay: `SYSTEM_ALERT_WINDOW` bubble
- **Why:** must render over any dialer UI. Notification-only interaction is too slow for a frightened user.
- **Alternatives:** Android `Bubbles` API — tied to notifications/conversation shortcuts, unreliable across OEMs for this use; full-screen Activity over call — hides dialer controls (dangerous). Rejected.
- **Failure:** permission denied → degrade to high-priority notification with expanded actions + full-screen post-call summary. OEM quirks in DEBUGGING_PLAYBOOK.md §4.

### D4 — ASR: **sherpa-onnx** runtime with per-language streaming models; whisper.cpp as offline-batch fallback
- **Why:** sherpa-onnx supports streaming (low latency), runs Zipformer/Conformer models incl. AI4Bharat-lineage Indic models, Apache-2.0, proven on Android ARM64. Whisper (small/base multilingual) is stronger on code-mixed speech but is batch-oriented — used for post-call re-transcription polish of the *kept-in-RAM* text, and as the model for languages lacking good streaming models.
- **Alternatives:** Android `SpeechRecognizer` (on-device) — quality/language coverage varies wildly by OEM/Play Services version, no guaranteed continuous mode across all devices; rejected as primary, kept as emergency fallback. Cloud ASR — rejected (see §1). Vosk — weaker Indic coverage than current AI4Bharat-era models.
- **Tradeoffs:** model download per language (~40–150 MB); WER on loudspeaker-through-air audio will be materially worse than clean benchmarks — the reasoning engine is designed for noisy input (AI_REASONING_ENGINE.md §4).
- **Verification note:** exact model choice per language requires on-device benchmarking in M1 — INDIAN_LANGUAGE_SUPPORT.md §4 defines the bake-off. Do not hardcode model names in code before that.

### D5 — Reasoning: Tier-0 deterministic signal engine on-device (P0); optional cloud LLM (P1)
- **Why:** in-call risk scoring must be offline, instant, explainable, and testable. A weighted-signal rules engine over transcript + call metadata + user-tapped cues achieves this. LLMs enter only where latency and connectivity allow: post-call complaint polish and (later) Tier-1 nuance scoring.
- **Alternatives:** on-device small LLM (Gemma-class) — promising but RAM/latency on 4 GB devices unproven for concurrent ASR+LLM; tracked as Q2. Cloud LLM live scoring — violates on-device-first default.
- Full spec: AI_REASONING_ENGINE.md.

### D6 — Storage: Room + SQLCipher; session data RAM-first
- **Why:** transcripts live in RAM during the call and are **discarded on session end unless the user saves evidence**. Persistent data (saved sessions, complaint drafts, guardian contacts, settings) is encrypted at rest (SQLCipher key in Android Keystore).
- **Alternatives:** EncryptedFile/plain Room — SQLCipher chosen for whole-DB encryption with queryability. DATABASE_DESIGN.md owns schema.

### D7 — Optional backend: **none in v1**; M2+ = pattern-pack CDN + LLM proxy
- If/when built: FastAPI + Postgres, stateless LLM proxy (no transcript persistence, no logs of content), signed pattern packs on a CDN. Decision deferred; interface contracts defined now (`:core:cloud` interfaces) so v1 code doesn't churn.

### D8 — Dependency policy
Minimal third-party surface: no analytics SDKs, no ad SDKs, no crash reporter that ships PII (Play-provided ANR/crash data + opt-in local log export instead — see DEBUGGING_PLAYBOOK.md §2). Every new dependency requires a CLAUDE_CODE_RULES.md §7 review.

## 5. Runtime data flow (protection session)

```
CallScreeningService.onScreenCall(number)
 → SessionCoordinator.callDetected()            [RAM only]
 → post "Protection Available" notification
User taps Enable
 → check consents (mic disclosure accepted? guardian configured?)
 → start ForegroundService (types: microphone|phoneCall) + overlay bubble
 → AudioEngine: AudioRecord(VOICE_RECOGNITION, 16kHz, mono, PCM16)
     → VAD gate → 300ms frames → streaming ASR → partial transcripts
 → RiskEngine.ingest(TranscriptEvent | ManualCueEvent | CallMetaEvent)
     → RiskState(score, stage, topSignals)  → bubble render (≤ 200ms budget)
     → QuestionSelector(stage, language)     → bubble suggestions
 → threshold crossings → AlertPrompt → user confirms → GuardianAlert (SMS/share)
Call ends (TelephonyCallback)
 → AudioEngine.stop(); audio buffers zeroed
 → ComplaintBuilder.assemble(sessionRAM)  → draft (RAM)
 → user: Save / Export / Discard
     Discard (default after 30 min unattended): wipe session RAM, done
     Save: persist transcript-text + events to SQLCipher DB
     Export: render PDF/DOCX/TXT/JSON to user-chosen SAF location
```

## 6. Performance budgets (binding)

| Path | Budget |
|---|---|
| Notification shown after call detected | ≤ 1.5 s |
| Tap "Enable" → bubble visible | ≤ 2 s |
| Audio frame → ASR partial | ≤ 1.5 s (streaming) |
| ASR partial → risk score update in bubble | ≤ 300 ms |
| End of call → complaint draft ready | ≤ 10 s on-device |
| Peak added RAM during protection | ≤ 1 GB (4 GB device) |
| Battery per 30-min protected call | ≤ 6% (measure in M1, revise with data) |

## 7. Security & threat model (summary — full: PRIVACY_SECURITY.md)

Assets: live transcript, saved evidence, guardian contact, complaint drafts.
Top threats: device theft/inspection (→ SQLCipher + Keystore + app-lock option); malicious pattern pack (→ Ed25519-signed packs, pinned key); overlay abuse/tapjacking against our bubble (→ `setFilterTouchesWhenObscured`); LLM prompt injection via scammer speech when cloud polish is on (→ transcript treated as untrusted data, never as instructions — AI_REASONING_ENGINE.md §7); shoulder-surfing by the scammer coaching the victim to disable VAARTA (→ UX never displays "disable" affordance prominently in-call; see MOBILE_UX_SPEC.md §9).

## 8. Failure & recovery matrix (top-level)

| Failure | Detection | Recovery |
|---|---|---|
| Mic busy / capture fails | AudioRecord init error / silence watchdog (10 s of zero-energy while call active) | Auto-switch bubble to Manual Mode with explanatory toast |
| ASR model missing/corrupt | checksum at load | Re-download prompt; Manual Mode meanwhile |
| Service killed by OEM battery manager | session heartbeat gap on next app open | Post-mortem notification + complaint draft from partial data; onboarding requests exemption on known-aggressive OEMs (DEBUGGING_PLAYBOOK.md §5) |
| Overlay permission revoked mid-call | WindowManager exception | Fall back to expanded notification controls |
| Crash mid-session | uncaught-handler writes encrypted session snapshot | On restart: "Recover last session?" → complaint draft |

## 9. Repository layout

```
vaarta/
  app/                     # UI, DI graph, navigation
  core/call/  core/overlay/  core/audio/  core/asr/
  core/reasoning/  core/intel/  core/alerts/
  core/complaint/  core/data/  core/cloud/  core/common/
  intel-packs/             # source-of-truth YAML pattern packs → compiled+signed at build
  docs/                    # this documentation set
  tools/                   # eval harness, corpus scripts (see TESTING_STRATEGY.md)
```
Module dependency rule: `app → core:* → core:common`; core modules never depend on each other except via interfaces in `core:common`. Enforced in CI (CLAUDE_CODE_RULES.md §4).

## 10. Future roadmap (architecture)

- M3+: "VAARTA Dialer" spike (ROLE_DIALER) to own audio routing → removes speakerphone friction (biggest UX risk, R-02).
- M3+: on-device Tier-1 LLM via AICore/Gemma when 4 GB-device benchmarks pass.
- M4: pattern-pack telemetry (privacy-preserving, aggregate, opt-in) to measure signal hit-rates in the wild.
