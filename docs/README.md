# VAARTA — Documentation Index

**VAARTA** protects Indian citizens from Digital Arrest scams: real-time, on-device call protection + automated cybercrime complaint generation.

This `/docs` set is the **single source of truth**. Foundation phase is complete; everything from here is implementation. When code and docs conflict, docs win; when a doc is wrong, amend it first (process in [CLAUDE_CODE_RULES.md](CLAUDE_CODE_RULES.md) §1).

## Reading order (new engineer onboarding)

1. [PRODUCT_PRD.md](PRODUCT_PRD.md) — mission, personas, features, non-goals, metrics
2. [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) — system design + **the binding platform constraint (§2 — read first)**
3. [PRIVACY_SECURITY.md](PRIVACY_SECURITY.md) — non-negotiable privacy properties, DPDPA, Play compliance
4. [SCAM_INTELLIGENCE.md](SCAM_INTELLIGENCE.md) — the domain: scam taxonomy, five-stage script, psychology
5. [AI_REASONING_ENGINE.md](AI_REASONING_ENGINE.md) — risk scoring, question selection, complaint generation
6. [AUDIO_PIPELINE.md](AUDIO_PIPELINE.md) — capture, VAD, ASR, latency budgets
7. [INDIAN_LANGUAGE_SUPPORT.md](INDIAN_LANGUAGE_SUPPORT.md) — 4-layer language model, code-mixed handling, bake-off protocol
8. [MOBILE_UX_SPEC.md](MOBILE_UX_SPEC.md) — surfaces, risk states, adversarial UX
9. [DATABASE_DESIGN.md](DATABASE_DESIGN.md) — RAM-first session model, encrypted persistence, export schema
10. [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) — M0→M4, exit criteria, critical path
11. [TESTING_STRATEGY.md](TESTING_STRATEGY.md) — code tests + judgment eval gates + the two-phone rig
12. [DEBUGGING_PLAYBOOK.md](DEBUGGING_PLAYBOOK.md) — observability, symptom trees, OEM survival
13. [RISK_REGISTER.md](RISK_REGISTER.md) — ranked risks with pre-agreed triggers
14. [CLAUDE_CODE_RULES.md](CLAUDE_CODE_RULES.md) — binding engineering rules (AI and human)
15. [CONTRIBUTING.md](CONTRIBUTING.md) — setup, workflow, hard vetoes

## Five facts everyone must internalize

1. **We cannot access call audio directly.** Mic + speakerphone is the only compliant live path; Manual Mode is a P0 peer feature, not a fallback. (ARCH §2)
2. **No permanent audio storage — architecturally.** Release builds have no PCM→disk code path. (PRIVACY P1)
3. **On-device by default.** Zero mandatory network traffic; cloud features are opt-in and text-only. (ARCH §1)
4. **The engine is deterministic and explainable** (Tier-0 signals + stage grammar); LLMs only post-call, opt-in. (AI §1)
5. **No legal advice, no accusations** — verification assistance and the one government-sourced fact: *no agency arrests anyone over a phone or video call.* (AI §5, SCAM §9)

Directories created during implementation: `docs/decisions/` (ADRs — bake-offs, tuning), `docs/postmortems/`.
