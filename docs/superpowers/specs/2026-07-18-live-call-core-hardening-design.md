# Live-Call Core Hardening — Design Spec

**Date:** 2026-07-18
**Status:** Approved direction (this doc is the written spec for user review)
**Owner branch:** `vaarta-v2-ux`

## 1. Goal

VAARTA's heart is the live-call coach. Today it is architecturally sound but narrow: the
deterministic pack and the coaching prompts only know the **digital-arrest** script, the grounded
classifier's findings never reach the coach, and every word picked up by the single speakerphone
mic is attributed to the caller. This spec hardens the core in four parts:

- **A. Detection breadth** — extend the deterministic signal pack to the other major Indian phone-scam
  families (purely additive; `RiskEngine` unchanged).
- **B. Intelligence wiring** — feed the grounded classifier's scam-type finding into the coach call so
  coaching matches the scam actually in progress.
- **C. Coaching generalization** — rewrite the coach's domain knowledge from one fixed script to
  general manipulation-pattern reasoning, plus live generation of adversarial probing questions.
- **D. Speaker attribution** — implicit, zero-enrollment on-device voice learning so the app can tell
  the user's speech from the caller's on one shared microphone.

## 2. Safety invariants (unchanged, verbatim requirements)

These hold before and after this work. Any task that would violate one is out of spec.

1. `RiskEngine` remains the sole owner of the risk score. No LLM output ever writes to the score.
2. `HybridAlert` ratchet: AI concern can only **raise** the displayed level, never lower it.
3. Reassurance and scam-type display require cited sources (`HybridAlert.mayReassure` /
   `mayShowScamType` semantics unchanged).
4. All LLM calls fail closed: network/parse failure → null → deterministic-only behavior.
5. `SuggestionSafetyFilter` remains the runtime enforcement of the HARD RULES deny-list.
6. The HARD RULES block in `CoachPrompt` survives Part C **byte-for-byte** (regression-tested).
7. New speaker-attribution logic may only **exclude** speech from scoring (high-confidence trusted
   voice); it may never suppress or down-weight unverified speech. Baseline behavior for
   unverified speech is exactly today's behavior.

## 3. Part A — Detection breadth (pack v2)

**File:** `core/reasoning/src/main/resources/packs/core-scam-v1.json` → bump `packId` to
`core-scam@2026.07.2` (same schema, same loader, `RiskEngine.kt` untouched).

Add HOOK-stage signals for the seven scam families already validated in the awareness feed, each
with `en` / `hi_latn` / `hi` patterns, following the existing signal schema:

| New signal id | Family | Stage | Weight (initial) |
|---|---|---|---|
| `SIG_HOOK_INVESTMENT` | Investment/trading app returns | HOOK | 12 |
| `SIG_HOOK_JOB_TASK` | Job/task "earn per task" | HOOK | 12 |
| `SIG_HOOK_LOAN_APP` | Instant loan app harassment | HOOK | 12 |
| `SIG_HOOK_LOTTERY` | Lottery/KBC/prize | HOOK | 12 |
| `SIG_HOOK_ELECTRICITY` | Electricity bill disconnection | HOOK | 12 |
| `SIG_HOOK_UPI_REFUND` | UPI "wrong payment" refund | HOOK | 12 |
| `SIG_HOOK_COURIER_COD` | Courier COD/OTP confirmation | HOOK | 10 |

The existing AUTHORITY / ISOLATION / ESCALATION / EXTRACTION signals are already family-generic
(urgency, secrecy, money transfer, UPI/QR) — they stay shared. Only the hooks are family-specific.
Pattern lists are drafted at implementation time from the awareness-feed card copy and public
cybercrime advisories; each signal gets the same transcript-match unit-test treatment as the
existing digital-arrest signals.

**Non-goal:** no new stages, no schema change, no weight retuning of existing signals.

## 4. Part B — classify → coach wiring

Today `GeminiClient.classify()` and `GeminiClient.coach()` run in parallel and never talk
(`CopilotSession.requestIntelligence`, app/src/main/java/ai/vaarta/CopilotSession.kt:309-360).

