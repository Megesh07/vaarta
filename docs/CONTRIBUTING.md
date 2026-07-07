# Contributing to VAARTA

**Status:** FOUNDATION · v1.0 · 2026-07-05

VAARTA protects people from digital-arrest scams. Contributions are welcome — but this codebase has unusual rules because its failure modes are privacy violations and missed crimes-in-progress. Read this fully before your first PR.

---

## 1. Before anything else

1. Read [PRODUCT_PRD.md](PRODUCT_PRD.md) (what we build and won't build), [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) §1–2 (the platform constraints), and [CLAUDE_CODE_RULES.md](CLAUDE_CODE_RULES.md) (binding engineering rules — they apply to humans too).
2. The `/docs` set is the single source of truth. Settled decisions are not re-opened in PR comments; propose a docs amendment with evidence instead.

## 2. Development setup

**This section describes the setup for the MVP as actually built (ADR-0001/0002). Some of the
architecture below in this doc predates that scope lock and describes a later/fuller vision — where
they conflict, the MVP scope lock wins; propose a docs amendment instead of trusting the older text.**

- **Requirements:** JDK 17+, Android SDK (platform 35, build-tools 35+). Android Studio is optional
  — the whole project has been built and tested from the CLI via the Gradle wrapper. A physical
  device is only needed to validate the live-audio layer under real call conditions; the emulator
  (with `-allow-host-audio` to route your PC's mic in) covers everything else, including a first
  pass at live audio — see [PROJECT_STATUS.md](../PROJECT_STATUS.md) for the exact method.
- Clone → `./gradlew :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Live-AI layer (optional, opt-in): copy `secrets.properties.example` → `secrets.properties` at the
  repo root and add a free Gemini key ([README.md](../README.md) has the full walkthrough). Without
  it, the app builds and runs fully — the AI toggle just doesn't appear.
- Intel packs live as JSON at `core/reasoning/src/main/resources/packs/` (not YAML, and not a
  separate `intel-packs/` module — that's planned, not current). Every signal with a `manualCue`
  must have a matching entry in `SessionViewModel.cues` — enforced by `PackParityTest`.
- There is no CI configured yet and no two-phone rig / `tools/corpus/` — those are future-roadmap
  items, not required for a working local setup today. Run `./gradlew test` before every PR; that's
  the current bar.

## 3. Branch & PR workflow

- Default branch: `main`, protected; work on `feat/<area>-<short>`, `fix/<area>-<short>`, `docs/<short>`, `pack/<lang>-<short>`.
- Conventional, module-prefixed commit messages: `core:audio — debounce route poller (per AUDIO_PIPELINE.md §6)`.
- One logical change per PR; net diff < ~600 lines unless mechanical; packs and code in separate PRs.
- CI must be green: build, unit + Robolectric, custom privacy lints, module-dependency check, budgets (from M1), text-mode eval (for pack/engine changes).

### PR checklist (copy into description)
```
[ ] Spec doc for the touched module read; change conforms (link section) or docs amended in this PR
[ ] Tests added (bug fix ⇒ regression test)
[ ] No new permissions / endpoints / dependencies — OR the matching doc tables updated here
[ ] No transcript/PII in logs; no file writes in core:audio
[ ] Pack changes: eval score table pasted; native-speaker review done (language changes)
[ ] UNVERIFIED-ON-DEVICE markers added where device behavior is assumed
```

## 4. What we will not merge (hard vetoes)
- Anything using AccessibilityService for call capture, or any raw-audio persistence path.
- Analytics/ads/crash-reporting SDKs; trackers of any kind.
- Features that accuse specific callers of crimes, auto-contact authorities, or store full Aadhaar/PAN.
- Legal-advice phrasing in user-facing strings or packs.
- Scam "how-to" detail beyond what detection requires (SCAM_INTELLIGENCE.md §9).
- Weakened eval gates or privacy lints to make CI pass.

## 5. Non-code contributions (highly valued)
- **Language:** native-speaker review of strings/question banks; code-mixed pattern phrases from real observed scam language (paraphrase — never post someone's personal data or raw recordings).
- **Scam intelligence:** new pattern reports with public sourcing → `pack/` PRs or issues using the pattern-report template.
- **Corpus:** scripted dialogues (original writing only; no real victim audio, ever) + voicing sessions.
- **Field testing:** device-matrix testing on OEM skins, elderly-user usability sessions (with informed consent).

## 6. Reporting

- **Security vulnerabilities:** do NOT open a public issue — see `SECURITY.md` (root) for the private contact; 90-day coordinated disclosure.
- **Privacy concerns:** treated at the same severity as security; same channel.
- **Missed-scam / false-positive reports:** use the judgment-report issue template; include language + paraphrased dialogue, never identities.

## 7. Conduct & licensing
- Be kind; many contributors here have family members who were victims. No shaming of victims, ever, anywhere in the project.
- Contributor code of conduct: Contributor Covenant v2.1 — see [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) (added at repo bootstrap, 2026-07-07).
- License: **MIT** — see [LICENSE](../LICENSE) (finalized at repo bootstrap, 2026-07-07, superseding the open decision this section used to describe).

## 8. Getting help
Open a discussion issue with the `question` label; maintainers triage weekly (see IMPLEMENTATION_ROADMAP.md standing rules). Read the DEBUGGING_PLAYBOOK.md before filing device-specific bugs — your answer may already be there.
