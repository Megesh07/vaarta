# VAARTA — Testing Strategy

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Android Engineer / AI Researcher

Testing philosophy: VAARTA's failure modes hurt real people mid-crime. We therefore test **the product's judgment** (does it catch scams? does it stay quiet on benign calls?) with the same rigor as its code. Two test universes:
- **Code correctness** — unit/integration/E2E, per commit.
- **Judgment quality** — corpus-driven eval of ASR+engine, per pack/model/weight change and weekly.

---

## 1. Test pyramid & ownership

| Layer | Scope | Runner | Gate |
|---|---|---|---|
| Unit (JVM) | normalization, fuzzy match, scoring math, stage machine, VAD gating logic, template renderer, pack validator | per PR | 100% pass; core:reasoning line coverage ≥ 90% |
| Robolectric | notification flows, session lifecycle, consent gating, DAO logic (in-mem SQLCipher) | per PR | pass |
| Instrumented (device farm + local bench) | AudioRecord lifecycle, overlay windows, foreground service, role acquisition, route changes, Keystore/SQLCipher on real devices | nightly + pre-release | pass on device matrix |
| E2E rig | full scripted-call runs (two-phone rig) | weekly + on ASR/pack/audio change | eval gates §6 |
| Manual matrix | OEM quirks, TalkBack, 200% font, Elder Mode | pre-release checklist | signed checklist |

## 2. The two-phone rig (canonical E2E harness)

- **Phone A ("the call")** plays a mixed mono track simulating what a speakerphone emits: far-end (scammer, band-limited 300–3400 Hz, slight AGC) + near-end (victim) from scripted dialogues, through its loudspeaker at calibrated volume/distance (50 cm default; 20/100 cm variants).
- **Phone B (device under test)** runs VAARTA in a rig mode (debug-only test hook starts a session without a real call; the real-call path is covered separately by instrumented telephony tests with a second SIM).
- Outputs per run: transcript vs. reference (WER), fired signals vs. planted signals (recall/precision), score trace vs. expected envelope, latency percentiles, RAM/battery.
- Scripts and reference annotations live in `tools/corpus/` (YAML: lines, speaker, planted signal IDs, expected stage timings). Corpus content notes: dialogues are **original works written from public reporting**, voiced by consenting speakers — no real victim audio, ever.

## 3. Real-telephony tests (small but irreplaceable)
Dual-SIM lab setup: SIM-to-SIM real calls exercising `CallScreeningService`, notification timing, in-call bubble over stock/OEM dialers, call-waiting, BT headset connect/disconnect. Semi-automated (test operator checklist + adb-driven assertions). Run pre-release and on any `core:call`/`core:audio` change.

## 4. Audio-specific tests
Covered rig variants: background TV/fan noise (+6 dB SNR steps), volume 40/70/100%, phone-on-table vs in-hand, three loudspeaker qualities (budget/mid/flagship). Regression rule: signal-recall drop > 3 points on any variant blocks the change.

## 5. Device matrix (minimum)

| Class | Example profile | Why |
|---|---|---|
| 4 GB budget, aggressive OEM | Redmi/POCO class (MIUI/HyperOS) | RAM budget + background-kill worst case |
| 4–6 GB, ColorOS/realme UI | Oppo/realme class | second kill-behavior family |
| 6–8 GB Samsung OneUI | mid Galaxy A | largest install base |
| Pixel (stock) | reference | platform-correct behavior baseline |
| Android Go / 3 GB (stretch) | | explicit *unsupported-degrade* verification: app must degrade to Manual Mode, not crash |

## 6. Judgment eval (binding gates — the product's exam)

Corpora (versioned in-repo, grow over time; sizes are M2 minimums):
- **SCAM-40:** ≥ 40 scripted dialogues covering SC-01..SC-05 × {hi, en, hinglish, +M2 languages}, including 20% "hard" variants (novel phrasing, slow-burn scripts, polite scammer).
- **BENIGN-60:** ≥ 60 dialogues: real-bank fraud-desk style, courier delivery, KYC reminder, insurance sales, angry family argument (stress-vocabulary control), police *genuinely* calling (rare but must not lock at 100).

| Gate | Threshold | Blocks |
|---|---|---|
| Scam recall (HIGH+ ≤ 3 min) | ≥ 90% | pack/model/weight/audio merges |
| Benign false-HIGH | ≤ 2% | same |
| Benign false-SCAM-PATTERN | 0 tolerated | same |
| Explanation correctness (top shown signal ∈ ground-truth set) | ≥ 95% | same |
| Complaint slot accuracy (on SCAM-40 ground truth) | ≥ 85% | complaint-engine merges |
| Manual-Mode-only recall (cues tapped per script) | ≥ 85% | reasoning merges |

Eval runs in CI in **text mode** (reference transcripts → engine, fast, per PR) and **rig mode** (audio end-to-end, weekly/on-demand). Text-mode green + rig-mode stale > 7 days = release blocked.

## 7. Language & copy QA
- Native-speaker signoff checklist per language per release: UI strings (L1), question bank naturalness read-aloud test (L4), pattern review for false-positive vocabulary (L3).
- Fuzzy-match unit tests per script with documented confusion pairs; romanization round-trip tests.
- Complaint narrative templates: golden-file tests per language (exact rendered output committed; diffs reviewed by humans).

## 8. Data & migration tests
- Room migration test per schema bump (all historical versions → current).
- Save/discard lifecycle: property test that **no file in app storage contains transcript text after discard** (string-scan of app dirs in instrumented test — this is the P1/P2 privacy property test).
- Keystore invalidation drill (enroll new biometric mid-test) → recovery flow works.
- Export golden files: PDF (text-extract compare), DOCX (unzip + XML compare), JSON (schema validate + golden), TXT.

## 9. Privacy & security tests (per release)
- Static: CI lint bans file-write APIs in `core:audio`, transcript/number/name tokens in release-log calls, full-Aadhaar/PAN patterns in persistence layer (DATABASE_DESIGN.md §3.7).
- Dynamic: network capture on release build default config = **zero non-Play traffic** (or pack-CDN only if C5 on); mic-indicator visible whenever capture active; uninstall leaves no external files (except user exports).
- Pack security: signature-tamper, downgrade, oversized, schema-poison packs all rejected in tests.
- Tapjacking test on bubble actions; `FLAG_SECURE` check on evidence screens.

## 10. Release checklist (condensed)
1. CI green incl. budgets + text-mode eval. 2. Rig eval fresh & green. 3. Device-matrix instrumented pass. 4. Manual matrix checklist signed. 5. Language signoffs for shipped languages. 6. Privacy dynamic tests pass. 7. Play pre-launch report reviewed. 8. Data-safety form diffed against data inventory. 9. Rollout staged with crash/ANR gates.

## Failure of the test system itself
Rig drift (Phone A speaker aging, room changes): monthly calibration track (fixed reference WAV, expected WER band); out-of-band → recalibrate before trusting results. Eval overfitting (packs tuned to corpus): quarterly held-out set refresh — 10% of new dialogues quarantined from developers until eval day.
