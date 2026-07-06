# VAARTA — Risk Register

**Status:** FOUNDATION · v1.0 · 2026-07-05 · **Owner:** CTO (review monthly; each risk has one owner)

Scoring: Likelihood (L) and Impact (I) 1–5; Exposure = L×I. Anything ≥ 12 needs an active mitigation with a milestone. Triggers define when a contingency activates — decided in advance so we don't rationalize in the moment.

| ID | Risk | L | I | Exp | Mitigation (active) | Contingency & trigger |
|----|------|---|---|-----|---------------------|-----------------------|
| R-01 | **ASR quality on speakerphone far-end audio too low** → signals missed, product feels broken | 4 | 5 | 20 | Signal-recall gate (not WER) as the binding metric; fuzzy matching + romanization channel designed for degraded text; rig-first evaluation from M1 | Trigger: any P0 language fails 90% recall after bake-off + 2 tuning cycles → ship that language Manual-Mode-first and say so honestly; accelerate Tier-1/whisper post-pass |
| R-02 | **Speakerphone requirement kills adoption** (users won't/can't switch; scammer forbids it) | 4 | 4 | 16 | Manual Mode as P0 peer feature; speaker coach UX; measure activation funnel in beta | Trigger: beta audio-mode activation < 15% of protection sessions → prioritize "VAARTA Dialer" (ROLE_DIALER) spike in M4; consider watch/second-device companion research |
| R-03 | **Play Store rejection/strike** (mic during calls, SEND_SMS, overlay) | 3 | 5 | 15 | Policy map maintained (PRIVACY_SECURITY.md §8); disclosure copy frozen per release; SMS fallback (share-intent) pre-built; no accessibility service ever | Trigger: rejection → respond with policy map + demo video; if SMS is the blocker, ship share-intent-only build within one sprint |
| R-04 | **False positives on genuine authority calls** (real police/bank) → user distrust, potential interference with real investigations | 2 | 5 | 10 | Stage-grammar design (benign calls don't progress past AUTHORITY); benign corpus includes genuine-police scripts; SCAM-PATTERN false rate gate = 0 on corpus; language always "matches patterns", never "is a scam" | Trigger: any credible field report of harmful false positive → S2 incident: add dialogue to corpus, retune, expedited pack update |
| R-05 | **OEM background kills break mid-call protection** | 4 | 3 | 12 | Onboarding exemptions; heartbeat + post-mortem recovery; device matrix targets worst OEMs from M1 | Trigger: >5% of beta sessions show heartbeat gaps → invest in OEM-specific onboarding deep-links + in-bubble "protection healthy" indicator |
| R-06 | **Legal exposure: call processing / DPDPA misstep** | 2 | 5 | 10 | Conservative architecture (no recording, on-device); consent log; counsel review gated before public launch (M2) | Trigger: counsel flags an issue → launch blocks on remediation; this is a hard gate, pre-agreed |
| R-07 | **Scam scripts evolve past shipped patterns** | 4 | 3 | 12 | Updatable signed packs (F13); behavior-level signals (stage grammar) age slower than phrasing; quarterly pattern review (SCAM_INTELLIGENCE.md §8) | Trigger: field reports of missed scam type → 2-week pack-update SLA; Manual chips cover the gap meanwhile |
| R-08 | **Legal/abuse risk from number-reputation ideas** (defamation, data sourcing) | 2 | 4 | 8 | F16 kept P2 and gated on a lawful data source existing; no UGC blocklists | Trigger: none — feature simply doesn't ship without the gate passing |
| R-09 | **Model/APK size excludes budget devices** | 3 | 3 | 9 | ≤250 MB per language pack budget; on-demand packs; APK ≤ 60 MB target; Android Go degrade test | Trigger: budget broken → cut bundled model, download-on-setup only |
| R-10 | **Cloud polish (F14) leaks or mishandles transcript text** | 2 | 5 | 10 | Off by default; preview-before-send; stateless proxy, no content logs; schema-validated output; injection defenses | Trigger: any S1 on this path → feature-flag off in next release (packs can carry a kill advisory), full post-mortem before re-enable |
| R-11 | **The app itself becomes an attack surface** (fake VAARTA clones, tampered APKs coaching victims) | 2 | 4 | 8 | Play-only distribution messaging; Play App Signing; in-app "how to verify you have real VAARTA" page | Trigger: clone discovered → report to Play/registrars; user advisory via pack banner |
| R-12 | **Team velocity: solo/small team + broad scope** | 4 | 3 | 12 | This documentation set (no re-thinking); Manual-Mode-first sequencing de-risks critical path; strict P0 discipline (PRD §7 non-goals) | Trigger: M1 slips > 4 weeks → cut P0 scope in this order: DOCX→Hinglish-specific tuning→bubble polish; never cut privacy properties or Manual Mode |
| R-13 | **Alert fatigue / crying wolf** (CAUTION shown too often on telemarketing) | 3 | 3 | 9 | CAUTION is visually quiet (amber, no haptic repeat); benign corpus includes telemarketing; thresholds tuned on false-CAUTION too (target ≤ 10%) | Trigger: beta feedback "it flags everything" → raise CAUTION floor, keep HIGH gates unchanged |
| R-14 | **Eval corpus overfits / gives false confidence** | 3 | 4 | 12 | Held-out quarterly refresh (TESTING_STRATEGY.md §10-note); hard-variant quota (20%); field-report → corpus pipeline | Trigger: field miss on a pattern the corpus "covered" → post-mortem on corpus design, not just the pack |

## Accepted risks (documented, no active mitigation)
- JVM String zeroing is imperfect → transcript remnants may persist in heap until GC (DATABASE_DESIGN.md §2). Accepted: attacker with live heap access already owns the device.
- Guardian SMS content is visible to anyone with the guardian's phone. Accepted: content is deliberately neutral (UX §9).
- A determined scammer aware of VAARTA could instruct victims to uninstall it. Partially mitigated by UX §9; fully preventing this is impossible and coercive designs are off the table.

## Review cadence
Monthly risk review; every S1/S2 post-mortem must map to a register row (or add one); exposure recalculated at each milestone gate.
