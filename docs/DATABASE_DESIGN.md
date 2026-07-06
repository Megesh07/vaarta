# VAARTA — Database Design

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Backend Engineer (on-device data)

Storage engine: **Room over SQLCipher** (AES-256, Keystore-wrapped key). One database: `vaarta.db`. Settings/consents in encrypted Preferences DataStore (not Room — different lifecycle, no relational needs).

**Prime rule (from PRIVACY_SECURITY.md):** live-call data is RAM-only. The database only ever receives data through an explicit user **Save** or **guardian/complaint setup**. There is no "auto-save session" write path.

---

## 1. Entity-relationship overview

```
saved_session 1 ── * risk_event
saved_session 1 ── * transcript_quote      (quotes only, not full transcript by default)
saved_session 1 ── 0..1 complaint_draft ── * export_record
guardian_contact (0..2 rows, standalone)
user_profile (0..1 row)
intel_pack_meta (bookkeeping for signed packs)
consent_log (append-only)         [DataStore candidate — kept in Room for queryable audit]
```

## 2. RAM session model (source of truth during a call — NOT persisted)

`ProtectionSession` (Kotlin object graph): sessionId (UUID), callerNumber, callStart/End, languageConfig, captureMode (AUDIO|MANUAL|MIXED), captureQuality flags, rolling transcript (list of segments), event list (all RiskEvents), score trace, stage-machine state, alertsSent.
Wipe semantics: on discard, char arrays backing transcript text are zeroed where feasible and references dropped; PCM ring buffer zeroed (AUDIO_PIPELINE.md). JVM GC limits true zeroing of Strings — accepted residual risk, documented; sensitive spans kept in `CharArray` where practical.

## 3. Persisted schema (Room entities — canonical DDL sketch)

### 3.1 `saved_session`
```sql
CREATE TABLE saved_session (
  id            TEXT PRIMARY KEY,            -- UUID from RAM session
  created_at    INTEGER NOT NULL,            -- epoch ms, call start
  ended_at      INTEGER,
  caller_number TEXT,                        -- E.164-ish as observed; nullable (hidden numbers)
  caller_label  TEXT,                        -- user-entered ("claimed CBI officer Sharma")
  capture_mode  TEXT NOT NULL,               -- AUDIO | MANUAL | MIXED
  languages     TEXT NOT NULL,               -- JSON array ["hi","en"]
  final_score   INTEGER NOT NULL,
  max_stage     TEXT NOT NULL,               -- HOOK..EXTRACTION | NONE
  matched_scam  TEXT,                        -- SC-01..SC-10 | NULL
  full_transcript TEXT,                      -- NULL unless user chose "save full transcript"
  notes         TEXT,                        -- user free text
  schema_v      INTEGER NOT NULL
);
```
Full transcript is **opt-in per save** (checkbox, default OFF — quotes usually suffice for complaints; minimization by default).

### 3.2 `risk_event`
```sql
CREATE TABLE risk_event (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id  TEXT NOT NULL REFERENCES saved_session(id) ON DELETE CASCADE,
  t_offset_ms INTEGER NOT NULL,              -- from call start
  kind        TEXT NOT NULL,                 -- SIGNAL | MANUAL_CUE | STAGE_CHANGE | ALERT_SENT | SCORE_STATE
  signal_id   TEXT,                          -- SIG_* / CUE_* (joins to pack, not FK — packs evolve)
  category    TEXT,                          -- AUTHORITY_CLAIM ... (denormalized: pack may be gone later)
  weight      INTEGER,
  score_after INTEGER,
  detail      TEXT                           -- JSON: {explain_lang:..., confidence:...}
);
CREATE INDEX idx_event_session ON risk_event(session_id, t_offset_ms);
```
**Why denormalize category/weight:** saved evidence must remain interpretable years later, independent of intel-pack versions. Pack is referenced by `intel_pack_meta` snapshot on the session (in `detail` of a session-start event).

### 3.3 `transcript_quote`
```sql
CREATE TABLE transcript_quote (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id  TEXT NOT NULL REFERENCES saved_session(id) ON DELETE CASCADE,
  t_start_ms  INTEGER NOT NULL,
  t_end_ms    INTEGER NOT NULL,
  text        TEXT NOT NULL,                 -- the sentence that fired a signal (± one sentence context)
  fired_signal TEXT NOT NULL
);
```

### 3.4 `complaint_draft`
```sql
CREATE TABLE complaint_draft (
  id            TEXT PRIMARY KEY,
  session_id    TEXT REFERENCES saved_session(id) ON DELETE SET NULL,  -- draft may outlive session deletion
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  status        TEXT NOT NULL,               -- DRAFT | FINALIZED
  narrative_lang TEXT NOT NULL,
  slots         TEXT NOT NULL,               -- JSON object: the entire structured complaint (schema §5)
  narrative     TEXT NOT NULL,               -- rendered narrative text
  loss_amount_inr INTEGER,                   -- paise? No: whole INR, losses are round; NULL = none
  slots_meta    TEXT NOT NULL                -- JSON: per-slot source DETECTED|USER|DEFAULT + verified flags
);
```

