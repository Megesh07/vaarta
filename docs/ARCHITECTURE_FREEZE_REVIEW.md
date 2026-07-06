# VAARTA — Architecture Freeze Review

**Status:** CLOSURE REVIEW — resolves FOUNDATION_AUDIT.md · v1.0 · 2026-07-05
**Reviewer role:** final pre-implementation launch review
**Input:** FOUNDATION_AUDIT.md and the full `/docs` set it audited.

This document closes the findings raised in [FOUNDATION_AUDIT.md](FOUNDATION_AUDIT.md). Two findings required an actual decision, not just a recommendation — those decisions are made below **and have been written back into PRIVACY_SECURITY.md §7/§8 and MOBILE_UX_SPEC.md §3.2/§3.7**, because a "locked" decision that isn't reflected in the source-of-truth doc isn't locked. Everything else in this review is a determination, not a redesign.

---

## Task 1 — Critical findings, resolved

### Finding #1: SEND_SMS vs. share intent

**LOCKED V1 DECISION: (A) Explicit share intent. `SEND_SMS` is not requested in v1.**

| Factor | Assessment |
|---|---|
| Google Play risk | `SEND_SMS` is gated to apps whose *core functionality* is messaging (Play's Permissions declaration policy). A single guardian-alert message is a defensible-but-not-strong case; carrying this into review as a live open question on a P0 feature is exactly the kind of self-inflicted risk a launch review should close, not schedule. Share intent requests no dangerous permission at all — it cannot generate this class of rejection. |
| User trust | A share-intent message arrives in the guardian's *existing* thread with the user (their own WhatsApp/SMS app, their own contact entry) rather than from an unfamiliar automated sender ID — this reads as more trustworthy, not less, which matters when the message is "I might be being scammed." |
| Privacy | Removes a permission grant entirely from the data-safety surface; smaller permission footprint is a direct instance of the minimization principle already binding elsewhere in PRIVACY_SECURITY.md. |
| Accessibility | Share sheet is a standard, TalkBack-compatible OS surface the user already knows from other apps; a bespoke SmsManager flow adds nothing accessibility-wise that the share sheet lacks. |
| Implementation cost | Lower: no permission-request flow, no SmsManager result-code handling, no dual-SIM subscription-selection UI (DEBUGGING_PLAYBOOK.md §3 previously had to account for `sub_id` — no longer needed for this path). |
| Adoption | One extra tap through the share sheet versus a zero-tap SmsManager send. Judged acceptable: the user has already decided to alert family (this is a deliberate, not time-critical-to-the-millisecond action, unlike the risk-scoring path). |

**Why this is a real decision and not a deferred fallback:** the audit's finding was that the choice had been left open until a hypothetical Play rejection — i.e., the safer path only activates *after* a failure. That ordering is backwards for a P0 feature. This review inverts it: share-intent is the actual v1 default, decided now, on its own merits, not as a contingency. Direct `SEND_SMS` is not on the roadmap; it is reopenable only with beta evidence of meaningful share-sheet drop-off **and** a prepared Play core-functionality justification — matching CLAUDE_CODE_RULES.md §1's amendment process, not a silent fallback.

**Written back to:** PRIVACY_SECURITY.md §7 (permissions manifest, `SEND_SMS` moved to "Never" list with rationale), §8 (compliance table row resolved), MOBILE_UX_SPEC.md §3.2 (new alert-outcome states reflecting share-intent semantics — see Task 5).

### Finding #2: Onboarding wording

**Generated and frozen** in PRIVACY_SECURITY.md §8.2 (new subsection). Two-paragraph structure, binding:

> **How VAARTA handles your calls**
> During a call, VAARTA listens through your microphone to check for scam warning signs — but only while you turn protection on, and **only on this phone**. Your call is never recorded, never saved, and never sent anywhere.
>
> To keep its scam-detection lists up to date, VAARTA does download small update files over the internet — no call content, no personal details, ever included. You can see and turn this off anytime in Settings → What VAARTA Sends.

Design rationale against the stated requirements: **simple** (two short paragraphs, no jargon, no conditionals); **legally safer** (does not claim "nothing leaves your phone" as a blanket statement — the claim is scoped precisely to call content, matching what the architecture actually guarantees); **human-readable** (active voice, second person, states what happens and why); **DPDPA-friendly** (states purpose — scam detection — and gives a discoverable path to see/control the one network flow that exists by default, satisfying transparency and user-control expectations without requiring the user to read a full privacy policy first); **no misleading statements** (the two claims — call privacy vs. pattern downloads — are structurally separated so they cannot be read as one blended promise, closing FOUNDATION_AUDIT.md's C-02 exactly as diagnosed). A binding rule accompanies the copy in §8.2: future features that change what leaves the device (e.g., F14 cloud polish) get their own separate disclosure moment and may not be folded into this screen.

---

## Task 2 — Implementation blockers verified

**AEC-per-OEM behavior and the ASR checkpoint bake-off are expected engineering/measurement activities, not architectural blockers.** Both were already correctly scheduled in IMPLEMENTATION_ROADMAP.md as M1 activities (not assumptions baked into the architecture), and TECHNICAL_ARCHITECTURE.md's own critical-path callout ("the critical path is ASR-on-real-devices") already names this as the thing to de-risk by building Manual Mode first — which the roadmap does.

**Can implementation begin before they finish? Yes, for the large majority of the system.** Call detection, the overlay bubble, Manual Mode, the Tier-0 engine's scoring math and stage machine, the database schema, the complaint template engine, and all four export formats have zero dependency on AEC settings or a specific ASR checkpoint — they consume an abstract `TranscriptEvent`/`ManualCue` interface (TECHNICAL_ARCHITECTURE.md §9 module boundaries; AI_REASONING_ENGINE.md §2) that doesn't change shape based on which model or AEC setting wins the bake-off.

**Verdict: PARTIAL GO.**
- **GO** for: `core:common`, `core:call`, `core:overlay`, `core:reasoning` (Tier-0 logic against fixture/reference transcripts), `core:complaint`, `core:data`, `core:alerts`, intel-pack toolchain — these can start immediately per IMPLEMENTATION_ROADMAP.md M0/M1 ordering, using the text-mode eval path (TESTING_STRATEGY.md §6) which doesn't require live audio at all.
- **Measurement-mode, not blocked, not fully implemented** for: `core:audio` (capture/VAD scaffolding can be built now; AEC on/off default and per-OEM overrides are pending the bake-off) and `core:asr` (JNI wrapper and streaming-event plumbing can be built against a placeholder model now; the shipped checkpoint per language is pending the bake-off).
- This is exactly the PARTIAL GO characterization, not a full GO, because the roadmap's own critical-path diagram makes ASR-on-real-devices the gating item for *alpha completion* (M1 exit criteria), even though it doesn't gate *alpha start*. Calling it a flat GO would understate that the audio module's final form is genuinely unknown until measured; calling it NO GO would misstate that most of the system has no such dependency.

---

## Task 3 — Missing scaffolding, triaged

| Artifact | Priority | Can implementation proceed without it? | Create immediately? |
|---|---|---|---|
| `docs/decisions/` (ADR directory + template) | **P0** | Yes, for M0 module/skeleton work. No, for the M1 exit criterion of recording the AEC and ASR bake-off results — those need a prescribed home before the bake-off concludes, or the results (per Task 2) have nowhere to land and risk being lost in commit history/chat. | **Yes — before the bake-off produces its first result.** This is the one scaffolding item with a near-term hard dependency (weeks, not months). |
| `SECURITY.md` (root, vulnerability disclosure policy) | **P1** | Yes, fully — nothing in M0/M1 implementation reads or depends on it. | Before any external exposure of the repo (public repo, beta testers, or first external contributor) — i.e., no later than the M2 beta-track opening (IMPLEMENTATION_ROADMAP.md M2), not before. |
| Postmortem template (`docs/postmortems/`) | **P2** | Yes — it is only invoked by an actual S1/S2 incident (PRIVACY_SECURITY.md §9, DEBUGGING_PLAYBOOK.md §8), which requires a running system with real sessions; M0/M1 has no incident surface yet. | No — create it when M2 beta opens and real incidents become possible, or reactively at the first incident if that's earlier than planned. Creating it before there is any operational surface risks the template being wrong/unused-stale by the time it's needed. |

None of the three block M0 start. Only `docs/decisions/` has a near-term (early-M1) hard dependency; the other two are correctly sequenced later and should **not** be front-loaded — a template written before the team has run an incident or opened external contribution tends to be guessed rather than fit-for-purpose.

---

## Task 4 — Privacy revalidation

| Area | Verdict | Explanation |
|---|---|---|
| DPDPA alignment | **WARNING** (unchanged) | Architecture posture is sound and this review's two decisions (share-intent-only alerts, precise onboarding copy) remove two concrete points a regulator or reviewer could have picked at — but formal counsel review remains an unstarted external gate (RISK_REGISTER.md R-06, M2). No amount of internal documentation resolves an unstarted legal review; this stays WARNING honestly rather than being marked PASS on the strength of engineering judgment alone. |
| Google Play alignment | **Upgraded: WARNING → PASS (design-level)** | The specific open risk the audit identified — `SEND_SMS` as "the highest-risk request" with its resolution deferred to a future rejection — is now closed by removing the permission from v1 entirely (Task 1). The onboarding prominent-disclosure copy is now frozen and precise. At the design level, nothing in the current permission/data-flow surface is a known Play policy risk. This is marked PASS **at the design level**, not as a guarantee of review outcome — actual Play review remains an external, non-foreclosed event, consistent with this doc set's own discipline of not asserting facts it can't verify. |
| Audio handling | **PASS** (unchanged) | No PCM-to-disk code path in release builds remains the enforcement mechanism (architectural, not policy); unaffected by this review's findings. |
| RAM-only design | **PASS** (unchanged, with disclosed residual risk) | JVM String-zeroing limitation remains an honestly accepted residual risk (DATABASE_DESIGN.md §2), not newly introduced by this review. |
| Transcript persistence | **PASS** (unchanged) | RAM-first, 30-minute discard default, opt-in full-transcript save — untouched by this review. |
| Consent flows | **PASS** (unchanged) | Five independently revocable consents (C1–C5) with append-only log — untouched. |
| Guardian notifications | **Upgraded: WARNING → PASS (mechanism)**, with one **open item carried forward, not a privacy defect** | The share-intent-only decision (Task 1) removes the permission-risk dimension of this area entirely and simplifies the delivery-outcome model (Task 5 adds the alert-outcome UX states). The mechanism itself is now clean. The **guardian-as-risk scenario** (a guardian who is unsafe for the user, e.g. domestic-abuse context) is a distinct, unresolved *product* question, not a mechanism flaw — carried forward explicitly in Task 5, not scored here as a privacy FAIL, because the data flow itself (user-configured, user-consented, in-person setup) is privacy-correct even though the product-safety question around *who* the user picks remains open. |
| Evidence exports | **PASS** (unchanged) | User-driven, previewed, multi-format, destination deliberately not logged (DATABASE_DESIGN.md §3.5) — untouched. |

**Net change from FOUNDATION_AUDIT.md:** two of eight areas move from WARNING to PASS as a direct result of Task 1's decisions; DPDPA remains WARNING because it depends on an external event (counsel review) this review cannot close by writing documentation.

---

## Task 5 — UX edge cases, reviewed (no redesign)

| Edge case | Already handled? | If NO — recommended location |
|---|---|---|
| TalkBack users | **NO** | The no-sound-during-calls rule (MOBILE_UX_SPEC.md §6) and TalkBack's reliance on spoken feedback from the OS are in tension and unresolved — does the user's own screen-reader speech leak to the scammer over speakerphone the way an alarm would? → **MOBILE_UX_SPEC.md §6/§7** should carry an explicit note naming this tension; not resolved here per the "do not redesign" instruction. |
| Low-literacy users | **NO** | Icon/color/short-text design serves this persona well for the bubble and Manual Mode chips, but the debrief and complaint-editor screens (§3.5/§3.6) are text-heavy with no read-aloud or audio-assist affordance specified. → **MOBILE_UX_SPEC.md §3.6 / §4 (Elder Mode)**. |
| Domestic abuse scenarios | **NO** | Not addressed anywhere in the doc set prior to this review. → **PRIVACY_SECURITY.md §4 (consent architecture) or a new MOBILE_UX_SPEC.md guardian subsection** — this review has added a scope note (§3.2.1) stating it's a known v1 limitation, not a redesign of guardian setup. |
| Guardian risk | **NO** (same item as above) | Same location — **MOBILE_UX_SPEC.md §3.2.1** (added this review as a scope note only). |
| Failed notifications (alert delivery) | **Resolved this review** | Previously NO; now **YES** — MOBILE_UX_SPEC.md §3.2 has the three-state alert-outcome UX (opening / sent-to-app / not-sent-retry) written in as part of closing Task 1's share-intent decision, since the failure semantics changed along with the delivery mechanism. |
| No speakerphone | **YES** | MOBILE_UX_SPEC.md §3.4 (speaker coach, 20s auto-Manual-Mode offer). |
| Bluetooth devices | **Partial — NO for explicit cross-reference** | AUDIO_PIPELINE.md §6 handles the route-change *mechanism* (pause ASR, coach once) and DEBUGGING_PLAYBOOK.md K-04 notes route flapping, but no doc explicitly states "BT-connected ⇒ same auto-Manual-Mode-offer path as no-speaker." → **AUDIO_PIPELINE.md §4 or MOBILE_UX_SPEC.md §3.4**, one cross-reference sentence. |
| Earpiece-only calls | **YES** | Same mechanism as "no speakerphone" — MOBILE_UX_SPEC.md §3.4. |
| Mixed languages | **YES** | INDIAN_LANGUAGE_SUPPORT.md §5/§8 (code-mix handling, graceful degrade when beyond the configured pair). |
| Tanglish/Hinglish | **YES** | INDIAN_LANGUAGE_SUPPORT.md §5 — explicit first-class treatment, not an afterthought. |
| Senior citizens | **YES** | Elder Mode (MOBILE_UX_SPEC.md §4), P1 persona in PRODUCT_PRD.md §4. |

Two items were resolved as part of closing Task 1 (failed notifications, and the guardian-risk item received a scope note, not a resolution). The remaining three NOs (TalkBack tension, low-literacy complaint review, Bluetooth cross-reference) are confirmed real, specific, small gaps — consistent with FOUNDATION_AUDIT.md's own assessment — and are left as recommended locations only, per this task's explicit "do not redesign" instruction.

---

## Task 6 — Architecture freeze checklist

| Item | Verdict | Explanation |
|---|---|---|
| No impossible features | **PASS** | PRODUCT_PRD.md §7 non-goals are explicit and consistently respected everywhere downstream (no call recording, no legal advice, no auto-contact of authorities, no iOS v1); nothing in the doc set assumes an unavailable platform capability except where explicitly marked as a future spike (ROLE_DIALER). |
| Privacy model locked | **PASS** | Seven numbered properties (P1–P7) enforced architecturally (by absence of code paths) rather than by policy; this review's two amendments (SMS removal, onboarding copy) strengthen rather than alter the model. External validation (DPDPA counsel) remains a scheduled M2 gate, not an open design question. |
| Audio model locked | **PASS** | Mic+speakerphone as the only compliant live path, with Manual Mode as a peer P0, is confirmed unshaken by this review. AEC defaults and ASR checkpoints are correctly left open as measurement (Task 2), which is the model working as designed, not a gap in it. |
| Tier-0 engine locked | **PASS** | Deterministic weighted-signal scoring + five-stage grammar unaffected by any finding in this review. |
| Manual Mode locked | **PASS** | Confirmed as the single strongest resilience mechanism in the design; nothing here touches it. |
| Language strategy locked | **PASS** | Four-layer gating framework (UI/ASR/patterns/output) and the P0/P1/P2 rollout matrix are unaffected; per-language ship timing is correctly gated by the bake-off (Task 2), which is the framework functioning as intended. |
| Backend assumptions locked | **PASS** | No mandatory backend in v1; `core:cloud` remains fully optional and feature-flagged; unaffected by this review. |
| Deployment assumptions locked | **PASS** | Play-only, phased rollout, no iOS v1 — unaffected. |
| Testing philosophy locked | **PASS** | Two-universe testing (code correctness + judgment eval via the two-phone rig) and binding eval gates are unaffected by this review's findings. |
| Roadmap locked | **PASS** | M0–M4 sequencing and critical-path analysis hold; this review adds exactly one scheduling note (`docs/decisions/` must exist before the M1 bake-off concludes — Task 3), which is a scope addition to M0's exit criteria, not a sequencing change. |

All ten items PASS. This is the expected outcome of a closure review: the checklist items unaffected by the audit's findings should already have been PASS in FOUNDATION_AUDIT.md's own logic (none of its findings challenged them), and the two items the audit did flag as open (privacy/Play posture, indirectly touching the privacy-model and roadmap rows) are resolved by Tasks 1–3 above.

---

## Task 7 — Final freeze decision

## OPTION A — ARCHITECTURE FROZEN. Implementation begins immediately.

**Justification.** FOUNDATION_AUDIT.md identified exactly two CRITICAL items, and both were characterized there as underspecified *defaults* on already-correctly-designed mechanisms, not missing designs or contradictions — that characterization is why this review was able to close them with decisions rather than redesign work. Both are now resolved: the SMS/share-intent default is decided and written into PRIVACY_SECURITY.md, and the onboarding copy is drafted, reviewed against every stated requirement, and frozen in the same document. The two flagged "measurement activities" (AEC, ASR bake-off) are confirmed in Task 2 to be expected engineering work already correctly scheduled in the roadmap, not architectural gaps — they gate the completion of one module (`core:audio`/`core:asr`), not the start of implementation. The three missing scaffolding artifacts are triaged in Task 3 with only one (`docs/decisions/`) having a near-term dependency, and that dependency is weeks away (early M1), not a day-one blocker. The privacy revalidation (Task 4) shows the review's own decisions improving two of eight areas with no area newly at risk. The UX edge-case review (Task 5) surfaces three real, narrow, already-anticipated gaps and resolves two others outright — none rise to a level that would require touching a locked architectural decision. The freeze checklist (Task 6) is unanimous PASS.

**What "frozen" means operationally, stated once and binding going forward (see IMPLEMENTATION_GUARDRAILS.md):** the decisions in PRODUCT_PRD.md, TECHNICAL_ARCHITECTURE.md, PRIVACY_SECURITY.md, AI_REASONING_ENGINE.md, AUDIO_PIPELINE.md, INDIAN_LANGUAGE_SUPPORT.md, MOBILE_UX_SPEC.md, DATABASE_DESIGN.md, and SCAM_INTELLIGENCE.md — as amended by this review — do not get re-opened by implementation-time convenience or by an agent's in-the-moment judgment call. They get re-opened only by the ADR process in CLAUDE_CODE_RULES.md §1, with evidence, once `docs/decisions/` exists.

**Immediate next actions (scaffolding, not architecture):** create `docs/decisions/` with an ADR template before the M1 ASR/AEC bake-off concludes (Task 3); begin IMPLEMENTATION_ROADMAP.md M0 today.
