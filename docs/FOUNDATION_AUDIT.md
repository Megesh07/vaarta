# VAARTA — Foundation Audit (Phase 0)

**Status:** AUDIT — reflects `/docs` as of 2026-07-05
**Auditor role:** cross-functional pre-implementation design review
**Scope:** the 15 foundation docs + README. No ADRs exist yet (none found in repo).

This document does not change architecture. Where it says "MODIFY" or "ADD," those are recommendations for a future docs PR, not actions taken here.

---

# 1. Current understanding

**What VAARTA is.** An Android app that protects Indian citizens from "Digital Arrest" scams (police/CBI/ED/Customs impersonation calls that coerce victims into transferring money under threat of arrest). It intervenes *during* the call with a live risk score and verification-question suggestions, offers a one-tap family alert, and after the call auto-drafts a cybercrime complaint (PDF/DOCX/TXT/JSON) ready for 1930 / cybercrime.gov.in / police. Source: PRODUCT_PRD.md §1–3.

**Mission.** Reduce harm in the specific window where existing awareness campaigns fail: the call itself. Ten stated core principles (privacy-first, consent-first, Play-compliant, no impossible features, no hidden assumptions, low latency, Indian-language support, reduce manual work, automate complaints, simple UX) recur as design constraints across every doc.

**User journey (canonical, PRD §5).** Incoming call → persistent "Protection Available" notification → user taps Enable → mini bubble opens → live ASR + risk engine run on-device → bubble shows risk score + one suggested verification question at a time → threshold crossing offers a family/guardian alert → call ends → complaint draft auto-assembled from session events → user reviews/edits → exports to PDF/DOCX/TXT/JSON.

**Architecture.** Fully on-device, single Android app, modular (`app`, `core:call`, `core:overlay`, `core:audio`, `core:asr`, `core:reasoning`, `core:intel`, `core:alerts`, `core:complaint`, `core:data`, `core:cloud`, `core:common`), no mandatory backend in v1 (TECHNICAL_ARCHITECTURE.md §3, §9). The one binding platform constraint driving everything downstream: third-party apps cannot access the call voice stream (system-only audio sources; AccessibilityService capture is Play-banned) → the only compliant live path is **microphone + speakerphone**, with **Manual Mode** (user-tapped cues) elevated to a P0 peer feature, not a fallback (ARCH §2).

**Tech stack.** Kotlin + Jetpack Compose; `CallScreeningService` role + `TelephonyCallback` fallback for call detection; `SYSTEM_ALERT_WINDOW` overlay bubble; `AudioRecord` (not MediaRecorder) at 16 kHz mono PCM16 with Silero VAD; sherpa-onnx streaming ASR (model checkpoints deliberately left open, resolved by an M1 bake-off, not hardcoded); Room over SQLCipher with Keystore-wrapped keys; optional Claude API (`claude-haiku-4-5` class) for post-call complaint-narrative polish only, opt-in, stateless proxy.

**Privacy model.** Architectural, not policy: no PCM-to-disk code path exists in release builds; live transcripts live in RAM and are wiped 30 minutes after call end unless the user explicitly saves; five separable, revocable consents (mic processing, call detection, guardian alerts, cloud polish, pattern auto-update); zero mandatory network traffic; complete data inventory (PRIVACY_SECURITY.md §2) with explicit "never processed" list (call logs, SMS content, contacts, location, ad ID, voiceprints).

**AI model.** Two-tier by design, only one tier shipping in v1: **Tier-0** deterministic weighted-signal engine with a five-stage "script grammar" (HOOK→AUTHORITY→ISOLATION→ESCALATION→EXTRACTION) that discriminates real scam progression from a genuine authority call that stalls at stage 2; **Tier-2** cloud LLM used only post-call for narrative polish, schema-validated output, transcript quotes treated as untrusted/adversarial input. Tier-1 (on-device LLM for nuance) is an unbuilt research spike (Q2).

**Language model.** Four independent layers (UI strings, ASR, intel patterns, output generation), each with its own pass/fail gate — a language isn't "supported" until all four pass. P0 = English + Hindi + Hinglish; P1 = Tamil/Telugu/Bengali/Marathi; P2 = six more. Binding quality gate is **signal-recall**, not WER — the product needs "CBI"/"arrest"/"UPI" to survive transcription, not a clean transcript.

**Testing philosophy.** Two test universes: code correctness (unit/instrumented/E2E, per-PR) and "judgment quality" (corpus-driven eval of the ASR+engine pipeline via a two-phone rig that plays scripted calls through a real loudspeaker into a second phone's mic — because that's the actual audio path). Binding eval gates: ≥90% scam recall by minute 3, ≤2% false-HIGH on benign calls, 0% false SCAM-PATTERN.

