# VAARTA — Scam Intelligence

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Cybercrime Intelligence Researcher

This is the domain knowledge base that drives intel packs (signals, stages, questions). Sources: public I4C/1930 advisories, RBI/TRAI/press advisories, published victim accounts, police press releases (2023–2026). Where a claim can't be tied to a verifiable source, it is marked. **Nothing in this file is legal advice; nothing here may be surfaced to users as accusation of a specific caller.**

---

## 1. Scam taxonomy (v1 coverage)

| Code | Scam | v1 priority | One-line pattern |
|---|---|---|---|
| SC-01 | **Digital Arrest — police/CBI/ED impersonation** | P0 | "You are involved in a crime; stay on the line; you are under digital arrest; pay to avoid arrest." |
| SC-02 | **Customs/courier parcel scam** (FedEx-style) | P0 | "A parcel in your name contains drugs/passports; transferring you to Mumbai/Delhi police." Classic *feeder* into SC-01. |
| SC-03 | **TRAI/SIM disconnection** | P0 | "Your Aadhaar-linked SIM used for illegal activity; number will be disconnected in 2 hours; press 9." Feeder into SC-01. |
| SC-04 | **Bank/RBI money-laundering accusation** | P0 | "Your account is linked to a laundering case; funds must be 'verified' via transfer to an RBI/'Supervised' account." |
| SC-05 | **Cybercrime-branch impersonation** ("your number in FIR") | P0 | |
| SC-06 | Investment/trading app scam | P1 | Groups, fake dashboards, withdrawal fees. Different shape (not usually a single coercive call) — needs its own pack. |
| SC-07 | UPI/QR "you'll receive money" reversal scam | P1 | "Scan to receive" (QR scanning always *pays*). |
| SC-08 | KYC-expiry / electricity-disconnection | P1 | Link/APK push; shorter calls. |
| SC-09 | Family-emergency impersonation ("your son is arrested", incl. AI voice clones) | P1 | Targets P1 persona directly. |
| SC-10 | Loan-app harassment | P2 | Different victim journey; complaint templates reusable. |

## 2. Threat-actor playbook (composite, from public reporting)

- Call centers run **scripted, multi-role productions**: L1 "courier/telecom agent" → warm transfer to L2 "police officer" (often on WhatsApp/Skype video with uniform, staged police-station backdrop, fake ID cards) → L3 "senior officer/prosecutor" for the money step.
- Props: forged letterheads (CBI/ED/RBI/"Supreme Court"), fake FIR numbers, fake "arrest warrant"/"asset seizure order" PDFs sent on WhatsApp, spoofed or +92/unknown international numbers, sometimes local numbers via VoIP.
- The **video call is the cage**: victims ordered to stay on camera, alone, sometimes for days ("digital custody"), report movements, book a hotel room. Camera-on isolation is the single most distinctive behavior of SC-01.
- Money flow: RTGS/NEFT/IMPS to mule accounts, "verification/refundable security deposit" framing, crypto in some cases; instructions to visit the bank while staying on the phone and to lie to bank staff ("personal purchase / family need").
- **NO VERIFIED EVIDENCE FOUND** for a stable set of originating locations or actor attribution suitable for product use; the product must key on *behavior*, never caller geography/identity claims.

## 3. Psychology model (why victims comply)

| Lever | Mechanism | Product counter |
|---|---|---|
| **Authority bias** | Uniforms, badge numbers, case IDs, legal jargon | Verification questions that authority can't survive (§6); the counter-fact line |
| **Fear** | Arrest, family shame, media exposure threats | Calm UI (UX §1), fact line, guardian alert |
| **Isolation** | "Confidentiality bond", "tell no one, not even family — they may be involved", stay on line/camera | Family alert is the direct counter-weapon; anti-isolation question ("I am including my family member on this call") |
| **Urgency** | Countdown ("SIM off in 2 hours", "warrant executes today") | Naming the tactic in the debrief; "I will verify and call back" script |
| **Commitment escalation** | Small compliances first (confirm name/Aadhaar digits) → larger (video call) → payment | Stage grammar detects the ladder (AI engine §4.2) |
| **Social proof / legitimacy theater** | Fake documents, hold music, "case transferred to" hand-offs | FIR-copy and official-email questions expose theater |

## 4. The Digital Arrest script — five stages (drives the engine's stage grammar)

1. **HOOK** — automated/agent contact: parcel seized (SC-02), SIM violation (SC-03), FIR registered (SC-05). Purpose: establish a problem + transfer to "police".
2. **AUTHORITY** — "officer" introduces self (name/rank/unit/case number), demands identity confirmation, may switch to video with uniform/backdrop. Aadhaar recitation *by the caller* is common (data from leaks) to prove legitimacy.
3. **ISOLATION** — secrecy orders, stay-on-line/camera, move to WhatsApp/Skype, "don't involve family/lawyer/bank staff".
4. **ESCALATION** — fake documents arrive; threats concretize (non-bailable warrant, account freeze, arrest of family); "national secrets act" style jargon; hours pass.
5. **EXTRACTION** — "fund verification"/"security deposit"/"RBI supervised account"; bank-visit coaching; repeat extraction until victim breaks or funds end.

