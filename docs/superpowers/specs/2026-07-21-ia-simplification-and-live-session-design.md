# IA simplification + live-session / floating-window — design

**Date:** 2026-07-21
**Status:** Approved in principle (design), pending spec review
**Areas:** Home IA, Ask VAARTA (recording merge), panic sheet, live-call session + floating overlay

## 1. Problem

The app has grown several overlapping surfaces that feel "loaded" and inconsistent, and the live-call
experience isn't a single coherent session:

- **Redundant doors:** "Check a recording" is a separate tile + screen, but Ask VAARTA already accepts
  an audio attachment — two doors to one job.
- **Canned panic:** "I'm on a scam call" shows the same static template every time; no personalization.
- **Opt-in intelligence:** the live "AI coach" is a toggle, off by default. Live protection should just
  BE intelligent when online, and honestly say "offline" when there's no internet — no switch.
- **Split live session:** the in-app live page and the floating overlay run as **separate**
  `CopilotSession` instances, so "Float" starts a fresh session instead of continuing the one on
  screen. Minimize/restore is not the same call.

Goal: an Apple-grade IA — fewer, purposeful surfaces, one design language, nothing repeated — and a
single continuous live session that flows **live page ⇄ floating bubble ⇄ History ⇄ ask-about-it**.

## 2. Non-goals

- No new AI backends; reuse `coach`/`classify`/`chat`/`analyzeAudio` as they are.
- No change to the deterministic on-device engine or the safety filters.
- No redesign of the Trending feed (just shipped) or the History/ask-about-call path (already works).
- Not adding call-recording of the *other party* beyond what the mic already captures on speaker.

## 3. Part A — IA simplification

### A1. Merge "Check a recording" into Ask VAARTA
- **Remove** the "Check a recording" Home tile and the standalone `AnalyzeScreen` from navigation.
- In Ask VAARTA, attaching an **audio** file runs the real structured analyzer (`analyzeAudio` →
  transcript + diarization + scam verdict), rendered inline as a **result card** in the thread; the
  analysis transcript becomes the conversation context so follow-up questions are grounded (same
  contract as opening a saved call). Screenshots/images continue through `chat()`.
- One AI surface: ask · attach screenshot · attach recording.

### A2. Personalize the panic sheet ("I'm on a scam call")
- On open, show the **instant, reliable safety steps immediately** (never a blank/slow panic screen).
- In parallel, fire an AI call that personalizes the heading + steps to the real situation, using
  whatever context exists: an **active live call's detected scam type/verdict**, else the **most
  recent analyzed recording/chat**, else none. When it returns, the steps update (labelled so the
  base vs. tailored distinction is honest). If AI is slow/fails/offline, the instant steps remain.
- The instant steps are the safety net; the AI is the personalization layer — never a dependency.

### A3. Home cleanup
- Home = purposeful blocks only, one card grammar, red reserved for the emergency:
  1. **"I'm on a scam call"** (emergency, instant + personalized)
  2. **Live protection** (the hero proactive action)
  3. **Ask VAARTA** (the one AI surface; absorbs recording)
  4. **Trending scams** feed
- No "Check a recording" tile. Consistent spacing/indigo chrome throughout.

## 4. Part B — Live intelligence + floating window

### B1. Always-on intelligence, connectivity-gated (remove the toggle)
- Delete the "AI live coach" opt-in toggle. Live protection always runs the AI coach when online.
- Add a **connectivity signal** (`ConnectivityManager` network callback). State shown in both the
  in-app live page and the overlay panel:
  - **Online:** "AI active" (coach/classify calls run; they already fail-closed per turn).
  - **Offline:** "Offline — on-device only" (the deterministic engine still scores every turn; AI
    calls are skipped, no error spam). Auto-resumes AI when connectivity returns — no user action.
- Consent: sending caller audio/words to Google is disclosed once (first live start) as an
  informational sheet, not a persistent switch. (Privacy copy retained; friction removed.)

### B2. One shared live session (the core refactor) — `LiveSessionHolder`
The in-app page and the overlay must render the SAME `CopilotSession`. Introduce a process-scoped
owner so the session survives the Activity being backgrounded AND the Service's lifecycle:

