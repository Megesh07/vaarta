# VAARTA — Product Requirements Document (PRD)

**Status:** FOUNDATION — Single Source of Truth
**Version:** 1.0 · 2026-07-05
**Owners:** Product / CTO

---

## 1. Mission

Protect Indian citizens from **Digital Arrest scams** by giving them a real-time, private, on-device co-pilot during suspicious calls — and by automating the cybercrime complaint they file afterward.

> "There is no such thing as a digital arrest. No agency of the Government of India arrests anyone over a phone or video call." — This is the single most important fact the product must communicate, at the right moment, in the user's language.

## 2. Problem

Digital arrest scams are organized, scripted social-engineering attacks in which criminals impersonate Indian authorities (CBI, ED, Police, Customs, TRAI, RBI) and hold victims in prolonged calls/video calls, isolating them from family and extracting money under threat of arrest.

- Victims lose life savings; elderly and NRIs' parents are disproportionately targeted.
- Public reporting (I4C / press, 2024–2025) indicates losses in the hundreds of crores per quarter attributable to digital-arrest-style fraud. Exact per-quarter figures vary by source — **NO VERIFIED EVIDENCE FOUND for a single authoritative loss figure**; treat all such numbers as directional, never print them in-app as fact.
- The critical failure window is **during the call**: victims are frightened, isolated, and obeying instructions. Post-facto awareness campaigns don't reach them in that window.
- After the scam, victims face a second wall: filing a complaint at 1930 / cybercrime.gov.in requires structured details (numbers, timestamps, amounts, narrative) that a shaken victim struggles to assemble.

## 3. Product thesis

1. **Intervene during the call**, not after — with verification prompts and a live risk score.
2. **Break isolation** — one-tap family alert.
3. **Remove friction after the call** — auto-generate a complete complaint draft ready for 1930 / cybercrime.gov.in / police.
4. **Never become the threat** — privacy-first, on-device by default, no permanent audio storage.

## 4. Personas

| Persona | Description | Key needs |
|---|---|---|
| **P1 "Amma/Appa"** (55–75, Tier 1–3 city) | Smartphone user, regional language first, low tech confidence. Primary scam target. | Huge fonts, one-tap actions, native language, family alert, zero configuration after setup. |
| **P2 "The NRI child"** (28–45, abroad or metro) | Installs and configures VAARTA on parents' phones. | Remote-friendly onboarding, guardian alerts, trust in privacy. |
| **P3 "Working professional"** (22–40) | Hinglish/Tanglish speaker, busy, gets loan/UPI/customs scam calls. | Fast, unobtrusive protection; quick complaint export. |
| **P4 "Cyber cell volunteer / ASHA-type helper"** | Helps others file complaints. | Clean exports (PDF/DOCX/JSON), accurate structured data. |

Primary design target: **P1**. If P1 can use it under stress, everyone can.

## 5. Core user flow (canonical)

```
Incoming call (unknown / flagged number)
  → Persistent notification: "VAARTA Protection Available"
  → User taps "Enable Protection"
      → First time in this call: speakerphone guidance + consent check
  → Mini bubble opens over the call UI
  → AI protection starts (on-device ASR + risk engine)
  → Bubble shows: live risk score + suggested VERIFICATION questions
  → Risk crosses threshold → user prompted → Family Alert (consented guardian)
  → Call ends
  → AI generates Cyber Crime Complaint Draft
  → User reviews/edits → exports PDF / DOCX / TXT / JSON
  → Ready for 1930, cybercrime.gov.in (NCRP), police complaint
```

## 6. Features

Priority: P0 = MVP launch blocker, P1 = fast-follow, P2 = later.

