# VAARTA — AI Reasoning Engine

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal AI Researcher

The engine answers three questions, continuously, on-device:
1. **How risky is this call right now?** (score 0–100 + stage)
2. **What should the user ask the caller?** (verification assistance)
3. **After the call: what happened, in complaint-ready form?**

---

## 1. Tier design

| Tier | What | Where | Status |
|---|---|---|---|
| **Tier-0** | Deterministic weighted-signal engine over transcript/manual-cue/metadata events | On-device, offline | P0 — the product |
| **Tier-1** | Small on-device LLM for nuance (sarcasm, novel phrasing) | On-device (AICore/Gemma-class) | P2 spike (PRD Q2) |
| **Tier-2** | Cloud LLM (Claude API) for complaint narrative polish + post-call analysis | Opt-in, text-only, ephemeral | P1 (F14) |

**Why Tier-0 rules and not an in-call LLM:** deterministic = testable (exact regression evals), explainable ("why HIGH RISK" shows the actual matched signals — required by UX §3.2), offline, <100 ms, zero hallucination risk during a safety-critical moment. Digital-arrest scams are heavily scripted (SCAM_INTELLIGENCE.md §4) — scripted attacks are exactly what pattern engines are good at.
**Alternatives considered:** live cloud LLM scoring — rejected (latency, privacy default, cost, connectivity); on-device embedding-similarity model — attractive, deferred to Tier-1 spike (adds recall on paraphrase at the cost of explainability and eval complexity).
**Tradeoff accepted:** Tier-0 misses truly novel scripts. Mitigation: pattern packs are updatable (F13), Manual Mode chips cover behavior-level (not phrasing-level) signals, and stage-grammar detection (§4.2) catches structure even when words are new.

## 2. Inputs (event model)

```kotlin
sealed interface RiskEvent {
  data class Transcript(text, tStart, tEnd, isFinal, langHint, confidence)
  data class ManualCue(cueId, t)                    // Manual Mode chips
  data class CallMeta(number, isContact, callType)  // at session start
  data class UserAction(action, t)                  // e.g. enabled speaker, sent alert
}
```
All processing is per-session, in RAM. Nothing here touches disk (DATABASE_DESIGN.md §2).

## 3. Signal taxonomy

Signals live in **intel packs** (`intel-packs/*.yaml`, compiled to a binary at build; schema below). Each signal:

```yaml
- id: SIG_AUTHORITY_CBI
  category: AUTHORITY_CLAIM          # see SCAM_INTELLIGENCE.md §5 for the 9 categories
  stage: HOOK                        # HOOK|AUTHORITY|ISOLATION|ESCALATION|EXTRACTION
  weight: 18
  decay_s: 600                       # contribution halves after 10 min without re-trigger
  patterns:
    hi: ["सीबीआई", "सी बी आई"]
    hi_latn: ["CBI", "C B I", "central bureau"]
    ta: ["சிபிஐ"]
    # ... per language + romanized forms
  match: fuzzy(1)                    # exact | stem | fuzzy(editDist) | regex
  explain:
    en: "Caller claims to be from CBI"
    hi: "कॉल करने वाला खुद को CBI का बता रहा है"
  manual_cue: CUE_CLAIMS_POLICE      # which Manual chip also fires this
```

Matching notes: transcripts are normalized (NFC, lowercase-latin, Indic-script-aware) before matching; fuzzy matching tolerates ASR errors ("digital arrest" ~ "digital a rest"). Numeric/UPI/amount extraction uses regex signals (`SIG_UPI_ID_SPOKEN`, `SIG_AMOUNT_DEMAND`).

## 4. Scoring

### 4.1 Base formula
```
score(t) = clamp( Σ_i  w_i · hit_i · decay(t - t_i)  +  combo_bonus(t)  −  benign_offset(t), 0, 100 )
```
- Repeated hits of the same signal count once per `refractory` window (default 120 s) to prevent one repeated word from saturating.
- `benign_offset`: small negative signals (caller is a saved contact: −15; user marks "I know this caller": −40 and stage engine off).

### 4.2 Stage grammar (`combo_bonus`)
Digital arrest follows a script: **HOOK → AUTHORITY → ISOLATION → ESCALATION → EXTRACTION** (SCAM_INTELLIGENCE.md §4). A small state machine tracks the highest stage with ≥1 signal. Bonuses reward *progression*, which single keywords can't fake:
- ≥2 consecutive stages present: +10; ≥3: +20; ISOLATION+ESCALATION together: +15 (the signature pair); any EXTRACTION signal (payment/UPI/"verification of funds"): floor score at 75.
**Why:** a bank's genuine fraud department may trigger AUTHORITY words, but never ISOLATION ("tell no one") or EXTRACTION-to-personal-UPI. Stage grammar is the false-positive killer.