**Deployment model.** Play Store only, phased rollout (5%→20%→100%), no backend for v1 (M2+ optionally adds a stateless LLM proxy + signed pattern-pack CDN). No iOS in v1 (platform APIs don't support the required audio/overlay access).

**Current MVP scope (PRD §6, F1–F16).** P0 = call detection, bubble, live audio capture, Manual Mode, risk scoring, verification questions, family alert, complaint draft, PDF/TXT/JSON export, Hindi+English+Hinglish. P1 = DOCX, debrief, pattern updates, cloud polish, four more languages, Elder Mode. P2 = six more languages, number-reputation lookup (explicitly gated on a lawful data source that may not exist).

**Roadmap.** M0 (skeleton, 0–2 wk) → M1 (Alpha: full loop working end-to-end, Manual Mode built *before* audio to de-risk the critical path, 2–10 wk) → M2 (Beta: eval corpora at full size, more languages, DPDPA counsel review as a hard gate, 10–20 wk) → M3 (public launch, 20–28 wk) → M4+ (telemetry, Tier-1 LLM, dialer spike).

---

# 2. Locked decisions

| # | Decision | Why | Alternatives rejected | Remain locked? |
|---|---|---|---|---|
| L1 | **Speakerphone-first live audio** (mic captures caller only via loudspeaker) | Only Play-compliant path to hear the other party; system audio sources and AccessibilityService capture are unavailable/banned | `CAPTURE_AUDIO_OUTPUT` system API, AccessibilityService recording | **YES** — this is a platform fact, not a preference; nothing short of a `ROLE_DIALER` rebuild changes it |
| L2 | **Manual Mode is P0**, not a fallback | Guarantees protection on earpiece/BT calls and when speaker can't be enabled or consent is withheld | Treating audio as mandatory and Manual as a degraded afterthought | **YES** — also functions as schedule de-risking (roadmap builds it before audio) |
| L3 | **No cloud by default / on-device-first** | Privacy default, latency (no round-trip on Tier-3 connectivity), zero per-user inference cost at launch, core trust claim | Cloud-streaming ASR (Google/Azure STT) | **YES**, with the caveat noted in §3 (C-02) that this claim needs a user-facing precision check |
| L4 | **No permanent audio storage — architectural** | No code path in release builds is the only enforcement that survives a rushed feature add later | Configurable retention setting | **YES** |
| L5 | **Tier-0 deterministic engine, not an in-call LLM** | Testable, explainable ("why HIGH RISK" must show real matched signals), offline, <100ms, zero hallucination risk during a safety-critical moment; scripted attacks suit pattern engines | On-device embedding-similarity model (deferred to Tier-1), live cloud LLM scoring | **YES** |
| L6 | **LLM (Tier-2) is post-call only, opt-in, text-only** | Keeps the safety-critical path deterministic; confines LLM risk (injection, hallucination, cost, connectivity) to a non-real-time, user-previewed step | Live LLM assistance during the call | **YES** |
| L7 | **SQLCipher + Keystore-wrapped key for all persistence** | Whole-DB encryption with queryability; standard Android-recommended pattern | Plain Room, EncryptedFile-per-record | **YES** |
| L8 | **RAM-first session model; DB write only on explicit user action** | Directly enforces P2 (temporary memory only) and P4 (user-controlled evidence) | Auto-save every session for "convenience" | **YES** |
| L9 | **CallScreeningService role + TelephonyCallback fallback**, not `ROLE_DIALER` | Avoids owning the entire in-call UI in v1; `READ_CALL_LOG` avoided (Play sensitive-permission friction) | Default dialer replacement | **YES for v1** — explicitly revisitable at M3+ if speakerphone friction proves fatal (R-02); this is a deliberately time-boxed lock, not permanent |
| L10 | **sherpa-onnx runtime**, checkpoint choice deferred to bake-off | Streaming support, Indic model lineage, proven Android ARM64 | Android `SpeechRecognizer` (OEM-variable quality), cloud ASR, Vosk | **YES** for runtime; checkpoint selection is explicitly *not* locked (correctly deferred, not an oversight) |
| L11 | **Signal-recall (not WER) as the ASR/engine quality gate** | Product only needs scam-indicative words to survive transcription, not a clean transcript | WER as primary metric | **YES** |
| L12 | **Stage-grammar scoring (HOOK→…→EXTRACTION) as the false-positive killer** | Genuine authority calls stall at AUTHORITY; only real scams progress through ISOLATION+EXTRACTION | Keyword-only scoring | **YES** |
| L13 | **No legal advice; one permitted normative fact** ("no agency arrests by phone/video") | Regulatory/liability boundary + avoids product overreach | Broader legal guidance in-app | **YES** |
| L14 | **No diarization in v1** | Unreliable on speakerphone-through-air audio; signals designed to be speaker-ambiguity-tolerant | Building diarization for attribution | **YES for v1**, reasonable given the audio path, but see §5 edge cases (EC-04) |
| L15 | **No mandatory backend in v1** | No infra cost/ops burden at launch; forces true on-device privacy default | Thin backend from day one for telemetry/updates | **YES** |
| L16 | **SEND_SMS with a pre-built share-intent fallback** | De-risks Play rejection without blocking the primary UX | SMS-only, share-only | **YES** — sensible hedge, decision point already scheduled (M2 submission) |

No locked decision in this set contradicts another. All are traceable to a stated platform, legal, or privacy constraint rather than taste.

---

# 3. Architecture consistency check

## CRITICAL

- **C-01 — SEND_SMS permission vs. "minimal permission surface" claim.** PRIVACY_SECURITY.md §7 flags SEND_SMS as "the highest-risk request" and TECHNICAL_ARCHITECTURE.md's dependency/permission philosophy (D8) emphasizes a minimal surface, yet PRD F7 ("Family Alert") is P0 and its primary path uses SEND_SMS. The fallback (share-intent) is designed but **not designated as the default** anywhere — RISK_REGISTER R-03's contingency trigger is "if rejected," meaning the decision is deferred to a Play rejection event during M2 submission, which is late in the schedule for a P0 feature's core mechanism. **Impact:** a rejection at M2 submission could force a UX change to a P0 feature days before a beta deadline. **Recommendation:** decide the default (share-intent-first, SMS as upgrade) before M1 UI implementation, not after a rejection.

- **C-02 — "Nothing leaves your phone" trust claim vs. F13/C5 pattern-pack downloads being default-ON.** PRIVACY_SECURITY.md §4 describes C5 (pattern auto-update) as default ON "since packs are downloads, no user data uploaded," and MOBILE_UX_SPEC.md's onboarding screen 2 states "Calls are processed on your phone. Nothing is saved unless YOU save it." These are compatible in substance (downloads aren't uploads) but the onboarding copy doesn't disambiguate "nothing leaves your phone" (call content) from "your phone does talk to the internet for pattern updates" (metadata: IP address, pack version requested, timing). A technically accurate claim stated in a way a user could reasonably misread as "fully airplane-mode capable" is a Play data-safety and trust risk if discovered by a security researcher or journalist. **Recommendation:** onboarding copy (frozen per PRIVACY_SECURITY.md §8) needs one clause distinguishing "your call" (never sent) from "protection updates" (small signed downloads, on by default, listed in Settings → What VAARTA Sends).

