# VAARTA

Real-time Android protection against **digital-arrest and other phone scams** for Indian citizens:
it listens live on speakerphone, scores the call as it escalates using a deterministic rule engine
(never an LLM — the score can't be hallucinated), coaches the user on what to say back with an
opt-in AI copilot, and auto-drafts the cyber-crime complaint.

> 📋 **[PROJECT_STATUS.md](PROJECT_STATUS.md) — read this first.** It's the single source of
> truth for what's built, what's not, exact toolchain gotchas, and the prioritized next-steps
> backlog. Anyone (human or AI agent) picking up this project should start there, not here. If
> this README and PROJECT_STATUS.md ever disagree, PROJECT_STATUS.md wins.

> **Build intent:** hackathon / portfolio **MVP** (not production-hardened), strictly **$0** to
> build and run. Scope is locked in [docs/decisions/0001-mvp-scope-lock.md](docs/decisions/0001-mvp-scope-lock.md).
> Full design docs live in [`docs/`](docs/README.md). The current work-in-progress plan is
> [docs/superpowers/plans/2026-07-19-portfolio-polish-to-10.md](docs/superpowers/plans/2026-07-19-portfolio-polish-to-10.md).

## What works today

- **Deterministic risk engine** (`core:reasoning`) — a 5-stage scam-progression grammar
  (HOOK → AUTHORITY → ISOLATION → ESCALATION → EXTRACTION) scores a live transcript 0–100.
  A real scam call escalates to **SCAM_PATTERN**; a genuine police callback ("your FIR is
  registered") correctly stays low — that asymmetry is the engine's core discriminator, and it's
  a zero-tolerance regression gate in the test suite.
- **~24-signal intel pack** (`core-scam-v1.json`, EN/HI/Hinglish) covering digital-arrest,
  courier/parcel, SIM-block, bank/RBI laundering accusations, investment/job/loan/lottery/
  electricity/UPI-refund lures, courier-COD OTP scams, bank KYC-expiry phishing, and
  family-emergency ("beta, it's me") impersonation.
- **Opt-in AI copilot** (Gemini) layered on top — live suggested replies, a scam-link checker
  (URLhaus + Google Safe Browsing), and web-grounded scam-type identification. The AI can only
  ever **raise** displayed concern, never lower it or set the score itself; every network call
  fails closed to the deterministic-only behavior on any error.
- **Floating overlay** — a draggable bubble/panel over the dialer showing live risk + coaching,
  independent of which app is in the foreground.
- **Encrypted local history** (SQLCipher/Room) — saved conversations, complaint drafts, and the
  one guardian contact you can configure, all encrypted at rest, nothing leaves the device unless
  you explicitly share it.
- **Real guardian contact picker** — pick one contact via the system picker (no `READ_CONTACTS`
  permission needed at all — see `docs/decisions/`), and "Warn my family" sends directly to them.
- **Complaint auto-draft** — JSON/TXT/PDF export, ready to file at cybercrime.gov.in.
- **हिन्दी + Hinglish** UI, in-app language picker, LLM responses mirror whatever script the user
  types in.

**167 automated tests, 0 failures, 0 lint errors** (fresh count as of 2026-07-19, re-verified from
a clean rebuild — see [PROJECT_STATUS.md §4](PROJECT_STATUS.md) for the evidence trail and what's
still genuinely unverified).

## Getting started

**Prerequisites:** JDK 17, Android SDK (platform 35, build-tools 35+) with `local.properties`
pointing at it. No Android Studio required — this project builds and tests entirely from the CLI.

```bash
git clone https://github.com/Megesh07/vaarta.git
cd vaarta
git checkout vaarta-v2-ux   # the active development branch — main is behind

# Run every unit test (pure JVM + Room-instrumented where noted, no device needed for the JVM ones):
./gradlew test

# Build the Android debug APK:
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (sideload onto an Android 10+ device or emulator)
```

### Enabling the AI layer (optional)

The app builds and protects with **zero external services** — the deterministic risk engine,
complaint export, and encrypted history all work fully offline with no API key. The AI copilot and
scam-link checker are opt-in and need free keys:

1. Copy [`secrets.properties.example`](secrets.properties.example) to `secrets.properties` (repo
   root, git-ignored — never committed) and fill in what you want to enable:
   - `GEMINI_API_KEY` — free at [aistudio.google.com/apikey](https://aistudio.google.com/apikey).
     Enables the AI copilot (live suggestions, chat, scam-type identification).
   - `SAFE_BROWSING_API_KEY` — free non-commercial-tier key at
     [developers.google.com/safe-browsing/v4/get-started](https://developers.google.com/safe-browsing/v4/get-started).
     Enables the scam-link checker's malicious-URL flagging. (URLhaus, the checker's other lookup,
     currently needs an Auth-Key this project hasn't wired up yet — tracked as `task_e2bb31b0` in
     PROJECT_STATUS.md — so Safe Browsing is the only one that can flag anything right now.)
2. Rebuild — `./gradlew :app:assembleDebug`. Leave a key blank and the corresponding feature is
   simply absent from the UI; nothing else changes.

## Testing on an emulator

```bash
# Boot the project's AVD (create one named vaarta_test, API 35, google_apis, x86_64, if you don't
# have it yet — Pixel 6 profile is what this project has been tested against):
emulator -avd vaarta_test -no-snapshot

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ai.vaarta.debug/ai.vaarta.MainActivity
```

From there: Home → "Get live help from VAARTA" → "Watch how it works" plays a scripted demo call
and should escalate to a red **SCAM_PATTERN** shield with an "Alert family" button. That's the
fastest way to confirm a build is healthy end-to-end.

## Testing on a real phone (wanted!)

**This is the single most valuable thing a collaborator with a physical Android phone can do for
this project right now.** Everything above has been verified on an emulator — but the app's
headline capability, *does the risk score actually move from a real caller's live speech through
your phone's speaker*, has never been proven on real hardware. PC/emulator acoustic testing
structurally can't answer this (speaker→air→laptop-mic loopback degrades speech quality too much
to judge fairly) — it needs an actual phone call, on an actual device.

**Steps (wireless `adb`, no cable needed):**

1. On your Android phone: **Settings → Developer options → Wireless debugging** (enable Developer
   options first if you haven't: Settings → About phone → tap "Build number" 7 times). Tap
   **"Pair device with pairing code"** — it shows an IP:port and a 6-digit code.
2. On your dev machine:
   ```bash
   adb pair <phone-ip>:<pairing-port>   # enter the 6-digit code when prompted
   adb connect <phone-ip>:<connect-port>  # the main IP:port shown on the Wireless debugging screen
   ```
3. Build and install:
   ```bash
   ./gradlew :app:assembleDebug
   adb devices   # confirm your phone shows up
   adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. On the phone: open VAARTA, grant microphone + "draw over other apps" permissions, start Live
   listening (or the floating overlay).
5. Place a real call on **speakerphone** — either a genuine incoming call, or have a second phone
   read a scam script aloud near your phone's speaker. Watch whether the risk ring actually moves
   as the caller talks.
6. **Report back either outcome** (open an issue, or tell whoever pointed you here) — both are
   useful: if it works, that's the flagship capability finally proven; if the transcription comes
   through garbled, that confirms a real, specific limitation worth documenting (tracked in
   PROJECT_STATUS.md as risk R-01) rather than an open question.

## Contributing

See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for setup, branch/commit conventions, and the
hard rules (no raw-audio persistence beyond the session, no analytics/trackers, no accusatory or
legal-advice phrasing in scam-facing strings). [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) applies to
all participation. Before touching a LOCKED architectural decision, read
[docs/IMPLEMENTATION_GUARDRAILS.md](docs/IMPLEMENTATION_GUARDRAILS.md) — binding for humans and AI
agents alike.

## License

MIT — see [LICENSE](LICENSE).

## Roadmap

1. ✅ Deterministic risk engine + complaint generator + intel-pack breadth (24 signals, 10 scam
   families)
2. ✅ AI copilot: live suggestions, chat, web-grounded scam-ID, safety-filtered, raise-only
3. ✅ Encrypted local history (SQLCipher), floating overlay, real guardian contact picker
4. ✅ हिन्दी + Hinglish UI and LLM language mirroring
5. ✅ Scam-link checker (URLhaus + Safe Browsing) — URLhaus needs an Auth-Key still (open item)
6. ⬜ **Real-device speakerphone test — validate live transcription quality on an actual call.**
   See [Testing on a real phone](#testing-on-a-real-phone-wanted) above — this is the top
   community-testable item.
7. ⬜ Native-speaker review of the Hindi/Hinglish strings (machine-drafted, checklist in
   PROJECT_STATUS.md §8)
8. ⬜ `CallScreeningService` real in-call auto-detection — deliberately deferred (Android 15
   foreground-service-start restrictions + Play policy; the app currently starts via a manual tap,
   not automatically on an incoming call)
9. ⬜ Voice-anomaly (deepfake) detection — spike-gated, not yet attempted; see the spec's
   Increment J for why this is measured before it's promised

Deliberately **out of MVP scope**: on-device LLM, Play Store publishing, DOCX export, Elder Mode,
languages beyond EN/HI/Hinglish, and the challenge's counterfeit-currency / fraud-graph /
geospatial pillars — VAARTA is one deep module of that broader "AI for Digital Public Safety" space.

Contributions of all kinds are welcome — see [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) to get
started, or just [test on a real phone](#testing-on-a-real-phone-wanted) and report back.