### 3.5 `export_record`
```sql
CREATE TABLE export_record (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  draft_id  TEXT NOT NULL REFERENCES complaint_draft(id) ON DELETE CASCADE,
  exported_at INTEGER NOT NULL,
  format    TEXT NOT NULL,                   -- PDF | DOCX | TXT | JSON
  destination TEXT NOT NULL                  -- SAF | SHARE  (never the actual URI/target app — minimization)
);
```
Purpose: user-facing history ("you exported this on…") + support debugging. Deliberately does not store where it went.

### 3.6 `guardian_contact`
```sql
CREATE TABLE guardian_contact (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  display_name TEXT NOT NULL,
  phone        TEXT NOT NULL,
  relationship TEXT,
  consent_confirmed_at INTEGER NOT NULL,     -- C3 timestamp
  preferred_channel TEXT NOT NULL            -- SMS | SHARE
);
```
Max 2 rows enforced in DAO (product decision: 1 primary + 1 backup; more = alert fatigue + consent sprawl).

### 3.7 `user_profile` (optional, complaint prefill only)
```sql
CREATE TABLE user_profile (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  full_name TEXT, address TEXT, id_doc_hint TEXT,  -- e.g. "Aadhaar ending 1234" — NEVER full ID numbers
  updated_at INTEGER NOT NULL
);
```
Full Aadhaar/PAN storage is **prohibited** (schema comment + DAO validation rejects 12-digit/PAN-pattern strings; CLAUDE_CODE_RULES.md §6).

### 3.8 `intel_pack_meta`
```sql
CREATE TABLE intel_pack_meta (
  pack_id TEXT PRIMARY KEY,                  -- e.g. "core-hi@2026.07.1"
  version TEXT NOT NULL, installed_at INTEGER NOT NULL,
  signature_ok INTEGER NOT NULL, active INTEGER NOT NULL
);
```

### 3.9 `consent_log` (append-only)
```sql
CREATE TABLE consent_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  consent_id TEXT NOT NULL,                  -- C1..C5
  action TEXT NOT NULL,                      -- GRANTED | REVOKED
  version TEXT NOT NULL,                     -- disclosure copy version shown
  at INTEGER NOT NULL, locale TEXT NOT NULL
);
```

## 4. Migration & key management

- Room schema versioning, exported schemas checked into repo, migration tests mandatory per version bump (TESTING_STRATEGY.md §8).
- SQLCipher key: 256-bit random, generated on first run, wrapped by a Keystore AES key; re-wrap on Keystore invalidation (biometric enrollment change) via recovery flow — if unwrap fails permanently, DB is unrecoverable **by design**; UX explains and offers reset. No cloud key escrow (would break the trust model).
- Backup rules: `android:allowBackup="false"` + `dataExtractionRules` excluding everything (encrypted DB with device-bound key is useless in a backup anyway; excluding prevents confusion).

## 5. Export JSON schema (complaint interchange)

Versioned schema `vaarta.complaint.v1` (JSON Schema file lives in repo at implementation): metadata (schema, app version, generated_at) · incident (start/end ISO-8601 IST, duration, caller numbers, platforms) · classification (scam code + name, confidence bucket, top signals with timestamps and quotes) · complainant (from profile, optional) · loss (amount, transaction refs, bank/UPI handles as *user-entered* strings) · narrative (lang, text) · evidence_manifest (list of quote items) · disclaimer block ("generated by VAARTA from on-device analysis; verify before filing").
PDF/DOCX/TXT are renderings of the same draft object — one source of truth, four renderers.

## 6. Query patterns & performance
Tiny data (10s–100s of rows); no perf risk. Indices only where listed. All DB access via DAOs on `Dispatchers.IO`; no DB reads on the session hot path (packs loaded into RAM at session start).

## 7. Failure cases

| Failure | Handling |
|---|---|
| DB corruption | SQLCipher integrity check on open → on failure: rename aside, recreate, notify user ("saved evidence unreadable") — never silent delete |
| Keystore unwrap failure | recovery flow §4; consent log lost with DB (accepted; consents re-collected) |
| Save during low storage | pre-check `StatFs`; block save with clear message before writing |
| Concurrent session save + user deleting sessions | Room transactions; session save is single transaction |

## 8. Debugging
Debug builds: DB inspector allowed (unencrypted debug variant uses plain Room — **debug variant never installed on real users' devices**, enforced by applicationId suffix `.debug`); `tools/dump-session.kts` renders a saved session to readable timeline. Release: no DB dump path.

## 9. Roadmap
- M2: complaint schema v2 aligned to NCRP field-level nomenclature after a real filing walkthrough (needs a volunteer filing session — roadmap task).
- M3: optional encrypted user-initiated backup file export ("take my evidence to a new phone"), same passphrase-wrapped design, explicitly user-driven.
