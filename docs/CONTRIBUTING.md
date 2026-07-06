# Contributing to VAARTA

**Status:** FOUNDATION · v1.0 · 2026-07-05

VAARTA protects people from digital-arrest scams. Contributions are welcome — but this codebase has unusual rules because its failure modes are privacy violations and missed crimes-in-progress. Read this fully before your first PR.

---

## 1. Before anything else

1. Read [PRODUCT_PRD.md](PRODUCT_PRD.md) (what we build and won't build), [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) §1–2 (the platform constraints), and [CLAUDE_CODE_RULES.md](CLAUDE_CODE_RULES.md) (binding engineering rules — they apply to humans too).
2. The `/docs` set is the single source of truth. Settled decisions are not re-opened in PR comments; propose a docs amendment with evidence instead.

## 2. Development setup

- **Requirements:** JDK 17+, Android Studio (current stable), Android SDK 36, NDK (for ASR JNI), a physical device on Android 10+ (emulators cannot exercise telephony/audio paths meaningfully).
- Clone → `./gradlew :app:assembleDebug` → install the `.debug`-suffixed app (installs alongside any release build).
- Model packs for local dev: `./gradlew downloadDevModels` (fetches the current benchmarked ASR models per `docs/decisions/asr-*.md`; not checked into git).
- Intel packs: edit YAML in `intel-packs/`, then `./gradlew :intel-packs:validate compilePacks` (runs schema, banned-phrase lint, and text-mode eval).
- Two-phone rig (for audio/ASR/eval work): see TESTING_STRATEGY.md §2 and `tools/corpus/README` (created at implementation).

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
- Contributor code of conduct: Contributor Covenant v2.1 (file added at repo bootstrap).
- License: to be finalized at repo bootstrap (M0) — decision record required weighing open-source trust benefits vs. clone/abuse risk (RISK_REGISTER.md R-11). Contributions before that are accepted under an interim CLA noted in the PR template.

## 8. Getting help
Open a discussion issue with the `question` label; maintainers triage weekly (see IMPLEMENTATION_ROADMAP.md standing rules). Read the DEBUGGING_PLAYBOOK.md before filing device-specific bugs — your answer may already be there.