```
object LiveSessionHolder {
    // App-scoped scope: outlives both Activity and Service; cancelled only when the call ends.
    private var scope: CoroutineScope? = null
    private var session: CopilotSession? = null
    val active: StateFlow<Boolean>                 // observers know when a call is live
    fun getOrCreate(appContext): CopilotSession     // idempotent — one instance per call
    fun end()                                       // close() + cancel scope + clear
}
```

- **`SessionViewModel`** stops creating its own session; it renders `LiveSessionHolder`'s session.
- **`OverlayService`** stops creating its own session; it renders the same instance.
- **Mic ownership:** `CopilotSession.startLiveListening()` is already guarded (`if (liveClient != null)
  return`), so whichever surface starts first owns the mic and the other just observes the same
  StateFlows — no double capture.
- **Background mic continuity (Android FGS rule):** mic capture in the background requires a
  foreground service. So the moment the call is **minimized/floated**, `OverlayService`
  (FGS type=microphone) must be running as the mic host; it keeps the shared session's capture alive
  while the app is backgrounded. When restored to the foreground Activity, the service may keep
  running (simplest, guarantees continuity) with its bubble hidden.
- **End of call:** `LiveSessionHolder.end()` tears down the one session + stops the service; the call
  is offered to History exactly as today (`Save this call`), then reachable via the existing
  ask-about-call path.

### B3. Minimize ⇄ restore UX
- **Minimize (from live page):** "Float over your call" → ensure `OverlayService` is running as host,
  show the bubble, `moveTaskToBack`. Same session continues; the bubble's panel shows the live
  `ChatThread`/risk state from the shared session.
- **Restore (tap bubble):** bring `MainActivity` to the front on the **live page** (an Intent with an
  extra), and hide the bubble while foregrounded. The live page renders the same shared session — the
  exact conversation, uninterrupted.
- **After the call:** it appears in History; opening it feeds its transcript/verdict as context so the
  user can ask the AI anything about that call (unchanged, already works).

## 5. Data flow (live lifecycle)

```
Start live (Home → Live page)
  → LiveSessionHolder.getOrCreate() → startLiveListening() (mic, Activity foreground)
  → connectivity: online ⇒ coach/classify run; offline ⇒ on-device only
Minimize / Float
  → OverlayService.start() (FGS mic host) + show bubble + moveTaskToBack
  → SAME session keeps scoring + coaching in the background
Tap bubble
  → MainActivity to front on Live page (extra) + hide bubble
  → live page renders SAME session
End call
  → LiveSessionHolder.end() + stop service → offer Save to History
Later
  → History → open call → ask AI about it (context-grounded chat)
```

## 6. Error handling (fail-closed throughout)

| Failure | Behavior |
|---|---|
| No internet during live | On-device engine still scores; AI calls skipped; "Offline" shown; auto-resume on reconnect. |
| Overlay permission denied | Live still works in-app (no float); a clear prompt explains the float feature needs it. |
| Service killed by OEM | Session ends cleanly via `LiveSessionHolder`; partial call still saveable to History. |
| `analyzeAudio` fails on a chat recording | Inline "couldn't analyze" + safe fallback message (as today). |
| Panic AI personalization slow/fails/offline | Instant static steps remain — never blocked. |

## 7. Testing

- **Pure/unit:** connectivity-state → coach-enabled mapping; panic context selection (live vs recent
  vs none); recording-attachment routing (audio → analyzer, image → chat).
- **Instrumented / on-device (Android framework types can't be JVM-unit-tested):** start live →
  minimize → confirm the bubble shows the SAME thread → restore → same session on the live page →
  end → appears in History → ask about it. Offline start shows on-device-only and recovers online.

## 8. Risks & sequencing

**Primary risk:** `LiveSessionHolder` session continuity + background-mic FGS ownership (B2) — it
touches the Activity/Service boundary and the exact live path needed for the demo. Sequenced last and
verified on a real device before relying on it.

**Recommended build order (each verifiable before the next):**
1. A3 Home cleanup + A1 remove "Check a recording" tile / route recording into Ask VAARTA.
2. B1 remove toggle + connectivity online/offline state (low risk, high clarity).
3. A2 panic personalization (instant base + AI refine).
4. B2 `LiveSessionHolder` shared session + B3 minimize/restore (the careful refactor).

Each step keeps the app shippable; if time runs out, the earlier steps still land a cleaner, more
intelligent app, and B2 is the one piece that can slip to a focused follow-up without breaking today.
```
