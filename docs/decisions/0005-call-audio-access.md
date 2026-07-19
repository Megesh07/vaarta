# ADR-0005 — Call-audio access is closed (speakerphone + mic is the only sanctioned path)

**Status:** Accepted
**Date:** 2026-07-19
**Deciders:** Product owner + implementation (AI-assisted)
**Relates to:** ADR-0002 (live AI voice-assist — already assumed this conclusion; this ADR records
the research behind it so it isn't re-investigated), AUDIO_PIPELINE.md, `AudioCapture`.

## Context

ADR-0002 (2026-07-07) already decided that VAARTA's live-audio path is "microphone + speakerphone,"
citing a 2022 Play-policy ban as the reason third-party call-stream capture is off the table. This
session re-researched the question from scratch, specifically to check whether that conclusion was
merely a *Play Store policy* restriction — which sideloading (VAARTA's actual distribution channel,
per ADR-0001) could plausibly route around — or something deeper. It is something deeper, and the
distinction matters enough to warrant its own ADR so a future session doesn't spend time
re-investigating a question that is already closed.

Findings (web-verified, this session):

1. **The `VOICE_CALL` / `VOICE_DOWNLINK` / `VOICE_UPLINK` audio sources have required the
   system-signature permission `CAPTURE_AUDIO_OUTPUT` since Android 10 (API 29).** This is an
   OS-level access control enforced by the platform itself, not a store review rule — a signature
   permission can only be held by apps signed with the platform key (i.e., pre-installed
   system/OEM apps such as the default dialer or a carrier's own call-recording app). A sideloaded,
   user-signed APK cannot acquire this permission on a stock, non-rooted device, regardless of how
   it's distributed. Corroborated via the GrapheneOS project's own issue tracker discussion of the
   same restriction (os-issue-tracker #3401), which documents the OS-level nature of the block from
   a hardened-Android-variant maintainer's perspective — a source with no reason to overstate a
   Google Play-specific restriction, since GrapheneOS devices don't install from Play by default.
2. **The historical workaround — an Accessibility Service reading call audio via its own capture
   path — was explicitly banned by Google from the Play Store in 2022** (contemporaneous coverage:
   The Register's 2022 reporting on Google's crackdown on accessibility-service-based call-recording
   apps). This ban is a distribution-channel policy, and **is irrelevant to VAARTA specifically
   because VAARTA sideloads** (ADR-0001) rather than publishing to Play. However, the *technique
   itself* remains a bad foundation independent of distribution: it depends on undocumented,
   OEM-specific behavior of how a given manufacturer's dialer exposes audio to accessibility
   listeners, breaks unpredictably across Android versions and skins, and several OEMs closed the
   underlying access paths outright after the 2022 policy change reduced the incentive to keep them
   working. Sideloading buys back the policy risk, not the reliability risk.
3. **Shizuku-based recording (as used by apps like ACR) requires re-arming its elevated ADB-derived
   permission after every device reboot.** Shizuku's own documentation and community references
   (e.g. ACR's own setup notes) confirm the permission does not survive a reboot without either
   re-running an ADB command from a PC or (on rooted/Sui-enabled devices only) an auto-start hook.
   For VAARTA's target audience — elderly, non-technical users being protected *from* being
   walked through instructions over the phone by a scammer — requiring them to periodically
   reconnect their phone to a PC and run an ADB command to keep call recording working is not a
   usable design. This isn't a soft preference; it defeats the product's own threat model (a user
   who could reliably follow ADB instructions from an attacker-adjacent premise is not the user
   VAARTA exists to protect).

## Decision

**Third-party apps — including a sideloaded VAARTA — cannot access the device's `VOICE_CALL` audio
stream on Android. This has been an OS-level, signature-permission-gated block since Android 10,
not merely a Google Play Store policy restriction, and sideloading does not bypass it.**

The two commonly-cited workarounds are both rejected for this project:
- **Accessibility-service call capture** — the Play ban doesn't apply to a sideloaded app, but the
  technique is independently fragile and OEM-dependent, and several manufacturers have since closed
  the paths it relied on. Not a foundation to build on regardless of how VAARTA is distributed.
- **Shizuku/root-adjacent capture** — works today, but the mandatory re-arm-via-ADB-per-reboot
  requirement is unusable for VAARTA's elderly-inclusive, non-technical audience.

**Consequence:** speakerphone + microphone capture — the app's existing, already-built approach
(`AudioCapture`, ADR-0002 Phase A) — is the only sanctioned live-audio path for this project. This
is not a stopgap pending a better solution; it is the conclusion of the platform's actual
constraints, and this ADR exists precisely so that conclusion isn't silently re-opened and
re-researched by a future session that hasn't seen this evidence.

## Consequences

- **What becomes easier:** any future session encountering "can we just capture the call directly
  instead of asking the user to enable speakerphone" can be pointed at this ADR instead of
  re-running the research. The speakerphone-coaching UX (MOBILE_UX_SPEC §3.4) is confirmed as
  necessary product surface, not a workaround to eventually remove.
- **What becomes harder / stays out of scope:** there is no path to a "just works silently in the
  background, no speakerphone needed" version of this feature on stock Android without either (a)
  becoming the user's default dialer and going through Play's system-app review for that role, or
  (b) requiring root/Shizuku with its reboot-fragility — both explicitly out of scope for this
  project (ADR-0001's $0/sideload/portfolio-MVP scope, and the audience-usability argument above).
- **Explicitly out of scope:** revisiting accessibility-service or Shizuku-based capture as a
  "future enhancement" without new evidence that changes one of the three findings above (the
  `CAPTURE_AUDIO_OUTPUT` signature-permission gate, the OEM-dependent fragility of accessibility
  capture, or the reboot re-arm requirement of Shizuku).

## Evidence / references

- GrapheneOS project, os-issue-tracker #3401 — discussion of the `CAPTURE_AUDIO_OUTPUT`
  signature-permission gate on `VOICE_CALL`/downlink/uplink audio sources as an OS-level restriction
  present since Android 10, from a hardened-Android-variant maintainer perspective independent of
  Play Store policy.
- The Register (2022) — coverage of Google's 2022 Play Store policy change banning
  accessibility-service-based call-recording apps from Play distribution.
- ACR (Call Recorder) / Shizuku documentation and setup notes — confirms Shizuku's elevated
  permission must be re-granted via ADB after each device reboot (absent root/Sui auto-start),
  documented as a known limitation of Shizuku-dependent recording apps.
- ADR-0002 (`docs/decisions/0002-live-ai-voice-assist.md`) — the earlier decision this ADR grounds
  with fuller research; ADR-0002's conclusion (mic + speakerphone) is unchanged, now with the
  OS-level-vs-policy distinction made explicit.

## Alternatives considered

| Option | Why not chosen |
|--------|----------------|
| Accessibility-service call audio capture | Play ban doesn't block a sideloaded app, but the technique is independently fragile and OEM-dependent regardless of distribution channel; several OEMs have closed the underlying paths. |
| Shizuku-based recording (ACR-style) | Requires re-arming via ADB after every reboot — unusable for an elderly-inclusive, non-technical audience; defeats the product's own threat model. |
| Become the default dialer / system app | Would grant `CAPTURE_AUDIO_OUTPUT` legitimately, but requires Play system-app review and a completely different distribution/trust model; out of scope for a $0 sideloaded MVP (ADR-0001). |
| Root the target device | Not a realistic ask of the app's real, non-technical end users; also narrows the install base to rooted devices only. |
| Speakerphone + mic capture (chosen) | Already built (ADR-0002 Phase A, `AudioCapture`), works on stock non-rooted devices, needs no elevated permission, and the UX coaching for it is already speced. The only downside — the user must remember to enable speakerphone — is a real but acceptable trade-off given every alternative is either impossible, unreliable, or unusable by this audience. |
