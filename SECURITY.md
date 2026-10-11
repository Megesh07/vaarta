# Security Policy

VAARTA is an Android application that handles sensitive context, including user-provided call/chat
content and complaint details. It is not a hosted service and has not been independently security
audited. Reports about security or privacy issues are welcome through the private channel below.

## Reporting a vulnerability

**Do not open a public GitHub issue for security or privacy vulnerabilities.**

Use GitHub's private vulnerability reporting from this repository's Security tab, or email the
maintainer at **megeshsundharaj@gmail.com**. Include impact and reproducible steps where possible;
redact personal information, call content, screenshots, and logs before sharing. Please allow the
maintainer time to investigate and coordinate a fix before public disclosure.

## Security notes

- **API keys:** configured client keys are embedded in the debug APK and can be extracted. Use only
  restricted development keys; never distribute privileged credentials in the Android app. If you
  find an exposed key in source or Git history, report it privately so it can be revoked.
- **Privacy:** configured AI features can send user-provided text/audio to Google's API, and threat
  checks can contact external services. See the [team guide](docs/TEAM_GUIDE.md) and
  [current architecture](docs/ARCHITECTURE_CURRENT.md) for the current data-flow summary.
