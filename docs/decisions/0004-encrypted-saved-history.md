# ADR-0004 — Encrypted saved history (opt-in on-device persistence)

**Status:** Accepted
**Date:** 2026-07-09
**Deciders:** Product owner + implementation (AI-assisted)
**Amends:** PRIVACY_SECURITY.md P2 ("RAM-only, nothing written to disk") and DATABASE_DESIGN.md §2 —
scoped to opt-in saved sessions only. All other state stays RAM-only.
**Builds on:** ADR-0003 (conversation copilot). Realises Phase 4B of the product-architecture plan.

## Context

Earlier ADRs kept the app strictly RAM-only: a session's transcript, score, and coaching lived in
memory and were discarded on exit (privacy P2). The owner refined the product shape so the **app is the
hub**: from it the user reviews **saved call history**, files complaints on a past call, and re-analyzes
a recording. That requires persistence — a deliberate, scoped reversal of the RAM-only stance.

The tension: persistence is privacy-material (a saved scam call contains sensitive, PII-laden text), but
the feature is genuinely useful (evidence for a complaint, reviewing what happened after a stressful
call). Resolve it the same way ADR-0003 resolved intelligence-vs-safety: keep the default safe, make the
reversal explicit and user-controlled, and protect what is stored.

## Decisions

### 1. Persistence is opt-in, per session, on-device only
Nothing is written automatically. A session is saved **only when the user taps "Save this call to
history"** (explicit consent). Saved data never leaves the device — no new network egress; this is
orthogonal to the ADR-0003 cloud copilot (which is separately opt-in). Un-saved sessions remain
RAM-only and vanish on exit, exactly as before.

### 2. Encrypted at rest — Room over SQLCipher, key wrapped by the Android Keystore
- Storage is **Room + SQLCipher** (`net.zetetic:sqlcipher-android`): the whole database file is
  encrypted on disk. An imaged/stolen device yields only ciphertext.
- The SQLCipher passphrase is a **random 32-byte value generated on first launch**, never hardcoded and
  never shipped. It is sealed with an **AES-256/GCM key that lives inside the Android Keystore** and
  cannot be exported; only this device's OS can unwrap it. The wrapped blob (IV + ciphertext) sits in
  ordinary `SharedPreferences` — useless without the Keystore key (`DatabaseKeyManager`).
- We deliberately **avoid the deprecated `androidx.security:security-crypto`** (EncryptedSharedPreferences)
  and wrap the passphrase directly with the Keystore: fewer moving parts, no deprecated dependency, same
  trust anchor (a non-exportable, hardware-backed key).

### 3. Module boundary stays acyclic
New Android library module **`core:data`** (Room + SQLCipher + Keystore) depends only on `core:common`.
It stores a coach turn's replies/sources as **opaque JSON strings**, so it never depends on
`core:reasoning`'s model types. The app maps `ChatItem ↔ TurnEntity` at the boundary
(`history/ChatHistoryMapping.kt`), keeping the graph `app → core:* → core:common`.

### 4. Crash-safe writes, RAM-only brain unchanged
`SessionViewModel` stays Context-free and RAM-only — the deterministic engine, score, and live pipeline
are untouched. Persistence lives in a separate `HistoryViewModel` (`AndroidViewModel`, holds the
Keystore/Room). Turns are written as they arrive and the session row is finalized (final score/level/
scam-type) on stop, so a mid-call crash never loses the thread.

### 5. User stays in control (retention + delete)
- **Delete a session** and **delete all** are always available in the history screen.
- **Retention:** keep forever (default) or auto-delete after 7 / 30 days; enforced on launch (cascade
  removes turns). A `RECORDING`-sourced session (Phase 4D) is stored the same way.

## Privacy (updates PRIVACY_SECURITY.md — same commit)
- P2 amended: RAM-only remains the default; **opt-in saved history is the sole exception**,
  encrypted-at-rest, on-device only, explicit-consent-to-save, user delete + retention. Data-inventory
  row + `docs/data-safety.json` updated (on-device encrypted store; no new egress).
- Because a saved thread contains scam-call text (PII), the encryption scheme and user-controlled
  deletion are the mitigations; the store is never backed up (`android:allowBackup=false` already set).

## Accepted deviations / risks
- **`SharedPreferences` holds the wrapped key blob**, not the key — safe (unwrap needs the Keystore),
  but noted for the RISK_REGISTER (persistence-at-rest row).
- **Keystore key loss** (device reset, key invalidation) makes an existing DB undecryptable — acceptable:
  the data is non-critical convenience history, and losing it fails safe (no plaintext leak).
- **OEM Keystore variance** — StrongBox/TEE availability differs; AES/GCM in the Keystore is broadly
  supported at minSdk 29. Real-device verification required (shared with R-05).
- **SQLCipher native lib size** — adds ABI `.so`s to the APK; acceptable for the MVP.

## Alternatives considered
| Option | Why not |
|--------|---------|
| Stay RAM-only | Blocks the owner's hub product shape (history, complaint-on-past-call, re-analyze). |
| Plain Room (no encryption) | A saved scam call is PII; plaintext on disk is unacceptable for this app. |
| `EncryptedSharedPreferences` / `security-crypto` for the whole store | Deprecated in 2024; not a DB; wrong tool for a queryable thread. Used neither for data nor (now) the key. |
| Hardcode / ship a passphrase | Defeats encryption entirely — anyone with the APK decrypts every device. Rejected. |
| Always-save (no consent) | Privacy-material; violates the explicit-consent principle. Save is a deliberate tap. |
