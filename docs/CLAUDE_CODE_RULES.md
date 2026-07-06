# VAARTA — Claude Code / AI-Assisted Development Rules

**Status:** FOUNDATION · v1.0 · 2026-07-05 · **Owner:** CTO

These rules govern all AI-assisted implementation work in this repo. They exist because AI agents are fast and confident — and this product's failure modes are privacy violations and missed scams. **When code and these docs conflict, docs win; when docs are wrong, amend the doc first, then the code.**

*(At implementation, mirror the operational subset of this file into the repo-root `CLAUDE.md` so agents load it automatically.)*

---

## 1. Source-of-truth discipline
1. The `/docs` set is the single source of truth. Do not re-decide settled decisions (marked "Status: FOUNDATION"). If a decision proves wrong, propose a docs PR with: what broke, evidence, new decision, migration cost.
2. Never implement a feature that lacks a home in PRODUCT_PRD.md's feature table. New feature idea → PRD amendment first.
3. Every module's behavior contract lives in its spec doc (e.g., `core:audio` → AUDIO_PIPELINE.md). Read the spec **before** touching the module.

## 2. No-placeholder rule
- No `TODO: implement later` on any code path reachable in release builds.
- No stub returns that fake success (`return true // for now`). If a dependency isn't ready, code against the `core:common` interface and wire a **loud** fake (logs `FAKE_IMPL` at startup, debug builds only, CI fails if a fake binds in release).
- No invented constants: latency thresholds, weights, model names come from the docs or from a measured decision record (`docs/decisions/`). Model names especially: D4/INDIAN_LANGUAGE_SUPPORT §2 forbid hardcoding un-benchmarked checkpoints.

## 3. Honesty about uncertainty
- If a platform behavior can't be verified in docs/emulator, write `// UNVERIFIED-ON-DEVICE:` with what must be checked and add it to the device-test checklist. The string is CI-scanned into a report; releases with open UNVERIFIED items in `core:audio`/`core:call` require explicit signoff.
- Claims in PR descriptions must match test evidence ("works on MIUI" requires the device-farm run link).

## 4. Architecture enforcement (CI-backed)
- Module dependency rule (TECHNICAL_ARCHITECTURE.md §9): `app → core:* → core:common` only. No `core:*` → `core:*` imports (interfaces in `core:common`). Enforced via Gradle dependency constraints + a lint check.
- All cross-module communication through the event/interface types in `core:common`; no reflection, no service locators.
- `core:cloud` code must be unreachable when its feature flags are off — verified by a release-build test that fails if any network class initializes with default settings.

## 5. Kotlin & code style
- Kotlin official style; Compose for all UI; coroutines + `StateFlow` (no LiveData, no RxJava).
- Audio/native boundary: all JNI wrapped in one class per engine with try/catch at every boundary crossing (AUDIO_PIPELINE.md §6).
- Public functions in `core:*` get KDoc only when the contract isn't obvious from signature + spec doc — don't paraphrase the spec, link it.
- Comments state constraints, not narration. (`// OEMs may feed zeros here; watchdog below` — good. `// start recording` — delete.)

## 6. Privacy red lines (violation = revert, no discussion)
1. No file-write APIs in `core:audio` (CI static scan) — P1 property.
2. No transcript text, phone numbers, or personal names in log statements outside debug-only session inspector paths (custom lint: flags string templates fed by transcript-typed values into `Log`/logger calls).
3. No new permissions without a PRIVACY_SECURITY.md §7 table update **in the same PR**.
4. No new network endpoints without §8 policy-map + data-inventory updates in the same PR.
5. No dependency additions without a review note in the PR: license, what it touches (network? storage? reflection?), size delta. Analytics/ads/crash SDKs are pre-vetoed.
6. Persistence layer rejects full Aadhaar/PAN patterns (DATABASE_DESIGN.md §3.7) — do not "fix" this validation to make a test pass.
7. Intel-pack content: detection-oriented only; the banned-phrase lint (legal-advice phrasing, AI_REASONING_ENGINE.md §5) and the no-how-to rule (SCAM_INTELLIGENCE.md §9) apply to every pack PR.

## 7. Testing rules
- Every PR: unit tests for new logic; bug fixes ship with the regression test that would have caught them (DEBUGGING_PLAYBOOK.md §8).
- Pack/weight/model changes: text-mode eval must run and its score table pasted into the PR (TESTING_STRATEGY.md §6).
- Never weaken an eval gate to merge. Gate changes are a docs PR to TESTING_STRATEGY.md with CTO signoff.
- Golden files updated only with human-reviewed diffs (no blanket regeneration commits).

## 8. Working agreements for AI agents
- One module per PR when feasible; keep diffs reviewable (< ~600 lines net unless mechanical).
- Commit messages: `module: what changed and why` (imperative); reference doc sections for decisions (`per AUDIO_PIPELINE.md §6`).
- Do not refactor opportunistically across module boundaries while implementing a feature; file it instead.
- If a spec is ambiguous, the agent must surface the ambiguity in the PR/issue rather than silently choosing — **except** trivially reversible choices, which it makes and notes.
- Generated code must compile and its tests pass locally before PR; "CI will tell us" is not a workflow.

## 9. Release discipline
- Release checklist (TESTING_STRATEGY.md §10) is executed literally, checkbox by checkbox, results archived in the release PR.
- Version bump PRs include: data-safety diff, permission diff (should be empty most releases), pack version notes.
- No release on Fridays (support coverage) — boring rules prevent exciting weekends.

## 10. Documentation upkeep
- Any merged change that contradicts a foundation doc without amending it is a defect — treat like a failing test.
- `docs/decisions/` (ADR-style, created at implementation) records every measured decision (ASR bake-offs, AEC defaults, threshold tunings) with data attached.
- Post-mortems in `docs/postmortems/` per DEBUGGING_PLAYBOOK.md §8.