## MEDIUM

- **M-01 — AEC (Acoustic Echo Canceler) behavior is marked unverified but sits on the critical path.** AUDIO_PIPELINE.md §2 flags "NO VERIFIED EVIDENCE FOUND for uniform AEC behavior across OEMs" — yet AEC on/off directly affects whether the caller's loudspeaker-relayed voice is preserved or partially cancelled as "echo." This is arguably the single highest-uncertainty item feeding the entire ASR/engine pipeline, but it's tracked only as a doc footnote, not elevated to RISK_REGISTER.md. **Recommendation:** R-01 (ASR quality) in RISK_REGISTER.md should explicitly reference AEC-per-OEM as its leading sub-risk, or a new register row should be added — currently a reader of the risk register alone would not learn this is a top uncertainty.

- **M-02 — Ownership ambiguity between AUDIO_PIPELINE.md and AI_REASONING_ENGINE.md on ASR confidence thresholds.** AI_REASONING_ENGINE.md §8 states "confidence-weighted hits (×0.5 below 0.6 conf)" as if this is an engine-owned constant, while AUDIO_PIPELINE.md doesn't define what "confidence" means for the chosen ASR runtime (sherpa-onnx per-token vs. per-utterance confidence scoring differs by model architecture). **Impact:** low — but two docs each assume the other defines this value's semantics. **Recommendation:** AUDIO_PIPELINE.md should state what confidence score sherpa-onnx actually emits per model class once the M1 bake-off completes; until then this is technically an open dependency, not a locked constant, and AI_REASONING_ENGINE.md's `0.6` should be marked provisional (it currently reads as final).

- **M-03 — Session heartbeat mechanism appears twice with different framing.** TECHNICAL_ARCHITECTURE.md §8 ("Service Killed by OEM" row) and DEBUGGING_PLAYBOOK.md §5 both describe a persisted heartbeat used to detect OEM kills, and DATABASE_DESIGN.md doesn't have a table for it (heartbeat is described as "persisted 30s tick, minimal metadata" in DEBUGGING_PLAYBOOK.md §5 without a DATABASE_DESIGN.md schema entry). **Impact:** an implementer would need to invent a storage location for this rather than find it specified. **Recommendation:** add a `session_heartbeat` note (or reuse an existing lightweight store) to DATABASE_DESIGN.md so the mechanism has one schema owner.

