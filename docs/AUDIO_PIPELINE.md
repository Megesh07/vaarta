# VAARTA — Audio Pipeline

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal Android Engineer / Telecom Engineer

---

## 1. Legal & platform ground truth (do not re-litigate)

1. **No third-party access to the call voice stream.** `VOICE_CALL`/`VOICE_DOWNLINK`/`VOICE_UPLINK` need `CAPTURE_AUDIO_OUTPUT` (system apps only). AccessibilityService capture is Play-banned. → We capture the **microphone**, which hears the caller only when **speakerphone** is on. (Full rationale: TECHNICAL_ARCHITECTURE.md §2.)
2. **Play compliance:** `RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` (and `FOREGROUND_SERVICE_PHONE_CALL` for the session service) with prominent disclosure + in-use notification. Android 12+ shows the mic privacy indicator — good; we *want* visible capture.
3. **Consent posture (India):** a party to a call processing their own call audio locally is the design basis; we still (a) never store raw audio, (b) show a persistent notification during capture, (c) document the analysis in PRIVACY_SECURITY.md §5. This is engineering documentation, not legal advice; counsel review is roadmap item M2 (RISK_REGISTER.md R-06).
4. **No permanent audio storage — architectural, not configurable.** There is deliberately no code path that writes PCM to disk in release builds (debug-only fixture recording exists behind a compile-time flag, stripped from release; CLAUDE_CODE_RULES.md §6).

## 2. Capture configuration

| Parameter | Value | Why |
|---|---|---|
| API | `AudioRecord` (not MediaRecorder) | raw PCM access, no file encoder |
| Source | `VOICE_RECOGNITION` | OEM-tuned for speech, disables some aggressive processing that hurts far-field speech; fallback `MIC` if init fails |
| Rate / format | 16 000 Hz, mono, PCM 16-bit | native input for all candidate ASR models; resample nothing |
| Buffer | 2× `getMinBufferSize`, read in 20 ms chunks into a 30 s ring buffer (RAM) | low latency + enough context for ASR windows |
| Effects | Prefer acoustic echo cancellation **on** (`AcousticEchoCanceler.create`) when speakerphone active; `NoiseSuppressor` on if available | the phone's own speaker output (scammer voice) re-entering the mic is *signal* for us, not echo — **but** AEC here cancels the *user's* voice echo path, not loudspeaker playback of the remote party; empirically AEC behavior varies by OEM. M1 bench decides per-effect defaults; store per-device override table in `core:audio` config. **NO VERIFIED EVIDENCE FOUND** for uniform AEC behavior across OEMs — treat as measurable unknown, not assumption. |

Threading: dedicated `AudioRecordThread` (THREAD_PRIORITY_URGENT_AUDIO) → lock-free SPSC queue → VAD/ASR consumer thread. UI never touches audio threads.

## 3. Pipeline stages

```
AudioRecord 16k mono PCM16
  → (1) DC-block + peak-normalize per 1 s window
  → (2) VAD: Silero VAD (onnx, ~2 MB, streaming) — gate + segmenter
  → (3) Segment assembler: speech chunks 0.3–8 s, 200 ms hangover
  → (4) Streaming ASR (sherpa-onnx) — partials every ~300 ms, finals on endpoint
  → (5) TranscriptEvent{text, tStart, tEnd, isFinal, langHint, confidence}
  → RiskEngine (core:reasoning)
Ring buffer: last 30 s PCM retained in RAM for ASR context only; zeroed on session end.
```

**Why Silero VAD:** tiny, robust to noise, MIT-licensed, proven on mobile; cuts ASR compute ~60% on typical calls (silence + user listening). Alternative — WebRTC VAD: cheaper but far worse precision in noise; energy threshold: rejected, fails with fan/TV background.

## 4. Speaker(phone) reality — what the mic actually hears

