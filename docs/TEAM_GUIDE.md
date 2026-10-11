# Vaarta team guide

This guide describes the repository as it exists today. Start here before changing code; the older
design notes and implementation plans in local working folders may describe features or modules
that were never built.

## Build and run checks

Requirements: JDK 17 and Android SDK platform 35 with build tools 35. Create an untracked
`local.properties` in the repository root and set `sdk.dir` to the local SDK path. Android Studio is
optional.

```sh
./gradlew test
./gradlew lintDebug
./gradlew :app:assembleDebug
```

The Windows wrapper is `gradlew.bat`. `test` runs JVM tests; it does not run `androidTest` tests,
which require an emulator or device. The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

## Optional API credentials

Copy `secrets.properties.example` to the untracked `secrets.properties` and add only development
credentials needed for the surface being worked on. The app reads credentials at build time and
embeds them in the APK. Anyone who obtains an APK can extract an embedded key. Use a restricted,
low-quota development key; do not put production or privileged credentials in a client app. Each
developer should use their own key and must not share it in chat, issues, fixtures, screenshots, or
commits.

Some features send user-provided text or audio to Gemini when enabled. Review the relevant flow and
its user disclosure before changing any data sent to a network service. The app has no Vaarta
backend; that does not mean all features are offline.

## Repository map

See [the current architecture](ARCHITECTURE_CURRENT.md) for ownership and data flow. In brief:

- `app/`: Android app, Compose screens, integration and orchestration.
- `core/common/`: shared data types and normalization.
- `core/reasoning/`: deterministic scam-risk logic, prompts/models, bundled knowledge and pure-JVM
  reasoning tests.
- `core/complaint/`: complaint domain and renderers.
- `core/data/`: Android persistence, Room and encrypted database support.
- `core/voice/`: on-device speaker embedding integration.
- `tools/eval/`: benchmark model, runner, scorer and tests. Live benchmark runs may need API keys
  and use external model quota; unit tests do not.
- `tools/demo/`: local demo and probes.

## Pull requests

Use a short-lived branch from `main`, make one coherent change at a time, and open a pull request
with its purpose, test evidence, and any known limitation. Add a regression test when fixing a bug.
Update the current team docs when a change affects architecture, setup, permissions, network data,
or persistence. Never include local SDK paths, build outputs, private academic submissions, model
responses, or credentials.

Before merge, reviewers should inspect the complete diff, confirm CI is green, and check whether
the change adds permissions, network destinations, dependencies, or persistent user-data paths.
GitHub branch protection and required-review settings are repository-host configuration and must
be checked in GitHub; a workflow file alone does not enforce them.

## Privacy and safety boundaries

- Keep deterministic risk scoring authoritative; AI may add context but may not lower the
  deterministic risk result.
- Do not add call recording or raw-audio persistence.
- Treat inbound call/chat content and model responses as untrusted data.
- Never log transcripts, keys, contact details, or complaint contents.
- Do not introduce new permissions or data destinations without an explicit design and user-facing
  explanation.

For security reports, use the private reporting channel in the repository's `SECURITY.md`.