- **M-04 — Complaint export "destination" field explicitly discards where evidence went, but Guardian Alert explicitly logs delivery result codes.** DATABASE_DESIGN.md §3.5 (export_record) intentionally omits the actual URI/target app "for minimization," while DEBUGGING_PLAYBOOK.md §3 ("Guardian never got the SMS") relies on `alerts.sms_result{code}` and `alerts.sub_id` being logged for debugging. These are different data classes (export destination vs. delivery diagnostics) so this isn't a contradiction, but the asymmetry (one flow is debuggable, the other is deliberately not) isn't explained anywhere as a deliberate tradeoff — a future contributor could "fix" the export_record schema to add the destination for debugging parity, unaware it was a deliberate minimization choice. **Recommendation:** a one-line rationale comment in DATABASE_DESIGN.md §3.5 would prevent this.

## LOW

- **L-01 — Bilingual terminology drift:** TECHNICAL_ARCHITECTURE.md calls the optional backend component "`core:cloud`"; PRD F14 calls the same capability "cloud LLM assistance"; PRIVACY_SECURITY.md calls it "C4 / cloud complaint polish." All refer to the same feature but a glossary/terms table doesn't exist, so cross-referencing requires inference. Low impact given the doc set is small enough to hold in one context window today; will compound as the doc set grows.
- **L-02 — RISK_REGISTER R-14 ("eval corpus overfits") references "TESTING_STRATEGY.md §10-note"** — an unusual cross-reference format (not a real numbered section) that will read as a broken link once documents are versioned/linted.
- **L-03 — MOBILE_UX_SPEC.md §3.7 onboarding step 4 (guardian setup) and PRIVACY_SECURITY.md §4 (C3 consent)** both describe guardian consent but neither specifies what happens if the *guardian* (not the user) wants to be removed/never consented in person — low priority since v1 guardian setup is in-person only, but worth flagging before Elder Mode (F15, P1) potentially changes this assumption.

Overall: **no CRITICAL item is a genuine architectural contradiction** (nothing here requires reversing a locked decision) — both CRITICAL items are under-specified defaults on features whose mechanism is already correctly designed with a fallback. That distinction matters for the freeze decision in §10.

---

# 4. Missing documents

| Document | Priority | Why |
|---|---|---|
| **ADR log / `docs/decisions/` actual entries** | P0 | Referenced by name in TECHNICAL_ARCHITECTURE.md, AUDIO_PIPELINE.md, INDIAN_LANGUAGE_SUPPORT.md, and CLAUDE_CODE_RULES.md §10 as the place where the ASR bake-off, AEC defaults, and weight-tuning results will live — but the directory and template don't exist yet. Multiple M1 exit criteria depend on artifacts this system is supposed to hold. Without it, "decision" results from the bake-off have no prescribed home and risk being lost in commit history. |
| **SECURITY.md (vulnerability disclosure policy)** | P0 | Referenced by PRIVACY_SECURITY.md §9, CONTRIBUTING.md §6, and DEBUGGING_PLAYBOOK.md as the disclosure contact/policy, but doesn't exist. A security researcher finding this repo today has no disclosure path. |
| **Postmortem template (`docs/postmortems/`)** | P1 | Referenced by PRIVACY_SECURITY.md §9, DEBUGGING_PLAYBOOK.md §8, CLAUDE_CODE_RULES.md §10 as mandatory for every S1/S2 — but no template exists, so the first incident will improvise structure under pressure. |
| **Data Safety form source (`docs/data-safety.json`)** | P1 | PRIVACY_SECURITY.md §8 and TESTING_STRATEGY.md §10 both assume a machine-readable data-safety artifact kept in sync with code via CI, but it isn't created — meant to exist "at implementation" per the doc, correctly deferred, but it's a hard M2 gate dependency and easy to forget since no doc currently owns *when* in M1/M2 it gets bootstrapped. |
| **AI safety / model-output policy for Tier-2 (LLM prompt-injection & content policy)** | P1 | AI_REASONING_ENGINE.md §6.2 describes injection defense at a mechanism level (schema validation, treat quotes as untrusted) but there's no standalone policy doc defining what the LLM is *contractually* allowed to output, red-team test cases, or a kill-switch procedure if the model is compromised/misbehaves in production — CLAUDE_CODE_RULES.md references "packs can carry a kill advisory" for this but the advisory mechanism itself isn't specified anywhere. |
| **Glossary / terms.md** | P2 | Would resolve L-01 above and give a single canonical name per concept (Tier-0/1/2, C1–C5, SC-01..10, F1–F16) as the doc set grows past what fits in one skim. |
| **Non-goals is currently embedded in PRD §7, not standalone** | P2 (already partially exists) | It exists and is reasonably complete; flagged only because the audit prompt explicitly asks to check for it — no action needed, this is a "already handled" note, not a gap. |
| **Phase-gate / go-no-go checklist as a standalone artifact** | P2 (already partially exists) | IMPLEMENTATION_ROADMAP.md has exit criteria per milestone and TESTING_STRATEGY.md §10 has a release checklist; these two together functionally cover this. A dedicated PHASE_GATES.md would only be worth adding if milestone gates start requiring cross-functional sign-off tracking (legal, security, product) beyond what a checklist in prose supports. |
| **Repository/module ownership (CODEOWNERS-equivalent)** | P2 | CLAUDE_CODE_RULES.md defines *rules* for modules but not *who* (even role-based, e.g. "audio changes need Android-lead review") approves changes to safety-critical modules (`core:audio`, `core:reasoning`, intel packs) — becomes load-bearing once more than one contributor exists. |

