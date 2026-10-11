# Vaarta Engineering Guide

## Project

Vaarta is an Android application built with Kotlin and Jetpack Compose. The deterministic risk
engine and shared domain logic live in Kotlin modules under `core/`; Android UI and integrations
live in `app/`; developer tooling lives in `tools/`. The current module map and data boundaries are
documented in [docs/ARCHITECTURE_CURRENT.md](docs/ARCHITECTURE_CURRENT.md).

## Local setup and checks

- Use JDK 17 and Android SDK platform/build tools 35. Set `sdk.dir` in an untracked
  `local.properties` file.
- Run `./gradlew test` for all JVM unit tests.
- Run `./gradlew lintDebug` for Android lint.
- Run `./gradlew :app:assembleDebug` to build the app.
- Use `gradlew.bat` equivalents on Windows.
- Emulator/device tests require an Android emulator or device; they are not run by `test`.

See [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md) for full onboarding and contribution instructions.

## Change expectations

- Make focused changes and add regression tests for bug fixes.
- Keep the deterministic risk engine authoritative. Generative AI output is advisory and must not
  lower a deterministic risk result.
- Treat transcripts, audio, phone numbers, complaint details, and API keys as sensitive. Never put
  real user content or secrets in fixtures, logs, commits, or issues.
- Do not claim a behavior is verified on-device unless it was tested on a device/emulator.
- Do not introduce new permissions, network destinations, or persistent user-data paths without
  documenting their purpose and privacy impact.
- Keep credentials out of source control. A key compiled into an Android app can be extracted;
  local `secrets.properties` is for development and does not make a distributed key confidential.
