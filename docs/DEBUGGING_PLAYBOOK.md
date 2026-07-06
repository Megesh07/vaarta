# VAARTA — Debugging Playbook

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Android Engineer

Debugging constraint unique to VAARTA: **we cannot ask users for the data that would explain most bugs** (their call content). The playbook is therefore built on: rich *content-free* structured logs, reproducible rig sessions, and user-initiated redacted diagnostics.

---

## 1. Observability design

- **Structured session log (RAM ring, content-free):** every component emits `(ts, component, event, metrics)` — e.g. `audio.route_change{to:SPEAKER}`, `asr.partial{latency_ms:840, conf:0.71, len_chars:42}`, `engine.state{score:62, stage:ISOLATION}`. **Never** transcript text, numbers, names in these events (lint-enforced).
- **User-initiated diagnostics:** Settings → "Report a problem" → exports the session log ring + device profile + settings snapshot as JSON, *shown to the user first*, shared via their chosen channel. No automatic upload exists.
- **Debug builds only:** live debug overlay (long-press bubble): route, RMS, VAD state, ASR latency, score trace; session inspector (AI_REASONING_ENGINE.md §9); fixture capture flag (AUDIO_PIPELINE.md §8).
- **Crash handling:** release builds rely on Play Console crash/ANR data (no third-party crash SDK — PRIVACY_SECURITY.md §6); uncaught handler additionally writes an encrypted session snapshot for the "Recover last session?" flow.

## 2. First-response triage (any field report)

1. Which surface failed? (notification / bubble / audio / score / alert / complaint / export)
2. Get: device model, Android version, OEM skin version, app version, language config, capture mode.
3. Ask user to reproduce with "Report a problem" export.
4. Match against the known-issues table (§4/§5) before deep-diving.
5. Attempt rig reproduction with the same language + a matching device from the matrix.

## 3. Symptom → cause trees

### "Notification never appears on incoming calls"
- Screening role held? `adb shell dumpsys role | grep -A3 SCREENING` → if another app (Truecaller!) holds it, we're on the fallback path → is `READ_PHONE_STATE` granted?
- Notifications blocked? `dumpsys notification_manager` / channel disabled?
- App force-stopped (OEM or user)? Force-stopped apps get no callbacks until manual relaunch — check §5.
- OEM "call assistant" conflicts (MIUI in-call features) — known-issues table.

### "Bubble doesn't show / disappears"
- `Settings.canDrawOverlays()` false? (some OEMs silently revoke on update).
- MIUI extra gate: "Display pop-up windows while running in background" is a **separate** MIUI permission — must be flagged in onboarding on MIUI.
- WindowManager `BadTokenException` in log ring → fall back path should have fired; if not, bug in `core:overlay` fallback.

### "Score stays at 0 during an obvious scam call"
Follow the pipeline in order — the log ring tells you where events stop:
1. `audio.frames` flowing? No → capture problem (§ below).
2. `vad.speech_ratio` sane (0.2–0.7 typical)? ~0 → mic hears nothing: speaker off? volume low? phone face-down on soft surface?
3. `asr.partial` events? No → engine crash/restart events? model checksum?
4. Partials but no `engine.signal` hits → language mismatch (ASR lang vs pack lang), or transcripts too degraded (check `conf` distribution; rig-reproduce with same setup).
5. Signals but low score → weights/refractory logic; inspect with session inspector on a debug repro.

### "Audio capture fails to start"
`AudioRecord` state in log (`audio.init_fail{code}`): mic held by another app (recorder apps, OEM call recorder)? `dumpsys audio` shows active clients. Known OEM behavior: some builds feed **zeros** to mic during calls instead of failing → covered by silence watchdog → confirm Manual-Mode fallback fired (`audio.watchdog_silence`).

### "High latency / phone heats up"
`asr.partial.latency_ms` p95 > 1500? → thermal status in log (`power.thermal{level}`)? → expected mitigation: model downshift event. RAM pressure (`mem.trim{level}`)? Verify no second ASR engine leaked after watchdog restart (restart counter > 1 without matching `asr.engine_closed` = leak bug).

### "Guardian never got the SMS"
`alerts.sms_result{code}`: SmsManager result codes; dual-SIM: which subscription? (log `alerts.sub_id`); DND/blocked on guardian's phone (out of our control — advise share-path retry). If `SEND_SMS` fell back to share-intent build variant, confirm user completed the share sheet (`alerts.share_launched` without `alerts.share_completed` is user-abandoned, not a bug).

### "Complaint PDF blank/garbled"
Indic text in PDF requires our bundled fonts — `complaint.render{font_fallback:true}` indicates missing subset glyphs → file against font subsetting; golden-file tests should have caught — add the failing string to goldens.

## 4. Known-issues table (living — extend per field report)

| # | Environment | Issue | Workaround |
|---|---|---|---|
| K-01 | MIUI/HyperOS | background pop-up permission separate from overlay | onboarding detects MIUI → extra step |
| K-02 | Several OEMs | force-stop kills all callbacks silently | onboarding battery-exemption step; post-mortem session recovery |
| K-03 | Devices with OEM call recorder active | mic contention | detect init-fail → Manual Mode + one-line hint |
| K-04 | Some BT headsets | route flaps SPEAKER↔BT repeatedly | debounce route poller 3 s; coach only once |
| K-05 | *(add as discovered — every closed field bug must either add a row here or a regression test)* | | |

## 5. OEM background-survival playbook
Aggressive killers (Xiaomi, Oppo/realme, Vivo, some Samsung power modes) can kill even foreground-service apps under memory/battery pressure or after force-stop.
- Onboarding requests: battery optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow), OEM autostart whitelist (deep-link per known OEM intent where available — table maintained in `core:common`; intents change per OEM version, verify per release; **NO VERIFIED EVIDENCE FOUND** for a stable cross-version intent list — treat as living config).
- Detection: session heartbeat (persisted 30 s tick, minimal metadata, no content) → on next launch, gap during an active session ⇒ show post-mortem: "Protection was interrupted by your phone's battery settings" + fix-it button + rebuild complaint draft from last persisted event snapshot (encrypted).
- dontkillmyapp.com is the community reference for per-OEM steps — link internally, never rely on it programmatically.

## 6. adb quick reference
```
dumpsys role                                  # who holds screening role
dumpsys audio                                 # active audio clients/routes
dumpsys notification_manager | grep vaarta
dumpsys activity services | grep -A8 vaarta   # FGS alive?
dumpsys deviceidle whitelist                  # battery exemption present?
appops get <pkg> SYSTEM_ALERT_WINDOW
cmd package compile -m speed <pkg>            # rule out JIT jank in perf repros
```
Plus in-repo: `tools/pull-diagnostics.sh` (debug builds) collects the above + log ring in one shot.

## 7. Performance debugging protocol
Reproduce on the reference device → Perfetto trace with custom track events emitted by `core:audio`/`core:asr` (debug builds) → compare against the checked-in baseline trace → attribute regression to stage using the latency budget table (AUDIO_PIPELINE.md §5). Never optimize without a trace diff.

## 8. Escalation & documentation rule
Every S1/S2 (PRIVACY_SECURITY.md §9) gets a written post-mortem in `docs/postmortems/` (template: impact, timeline, root cause, detection gap, action items with owners). Every closed bug adds either a regression test or a known-issues row — no silent closes (CLAUDE_CODE_RULES.md §8).