---

# 5. Missing edge cases

| Edge case | Handled in docs? | If NO — where it belongs |
|---|---|---|
| User has no speaker option (audio route locked, e.g. certain accessibility configurations or a car Bluetooth that can't be disabled) | Partial — MOBILE_UX_SPEC §3.4 auto-offers Manual Mode after 20s, AUDIO_PIPELINE §6 handles route-change mid-call | Sufficiently handled |
| Two SIMs, call on SIM2 while VAARTA session assumes single-telephony state | Not explicitly addressed — DATABASE_DESIGN doesn't carry a `sub_id` on `saved_session`, though DEBUGGING_PLAYBOOK §3 mentions `alerts.sub_id` for SMS only | AUDIO_PIPELINE.md / TECHNICAL_ARCHITECTURE.md call-detection section; DATABASE_DESIGN.md §3.1 schema |
| Bluetooth headset connected (no speaker, no accessible mic-of-caller path) | Partially — K-04 in DEBUGGING_PLAYBOOK acknowledges route flapping, but the *product* behavior (Manual Mode should auto-trigger, per §3.4 logic) isn't explicitly cross-referenced from the BT case | AUDIO_PIPELINE.md §4 or MOBILE_UX_SPEC §3.4 — should explicitly state BT-connected ⇒ same auto-Manual-Mode path as no-speaker |
| Romanized/code-mixed input for P2 languages that haven't passed ASR gate (e.g., user's phone locale is Kannada, scam call happens before Kannada ships) | Handled — INDIAN_LANGUAGE_SUPPORT §8 explicitly covers "language not in ASR-supported set" | Sufficiently handled |
| Low-end device (< 4 GB, e.g. Android Go) | Handled — TESTING_STRATEGY §5 explicit "unsupported-degrade" verification row; RISK_REGISTER R-09 | Sufficiently handled |
| Battery saver / Doze killing the foreground service mid-extraction-stage call (worst possible moment) | Handled generically (TECHNICAL_ARCHITECTURE §8, DEBUGGING_PLAYBOOK §5) but not specifically flagged as "worse if it happens at HIGH RISK/EXTRACTION stage" — no stage-aware recovery priority | RISK_REGISTER.md or DEBUGGING_PLAYBOOK §5 — recommend noting that heartbeat-gap recovery should treat sessions that died at HIGH+ as elevated-priority post-mortems (e.g., surfaced first in the recovery UI) |
| Family/guardian alert failure silent to the user (SMS silently fails, no delivery receipt on non-RCS) | Partially — DEBUGGING_PLAYBOOK §3 covers diagnosing it after the fact, but no doc specifies what the **user** sees in the moment if `SmsManager` reports a non-success result during the call itself | MOBILE_UX_SPEC.md §3.2 — bubble should have a defined "alert failed, retry?" state, currently undefined |
| LLM (Tier-2) unavailable mid-flow (network drop after user opts in and taps "polish") | Handled — AI_REASONING_ENGINE §6.2 "Failure: timeout 20s → template draft, silent fallback with a toast" | Sufficiently handled |
| False negative causing real harm (engine stays OBSERVING through an actual scam using entirely novel phrasing) | Product-level mitigation exists (Manual Mode, pattern updates, stage grammar) but there's no defined **user-facing signal** for "the system is uncertain / low confidence" as distinct from "OBSERVING = safe" — a frightened user seeing a calm shield during a real, novel-script scam has no way to know the engine is simply blind to it | MOBILE_UX_SPEC.md §2/§3.2 — worth considering a distinct "uncertain / limited language support / Manual Mode recommended" visual state versus a flat OBSERVING that could read as reassurance |
| False positive on a real bank/police call escalating to user distrust after repeat occurrences | Handled at the design level (stage grammar, R-04, R-13) with test gates, but no doc defines a **product response** if a specific real institution's calls repeatedly trigger CAUTION/HIGH (e.g., a bank whose legitimate fraud-verification script resembles AUTHORITY+URGENCY) | SCAM_INTELLIGENCE.md §8 (pack lifecycle) — could use an explicit "known-institution allowlist consideration" research note, marked P2/deferred rather than silently absent |
| Accessibility: TalkBack user in a live call trying to operate the bubble one-handed while also holding the phone to their ear (contradiction: TalkBack users typically don't use speakerphone-dependent flows the same way sighted users do) | Not addressed — MOBILE_UX_SPEC §7 checklist covers TalkBack labeling generically but doesn't address that a TalkBack user by definition needs audio feedback from their *own device*, which conflicts with "no sounds from VAARTA during a call" (§6) | MOBILE_UX_SPEC.md §6/§7 — flag as an unresolved tension: the anti-eavesdropping "no sound" rule and TalkBack's spoken-feedback dependency need an explicit resolution (e.g., haptic-only is already the design, but TalkBack's *own* screen-reader speech is a distinct question the doc doesn't consider — does the OS TalkBack voice leak to the scammer through speakerphone the same way alarms would?) |
| Legal: user records/exports a complaint draft containing a caller's personal claims (e.g., a real name the scammer gave) — defamation exposure if published/shared publicly by the user | Not addressed | PRIVACY_SECURITY.md or a new brief note in SCAM_INTELLIGENCE.md §9 — the export disclaimer in DATABASE_DESIGN.md §5 ("verify before filing") partially covers this but doesn't address user-initiated public sharing of the draft (e.g., posting to social media) |
| Multiple guardians disagreeing, or a guardian being the abuser (domestic-abuse scenario where "family alert" could itself be dangerous) | Not addressed anywhere | PRIVACY_SECURITY.md §4 or MOBILE_UX_SPEC — worth a deliberate, explicit note (even if the answer is "out of scope for v1, user chooses their own guardian") since digital-arrest victims skew elderly and may have complex family dynamics |

---

# 6. Privacy audit

| Area | Verdict | Explanation |
|---|---|---|
| DPDPA compliance | **WARNING** | Architecture is conservative and well-reasoned (RAM-first, consent log, minimal collection), but PRIVACY_SECURITY.md §4 and RISK_REGISTER R-06 both explicitly state formal counsel review has not happened and is a gate at M2 — meaning current compliance is an engineering judgment, not a verified legal status. Correctly flagged as such in the docs themselves (not an audit finding so much as a confirmation that the docs are honest about it). |
| Google Play compliance | **WARNING** | Policy map (PRIVACY_SECURITY.md §8) is thorough and the mic/no-recording position is well-argued, but SEND_SMS is self-identified as "the highest-risk request" with the decision deferred to a post-submission event (see C-01 above) — a live open risk, not a pass. |
| Data minimization | **PASS** | Explicit inventory (PRIVACY_SECURITY.md §2) with a stated "never processed" list; full-Aadhaar/PAN rejection enforced at the DAO/validation layer (DATABASE_DESIGN.md §3.7); export_record deliberately omits destination (§3.5). |
| Audio retention | **PASS** | No code path for persistence in release builds (P1), enforced by a described CI static scan, not just a policy statement — this is the strongest property in the whole set. |
| Transcript retention | **PASS**, with one **WARNING** sub-note | RAM-first, 30-minute discard default, opt-in full-transcript save. Sub-note: JVM String zeroing limitation is honestly disclosed as an accepted residual risk (DATABASE_DESIGN.md §2, RISK_REGISTER "Accepted risks") rather than hidden — this is good practice, but it does mean the P2 property ("temporary memory only") is best-effort, not absolute, and should be described that way to any external auditor rather than as a hard guarantee. |
| Consent flows | **PASS** | Five distinct, independently revocable consents (C1–C5) with an append-only consent log schema (DATABASE_DESIGN.md §3.9) — a genuinely well-structured design. |
| Family sharing | **WARNING** | Consent model for the *user* enabling guardian alerts is solid (C3), but as noted in §5 above, there's no treatment of the guardian-as-risk scenario or of guardian-side consent/removal — not a flaw in what exists, but a real gap in what's covered. |
| Evidence export | **PASS** | User-driven, previewed, multi-format, schema-versioned (DATABASE_DESIGN.md §5); disclaimer block included in the export schema itself. |
| Storage encryption | **PASS** | SQLCipher + Keystore-wrapped key, StrongBox where available, documented key-recovery failure mode (unrecoverable by design, explained to user) — appropriately strict. |
| Database design | **PASS** | Schema directly enforces stated privacy properties (denormalized signal data for future-proofing against pack changes, opt-in full transcript, no backup extraction rules) — design and privacy doc are consistent with each other. |

**Overall privacy verdict: WARNING** (not FAIL) — the two WARNING items (Play SMS risk, DPDPA counsel pending) are both *already tracked* with contingencies in the docs; nothing here represents undiscovered exposure, but nothing here is a verified PASS either, since both depend on external validation (Play review, legal counsel) that hasn't happened yet.

---

# 7. UX audit

| Area | Verdict | Explanation |
|---|---|---|
| Incoming call experience | **PASS** | Notification-first, non-intrusive, single primary action, auto-dismiss on call end — matches stated "calm authority" principle. |
| Floating bubble approach | **PASS**, with one **WARNING** | Collapsed/expanded states, one-action-at-a-time rule, bottom-exclusion-zone awareness are all well specified. Warning: no defined behavior for alert-send failure feedback in the bubble itself (see §5 edge case) — a real gap in an otherwise complete spec. |
| Notifications | **PASS** | Channel importance, vibrate-only-during-call reasoning, auto-dismiss — reasonable. |
| Manual mode | **PASS** | Elevated to P0, chip-based, localized, explicitly the resilience mechanism for the entire product's core constraint (§1 L2) — this is the standout strength of the whole spec. |
| Suspicious call activation | **PASS** | User always initiates ("Enable Protection"); no auto-start; explicitly a trust-preserving design choice. |
| Family alerts | **WARNING** | Neutral wording (protects a possibly-watched victim) is a genuinely sophisticated design choice; but see privacy §6 — guardian-as-risk isn't addressed, and delivery-failure UX isn't defined. |
| Complaint generation | **PASS** | Auto-filled fields marked "verify" until touched, slot-based (non-hallucinatory) generation, four export formats — solid. |
| Accessibility | **WARNING** | Checklist is genuinely rigorous (200% font, TalkBack labeling, 56dp touch targets, color-independent state) but the TalkBack-vs-no-sound tension identified in §5 is unresolved and could mean the accessibility checklist, as written, is not fully achievable without a documented exception. |
| Senior citizens | **PASS** | Elder Mode (F15) directly targets this persona (P1 in PRD personas table); XXL type, reduced options, guardian-assisted setup are appropriately scoped even though it's P1 not P0 — reasonable sequencing given P0 already targets "usable under stress." |
| Low-literacy users | **WARNING** | Design leans on icon+color+short text (good), and Manual Mode uses icon/short-label chips (good), but no doc explicitly addresses low-literacy users *reading* the complaint draft before export — a debrief/complaint screen is text-heavy by nature (MOBILE_UX_SPEC §3.5–3.6) and no read-aloud/audio-assist affordance is specified for this persona. |
| Regional language support | **PASS** | Four-layer gating, code-mixed handling, native-speaker signoff requirement, honest degrade messaging — thorough. |

**Overall UX verdict: PASS with WARNINGS** — no FAIL; the three WARNING items (alert-failure feedback, TalkBack/no-sound tension, low-literacy complaint-review support) are all specific, fixable, doc-level gaps rather than structural problems with the design philosophy.

---

# 8. Implementation readiness

**Answer: PARTIAL.**

**What's ready today:**
- The platform-constraint analysis (mic+speakerphone only) is correct, fully reasoned, and nothing in later docs contradicts it — module boundaries, dependency rules, and the M0/M1 build order in IMPLEMENTATION_ROADMAP.md can be started immediately (module skeleton, `core:common` contracts, intel-pack toolchain, CI privacy lints — none of these depend on the open items below).
- Manual Mode, the risk-engine scoring model, the complaint-draft schema, and the database schema are specified in enough detail (field-level, in DATABASE_DESIGN.md and AI_REASONING_ENGINE.md) to implement without further design discussion.
- Privacy architecture (RAM-first, SQLCipher, consent log) is implementable as specified.

**What must be locked/decided first (blocks specific M1 work, not all of M1):**
1. **SEND_SMS default posture (C-01)** — blocks `core:alerts` UI implementation; a 30-minute product decision, not a redesign.
2. **Onboarding disclosure copy precision (C-02)** — blocks final onboarding-screen copy freeze (PRIVACY_SECURITY.md §8 requires copy be frozen per release); doesn't block starting the screen's layout/logic.
3. **AEC per-OEM behavior (M-01)** — genuinely can't be resolved by decision, only by measurement; correctly scheduled as an M1 bake-off activity, not a blocker to *starting* M1, but is a blocker to *finishing* the audio pipeline module.
4. **ASR checkpoint selection** — same category as #3: correctly deferred to M1 bake-off, not a foundation gap.
5. **`docs/decisions/` and `SECURITY.md` scaffolding** — trivial to create, but should exist before the bake-off produces its first result, or that result has nowhere prescribed to live.

**What can wait (does not block M0/M1 start):**
- DOCX export, cloud LLM polish, Elder Mode, P1/P2 languages, pattern-pack remote update infra, dialer spike — all correctly sequenced later in the roadmap already.
- Glossary, CODEOWNERS-equivalent, phase-gate artifact — quality-of-life for a growing team, not launch-blocking at current team size (PRD/roadmap assume 1–3 engineers).

**Verdict detail:** a solo/small team using this doc set could begin M0 today with zero ambiguity. Full M1 completion depends on two small product decisions (items 1–2, resolvable in a working session, not a redesign) and two measurement activities that the roadmap already correctly scheduled as bake-offs rather than assumptions.

---

# 9. Final recommendations

**KEEP**
- The platform-constraint framing (speakerphone + Manual Mode as peer P0s) — this is the correct foundation and everything downstream depends on it holding.
- The Tier-0 deterministic engine + stage-grammar design — the single best risk-mitigation idea in the doc set (kills false positives structurally, not via tuning).
- RAM-first data lifecycle and the "no code path" enforcement style for privacy properties (P1–P7) — enforcement-by-absence is stronger than enforcement-by-policy and should remain the template for any future privacy property.
- Signal-recall (not WER) as the ASR quality gate — correctly optimizes for the product outcome, not a proxy metric.
- The explicit "NO VERIFIED EVIDENCE FOUND" discipline used throughout — it's what makes this audit possible to trust; keep applying it to every future doc.

**MODIFY**
- Onboarding privacy-disclosure copy (MOBILE_UX_SPEC.md §3.7 / PRIVACY_SECURITY.md §8.2) — add the one clause distinguishing call-content (never sent) from protection-updates (small signed downloads, on by default) to close C-02.
- SEND_SMS default posture — make share-intent-first the explicit v1 default in PRIVACY_SECURITY.md §8 and MOBILE_UX_SPEC.md, with SMS as an enhancement path, rather than leaving the choice to a future rejection event (closes C-01).
- RISK_REGISTER.md R-01 — fold in the AEC-per-OEM uncertainty explicitly as a named sub-risk rather than leaving it as an AUDIO_PIPELINE.md footnote (closes M-01).
- MOBILE_UX_SPEC.md bubble spec — add an explicit "alert failed / retry" state (closes the family-alert-failure UX gap in §5/§7).

**ADD**
- `docs/decisions/` directory + one ADR template file (blocks nothing today, but multiple docs already reference it as existing).
- `SECURITY.md` at repo root (referenced by three docs; currently a dead reference).
- A short explicit note (2–3 sentences, doesn't need a new file) addressing the TalkBack/no-sound tension and the guardian-as-risk scenario — both can live as new subsections in MOBILE_UX_SPEC.md and PRIVACY_SECURITY.md respectively rather than new documents.

No recommendation in this section proposes new features, new architecture, or scope expansion — all are either copy/default clarifications or scaffolding for mechanisms the docs already assume exist.

---

# 10. Architecture freeze decision

## OPTION B — Architecture is essentially complete; small, enumerated additions needed before full-speed implementation.

**Justification.** Across ten sections of audit, zero findings required reversing a locked decision (§2), and the consistency check (§3) surfaced two CRITICAL items — both of which are *underspecified defaults on already-correctly-designed fallback mechanisms* (SMS→share-intent, and a copy clarification), not missing mechanisms or contradictory designs. The MEDIUM and LOW findings are cross-referencing and schema-ownership gaps typical of a first-pass documentation set, not signs of conceptual disagreement between documents. The missing-documents list (§4) is entirely scaffolding (ADR directory, SECURITY.md, postmortem template) that other docs already assume exists — none of it requires new design thinking, only creating the files the design already points to. The missing-edge-cases list (§5) is the most substantive finding set, but every item is a **specification gap** (a state that isn't drawn yet) rather than an **architectural gap** (a mechanism that doesn't exist) — Manual Mode, the consent system, and the risk engine already have the right shape to absorb these cases once specified.

This is why the decision is B and not A: a genuine "proceed exactly as documented" verdict would require the two CRITICAL items to already have committed defaults, and they don't. It is not C: nothing found here threatens the on-device-first, speakerphone-plus-Manual-Mode, Tier-0-deterministic-engine foundation — reversing any of those would be a materially larger and unjustified undertaking than what the findings call for.

**Recommended sequence before declaring the freeze final:** resolve C-01 and C-02 (copy/default decisions, not redesigns — hours, not weeks), create the three scaffolding artifacts in §4/§9, and add the two short UX notes recommended in §9. None of this blocks starting M0 today per §8; it only needs to land before M1's UI/onboarding work reaches the screens it affects and before the audio module's AEC bake-off needs somewhere to record its result.
