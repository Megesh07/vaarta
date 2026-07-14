# VAARTA v2 — "Intelligence Everywhere" UX + Feature Redesign

**Date:** 2026-07-14
**Status:** Design — awaiting owner review before planning
**Supersedes UX of:** ADR-0003 Phase 3 "UI redesigned" (kept the engine, replaces the shell)
**Relationship to scope lock (ADR-0001):** stays inside it — strict $0, sideload, on-device-first,
hackathon/portfolio MVP. This reshapes the *surface* and adds *conversational intelligence*; it does
**not** reopen the counterfeit-currency / fraud-graph / geospatial pillars.

---

## 1. Why this exists (the problem)

The engine is solid — deterministic risk scoring, live-audio→AI coaching, recorded-audio analysis,
encrypted history, and a hardened safety filter are all built and verified. The **shell drifted from
the owner's intent**:

1. **Manual Mode is dead weight.** It returns the *same canned answer to everyone* — zero
   intelligence, no user benefit. The owner's north star is *real* AI help, everywhere.
2. **Home is overloaded and repetitive** — too many entry points, competing cards, no single clear
   "what do I do" path for a non-technical or elderly user.
3. **The overlay is unusable in a real call** — it parks at the bottom and covers the phone's own
   call controls (hang up, mute, keypad, record). It is not draggable-to-safety or resizable, and it
   does not expand *from* the icon.
4. **History is a dead end** — a read-only replay. The owner wants each saved call to be a living
   context you can *interrogate* with a full ChatGPT/Gemini-style assistant.

