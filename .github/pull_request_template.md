## Purpose

<!-- What problem does this change solve? -->

## Verification

- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew :app:assembleDebug`
- [ ] Device/emulator checks (if Android behavior changed; describe device/API)

## Risk and data-flow review

- [ ] No secrets, local config, personal data, or generated evaluation responses added
- [ ] Permissions, network destinations, persistence, and third-party dependencies are unchanged
      or their purpose and privacy impact are documented
- [ ] Deterministic risk score remains authoritative over generated AI output
- [ ] User-facing claims and team docs match the code change

## Known limitations

<!-- List anything this change does not verify or any follow-up needed. -->
