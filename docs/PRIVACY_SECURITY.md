# VAARTA — Privacy & Security

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Privacy Engineer / Security Engineer

VAARTA listens to phone calls of frightened people. If this document is wrong, the product is a liability instead of a protection. Privacy properties here are **architectural** (enforced by absence of code paths), not policy promises.

---

## 1. Non-negotiable properties

| # | Property | Enforcement |
|---|---|---|
| P1 | **No permanent audio storage** | No PCM→disk code path in release builds (compile-time; CI scans for file-write APIs in `core:audio` — CLAUDE_CODE_RULES.md §6) |
| P2 | **Real-time processing, temporary memory only** | Session data in RAM; 30 s PCM ring buffer zeroed on session end; transcripts discarded unless user saves |
| P3 | **No cloud by default** | `core:cloud` behind feature flags default-off; release network security config allowlists only the two opt-in endpoints |
| P4 | **User-controlled evidence** | Persistence and export only via explicit user actions with preview |
| P5 | **Consent-first** | Every capability gated on its own consent moment (§4); all revocable in one screen |
| P6 | **Minimal retention** | Defaults in §3; user-configurable shorter, never silently longer |
| P7 | **Family sharing requires consent** | Guardian configured by user + confirm-before-every-send; alert content previewed |

## 2. Data inventory (complete — if it's not here, the app must not process it)

| Data | Purpose | Location | Default retention |
|---|---|---|---|
| Live mic PCM | ASR input | RAM ring (30 s) | zeroed at session end — never stored |
| Live transcript segments | risk engine | RAM session store | discarded at session end unless saved |
| Risk events/signals | UI + debrief + complaint | RAM → SQLCipher only on Save | saved: until user deletes; unsaved: 30 min post-call then wiped |
| Caller number + call times | session context, complaint | RAM → saved session | with session |
| Complaint drafts | user deliverable | SQLCipher | until user deletes |
| Guardian contact (name, number) | family alert | SQLCipher | until user removes |
| User profile for complaints (name, address — optional) | complaint prefill | SQLCipher | until user edits/deletes |
| Settings/consents (+ timestamped consent log) | operation + DPDPA accountability | DataStore (encrypted) | life of install |
| Intel packs | detection | app storage (public content, signed) | replaced by updates |
| Debug logs | diagnostics | RAM buffer; export only by user action | session-scoped |

**Explicitly never processed:** contact list (guardian picked via system picker returns one entry), call logs (`READ_CALL_LOG` never requested), SMS content, location, advertising ID, voiceprints/biometrics, other apps' data.

## 3. Data lifecycle

```
Session start → RAM only
Call end → draft window (RAM, 30 min timer)
  ├─ user SAVES → SQLCipher (encrypted, local)
  ├─ user EXPORTS → user-chosen SAF location / share target (leaves our control — warn once)
  └─ timeout/DISCARD → secure wipe of session objects (arrays zeroed, refs dropped)
Uninstall → everything gone (no cloud copy exists)
```

## 4. Consent architecture (DPDPA-aligned)

Distinct, individually revocable consent moments, each recorded `(consent_id, version, timestamp, locale)` in the consent log:

| C# | Consent | Moment | Revoke effect |
|---|---|---|---|
| C1 | Mic processing during calls (prominent disclosure) | onboarding, before first mic permission | audio features off; Manual Mode remains |
| C2 | Call detection (screening role / phone state) | onboarding | notifications stop; manual launch remains |
| C3 | Guardian alerts | guardian setup | contact deleted, alerts off |
| C4 | Cloud complaint polish (F14) | first use of the feature, with "what is sent" preview | feature off; templates remain |
| C5 | Pattern-pack auto-update | settings (default ON — packs are *downloads*, no user data uploaded; documented as such) | packs frozen at current version |

"What VAARTA sends" settings screen enumerates every network flow (v1 default build: pattern downloads only, or literally nothing if C5 off). DPDPA notes: user = data principal; on-device processing without collection by us keeps VAARTA out of data-fiduciary scope for call content in the default build; C4 (cloud polish) makes us/our processor a fiduciary/processor for that transaction → C4 flow carries notice, purpose limitation, and deletion guarantee (stateless proxy, no content logging). **Formal DPDPA counsel review is a launch gate (M2, RISK_REGISTER.md R-06).**

## 5. Call-processing legal posture (engineering summary, not legal advice)

- VAARTA processes audio **as the call participant's own tool, on their own device, at their own initiation, without recording**. This is materially more conservative than the call-recorder apps historically on Play.
- The persistent foreground notification + Android mic indicator make processing visible on-device. VAARTA does not notify the remote party; requiring that would misunderstand the product (the remote party is the suspected attacker) — but this exact question is flagged for counsel (R-06).
- Complaint exports are user-generated documents; the user chooses where they go.

## 6. Security architecture

**Threat model (assets × adversaries):** scammer coaching victim in real time (UX countermeasures — MOBILE_UX_SPEC.md §9); scammer-controlled speech as engine/LLM input (injection — AI_REASONING_ENGINE.md §6.2); device thief/abusive family member reading saved evidence; malicious pattern pack / supply chain; on-path network attacker; curious co-installed apps.

