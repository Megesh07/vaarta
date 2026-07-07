# Security Policy

VAARTA is a hackathon/portfolio MVP, not a production service — but it handles sensitive context
(suspected scam calls, live audio, complaint drafts), so security and privacy reports are taken
seriously.

## Reporting a vulnerability

**Do not open a public GitHub issue for security or privacy vulnerabilities.**

Instead, use GitHub's private reporting: open this repo's **Security** tab → **Report a
vulnerability**, or email the maintainer directly at **megeshsundharaj@gmail.com** with:

- A description of the issue and its potential impact
- Steps to reproduce (if applicable)
- Any relevant logs or screenshots — with PII/real call content redacted

You should get an acknowledgment within a few days. This is a solo/small-team project, so there is
no formal SLA, but reports are prioritized over feature work.

## Scope notes specific to this project

- **API keys:** the debug build embeds a Gemini API key read from a git-ignored
  `secrets.properties` at build time (see [README.md](README.md)). If you ever find a live key
  committed anywhere in this repo's history, please report it privately and immediately — that is
  always a bug, never intentional.
- **Privacy:** the app is designed to keep call audio/transcripts in RAM only, with no disk
  persistence (see `docs/PRIVACY_SECURITY.md`). A report that this guarantee is violated anywhere
  in the code is treated as a security issue, not a feature request.
