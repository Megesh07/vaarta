# VAARTA — Implementation Guardrails

**Status:** FROZEN — permanent repository rules, effective on ARCHITECTURE_FREEZE_REVIEW.md Option A (2026-07-05)
**Applies to:** every contributor, human or AI agent, for every PR from M0 onward.

These are not suggestions. They are the operational meaning of "architecture frozen." A PR that violates a NEVER rule is reverted regardless of how good the surrounding change is. A PR that skips an ALWAYS rule is incomplete, not merge-ready.

---

## NEVER

1. **Never store raw audio.** No code path in `core:audio` (or anywhere) writes PCM/audio to persistent storage in a release build. This is enforced by CI static scan (CLAUDE_CODE_RULES.md §6) — do not add a "temporary" debug write path without the compile-time flag that strips it from release.
2. **Never add cloud dependencies that are on by default.** `core:cloud` and any network call beyond the two first-party endpoints in the network security config must be feature-flagged off until the user explicitly opts in (PRIVACY_SECURITY.md §4). "It's more convenient this way" is not sufficient justification to change a default.
3. **Never put an LLM in the live/in-call scoring path.** Tier-0 (deterministic signal engine) is the only engine allowed to set the risk score during an active call. LLM involvement (Tier-2) is post-call, opt-in, and produces a schema-validated draft — never a live score, never a live suggested question (AI_REASONING_ENGINE.md §1, §6.2).
4. **Never add WhatsApp/Skype/any third-party call or message *interception*.** VAARTA detects that a scammer is *asking* to switch channels (a `CHANNEL_SWITCH` signal — SCAM_INTELLIGENCE.md §5) — it never joins, records, or intercepts the destination channel. There is no scenario in this product where reading another app's call/message content is in scope.
5. **Never remove or demote Manual Mode.** It is a P0 peer of audio protection, not a fallback UI state to be simplified away (TECHNICAL_ARCHITECTURE.md §2, PRODUCT_PRD.md F4). Any PR that makes Manual Mode harder to reach, slower to activate, or conditional on audio-path failure alone is a regression.
6. **Never change privacy assumptions (P1–P7 in PRIVACY_SECURITY.md) without a docs PR first.** Code changes that touch retention windows, what's persisted, what's logged, or what's sent over the network require the doc to be amended in the same PR — not after, not "we'll document it later."
7. **Never require `SEND_SMS` without a fresh, evidence-based decision.** V1 ships share-intent-only for guardian alerts (ARCHITECTURE_FREEZE_REVIEW.md Task 1, PRIVACY_SECURITY.md §7). Reintroducing `SEND_SMS` as a default requires beta drop-off evidence and a prepared Play core-functionality justification — both, not either.
8. **Never reopen a frozen/locked decision without an ADR and evidence.** Every "LOCKED" item in FOUNDATION_AUDIT.md §2 and every decision in ARCHITECTURE_FREEZE_REVIEW.md stands until an ADR in `docs/decisions/` documents what changed, why, and what evidence justified it (CLAUDE_CODE_RULES.md §1, §10). A code comment or PR description is not an ADR.
9. **Never ship legal-advice phrasing.** Verification questions and risk copy state facts and procedures only; the banned-phrase lint (AI_REASONING_ENGINE.md §5) is a hard gate, not a style suggestion — do not weaken it to land a PR.
10. **Never weaken an eval gate to merge.** The judgment-quality gates in TESTING_STRATEGY.md §6 (recall ≥90%, false-HIGH ≤2%, false-SCAM-PATTERN = 0) are binding on every pack/model/weight/audio change. A failing gate means the change isn't ready — it does not mean the gate is wrong. Gate changes are their own docs PR with CTO signoff.
11. **Never add analytics, ads, or third-party crash-reporting SDKs.** Pre-vetoed in CLAUDE_CODE_RULES.md §6. Diagnostics are content-free and user-initiated only (DEBUGGING_PLAYBOOK.md §1).
12. **Never let a new signal/pack ship without the no-how-to check.** Intel content describes detection, not scam operation (SCAM_INTELLIGENCE.md §9) — pack PRs that read as a "how a scam works" tutorial for a reader, rather than "what to detect," are rejected regardless of detection value.

## ALWAYS

1. **Always update the relevant ADR when a measured decision lands** (ASR checkpoint per language, AEC default per OEM class, engine weight tuning). `docs/decisions/` is the prescribed home (created per ARCHITECTURE_FREEZE_REVIEW.md Task 3) — a bake-off result that only lives in a Slack thread or PR description didn't happen as far as the next contributor is concerned.
2. **Always add tests with the change**, and for bug fixes, always add the regression test that would have caught it (CLAUDE_CODE_RULES.md §7). "CI will catch it" is not a plan.
3. **Always measure latency against the published budgets** (AUDIO_PIPELINE.md §5, TECHNICAL_ARCHITECTURE.md §6) for anything touching the audio→ASR→engine→bubble path. A budget regression blocks merge exactly like a failing test, from M1 wk 6 onward.
4. **Always respect the privacy data inventory** (PRIVACY_SECURITY.md §2) — if a change introduces a new piece of data the app touches, it goes in that table, with retention and location specified, in the same PR.
5. **Always keep the Tier-0 engine deterministic and inspectable.** Any new signal or scoring adjustment must be traceable to a specific weight/rule a developer can point to — no black-box scoring components in the live path (AI_REASONING_ENGINE.md §1).
6. **Always run the text-mode eval and paste the score table into the PR** for any pack, weight, model, or audio-pipeline change (CLAUDE_CODE_RULES.md §7, TESTING_STRATEGY.md §6).
7. **Always mark unverified device/platform behavior explicitly** with `// UNVERIFIED-ON-DEVICE:` and get it onto the device-test checklist rather than assuming it (CLAUDE_CODE_RULES.md §3) — this is how the doc set's "NO VERIFIED EVIDENCE FOUND" discipline carries into code.
8. **Always update the permission/data-flow tables in the same PR as any new permission or network endpoint** (CLAUDE_CODE_RULES.md §6) — never as a follow-up.
9. **Always treat transcript-derived text as untrusted input at any LLM boundary** (AI_REASONING_ENGINE.md §6.2) — a scammer's speech reaching a cloud model through the victim's phone is a real attack path, not a theoretical one.
10. **Always give Manual Mode parity** when adding a new audio-derived signal — if the engine can learn something from a transcript, an equivalent Manual Mode chip or cue should exist so the signal isn't audio-exclusive (SCAM_INTELLIGENCE.md §5, AI_REASONING_ENGINE.md §5).

## How this file is used

- Referenced from the repo-root `CLAUDE.md` (created at M0 bootstrap) so it loads automatically for AI agents working in this repository.
- A PR that violates a NEVER item is grounds for immediate revert, independent of the rest of the PR's quality.
- A PR that skips an ALWAYS item is not "done" — request changes, don't merge-then-fix.
- This file itself is amendable only through the same ADR process it enforces on everything else (see NEVER #8) — guardrails don't get to exempt themselves.