| ID | Feature | Priority | Notes |
|---|---|---|---|
| F1 | Call detection + "Protection Available" notification | P0 | `CallScreeningService` role + `TelephonyCallback` fallback. No auto-start; user must tap. |
| F2 | Overlay mini-bubble during call | P0 | `SYSTEM_ALERT_WINDOW`; collapsible; risk color states. |
| F3 | Live audio capture (speakerphone mode) + on-device ASR | P0 | See AUDIO_PIPELINE.md. Mic-only, foreground service, visible indicator. |
| F4 | **Manual Mode** (no audio) — user taps observed cues | P0 | Guarantees product works when speakerphone is impossible or consent withheld. Same risk engine, human-fed signals. |
| F5 | Risk score (0–100) + staged risk states | P0 | On-device rule/signal engine (Tier-0). See AI_REASONING_ENGINE.md. |
| F6 | Suggested verification questions (not legal advice) | P0 | Localized, stage-aware. See SCAM_INTELLIGENCE.md §6. |
| F7 | Family/Guardian alert | P0 | Pre-consented contact; SMS + share-sheet (WhatsApp). One tap, confirm-before-send. |
| F8 | Complaint draft generation | P0 | Template engine on-device; optional LLM polish (opt-in, cloud). |
| F9 | Export PDF / TXT / JSON | P0 | Android `PdfDocument`, plain writers. |
| F10 | Export DOCX | P1 | Template-zip DOCX writer (no heavy deps). |
| F11 | 10 Indic languages + code-mixed (Hinglish etc.) | P0 = Hindi + English + Hinglish; P1 = Ta, Te, Bn, Mr; P2 = Kn, Ml, Gu, Pa, Or, As | Gated by ASR model quality per language — see INDIAN_LANGUAGE_SUPPORT.md. |
| F12 | Post-call safety debrief ("This matched CBI-impersonation pattern…") | P1 | Educational, cites observed signals only. |
| F13 | Scam pattern database updates (bundled + remote refresh) | P1 | Signed pattern packs; no user data upstream. |
| F14 | Optional cloud LLM assistance (Claude API) | P1 | Explicit opt-in per DPDPA; transcript text only, ephemeral. |
| F15 | Elder Mode (XXL UI, guardian-managed settings) | P1 | |
| F16 | Number-reputation lookup | P2 | Only with a lawful data source; **NO VERIFIED EVIDENCE FOUND** that a free lawful bulk API exists for India — do not promise. |

## 7. Explicit NON-goals (v1)

- **No call recording as a product feature.** Audio is processed in RAM and discarded (see PRIVACY_SECURITY.md). "Evidence save" persists *transcript text*, never raw audio, and only on explicit user action.
- **No legal advice.** Only verification assistance and factual safety information.
- **No automatic call blocking/answering.** User stays in control.
- **No iOS in v1.** iOS does not permit call-audio access or in-call overlays of this kind.
- **No scraping/UGC number database.** Legal exposure; see RISK_REGISTER.md R-08.
- **No monetization mechanics in v1.**

## 8. Success metrics

| Metric | Target (6 months post-launch) |
|---|---|
| Protection activation rate (notification → enabled) on unknown-number calls | ≥ 25% |
| Median time from tap to first risk update | ≤ 8 s |
| Risk engine: recall on scripted digital-arrest test corpus | ≥ 90% flagged HIGH by minute 3 |
| Risk engine: false HIGH rate on benign-call corpus | ≤ 2% |
| Complaint draft: fields auto-filled correctly (eval set) | ≥ 85% |
| Crash-free sessions | ≥ 99.5% |
| Play Store policy strikes | 0 |

## 9. Constraints (binding on all teams)

1. Android 10+ (minSdk 29), targetSdk = current Play requirement (36 as of 2026). Rationale: `CallScreeningService` role APIs and modern audio behavior; devices below Android 10 are a shrinking, riskier surface.
2. On-device inference must run on 4 GB RAM devices (₹10–15k segment). Model budget: ≤ 250 MB total download per language pack, ≤ 1 GB peak RAM during protection.
3. All in-call UI must be operable one-handed, glanceable in ≤ 2 s, and usable at 200% font scale.
4. Every network call is opt-in and enumerable in a single settings screen ("What VAARTA sends").
5. Play Store compliance is a launch gate, reviewed per release (PRIVACY_SECURITY.md §8).

## 10. Release phases

- **M1 (Alpha):** F1–F9, Hindi + English + Hinglish, sideload/internal testing.
- **M2 (Beta / Play internal):** F10–F12, +Tamil/Telugu/Bengali/Marathi, Play pre-launch report clean.
- **M3 (Public launch):** policy-hardened, Elder Mode, pattern updates.
- Details: IMPLEMENTATION_ROADMAP.md.

## 11. Open questions (tracked, non-blocking)

| # | Question | Owner | Due |
|---|---|---|---|
| Q1 | Partnership with I4C/1930 for direct complaint hand-off — feasible? | Product | M2 |
| Q2 | On-device LLM (Gemma-class via AICore) vs rules+cloud for reasoning Tier-1 | AI | M2 |
| Q3 | Distribution partnerships (banks, telcos) | Product | M3 |

## 12. Document map

This PRD is the root. Binding specs: TECHNICAL_ARCHITECTURE.md, MOBILE_UX_SPEC.md, AUDIO_PIPELINE.md, AI_REASONING_ENGINE.md, INDIAN_LANGUAGE_SUPPORT.md, PRIVACY_SECURITY.md, DATABASE_DESIGN.md, SCAM_INTELLIGENCE.md, IMPLEMENTATION_ROADMAP.md, TESTING_STRATEGY.md, DEBUGGING_PLAYBOOK.md, RISK_REGISTER.md, CLAUDE_CODE_RULES.md, CONTRIBUTING.md.