**Change:** `CopilotSession` keeps the latest source-backed `GroundedAssessment.scamType` (the same
value already gated by `HybridAlert.mayShowScamType`) in session state. Each `coach()` call passes
it as an extra context line appended to the conversation history block:

```
[CONTEXT] Grounded classification so far: "<scamType>" (source-backed). Empty if none yet.
```

Rules:
- Only a **source-backed** scamType is forwarded (same gate as the UI banner — never forward an
  uncited claim into the coach).
- The coach prompt (Part C) instructs the model to treat this line as advisory context, not ground
  truth, and to keep reasoning from the transcript itself.
- No new API call; no timing change; `coach()` and `classify()` still run concurrently — the coach
  simply sees the *previous* turn's classification, which is acceptable (one-turn lag).

## 5. Part C — Coaching generalization + adversarial probes

**Files:** `app/src/main/java/ai/vaarta/ai/CoachPrompt.kt`,
`app/src/main/java/ai/vaarta/ai/SharedScamPrompt.kt`.

Rewrite the DOMAIN KNOWLEDGE section of both prompts from "digital-arrest 5-stage script" to
**manipulation-pattern reasoning**:

- The model is told to recognize four universal manipulation moves in the live transcript, whatever
  the surface story: **authority-impersonation** (police/bank/telecom/company), **urgency-manufacturing**
  (deadlines, threats, "right now"), **isolation-demanding** (secrecy, stay on line, move channels),
  and **financial-extraction** (transfer, OTP, deposit, "verification fee").
- Known families (digital arrest, investment, job-task, loan app, lottery, electricity, UPI refund,
  courier) are listed as **illustrative examples only** — explicitly "not an exhaustive list; new
  variants appear weekly; reason from the moves, not the story."
- Explicit fallback clause: if the call matches no known family, the same HARD RULES and the same
  verify/refuse/exit reply structure still apply — never silence, never a refusal.

**Adversarial probing questions:** the coach's "verify" reply kind is upgraded with an explicit
instruction: generate a question **calibrated to the caller's specific claim** that a legitimate
party could answer trivially but a scripted or AI-voice operator cannot — e.g. verifiable
callback details, specifics only the real institution would know, or a request that breaks the
script (adding a family member, calling back on an official number). The existing
"Which police station are you calling from?" becomes one worked example of the pattern, not the
pattern itself.

**Unchanged:** HARD RULES block byte-for-byte (regression test asserts the exact substring);
language-mirroring contract; JSON output contract; `IndiaContext.BLOCK` suffix;
`SuggestionSafetyFilter` still sanitizes every reply.

## 6. Part D — Speaker attribution (implicit voice learning)

### 6.1 Problem and constraint

One speakerphone mic carries both voices; Android permanently blocks third-party access to the
downlink call audio (accessibility loophole closed 2022; both-side capture requires root/system
dialer). Speakerphone is therefore the only way VAARTA can hear the caller — the fix must work on
the shared stream. Today everything is attributed to CALLER except near-verbatim read-aloud replies
caught by `OwnWordsGate` (text similarity).

### 6.2 Design: zero-enrollment voice verification

No setup step. The app builds the user's voiceprint silently from moments it is already certain
the audio is the user:

1. **Chat voice input** (`ConversationScreen` mic button) — sole speaker is the user by definition.
2. **Read-aloud replies in live calls** — segments `OwnWordsGate` confirms as near-verbatim echoes
   of displayed replies (text-confirmed user speech).

Each harvested sample → a speaker embedding computed **on-device** via sherpa-onnx (compact
speaker-recognition ONNX model from the sherpa-onnx speaker-recognition-models collection, e.g.
3D-Speaker CAM++; size budget ≤ 30 MB, exact model chosen at implementation with a fixture-audio
bake-off). Embeddings aggregate into a rolling centroid ("voiceprint").

**Activation gate:** verification stays OFF until the voiceprint has ≥ 3 samples totaling ≥ 20 s of
speech. Before that, behavior is exactly today's.

### 6.3 Decision rule (fail-safe, one rule)

For each transcribed segment in a live call:

- Segment < 1.5 s → **unverified** (embeddings unreliable when short) → treated as today.
- Cosine similarity to voiceprint ≥ **T_user** (initial 0.75, tuned on fixtures) → labeled **USER**:
  excluded from `RiskEngine` scoring, appended to coach history as `USER:`.
