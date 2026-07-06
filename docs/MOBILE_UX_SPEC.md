# VAARTA — Mobile UX Specification

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal UX Designer / Product

Design target: a frightened 62-year-old, one hand on the phone, scammer talking in their ear. Every in-call element must be understandable in ≤ 2 seconds with no reading of paragraphs.

---

## 1. Design principles

1. **Calm authority.** The scammer manufactures panic; VAARTA's visual language counters it. Steady colors, no flashing, no alarm sounds by default (the scammer can hear alarm sounds on speakerphone — see §9).
2. **One decision at a time.** Never show more than one primary action in the bubble.
3. **Localized, colloquial, respectful.** "आप" register in Hindi; equivalents per language. No legalese.
4. **Glanceable risk.** Color + icon + one line. Numbers are secondary.
5. **Accessible by default.** All layouts verified at 200% font scale; TalkBack labels on everything; touch targets ≥ 56 dp in-call.

## 2. Risk states (system-wide vocabulary)

| State | Score | Color | Icon | Bubble line (EN / HI example) |
|---|---|---|---|---|
| OBSERVING | 0–24 | Neutral slate | 👁 shield-outline | "Listening & checking…" / "जाँच जारी है…" |
| CAUTION | 25–49 | Amber | ⚠ shield-half | "Some warning signs" / "कुछ चेतावनी संकेत" |
| HIGH RISK | 50–74 | Deep orange | 🛑 shield-alert | "Strong scam signs" / "धोखाधड़ी के प्रबल संकेत" |
| SCAM PATTERN | 75–100 | Red | 🚨 shield-x | "This matches a known scam" / "यह ज्ञात ठगी का तरीका है" |

Colors must pass WCAG AA against bubble background in light/dark. Exact tokens defined in the design-tokens file at implementation; semantic names above are binding.

## 3. Surfaces

### 3.1 Persistent notification (call detected)
- Channel: `protection_offers` (IMPORTANCE_HIGH, no sound if user in call — vibrate only).
- Content: **"VAARTA Protection Available"** + caller number + one action button **[Enable Protection]** + secondary **[Not now]**.
- Tapping Enable never opens the full app during a call — it starts the session and shows the bubble.
- Auto-dismiss on call end.

### 3.2 Mini bubble (in-call overlay)
Collapsed (default, draggable, snaps to edge): 64 dp circle, risk color ring, shield icon, small score badge.
Expanded (tap): card ≤ 40% screen height, bottom-anchored:

```
┌─────────────────────────────────────┐
│ 🛑 Strong scam signs           [—]  │   ← state line + collapse
│ "They claim to be CBI and demand    │   ← top signal, 1 line, why-visible
│  secrecy — real police never do."   │
│                                     │
│ ASK THEM:                           │
│ ❝ Which police station are you      │   ← ONE suggested question,
│   from? I will call 1930 to verify.❞│      tap = next suggestion
│                                     │
│ [ 🔔 Alert family ]                 │   ← single primary action (state-dependent)
│ ⋮ Manual cues · Speaker help        │   ← overflow, small
└─────────────────────────────────────┘
```

Rules:
- Exactly one suggested question visible; ⟳ tap cycles. Questions are **verification assistance only** — copy reviewed against "no legal advice" rule (AI_REASONING_ENGINE.md §6).
- Primary action by state: OBSERVING→none; CAUTION→"See why"; HIGH→"Alert family"; SCAM PATTERN→"Alert family" + persistent one-line fact: **"No agency arrests anyone by phone or video call."**
- Bubble never covers the dialer's end-call button area (respect bottom 20% exclusion zone when expanded over common dialers; draggable to resolve conflicts).
- **Alert-send outcome states (locked, ARCHITECTURE_FREEZE_REVIEW.md Task 1):** guardian alert is a share-intent handoff (PRIVACY_SECURITY.md §7), so "sent" means the user completed the share sheet, not a delivery receipt. Bubble shows exactly three transient states after tapping "Alert family": `Opening share sheet…` → on return-to-app either `✓ Sent to [app]` (share intent completed) or `⚠ Not sent — tap to try again` (user backed out of the share sheet without picking a target). The retry action re-opens the same pre-filled share intent; it is never auto-retried silently. No delivery confirmation is claimed beyond "handed off to [app]," since VAARTA cannot know if the guardian received it.

### 3.2.1 Guardian consent scope note
Guardian consent (C3) is collected from the **user configuring VAARTA**, not from the guardian, and setup is in-person only in v1 (MOBILE_UX_SPEC §3.7 step 4). VAARTA does not evaluate whether the chosen guardian is a safe contact for the user (e.g., a household abuser) — this is a known, explicitly out-of-scope limitation for v1, not an oversight (ARCHITECTURE_FREEZE_REVIEW.md Task 5). No redesign is planned for v1; a future revision should consider whether guardian setup needs a private, non-witnessed configuration path.

### 3.3 Manual Mode panel (F4)
Reached from bubble overflow or auto-fallback. Grid of large tappable cue chips (localized):
`Says they are POLICE/CBI/ED/CUSTOMS` · `Threatens ARREST` · `Says DON'T TELL ANYONE` · `Demands MONEY/UPI now` · `Asks to move to WHATSAPP/SKYPE video` · `Mentions PARCEL with drugs` · `Mentions AADHAAR/SIM misuse` · `Pressure to stay on line`
Each tap feeds the risk engine exactly like a transcript signal. Chips show a ✓ once tapped.