Benign calls (real bank fraud desks, real police) may resemble stage 2 briefly but **never** progress to 3+ — this asymmetry is the engine's core discriminator.

## 5. Signal categories (canonical, referenced by intel packs)

1. `AUTHORITY_CLAIM` — CBI/ED/police/customs/TRAI/RBI/court self-identification
2. `LEGAL_THREAT` — arrest, warrant, FIR, non-bailable, summons, "digital arrest" itself
3. `ISOLATION_ORDER` — secrecy, don't tell family, stay on line/camera
4. `CHANNEL_SWITCH` — move to WhatsApp/Skype video, "download this app" (incl. screen-share apps = also credential theft)
5. `URGENCY_PRESSURE` — deadlines, "within 2 hours", immediate action
6. `IDENTITY_PHISH` — Aadhaar/PAN/OTP/account confirmation demands
7. `EXTRACTION_MOVE` — transfer/deposit/UPI/verification-of-funds/refund promises/QR
8. `LEGITIMACY_THEATER` — badge numbers offered unprompted, case IDs, document sending, hold-transfer between "departments"
9. `PARCEL_PRETEXT` — courier/customs/contraband vocabulary

Each category has per-language pattern lists (native script + romanized + code-mixed) maintained in `intel-packs/` — sample entries in AI_REASONING_ENGINE.md §3; full lists are implementation artifacts built in M1 with native-speaker review (TESTING_STRATEGY.md §7).

## 6. Verification question bank (rationale)

Design rule: each question is safe for a false-positive (polite to a genuine caller) and lethal to the script:

| Question | Why it breaks the script |
|---|---|
| "Which police station? I'll call them directly / via 112." | Script has no verifiable station; real police can name one. |
| "Send the FIR copy to my address or from an official gov.in email." | Forgeries exist but demanding *official channel* delivery stalls the urgency lever. |
| "I will verify with 1930 and call back." | Hanging up breaks the isolation cage; scripts forbid it — watch the reaction. |
| "Your name, designation, badge number — I am writing this down." | Legitimacy theater welcomes this; the *follow-up* ("I'll verify with the department") doesn't. |
| "I am adding my son/daughter to this call now." | Direct isolation breaker; refusal is itself a near-certain scam tell (fires `ISOLATION_ORDER`). |
| "Why would a government agency need payment to avoid arrest?" | Only for HIGH+ states; forces the logical absurdity into view. |

Facts the product may state (government-advisory sourced): no "digital arrest" exists in Indian law; police/agencies don't demand money by phone; QR scanning pays, never receives; 1930 is the national cyber-fraud helpline; cybercrime.gov.in is the NCRP portal.

## 7. Post-scam guidance content (debrief screen)

Within minutes matters ("golden hour"): call **1930** immediately if money moved (banks can freeze in-transit funds); file at **cybercrime.gov.in**; preserve everything (numbers, screenshots, receipts, UPI refs); inform the bank's fraud line; do not delete chats. VAARTA automates the complaint body for exactly this moment (PRD F8).

## 8. Intelligence lifecycle

- **Sources watched:** I4C/1930 advisories, RBI/TRAI/PIB releases, state cyber-cell bulletins, credible press investigations, user-reported patterns (M4, opt-in).
- **Pack update flow:** researcher edits YAML → native-speaker review → eval suite run (recall/false-positive gates must not regress) → signed pack release (Ed25519) → in-app refresh (F13). Bundled base pack ships with every APK so day-one protection never depends on network.
- **Deprecation:** signals carry `added`/`review_by` dates; stale patterns re-validated quarterly.

## 9. Ethical red lines

- Never display "this caller IS a criminal" — only pattern-match language ("matches known scam patterns").
- Never auto-contact police/1930 on the user's behalf.
- Never use scare UI to drive engagement — VAARTA must not become the thing it fights.
- Intelligence content describes *detection*, not scam *operation*; this file and packs must not read as a how-to. Pack PRs are reviewed against this rule (CLAUDE_CODE_RULES.md §6).

## 10. Open research (tracked)

| # | Item | Milestone |
|---|---|---|
| R1 | Voice-clone family-emergency (SC-09) signals — what's detectable from text alone? | M2 |
| R2 | Screen-share app push (`CHANNEL_SWITCH` subtype) — enumerate current app names lawfully | M1 |
| R3 | Regional script variants (Tamil Nadu "cyber police" flavor etc.) — per-state pattern deltas | M2 |
