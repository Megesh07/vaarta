# VAARTA

Real-time, on-device Android protection against **digital-arrest scams** for Indian citizens:
it scores a suspicious call live, coaches verification, and auto-drafts the cyber-crime complaint.

> 📋 **[PROJECT_STATUS.md](PROJECT_STATUS.md) — read this first.** It's the single source of
> truth for what's built, what's not, exact toolchain paths, and the prioritized next-steps
> backlog. Anyone (human or AI agent) picking up this project should start there, not here.

> **Build intent:** hackathon / portfolio **MVP** (not production), strictly **$0** to build.
> Scope is locked in [docs/decisions/0001-mvp-scope-lock.md](docs/decisions/0001-mvp-scope-lock.md).
> Full design lives in [`docs/`](docs/README.md) (foundation frozen).

## What works today (built + tested, `$0`, no device needed)

| Module | What | Status |
|---|---|---|
| `core:common` | Event model, intel-pack model, text normalization | ✅ |
| `core:reasoning` | **Tier-0 engine**: signal matching, stage grammar, scoring, hysteresis | ✅ 8 tests |
| `core:complaint` | Slot-based complaint builder + JSON/TXT export | ✅ 4 tests |
| `intel-packs` (bundled) | SC-01..SC-05 signals, EN / HI / Hinglish | ✅ |
| `tools:demo` | Text-mode rig — live risk trace + generated complaint | ✅ runs |
| `app` | Compose UI: risk card, Manual Mode, family alert, complaint export | 🔨 building |

The stage-grammar thesis is proven in code: a scam call escalates to **SCAM PATTERN**, while a
genuine police call ("FIR registered") correctly stays at **CAUTION**.

## Build & run

Requires JDK 17 + Android SDK (platform 35, build-tools 35). This repo was bootstrapped with a
standalone Gradle at `C:\Users\Meges\tools\gradle-8.11.1`.

```bash
# Run the engine + complaint tests (pure JVM, no device):
gradle test

# Run the text-mode demo (live risk trace + complaint):
gradle :tools:demo:run -q

# Build the Android app (debug APK):
gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk  (sideload onto an Android 10+ phone)
```

## Roadmap (MVP)

1. ✅ Tier-0 engine + complaint generator (headless, tested)
2. 🔨 Android app: Manual Mode + rig-mode demo screen + complaint export
3. ⬜ On-device ASR spike (sherpa-onnx, HI/EN) — the "listen live" stretch
4. ⬜ Overlay bubble + `CallScreeningService` integration (real in-call)

Deliberately **out of MVP scope**: cloud LLM polish, Play publishing, DOCX, Elder Mode,
P1/P2 languages, and the challenge's counterfeit-currency / fraud-graph / geospatial pillars
(VAARTA is one deep module of that broader platform).
