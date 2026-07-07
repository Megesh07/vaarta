# VAARTA

Real-time Android protection against **digital-arrest scams** for Indian citizens: it listens live
on speakerphone, scores the call as it escalates, coaches the user on what to say back — backed by
a specialized live AI, never just a canned script — and auto-drafts the cyber-crime complaint.

> 📋 **[PROJECT_STATUS.md](PROJECT_STATUS.md) — read this first.** It's the single source of
> truth for what's built, what's not, exact toolchain gotchas, and the prioritized next-steps
> backlog. Anyone (human or AI agent) picking up this project should start there, not here.

> **Build intent:** hackathon / portfolio **MVP** (not production-hardened), strictly **$0** to
> build and run. Scope is locked in [docs/decisions/0001-mvp-scope-lock.md](docs/decisions/0001-mvp-scope-lock.md).
> The live-AI layer is documented in [docs/decisions/0002-live-ai-voice-assist.md](docs/decisions/0002-live-ai-voice-assist.md).
> Full design docs live in [`docs/`](docs/README.md).

## What works today

| Module | What | Status |
|---|---|---|
| `core:common` | Event model, intel-pack model, text normalization | ✅ |
| `core:reasoning` | **Tier-0 deterministic engine**: signal matching, 5-stage scam grammar, scoring, hysteresis | ✅ |
| `core:reasoning` | Verification-question selector, live-AI suggestion schema + safety filter | ✅ |
| `core:complaint` | Slot-based complaint builder + JSON/TXT/PDF export | ✅ |
| `app` | Compose UI — risk card, Manual Mode, verification questions, demo call, complaint share/PDF | ✅ |
| `app` | **Live AI voice assist** (ADR-0002): mic capture → Gemini Live streaming → safety-filtered suggested replies | ✅ PC-verified |
| `tools:demo` | Headless CLI rig + Gemini Live protocol probe | ✅ runs |

**24 automated unit tests, 0 failures** (pure JVM, no device needed) plus manual end-to-end
verification on an Android emulator, including a live mic → Gemini Live → suggestion round trip.
Exact breakdown and evidence: [PROJECT_STATUS.md §4](PROJECT_STATUS.md).

The stage-grammar thesis is proven in code: a scam call escalates to **SCAM PATTERN**, while a
genuine police call ("FIR registered") correctly stays at **CAUTION**. On top of that deterministic
core, an opt-in specialized Gemini Live layer listens to the actual call audio and suggests a safe,
context-aware reply in real time — it never sets the risk score, only advises (ADR-0002).

## Getting started

**Prerequisites:** JDK 17+, Android SDK (platform 35, build-tools 35+) with `ANDROID_HOME` /
`local.properties` pointing at it. No Android Studio required — this was built and tested entirely
from the CLI.

```bash
git clone https://github.com/Megesh07/vaarta.git
cd vaarta

# Run the engine + complaint unit tests (pure JVM, no device/SDK needed):
./gradlew test

# Run the text-mode demo (live risk trace + generated complaint, CLI):
./gradlew :tools:demo:run -q

# Build the Android debug APK:
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (sideload onto an Android 10+ device or emulator)
```

### Enabling the live-AI layer (optional)

The app builds and fully protects with **zero external services** — Manual Mode, the deterministic
risk engine, and complaint export all work offline with no API key. The live-AI suggestion layer is
opt-in and needs a free key:

1. Get a free key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey).
2. Copy [`secrets.properties.example`](secrets.properties.example) to `secrets.properties` (repo
   root) and paste your key in. This file is git-ignored — it will never be committed.
3. Rebuild — `./gradlew :app:assembleDebug`. The AI opt-in toggle appears in-app once a key is
   compiled in; leave it blank and the app runs exactly as before, just without that toggle.

### Testing live audio on a PC (no phone needed for a first pass)

The Android emulator can route your PC's real microphone into the app via
`emulator -avd <name> -allow-host-audio`, which is how the live-audio layer was verified during
development (see [PROJECT_STATUS.md](PROJECT_STATUS.md) for the full method and its one known
limitation: PC acoustic loopback degrades speech-transcription quality more than a real phone call
does — a real-device speakerphone test is the next step to fully validate that path).

## Contributing

See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for setup, branch/commit conventions, and the
hard rules (no raw-audio persistence, no analytics/trackers, no accusatory or legal-advice phrasing
in scam-facing strings). [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) applies to all participation.

## License

MIT — see [LICENSE](LICENSE).

## Roadmap

1. ✅ Tier-0 deterministic engine + complaint generator (headless, tested)
2. ✅ Android app: Manual Mode, demo screen, verification questions, complaint export (JSON/TXT/PDF)
3. ✅ Live AI voice assist (Gemini Live): mic capture, streaming, safety-filtered suggestions — proven on PC
4. ⬜ Real-device speakerphone test — validate live transcription quality on an actual call
5. ⬜ Floating overlay bubble + `CallScreeningService` — real in-call detection, not just manual/demo
6. ⬜ Hardening pass: prompt-injection red-team, latency budget, fallback drills

Deliberately **out of MVP scope**: on-device LLM, Play Store publishing, DOCX export, Elder Mode,
languages beyond EN/HI/Hinglish, and the challenge's counterfeit-currency / fraud-graph /
geospatial pillars — VAARTA is one deep module of that broader "AI for Digital Public Safety" space.