- Otherwise → **unverified** → treated exactly as today (scored, appended as caller).

A USER label can never lower an already-raised risk level (ratchet, invariant 2/7). `OwnWordsGate`
remains active as an independent second layer (its text match also feeds harvesting).

### 6.4 Edge cases (resolved by the one rule)

| Case | Outcome |
|---|---|
| User hands phone to friend/family | Unverified → scored as today (no regression). No multi-voice enrollment UI (YAGNI). |
| No voiceprint yet (new install) | Verification off; today's behavior. |
| User's voice changed (cold) | Similarity drops → unverified → safe. Centroid keeps adapting from new confirmed samples. |
| Scammer voice similar / cloned | High threshold + OwnWordsGate second factor + ratchet: a false USER label cannot lower a raised level. |
| Overlapping speech | Mixed embedding → low similarity → unverified → scored. |
| Call-center handoffs (multiple far-end voices) | All unverified → all scored. |
| Not on speaker / Bluetooth route | If ≥ 95% of speech in the first 60 s matches the voiceprint, show nudge: "Put the call on speaker so VAARTA can hear the caller." |
| Short utterances ("haan", "ok") | Below 1.5 s → unverified → scored. |

### 6.5 Privacy (hard rules)

- The voiceprint (embedding centroid + sample metadata) is stored **on-device only**, inside the
  existing SQLCipher-encrypted database. It never leaves the device; raw harvested audio is
  discarded after embedding.
- One settings row: **"Clear voice data"** — deletes the voiceprint immediately. No other UI.

### 6.6 New components

| Component | Responsibility |
|---|---|
| `core/voice` module (new) | sherpa-onnx wrapper: `SpeakerEmbedder.embed(pcm): FloatArray`; pure, testable. |
| `VoiceprintStore` | Encrypted persistence of centroid + sample count/duration; `add(sample)`, `similarity(embedding)`, `clear()`. |
| `SpeakerAttributor` | The decision rule of §6.3; consumed by `CopilotSession` where transcript segments are processed today. |
| Harvest hooks | Chat voice input path + `OwnWordsGate` confirmation path call `VoiceprintStore.add`. |

`CopilotSession` change is minimal: the segment-processing path asks `SpeakerAttributor` for a
label before scoring, and the coach-history writer uses the label.

## 7. Error handling

- sherpa-onnx init failure / model missing → attribution permanently unverified for the session →
  today's behavior (fail-open to baseline, fail-safe for scoring).
- Embedding computation over budget (> 200 ms per segment on a mid-range device) → skip
  verification for that segment (unverified) rather than delay coaching.
- Voiceprint DB corruption → `clear()` and restart harvesting; never crash the call screen.

## 8. Testing

- **Pack v2:** transcript-match unit tests per new signal (same style as existing digital-arrest
  signal tests); regression: existing digital-arrest scoring outputs unchanged for existing fixtures.
- **Prompts:** regression test asserts HARD RULES exact substring in `CoachPrompt.INSTRUCTION` and
  `SharedScamPrompt.INSTRUCTION`; test asserts the `[CONTEXT]` line is present when a source-backed
  scamType exists and absent otherwise.
- **SpeakerAttributor:** pure-logic unit tests (given embeddings/similarities/durations → labels);
  fixture-audio integration test for `SpeakerEmbedder` (same-speaker pair similarity > cross-speaker
  pair) run locally, not in CI if model download is needed.
- **Fail-safe property test:** for any attribution output, the set of segments reaching
  `RiskEngine` is a subset of today's — never a superset modification of caller signal.
- Emulator verification: demo-call flow with attribution off (no voiceprint) is byte-identical to
  today; chat voice input harvests samples; "Clear voice data" empties the store.

## 9. Non-goals

- No real diarization of unknown third parties; no multi-voice enrollment UI.
- No change to `RiskLevel`, `HybridAlert`, `SuggestionSafetyFilter`, or the Live API pipeline.
- No cloud diarization (the earlier Gemini-batch idea is dropped: unstable labels, quota cost, lag).
- Personalized feed is a separate spec (2026-07-18-personalized-awareness-feed-design.md).
