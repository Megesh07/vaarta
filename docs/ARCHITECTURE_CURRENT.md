# Vaarta current architecture

This is a code-grounded map of the current implementation. It supersedes older untracked planning
documents when they name modules or behaviors that do not exist in the source tree.

## Modules

| Module | Responsibility | Platform |
| --- | --- | --- |
| `:app` | Compose UI, navigation, Android integrations, feature orchestration | Android |
| `:core:common` | Shared types and normalization | Kotlin/JVM |
| `:core:reasoning` | Risk rules, scam knowledge, prompt/domain models and reasoning logic | Kotlin/JVM |
| `:core:complaint` | Complaint data model, assembly and rendering | Kotlin/JVM |
| `:core:data` | Room persistence, Android Keystore integration and SQLCipher database | Android |
| `:core:voice` | Sherpa-ONNX speaker embedding wrapper and native AAR | Android |
| `:tools:demo` | Local command-line/demo probes | Kotlin/JVM |
| `:tools:eval` | Scam-classification benchmark, cache, throttling and reports | Kotlin/JVM |

The app depends on the core modules. `:core:reasoning` and `:core:complaint` are independently
testable on the JVM. The speaker library is vendored at `core/voice/libs/`; review its provenance
and license before changing or replacing it.

## Risk and AI boundary

The deterministic risk engine runs locally in `:core:reasoning`. Gemini clients and prompt
integration live under `app/.../ai`, with shared domain types in `:core:reasoning`. Gemini is a
remote service: when configured and used, relevant user-provided text/audio is sent to Google's API.
The repository does not run a Vaarta backend. The app's safety rule is that generative output is
advisory and cannot reduce the deterministic risk result.

The separate `tools:eval` module can send benchmark cases to Gemini during a live benchmark run.
Its fixture-based unit tests do not require credentials or external model calls. Run the live
benchmark only when authorized API quota is available; reports and raw responses are local output,
not source fixtures.

## Data and persistence

`core:data` owns Android database and Keystore integration. The app coordinates persistence and
feature flows. Do not infer that every feature is on-device merely because there is no app backend:
configured AI and threat-intelligence integrations can make external requests. Before adding a
network call or persistent field, identify the data involved, consent point, retention behavior,
and failure fallback in the code and update the docs/tests.

## Build graph and verification

The root Gradle build uses Kotlin 2.0.21, AGP 8.7.3 and Java 17 (see
`gradle/libs.versions.toml`). Android compilation uses SDK 35 and min SDK 29. Run `./gradlew test`,
`./gradlew lintDebug`, and `./gradlew :app:assembleDebug`. Instrumented tests under `androidTest`
need a device/emulator and are not included in the JVM `test` task.

## Known boundaries to verify before release

- The debug client embeds configured API keys in its APK; those keys are extractable. Do not treat
  an Android client as a secret-holding backend.
- JVM tests and lint do not prove real microphone, overlay, telephony, Keystore, WebView or OEM
  behavior. Run the relevant instrumented/device checks before making those claims.
- No automated workflow can prove branch protection or required reviews; those settings live in
  GitHub repository configuration.
