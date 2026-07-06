# ADR-0001 — MVP scope lock (hackathon / portfolio build, $0 constraint)

**Status:** Accepted
**Date:** 2026-07-07
**Deciders:** Product owner + implementation (AI-assisted)
**Relates to:** PRODUCT_PRD.md §6/§10, IMPLEMENTATION_ROADMAP.md M0/M1, ARCHITECTURE_FREEZE_REVIEW.md (Option A), IMPLEMENTATION_GUARDRAILS.md

## Context

The `/docs` set describes a full **production** product: a 28-week roadmap, Play Store
launch, 10+ languages, optional cloud polish, a device farm, and DPDPA counsel. This
build is deliberately narrower:

- **Intent:** a hackathon-ready, **portfolio-worthy MVP/prototype** — usable by a few real
  people, demonstrating genuine benefit. Explicitly **not** production-grade.
- **Hard constraint:** **$0 build cost.** No paid APIs, no server, no store fee.
- **Target theme** ("AI for Digital Public Safety") names five pillars. This MVP commits to
  **digital-arrest scam detection + citizen shield + complaint auditability**, and does
  **not** attempt counterfeit-currency computer vision, fraud-network graph intelligence,
  or a geospatial law-enforcement command centre.
- **Environment:** the build machine had no Android toolchain installed; the owner has a
  physical Android device and directed: *install what is required, do not compromise, make
  it perfect per the locked plan.*

## Decision

**Build target: a native Android app (Kotlin + Jetpack Compose), distributed by sideload
(no Play publishing in the MVP).** Native is chosen over a web simulation because VAARTA's
defining property — on-device, in-call assistance under the platform constraint
(TECHNICAL_ARCHITECTURE.md §2) — cannot be demonstrated by a web app.

This ADR **narrows PRODUCT_PRD.md §6 for the MVP milestone**. It **reverses no LOCKED
decision** (FOUNDATION_AUDIT.md §2, L1–L16) — it defers features, it does not redesign.

**IN (MVP — all $0, device-independent parts buildable now):**
- `core:common` — event model + `SessionCoordinator`/`RiskEvent` contracts + normalization.
- `core:reasoning` — Tier-0 deterministic engine: signal matching, HOOK→AUTHORITY→
  ISOLATION→ESCALATION→EXTRACTION stage grammar, 0–100 scoring, hysteresis.
- `intel-packs` — SC-01..SC-05 signals + question bank in **hi / en / hinglish**.
- **Manual Mode** — the reliable, zero-audio path (a P0 peer, never a fallback — L2).
- **Risk UI** — four states, one verification question at a time, the single counter-fact line.
- `core:complaint` — slot-based complaint builder + **PDF / TXT / JSON** export.
- **Guardian alert** — share-intent only (no `SEND_SMS` — PRIVACY_SECURITY.md §7).
- **Text-mode eval harness** — reference transcript → engine → score (TESTING_STRATEGY.md §6).

**STRETCH (spike-gated, never a blocker):**
- Live on-device ASR (sherpa-onnx, hi/en) — gated behind the M1 bake-off spike (R-01).
- Overlay bubble over the dialer + `CallScreeningService` integration.
- SQLCipher persistence (RAM-first model works without it for the demo).

**OUT (deferred — cost or non-essential for MVP):**
- Cloud LLM polish (F14 — costs $), Play publishing ($25), DOCX (F10), Elder Mode (F15),
  pattern-pack remote update + signing infra (F13), P1/P2 languages, number reputation
  (F16), device farm, DPDPA counsel, OEM background-survival hardening.

**$0 rules:** free/open tooling and models only; test on the owner's own device.
**Demo vehicle:** "rig mode" — a debug test hook that starts a session and drives it from a
scripted transcript (or a played audio clip), so the live-reacting experience is shown
without depending on a real inbound scam call.

## Consequences

- **Strong on:** Technical Excellence, UX, and the theme's eval-focus items *digital-arrest
  precision/recall*, *very low false-positive citizen tool*, and *auditability of
  intelligence packages* (the exportable NCRP-aligned complaint).
- **Explicitly out of scope, accepted:** counterfeit-currency, fraud-graph, geospatial,
  LEA command-centre, and the *fraud-network detection lead time* eval item — the last is
  structurally unaddressable by an on-device, non-aggregating design (and that is fine).
- **Top residual unknown:** live-ASR quality on speakerphone-through-air Indic audio
  (R-01). Gated behind a spike; the product is never blocked on it because Manual Mode is a
  peer path (L2). The ASR spike result will be recorded in a follow-up ADR.
- Toolchain (JDK, Android SDK/NDK, Gradle) installed via command line; Android Studio GUI
  optional. Device-independent modules are unit-testable headlessly.
- **Follow-ups:** ADR-0002 (ASR spike result); `SECURITY.md` + root `CLAUDE.md` before any
  external repo exposure.

## Alternatives considered

| Option | Why not chosen |
|--------|----------------|
| Web experience-prototype as the deliverable | Cannot demonstrate on-device in-call interception — the product's whole point. Retained only as a possible future demo aid. |
| Full production roadmap as written | Out of scope for a $0 hackathon/portfolio MVP; deferred, not discarded. |
| Cloud-LLM-centric build | Violates the $0 rule and the on-device-first lock (L3). |
| Broaden to all five theme pillars (counterfeit CV, graph, geo) | Not achievable at $0 by a small build; each is a separate product. VAARTA is framed as one deep module of the broader platform. |