| Control | Detail |
|---|---|
| Encryption at rest | SQLCipher (AES-256) for DB; key generated in, and wrapped by, Android Keystore (StrongBox where available); DataStore encrypted with Keystore-backed key (e.g., Jetpack Security-style AES-GCM keyset) |
| App lock (optional) | BiometricPrompt gate on evidence & complaint screens |
| Network | TLS 1.2+; certificate pinning on the two first-party endpoints; no cleartext (NSC enforced) |
| Pattern packs | Ed25519 signature, key pinned in-app; version monotonicity (no rollback); size cap; schema-validated before activation; failure → keep current pack |
| Overlay hardening | `setFilterTouchesWhenObscured(true)` on bubble actions (tapjacking) |
| Export hygiene | exported files carry no hidden metadata beyond content; JSON schema documented (DATABASE_DESIGN.md §5) |
| Build | R8, no debug flags in release, Play App Signing, dependency verification (Gradle checksums), Renovate-style CVE watch |
| Secrets | no API keys in APK for v1 (no backend); C4 uses backend proxy when built — never a raw LLM key client-side |
| Logs | release logs contain no transcript text, numbers, or names (lint rule — CLAUDE_CODE_RULES.md §6) |

## 7. Permissions manifest (complete list + justification)

`RECORD_AUDIO` (C1), `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW` (bubble), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` + `FOREGROUND_SERVICE_PHONE_CALL`, `READ_PHONE_STATE` (fallback call detection), `INTERNET` (opt-in features only). **Never:** `READ_CALL_LOG`, `READ_CONTACTS` (system picker instead), `READ_SMS`, `SEND_SMS`, location, accessibility service.

**LOCKED (ARCHITECTURE_FREEZE_REVIEW.md Task 1, 2026-07-05): guardian alert (F7) ships in v1 without the `SEND_SMS` permission.** Mechanism (updated by Task 9's guardian-contact picker): if a guardian contact is configured, warn-family actions fire `ACTION_SENDTO` (`smsto:`) to open the user's own SMS app pre-filled with the alert text — the user still taps Send manually, no message is dispatched programmatically; if no `ACTION_SENDTO`-capable app responds, or no guardian is configured, it falls back to the original `ACTION_SEND` share-intent chooser (to the user's own SMS/WhatsApp/etc. app, pre-filled with the alert text). Either way, **no `SEND_SMS` permission is requested in v1.** Rationale: (a) Play restricts `SEND_SMS` to apps whose *core functionality* is messaging — a single guardian alert is a hard case to justify and risks review friction on a P0 feature; (b) share-intent needs zero dangerous permissions, so it cannot itself be a rejection or data-safety-form risk; (c) cost is one extra tap through the share sheet, which is an acceptable latency trade against permission risk during a high-stress moment (the user is already handing off to family, not racing a deadline); (d) it reuses the guardian's existing message thread with the user, which reads as more trustworthy than an SMS from an unfamiliar automated sender ID; (e) it sidesteps a DPDPA/permission-minimization question entirely rather than resolving it under review pressure. Direct `SEND_SMS` is **not** on the roadmap unless field data from beta shows meaningful drop-off at the share-sheet step *and* a specific Play core-functionality justification is prepared in advance — this is a reopenable decision only with that evidence (per CLAUDE_CODE_RULES.md §1), not a default to fall back into after a rejection.

## 8. Play Store compliance map

| Policy area | Our position |
|---|---|
| Call recording | We do not record calls (no audio storage); mic used for real-time on-device processing with disclosure. Accessibility capture: not used. |
| Prominent disclosure & consent | Onboarding screen 2 (UX §3.7): standalone disclosure of mic use, purpose, on-device nature, before runtime permission; approved copy string IDs frozen per release — exact frozen text in §8.2 below |
| Foreground service types | microphone + phoneCall, with in-use notification |
| Sensitive permissions | **Resolved 2026-07-05:** `SEND_SMS` is not requested in v1 — guardian alert is share-intent only (see §7). This removes the highest-risk permission from the v1 submission entirely rather than carrying it as an open rejection risk. |
| Data safety form | matches §2 inventory exactly; CI check keeps a machine-readable copy (`docs/data-safety.json` at implementation) in sync with code-level flags |
| Families/ads | not a kids' app; zero ads/analytics SDKs |

### 8.2 Frozen onboarding disclosure copy (Play prominent-disclosure surface)

Resolves the ambiguity between "on-device processing" and "opt-in pattern-pack downloads" flagged in FOUNDATION_AUDIT.md (C-02). This is the canonical English source string; per-language versions are native-speaker-reviewed translations of this meaning, not literal machine translations (INDIAN_LANGUAGE_SUPPORT.md §6), and must preserve both clauses below distinctly.

> **How VAARTA handles your calls**
> During a call, VAARTA listens through your microphone to check for scam warning signs — but only while you turn protection on, and **only on this phone**. Your call is never recorded, never saved, and never sent anywhere.
>
> To keep its scam-detection lists up to date, VAARTA does download small update files over the internet — no call content, no personal details, ever included. You can see and turn this off anytime in Settings → What VAARTA Sends.

Rules for this copy (binding, same status as other frozen strings in this doc): the two paragraphs must remain visually separated (distinct claims, not one blended sentence); "only on this phone" / "never sent anywhere" may only describe *call content* and must never be edited to imply zero network activity by the app as a whole; any future feature that changes what leaves the device (e.g., F14 cloud polish) requires its own separate, additional disclosure moment (already the design for C4 — PRIVACY_SECURITY.md §4) and must not be folded into this screen's wording.

## 9. Incident response

- Severity classes: S1 = user data exposure; S2 = protection silently broken (e.g., ASR down fleet-wide); S3 = functional bugs.
- S1 playbook: reproduce → scope (which builds) → kill-switch flags (remote-config-free design means: expedited store release + in-app pack-delivered advisory banner capability, built in M2) → user notice per DPDPA breach rules → post-mortem in repo.
- Vulnerability intake: `SECURITY.md` in repo root (created at implementation) with contact + 90-day disclosure policy.

## 10. Future privacy work
- M4 aggregate telemetry (signal hit-rates) only under a differential-privacy or threshold-aggregation design reviewed against this doc; default remains OFF.
- Periodic (per-release) re-verification of §7/§8 against current Play policy — policies change faster than architecture.
