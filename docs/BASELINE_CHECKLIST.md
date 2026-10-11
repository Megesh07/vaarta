# Team baseline checklist

Keep this list current as the repository changes. A checked box means the named evidence has been
observed; it does not certify the app for production or prove behavior outside that environment.

## Local evidence (2026-10-11)

The verified `expert-brain-eval` source at revision `eb37f1d` completed these checks on Windows with
JDK 17 and Android SDK 35:

- [x] `gradlew.bat clean test lintDebug :app:assembleDebug` — build succeeded.
- [x] JVM test reports: 320 tests, 0 failures, 0 errors, 4 skipped.
- [x] `lintDebug` task succeeded; Android Lint reported warnings (82 in `:app`, 2 in `:core:data`,
  and 2 in `:core:voice`). These warnings have not all been triaged or fixed.
- [x] Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- [x] Gradle 8.11.1 distribution checksum configured; wrapper JAR checksum matches the official
  Gradle reference.

This is local evidence for that source revision, not a device/release certification. GitHub Actions,
a real device/emulator run, and remote branch protection have not yet been verified.

## Local baseline

- [x] `./gradlew clean test` passes on a clean working tree with JDK 17 and Android SDK 35.
- [x] `./gradlew lintDebug` passes (86 warnings remain; see evidence above).
- [x] `./gradlew :app:assembleDebug` passes.
- [ ] Instrumented tests pass on a documented emulator/device for the affected Android flows.
- [ ] No credentials, local SDK paths, private submissions, or generated evaluation responses are
  tracked.

## GitHub baseline

- [ ] CI runs on pull requests and the default branch.
- [ ] Required status checks and review rules are enabled on the default branch.
- [ ] The repo's actual default branch and contributor instructions agree.
- [ ] Dependency and secret alerts are enabled where available.

## Release-specific evidence

- [ ] AI data flows and key exposure are reviewed for the exact build being distributed.
- [ ] Permission, privacy disclosure, device matrix and accessibility checks are current.
- [ ] Rollback/recovery and release ownership are documented.

## Prioritized follow-up (not represented as completed)

- **Before any public/production distribution:** do not ship a privileged Gemini key in the Android
  client. Design an appropriate credential strategy and document its privacy, abuse, and recovery
  tradeoffs; client-embedded keys are extractable.
- **Before making device-behavior claims:** run and record microphone consent/denial, recording,
  lifecycle interruption, transcription, overlay, persistence, and accessibility checks on a
  documented Android emulator/device matrix. JVM tests do not cover these behaviors.
- **Before claiming empirical model quality:** expand and source/document the benchmark. The current
  184-case fixture set contains only two `REAL_DERIVED` examples and no rows from a public labeled
  dataset; its current scores are developmental signals, not generalizable accuracy evidence.
- Triage the 86 existing Android Lint warnings and fix the actionable ones without suppressing
  findings wholesale.
- After the CI workflow has run on GitHub, enable and verify a required status check and suitable
  review rules on the default branch. A checked-in workflow alone is not enforcement.