### 3.4 Speakerphone coach
Shown once per call when audio protection selected and audio route ≠ speaker: full-width banner in bubble: "Turn on Speaker 🔊 so VAARTA can hear the caller" + a picture of the speaker button. If not enabled within 20 s → auto-offer Manual Mode. Never nag twice in one call.

### 3.5 Post-call debrief screen
Full app screen, auto-opened (or notification if user is busy):
1. Outcome header ("This call matched: Digital Arrest — CBI impersonation" or "No significant risk detected").
2. Timeline of detected signals (each: timestamp, quote/cue, plain-language why).
3. **Primary CTA: "Prepare complaint"** → complaint editor.
4. Secondary: Save evidence / Discard (discard is default after 30 min).
5. If HIGH+: card with 1930 tap-to-call and cybercrime.gov.in link.

### 3.6 Complaint editor & export
- Sectioned form pre-filled by ComplaintBuilder: Incident details, Caller details, Narrative, Financial loss (if any), Evidence list.
- Every AI-filled field carries a small "auto-filled — please verify" marker until touched.
- Export bar: **PDF · DOCX · TXT · JSON** → Android share sheet / SAF save.
- Big helper card: "File at 1930 (call) or cybercrime.gov.in" with step list.

### 3.7 Onboarding (first run, ≤ 4 screens + permission moments)
1. **What VAARTA does** (one screen, illustration, language picker first — device language pre-selected).
2. **Privacy promise** (plain language: "Calls are processed on your phone. Nothing is saved unless YOU save it." Link to full policy). This is the Play **prominent disclosure** surface for mic use — exact approved copy in PRIVACY_SECURITY.md §8.2.
3. **Permissions, just-in-time framed:** call screening role → notifications → overlay → microphone (mic explained *before* the system dialog).
4. **Guardian setup** (optional, skippable): pick contact, explain what they receive, obtain user confirmation that the guardian consents (DPDPA note: guardian's number is the user's data on the user's device; alert content is user-initiated sharing).
5. OEM battery-exemption step shown only on known-aggressive OEMs (DEBUGGING_PLAYBOOK.md §5).

## 4. Elder Mode (P1, F15)
- XXL type (min 22 sp body in-app; bubble text 18 sp+ always), high-contrast theme, reduced options (audio protection + guardian alert only), guardian can pre-configure via a shared setup session (in person — no remote control in v1).

## 5. Language & copy system
- All strings via standard Android resources; 10 Indic languages + English (rollout per PRD F11).
- **Suggested questions and risk lines are NOT in strings.xml** — they live in intel packs with per-language variants including romanized/code-mixed forms (INDIAN_LANGUAGE_SUPPORT.md §6).
- Output language user-configurable independent of device locale (a Tamil speaker on an English phone must get Tamil prompts).
- Copy tone: short sentences, verbs first, zero jargon. Every string reviewed by a native speaker before release (TESTING_STRATEGY.md §7).

## 6. Motion & sound
- Bubble state changes: 200 ms color cross-fade, no bounce.
- **No sounds from VAARTA during a call by default** (speakerphone leaks them to the scammer). Haptic patterns instead: CAUTION = 1 short; HIGH = 2 short; SCAM PATTERN = 3 short + repeat every 60 s until acknowledged.

## 7. Accessibility checklist (per-screen gate)
- [ ] 200% font scale: no clipped text, no lost actions
- [ ] TalkBack: every control labeled; risk state announced on change (politeness: assertive at HIGH+)
- [ ] Touch targets ≥ 48 dp (56 dp in bubble)
- [ ] Color-independent state (icon + text always accompany color)
- [ ] Works in dark theme

## 8. UX failure cases

| Case | Behavior |
|---|---|
| Overlay permission missing | Notification-driven session: expanded notification carries risk line + one question + alert action |
| User dismisses bubble mid-call | Session continues headless; notification remains; debrief still generated |
| Two calls (call waiting) | Session binds to the first call; new call detected → notification offers switching (session per call, only one active) |
| Screen off during speakerphone | Session continues; haptics still fire; AOD not used |
| App language pack for ASR missing | Bubble shows Manual Mode automatically with download prompt for later |

## 9. Adversarial UX (scammer-aware design)
The scammer often *instructs the victim* ("don't touch that app", "hang up and I'll call on WhatsApp").
- Bubble shows no large "STOP PROTECTION" control; stopping requires overflow → confirm.
- Family alert confirm dialog is worded neutrally ("Send update to Rekha?") — nothing on screen accuses anyone, so a victim being watched on video call isn't endangered. **Rationale:** digital-arrest scammers frequently have victims on camera; screen content may be visible or described.
- The one persistent counter-fact ("No agency arrests anyone by phone…") is phrased as general information, not an accusation.
- WhatsApp/Skype-switch cue is a first-class Manual chip because moving off the cellular call is the #1 escalation step (SCAM_INTELLIGENCE.md §4).

## 10. Deliverables for implementation
- Figma file (to be produced in M0 — tracked in IMPLEMENTATION_ROADMAP.md) must match this spec; where they conflict, **this document wins** until formally amended.
- Compose implementation notes: bubble = `WindowManager` + Compose `ComposeView`; state hoisted from `SessionCoordinator` StateFlow; previews for all four risk states × 3 languages × 200% font are checked into the repo.
