# VAARTA — Implementation Roadmap

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** CTO / PM

Team assumption: 1–3 engineers working with AI coding agents (Claude Code), part-time researcher/native-speaker reviewers. Estimates are calendar weeks at that staffing; adjust proportionally. **Rule: no milestone is "done" until its exit criteria are demonstrated, not claimed.**

---

## M0 — Foundation freeze & skeleton (Week 0–2)

Build nothing user-visible; make everything after this boring.
- Repo bootstrap: module skeleton per TECHNICAL_ARCHITECTURE.md §9, CI (build, unit tests, lint incl. custom privacy lints), version catalog, debug/release variants (`.debug` suffix).
- `core:common` contracts: `RiskEvent`, `SessionCoordinator` interfaces, normalization utilities (+ tests).
- Intel-pack toolchain: YAML schema, validator, compiler, Ed25519 signing, banned-phrase lint (AI_REASONING_ENGINE.md §5).
- Two-phone corpus rig scripted (tools/), first 5 Hindi/Hinglish scam scripts written.
- Figma per MOBILE_UX_SPEC.md.
**Exit:** CI green on skeleton; pack compiler round-trips a sample pack; rig produces a WAV + reference transcript.

## M1 — Alpha: the core loop works end-to-end (Week 2–10)

Order of build (dependency-driven):
1. **Call detection + notification** (`core:call`) — screening role + fallback. *(wk 2–3)*
2. **Session coordinator + Manual Mode + bubble** (`core:overlay`, `core:reasoning` Tier-0 with starter pack) — **the product works with zero audio at this point**; ship order deliberately puts Manual Mode before audio so risk logic is exercised early. *(wk 3–5)*
3. **Audio pipeline** (`core:audio`): capture, VAD, ring buffer, watchdogs. *(wk 4–6)*
4. **ASR integration** (`core:asr`): sherpa-onnx JNI, Hindi+English bake-off (INDIAN_LANGUAGE_SUPPORT.md §4), streaming events into engine. *(wk 5–8)*
5. **Guardian alert** (`core:alerts`): SMS + share paths, consent flow. *(wk 6–7)*
6. **Debrief + complaint template engine + PDF/TXT/JSON export** (`core:complaint`). *(wk 7–9)*
7. **Persistence** (`core:data`): SQLCipher, save/discard lifecycle, 30-min wipe. *(wk 8–9)*
8. Hardening pass: failure matrix drills (TECHNICAL_ARCHITECTURE.md §8), OEM battery testing on ≥ 4 devices. *(wk 9–10)*

**Exit criteria (demonstrated on a 4 GB device):** scripted digital-arrest rig call → notification ≤ 1.5 s → bubble → score reaches SCAM PATTERN by min 3 → alert SMS sent → call end → complaint PDF exported, all offline, all budgets in TECHNICAL_ARCHITECTURE.md §6 met; benign-corpus false-HIGH ≤ 2%; full-session RAM peak logged < 1 GB.

## M2 — Beta: quality, languages, compliance (Week 10–20)

- Eval corpora to full size (40 scam / 60 benign — TESTING_STRATEGY.md §6); weight tuning (logistic fit).
- Languages: Tamil, Telugu, Bengali, Marathi through the 4-layer gate; code-mixed corpora mandatory.
- DOCX export (F10); post-call debrief education cards (F12); pattern-pack remote update (F13) with signing infra.
- Optional cloud polish (F14): stateless proxy service (FastAPI, deployed minimal), C4 consent flow, injection-hardened prompting + schema validation.
- **Compliance gate:** DPDPA counsel review (R-06); Play data-safety form dry run; prominent-disclosure copy freeze; SEND_SMS contingency decision (PRIVACY_SECURITY.md §8).
- Play internal testing track → closed beta (target 200 testers incl. elderly users via NGO/RWA partnerships).
**Exit:** eval gates green in CI; Play pre-launch report clean; beta NPS/task-completion study on P1 persona (≥ 80% can enable protection and read risk state unaided); zero S1/S2 incidents open.

## M3 — Public launch (Week 20–28)

- Elder Mode (F15); remaining P2 language UI (Manual-Mode-only where ASR not gated); accessibility audit (external if budget allows).
- Store listing (localized), phased rollout 5% → 20% → 100% with crash/ANR gates at each step.
- Incident-response drill (S1 tabletop, PRIVACY_SECURITY.md §9); SECURITY.md live; support channel.
- Launch comms: partnership conversations (Q1/Q3 from PRD §11).
**Exit:** 100% rollout, crash-free ≥ 99.5%, policy strikes 0, complaint-export success rate ≥ 99% (local funnel metric, on-device count only).

## M4+ — Post-launch (directional)

Opt-in aggregate signal telemetry (privacy design first) → pack tuning loop; P2 language ASR; Tier-1 on-device LLM spike (Q2); "VAARTA Dialer" spike (R-02 mitigation); SC-06..SC-10 packs; NCRP schema v2 alignment; multi-profile/family dashboard exploration (consent-heavy — design doc required first).

## Dependency graph (critical path)

```
M0 contracts ─→ Manual Mode+bubble ─→ Audio ─→ ASR ─→ eval gates ─→ M2 languages ─→ launch
        └────→ pack toolchain ──────→ Tier-0 engine ─┘
Complaint engine ← session model (parallel track from wk 5)
Compliance track (parallel from M0; hard gate before M3)
```
The critical path is **ASR-on-real-devices**; everything UI/complaint can proceed in parallel. If ASR slips, Manual Mode lets alpha proceed — this is deliberate de-risking.

## Standing rules

1. Every feature lands with: tests per TESTING_STRATEGY.md, failure-mode handling from its spec doc, and a docs delta if it deviates from foundation docs (CONTRIBUTING.md process).
2. Weekly rig run (full eval) even when "nothing changed" — device/OS drift is real.
3. Any Play-policy-relevant change (permissions, disclosure copy, data flows) requires PRIVACY_SECURITY.md sign-off in the PR.
4. Budgets (TECHNICAL_ARCHITECTURE.md §6) are CI-enforced from M1 wk 6 onward; a red budget blocks merge like a failing test.

## Top schedule risks (details RISK_REGISTER.md)
R-01 ASR quality on far-end speakerphone audio (mitigation: signal-recall gate not WER; Manual Mode) · R-02 speakerphone UX friction (measure activation funnel in beta; dialer spike if < 15%) · R-03 Play review friction on mic/SMS (contingencies pre-built) · R-05 OEM service kills (device matrix early, in M1 not M3).