**Mission framing (owner's words, distilled):** this is a *social-good* product — keep ordinary
Indians (especially the vulnerable) from being scammed, and *educate society* so fewer people fall in
the first place. It must justify the hackathon theme **and** stand as genuine public benefit.

---

## 2. Goals / Non-goals

**Goals**
- Every user-facing answer is *real AI intelligence* grounded in context — never a canned template.
- A dead-clean 3-tab home a non-technical person understands in one glance.
- One reusable "Understand this call" screen: verdict → clean transcript → download → multimodal chat.
- An always-current, AI-generated, web-grounded scam-awareness feed on the home screen.
- A live overlay that never blocks the native call UI: corner icon → expand-from-icon → drag + resize.
- Complaint guidance + education as first-class, woven throughout (the social-good pillar).

**Non-goals (this spec)**
- No paid APIs, backend, or Play publishing. Still $0 / sideload.
- Not reopening the deferred challenge pillars.
- Not a full "Elder Mode" as a separate mode — accessibility is the *default*, not a toggle.
- General document/PDF chat upload is **stretch**, not core (images + audio cover the real evidence).

---

## 3. The one principle that unlocks everything: hidden engine, visible intelligence

There are two different "deterministic" things in the current app, and the owner's complaint is about
exactly one of them:

- **Manual Mode UI** (the chips giving identical canned answers) → **DELETED.** Removed from the app:
  `ui/ManualModeGrid.kt` deleted, all Manual Mode entry points and chip rendering removed from
  `MainActivity` / `SessionViewModel`, and the `manualCue` **display path** retired from the UI. The
  intel-pack `manualCue` data and `PackParityTest` stay (harmless, and keep pack authoring disciplined).
- **The deterministic `RiskEngine`** (`core:reasoning`) → **KEPT, but INVISIBLE.** It never speaks to
  the user. Its sole job is the **anti-hallucination safety floor**: via `HybridAlert`, the AI may
  *raise* the danger level (novel scams) but can **never lower** the engine's floor. This stops a
  clever scammer from talking the AI into "you're safe." It is a seatbelt the user never sees.

**Net effect:** the user only ever experiences genuine, contextual AI. The engine silently guarantees
the AI can't be socially-engineered into a dangerous reassurance. This is a $0 safety property we keep.

---

## 4. Information architecture  (revised 2026-07-14 per owner — unified "Conversations")

Bottom **NavigationBar** (MD3), 3 destinations. Single-Activity + Compose.

```
┌────────────────────────────────────────────┐
│  (screen content)                           │
│                                             │
├────────────────────────────────────────────┤
│  🛡️ Home     💬 Conversations     🆘 Help    │   ← MD3 NavigationBar
└────────────────────────────────────────────┘
```

**Core model: everything is a Conversation.** A live call, an uploaded recording, and a blank
"New chat" are all the same object — a conversation you can open and keep talking to (ChatGPT-style).
This removes the old Chat-vs-History split entirely.

### 4.1 🛡️ Home  (landing — ACTIONS first, education lower & clean)
Top → bottom, thumb-zone aware. Actions lead; the news feed sits **below** them, never clumsy:
1. **Panic action (hero):** a large button — **"I'm on a scam call right now"** → an immediate
   *Do this now* sheet: **Don't pay. Never share an OTP. Hang up. Call 1930.** The single most
   life-saving control, first in the thumb zone.
2. **Action cards:** **"Help me on a call"** (live) · **"Ask VAARTA"** (opens a new multimodal chat)
   · **"Check a recording"** (upload/record). These are what the user reaches for.
3. **Trending scams (lower):** AI-generated, web-grounded cards of *current* India scams, in their
   own clearly-separated lower section. Tap → article summary (§6.1).

### 4.2 💬 Conversations  (the ChatGPT-style list — replaces History)
- A **"＋ New chat"** button at top → opens a blank multimodal conversation (§6.5).
- Below: every saved conversation, newest first, grouped **This week / Earlier**. Each row shows a
  title (AI-derived), a type glyph (📞 live · 🎧 recording · 💬 chat), a risk ring when it came from a
  call, and time. Tap → opens that **Conversation screen** (§4.4).
- Retention controls kept (Keep / 7d / 30d, per-row + delete-all) — ADR-0004 consent preserved.
- **Live calls auto-save here** the moment a live session ends (see §6.5) — the user no longer has to
  remember to tap "save"; they can still delete any conversation.

### 4.3 🆘 Help  (the social-good pillar, always reachable)
- **How & where to complain:** 1930 helpline (one-tap dial), cybercrime.gov.in (open safely), the
  in-app complaint draft (reuses `core:complaint` + `PdfExporter`).
- **"What to do if you were scammed"** plain steps. **"Warn my family"** share.

### 4.4 Conversation screen  (the reusable heart — one screen for calls, recordings, and chats)
Opened from any Conversations row, a fresh analysis, or "New chat" / "Ask VAARTA". Top → bottom:
1. **Context header (only when it came from a call/recording):** a **verdict card** (plain-language
   "what this call was", risk ring, scam-type, cited sources) + a **clean transcript** rendered as a
   readable conversation (**Caller** vs **You**, timestamps, scam-flagged lines highlighted — not raw
   text), with a **Download** action (transcript + verdict as PDF/TXT via `PdfExporter`). A blank
   chat has no context header.
2. **Chat thread + multimodal composer** — a full ChatGPT/Gemini-style assistant (§6.5). For a
   call/recording it's grounded in that call's context; for a blank chat it's a general scam-help
   assistant. Everything the user says/attaches persists to this conversation.

---

## 5. Design system — "Calm Guardian" expressed in Material 3 (Compose)

One aesthetic, committed. MD3 provides the mechanics; Calm Guardian is the personality within it.

- **Framework:** `androidx.compose.material3`, `MaterialTheme(colorScheme, typography, shapes)`.
  Static light/dark schemes (NOT dynamic wallpaper color — a safety tool needs *stable, trustworthy*
  color semantics, not the user's wallpaper). Build on existing `ui/theme/*` + `RiskRing`.
- **Color roles (semantic tokens only — no raw hex in components):**
  - `primary` = calm trustworthy blue/teal (guardian, not alarming).
  - **Risk is `error`/`tertiary` semantics, reserved** — strong red only for real danger, never décor
    (mobile-UI 60/30/10; "save strong colors for meaningful moments").
  - Surfaces via tonal `surface-container-*`, depth via tonal color not shadows.
- **Typography:** MD3 type scale (Roboto default is correct on Android). Max ~4 sizes / 2 weights.
  Body defaults **large** for legibility. Numbers/risk use emphasized/monospace weight.
- **Shape:** cards `medium` (12dp), buttons `full`, sheets/dialogs `extra-large` (28dp).
- **Spacing:** strict 8dp grid; card padding 24dp; related-closer/unrelated-farther rhythm.
- **Motion:** MD3 emphasized easing for the overlay expand-from-icon and screen transitions.
- **Accessibility (default, not a mode):** ≥48dp touch targets, WCAG AA contrast, TalkBack semantics
  on every actionable element, large type. This *is* our "elder friendliness."
- **Peak-end:** the "peak" is the moment a scam is caught + the user is told exactly what to do; the
  "end" is a calm, affirming verdict + one clear next step (complain / warn family).

---

## 6. Feature specifications

### 6.1 Home education feed — AI-generated, web-grounded (owner-chosen)
- **New:** `GeminiClient.awarenessFeed()` — a web-grounded call returning current India scam explainer
  cards. Because grounding can't combine with structured output on the free 2.5 model (known
  constraint, ADR-0003), this uses the **same two-step pattern already proven**: grounded prose call →
  parse to `List<AwarenessCard>{title, oneLine, scamType, sources[]}` via a new tolerant parser in
  `core:reasoning` (unit-tested, fails closed to bundled seed on any malformed output).
- **Caching:** last good feed cached locally (encrypted store or app files) with a fetched-date;
  refreshed when online + stale. **Offline / failure → bundled seed** (~10 curated real scam cards
  shipped in assets) so the screen is never empty.
- **Tap a card → article summary screen:** a clean banner (title + scam-type + source name), then a
  **plain-language AI summary of the *real* source article** — "what it is / how to spot it / what to
  do". VAARTA reads the cited source and summarizes it; it does **not** dump the raw page. The source
  is named and **tappable** (opens in browser on user action, safely credited) so the user can see the
  genuine article — this attribution is how we answer "are we showing the *right* article?": every
  card is bound to a real cited URL, shown, not fabricated. `GeminiClient.summarizeArticle(url, title)`
  is web-grounded + safety-filtered; fails closed to the card's one-line if summarization errors.
- **"Ask about this"** on the summary → opens a **new Conversation** (§6.5) seeded with the article
  context. The summary itself is ephemeral (regenerated cheaply on reopen); only a conversation the
  user actually engages in is saved — no clutter, nothing important lost.

### 6.2 Live call help + overlay rebuild (phone-tested)
Underlying pipeline is unchanged and proven: `AudioCapture` (16kHz PCM16) → `GeminiLiveClient` (WS) →
caller transcript → `RiskEngine` (score) + AI coaching → `SuggestionSafetyFilter` → shared thread,
all inside `CopilotSession` hosted by `OverlayService` (FGS type=microphone). **What changes is the
window UX** in `OverlayService`:

- **Corner icon**, draggable anywhere (Compose `pointerInput`/`detectDragGestures`, the proven
  approach; snaps to nearest edge on release).
- **Tap → expand *from* the icon**: MD3 emphasized-motion scale/position animation from the icon's
  location into a compact panel (not a fixed bottom sheet).
- **Panel is draggable AND resizable**: a corner resize handle updates the `WindowManager.LayoutParams`
  width/height within min/max bounds; position persists across expand/collapse.
- **Never covers native call controls:** default spawn position is the **top** region; because the
  panel is fully movable + resizable + collapsible, the user can always clear the hang-up/mute/keypad
  bar. Collapse (▾) returns to the icon.
- **Auto-show on call — HONEST CONSTRAINT:** Android 15 restricts auto-launching an overlay the moment
  a call rings. MVP default = user taps to start (fast). We will *attempt* best-effort auto-show via a
  phone-state listener and **verify on the owner's physical phone** whether the device permits it; it
  is **not promised** until proven on hardware (R-05 OEM variance).

### 6.3 Recording analysis (kept, re-homed)
`AudioScamAnalyzer` + `GeminiClient.analyzeAudio` unchanged. Entry moves to Home "Check a recording"
→ SAF `GetContent` picker (no storage permission) → result renders in the **Understand-this-call**
screen (§4.4), identical to a history item. One screen, two entry points.

### 6.4 Understand-this-call screen (verdict + transcript + download)
- Reuses `ChatView` thread composables for the transcript render; adds a **verdict header**, a
  **flagged-line highlight** on caller turns the engine matched, and a **Download** action.
- Transcript source: for history, from `core:data`; for a fresh analysis, from the in-memory result
  (RAM-only until the user taps Save — ADR-0004 consent kept).

### 6.5 Multimodal AI chat (the core new intelligence)
A full ChatGPT/Gemini-style assistant. It works **standalone** (a blank "New chat" / "Ask VAARTA" — a
general scam-help assistant) **or scoped to a call/recording** (grounded in that conversation's
verdict + transcript). Same screen, same composer; the only difference is whether a context header is
present.

- **New:** `GeminiClient.chat(context: CallContext?, history, userMessage, attachments)` — `context`
  is null for a blank chat, or the call's transcript + verdict when scoped. Sends prior turns + the new
  message (and inline image/audio attachments) to `gemini-2.5-flash`; web-grounded for scam/complaint
  questions; **every reply passes `SuggestionSafetyFilter`** (fails closed to a safe deterministic
  message). Answers **in the user's language** (EN / HI / Hinglish).
- **Composer (all $0-feasible):**
  - **Text** — freeform.
  - **🎤 Voice input** — Android `SpeechRecognizer` → text (free, no API cost).
  - **🖼️ Attach image** — SAF picker; screenshot of a suspicious SMS/WhatsApp/email → Gemini inline
    image understanding (free tier).
  - **🎧 Attach audio** — a clip → reuses the proven inline-audio path (≤14 MB).
  - **Stretch:** general document/PDF attach (deferred; images+audio cover real evidence).
- **State & persistence:** a `ConversationViewModel` owns the turn list + streaming/pending/error
  states. **Every conversation is a `core:data` row** (schema: a `Conversation` with a `kind` =
  live / recording / chat, optional call-context, and its turns). **Live calls auto-persist** when the
  session ends (the copilot writes turns live, crash-safe, and finalizes on stop — ADR-0004 consent is
  satisfied by the in-app "VAARTA is protecting this call" state, and the user can delete any
  conversation). A blank chat is created on first send. Recordings persist on analysis completion.
  This is what makes the Conversations list (§4.2) a single unified store.

### 6.6 Complaint + education weave
- From any HIGH-risk verdict or chat: **"File a complaint"** → existing `core:complaint` draft +
  `PdfExporter`; **"Call 1930"** one-tap dial; **"Open cybercrime.gov.in"** (user-confirmed browser
  open). **"Warn my family"** share on verdicts and awareness cards.

---

## 7. New / changed components (map)

| Area | Change |
|---|---|
| `ui/ManualModeGrid.kt` | **Delete.** |
| `MainActivity.kt` | Replace ad-hoc screen switching with a `NavHost` (Home/History/Help + detail/chat/overlay-launch routes) + MD3 `NavigationBar`. Remove Manual Mode wiring. |
| `SessionViewModel.kt` | Drop Manual Mode chip state. Keep live/session logic (already extracted to `CopilotSession`). |
| `ai/GeminiClient.kt` | **Add** `awarenessFeed()` and `chat(...)` (multimodal, grounded, safety-filtered). Reuse existing request scaffolding + fail-closed. |
| `core:reasoning` | **Add** `AwarenessCard` model + tolerant parser (unit-tested); reuse `SuggestionSafetyFilter`, `HybridAlert`. |
| `core:data` | **Add** chat-turn persistence linked to a saved session (Room migration; SQLCipher unchanged). |
| `OverlayService.kt` | Rebuild window UX: corner icon, expand-from-icon animation, drag + **resize** via `LayoutParams`, top-default, collapse. |
| New: `ui/home/*`, `ui/feed/*`, `ui/chat/*`, `ui/help/*` | Compose screens per §4, MD3 + Calm Guardian. |
| New: `voice/SpeechInput.kt` | `SpeechRecognizer` wrapper → text, permission-guarded, fails gracefully. |
| Assets | Bundled awareness-seed JSON (offline fallback). |

---

## 8. Error handling, privacy, $0 discipline

- **Fail closed, always:** any AI/network/parse error → a safe deterministic message, never a fake
  "you're safe." The engine floor and `SuggestionSafetyFilter` remain the backstops.
- **Consent (ADR-0004 kept):** nothing persists without explicit Save; audio clips are never stored,
  only transcripts/verdicts/chat the user chooses to keep; chat attachments are RAM-only unless saved.
- **Web grounding:** only scam/complaint queries; content-free logging only (no PII, no key).
- **$0:** free Gemini tier + web grounding + on-device `SpeechRecognizer`; no new paid surface. API
  key stays in git-ignored `secrets.properties` (embedded in debug APK — flagged for rotation).
- **Untrusted content:** attached images/audio and web results are *data, not instructions* — the chat
  prompt frames them as untrusted (extends existing `AudioAnalyzePrompt` framing); safety filter still
  gates the output.

---

## 9. Testing strategy

- **Pure JVM unit tests (`core:*`):** `AwarenessCard` parser (well-formed / malformed / empty →
  fail-closed to seed); chat context assembly + language selection; any new safety-filter surface for
  chat replies. Keep the existing 85 green.
- **On-device (emulator) verify:** nav + Home + feed render, feed offline fallback, Understand-this-call
  (verdict/transcript/download), chat text + image + voice, history reorg + persistence round-trip.
- **On-device (physical phone) verify — required for these:** overlay expand-from-icon + drag + resize
  + non-blocking over a real dialer; live call help end-to-end on speakerphone (caller speech →
  transcription → score → coaching); best-effort auto-show.
- **Evidence rule (unchanged):** count tests from fresh XML; "I wrote it" ≠ "it works"; screenshot/
  dumpsys evidence for device claims.

---

## 10. Build order (phases — each independently shippable & verifiable)

1. ~~**Nav + Home shell + Help + delete Manual Mode.**~~ **DONE (2026-07-14, emulator-verified.)**
   3-tab MD3 shell, clean Home (panic + action cards + feed placeholder), Help tab. The Phase-1
   History tab is the base that Phase 2 evolves into "Conversations".
2. **Conversation store + Conversations list.** Evolve `core:data` to a unified `Conversation`
   (kind = live/recording/chat, optional call-context, turns) with a guarded Room migration; rebuild
   the History tab into **Conversations** (＋ New chat, unified grouped list). Reorder Home to add the
   **"Ask VAARTA"** action; move the news feed lower. *(Emulator-verifiable — persistence round-trip.)*
3. **Multimodal chat on the Conversation screen (the heart).** `GeminiClient.chat(context?)` +
   composer (text → 🎤 voice → 🖼️ image → 🎧 audio) + the call/recording **context header** (verdict +
   clean transcript + Download). "Ask VAARTA" and every Conversations row open it. *(Emulator — audio
   attach verifiable; live-audio edge on phone.)*
4. **Live + recording → auto-save as conversations.** Wire `CopilotSession` (live) and
   `AudioScamAnalyzer` (recording) to persist as conversations and populate the context header from
   real calls. *(Emulator; live speech on phone.)*
5. **Education feed + article summary.** `awarenessFeed()` + `summarizeArticle()` + clean banner +
   seed fallback + "Ask about this" → new conversation. *(Emulator.)*
6. **Overlay rebuild.** Corner icon → expand-from-icon → drag + resize + never blocks call controls.
   *(Physical-phone-verified.)*
7. **Live-call hardening on hardware** (+ best-effort auto-show) **& Help deepening.**
   *(Physical-phone-verified.)*

Deck/Demo video remain deferred to the very end (do once), per PROJECT_STATUS §5.

---

## 11. Risks & open constraints

- **R-05 OEM overlay/FGS variance** — overlay resize/drag/non-blocking + auto-show only truly known on
  real hardware. Mitigation: phone test (owner has a device); ship tap-to-start as the reliable path.
- **Live ASR quality on speakerphone** (R-01) — the one thing PC testing can't prove; phone test gates it.
- **Grounding hallucination/manipulation** — mitigated by cited-source requirement + `HybridAlert`
  never-lower + fail-closed (existing design).
- **Room migration** for chat turns — additive, guarded migration; verify round-trip survives restart.

---

## 12. Out of scope (explicit)

Counterfeit-currency / fraud-graph / geospatial pillars · Play publishing · DOCX · dynamic wallpaper
color · full Elder Mode toggle · general document/PDF chat upload (stretch) · any paid API/backend.