- **User voice:** near-field, good SNR.
- **Caller voice:** phone loudspeaker → air → mic. Band-limited (8 kHz telephony), possibly AGC-pumped, room reverb, distance-dependent.
- Design consequences:
  - ASR models must be evaluated on a **re-recorded corpus** (played through a phone speaker, captured by another phone's mic) — not clean benchmarks. TESTING_STRATEGY.md §4 defines the rig.
  - No speaker diarization in v1 (unreliable in this setup). The risk engine does not need to know who said what for most signals; keyword source ambiguity is handled by signal design (e.g., "arrest warrant" is scam-indicative regardless of speaker; see AI_REASONING_ENGINE.md §4.3).
  - Audio route detection: `AudioManager.communicationDevice` / `isSpeakerphoneOn` polled at 1 Hz → drives the Speakerphone Coach (MOBILE_UX_SPEC.md §3.4) and the `capture_quality` flag on the session.

## 5. Latency budget (end-to-end ≤ 2 s speech→score)

| Stage | Budget |
|---|---|
| Capture chunk | 20 ms |
| VAD decision | ≤ 30 ms |
| ASR partial emit | ≤ 1.2 s from word spoken |
| Risk engine ingest→state | ≤ 100 ms |
| Bubble recompose | ≤ 200 ms |

Measured continuously via debug overlay (DEBUGGING_PLAYBOOK.md §3); regressions fail CI perf test on reference device (M1 chooses reference: one 4 GB Snapdragon 6-series class device).

## 6. Failure cases & recovery

| Failure | Detection | Recovery |
|---|---|---|
| `AudioRecord` init fails (mic held by dialer/OEM on some devices during calls — known on some MIUI/ColorOS builds; **NO VERIFIED EVIDENCE FOUND** for a current authoritative device list, build one empirically in M1) | init exception / first read returns error | Retry once with `MIC` source → else auto Manual Mode + device flagged in local config |
| Silent capture (mic muted by user, or OEM feeds zeros during call) | energy watchdog: 10 s zero-energy while call active + route=speaker | Bubble notice "Can't hear the call" → Manual Mode offer |
| Route changes to earpiece/BT mid-call | route poller | Pause ASR (feed is useless), bubble shows coach again once, keep session alive |
| ASR engine crash (native) | JNI wrapper catches, isolates in own process? **Decision: run ASR in main app process, guarded by try/catch at JNI boundary + watchdog restart (once per session)**; separate process rejected (RAM duplication on 4 GB devices) | restart engine with ring-buffer replay of last 5 s; second crash → Manual Mode |
| Thermal throttling / battery saver | `PowerManager` thermal status listener | drop ASR to smaller model if available, else notify + Manual Mode |
| OOM pressure | `onTrimMemory` ≥ CRITICAL | release ring buffer to 10 s, drop Whisper post-pass |

## 7. What is explicitly NOT in this pipeline
- No raw-audio persistence (release builds have no code path).
- No cloud audio streaming, ever (text-only cloud features exist elsewhere, opt-in).
- No voiceprint/biometric extraction (DPDPA significant-data risk; zero product need).
- No call-audio injection/TTS into the call (not possible for third-party apps; also undesirable).

## 8. Testing strategy (owned jointly with TESTING_STRATEGY.md)
- **Unit:** VAD gating logic, segment assembler edge cases (speech at buffer wrap, 8 s max cut), watchdogs.
- **Instrumented:** AudioRecord lifecycle on device farm; route-change storms; permission-revoked-mid-session.
- **Corpus rig:** two-phone rig — Phone A plays scripted scam call audio (both parties mixed as a speakerphone would emit), Phone B runs VAARTA; measures WER + signal recall end-to-end. This rig is the canonical quality gate for any ASR/VAD/model change.
- **Fixture capture:** debug builds can record rig sessions to WAV (compile-time flag `fixtureCapture`, never in release) to grow the regression corpus.

## 9. Debugging quick reference
- `adb shell dumpsys audio | grep -A5 requestAudioFocus` — who holds audio.
- Debug overlay (long-press bubble in debug builds): live route, RMS meter, VAD state, ASR partial latency, engine restarts.
- Structured logs: `AudioEngine` tags, PII-free (log energy/latency/state, never transcript text — CLAUDE_CODE_RULES.md §6).

## 10. Roadmap
- M2: per-OEM tuning table (AEC/NS on-off, source fallback) driven by device-farm data.
- M3: evaluate on-device speech enhancement (e.g., tiny denoiser) if rig WER on far-end speech < target.
- M3+: if "VAARTA Dialer" (ROLE_DIALER) ships, revisit audio routing control (we still cannot record the stream, but we can toggle speaker ourselves with user consent).