### 4.3 Speaker-ambiguity rule
No diarization (AUDIO_PIPELINE.md §4). Signals are chosen to be scam-indicative regardless of who uttered them (if the *victim* says "arrest warrant?" it's because the caller introduced it). Signals that would misfire when user-spoken (e.g., "1930") are marked `user_safe: true` and excluded from scoring.

### 4.4 Thresholds & hysteresis
States per MOBILE_UX_SPEC.md §2 (25/50/75). Upward transitions immediate; downward only after 90 s below the boundary (a frightened user must not see the shield flicker green because the scammer paused). Score never decreases past the max-stage floor (once EXTRACTION seen, never below 75 for the session).

## 5. Question selector (verification assistance)

- Question bank lives in intel packs, keyed by `stage × category × language`, each with `id`, text (native + romanized variant), `goal` (what a truthful answer looks like), and `never_repeat: true` handling per session.
- Selection: highest-stage active category → pick unused question → UX shows one at a time.
- Canonical bank (EN forms; full localization in packs — see SCAM_INTELLIGENCE.md §6):
  - "Which police station are you calling from? I will call them directly."
  - "Please send the FIR copy by post or official email. I will not act before that."
  - "I will verify this with the 1930 cyber helpline and call back."
  - "What is your official government email ID? I will write to you."
  - "Give me your name, designation and badge number."
  - "I am going to include my family member on this call." (anti-isolation)
- **Hard rule — no legal advice:** questions and risk lines state facts and verification steps only. Banned phrasings are lint-checked in the pack build: anything matching "you should (not)? pay/refuse/sue", "this is (il)?legal", "you have the right to…" fails CI. The one permitted normative line is the government-sourced fact: "No agency arrests anyone over a phone or video call."

## 6. Complaint draft generation (post-call)

### 6.1 On-device template engine (P0)
Input: session events (signals + timestamps + quotes), call metadata, user-entered fields (amount lost, transaction refs).
Output: structured `ComplaintDraft` (DATABASE_DESIGN.md §3.4) rendered into the NCRP-aligned sections: complainant details (from settings, optional) · incident date/time/duration · suspect number(s) & platform(s) · category (mapped from top scam pattern) · chronological narrative (generated from the signal timeline in the user's output language) · loss details · evidence list.
Narrative generation is **slot-based template text**, not free generation — deterministic, testable, no hallucination. Every auto value carries `source: DETECTED|USER|DEFAULT` for the "verify" markers in UX §3.6.

### 6.2 Cloud polish (Tier-2, opt-in, P1)
- Model: `claude-haiku-4-5` class for cost/latency; single request per draft.
- Sent: transcript-derived signal quotes + slot values. **Never sent:** raw audio (doesn't exist), contact book, anything not shown to the user in a "you are about to send this" preview.
- **Prompt-injection defense:** transcript quotes are untrusted data. The system prompt instructs the model to treat quoted material strictly as evidence text; output is schema-validated JSON (slots only, no free instructions executed); any output failing schema → fall back to template draft. The scammer talking to our LLM through the victim's phone is a real attack path — treat every quote as adversarial.
- Failure: timeout 20 s → template draft, silent fallback with a toast.

## 7. Evaluation (binding quality gates — details TESTING_STRATEGY.md §6)

- **Scripted-scam corpus:** ≥ 40 scripted dialogues across scam types × languages (built in M1 from public reporting + red-team writing), rendered via the two-phone rig.
- **Benign corpus:** ≥ 60 dialogues: real bank fraud-desk calls, courier delivery, KYC reminders, family calls, telemarketing.
- Gates: recall ≥ 90% (HIGH+ by minute 3 on scam corpus), false-HIGH ≤ 2% on benign, explanation correctness (top signal shown matches ground truth) ≥ 95%.
- Every intel-pack change runs the full eval in CI; score deltas are printed in the PR.

## 8. Failure cases & recovery

| Failure | Behavior |
|---|---|
| ASR garbage (low confidence) | confidence-weighted hits (×0.5 below 0.6 conf); Manual Mode always available |
| No signals but user is scared | Manual chips + "None of these? Describe after call" — debrief still produced |
| Pack corrupt / missing language | engine falls back to `en + hi_latn` universal pack (bundled, never removable) |
| Score oscillation | hysteresis §4.4; monotone stage floors |
| Cloud polish returns junk | schema validation → template fallback |

## 9. Debugging
Debug builds: session inspector screen — live event stream, per-signal contributions, stage machine state, score trace graph; export session trace as JSON (redacted mode strips quote text). Log tag `RiskEngine`, PII-free in release.

## 10. Roadmap
- M2: weight learning — fit `w_i` on the eval corpora via logistic regression (weights stay inspectable; no black box).
- M2: Tier-2 post-call "what happened" summary in user's language.
- M3: Tier-1 on-device LLM spike (Q2); embedding-based paraphrase recall.
- M4: federated-style aggregate signal stats (opt-in) to tune packs (privacy design first — PRIVACY_SECURITY.md §10).
