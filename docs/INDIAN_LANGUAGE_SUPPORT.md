# VAARTA — Indian Language Support

**Status:** FOUNDATION · v1.0 · 2026-07-05
**Owner:** Principal AI Researcher / UX

Language support spans four independent layers — a language is "supported" only when all four pass their gates:

| Layer | Artifact | Gate |
|---|---|---|
| L1 UI strings | Android resources | native-speaker review |
| L2 ASR | per-language model | rig WER + engine recall gates (§4) |
| L3 Intel patterns | pack entries (native + romanized + code-mixed) | eval recall per language |
| L4 Output generation | question bank + debrief + complaint templates | native-speaker review |

## 1. Rollout matrix (binding, from PRD F11)

| Language | Script | P0/P1/P2 | Notes |
|---|---|---|---|
| English (India) | Latin | P0 | |
| Hindi | Devanagari | P0 | |
| **Hinglish** (code-mixed) | Latin+Deva | P0 | Majority register for urban scam calls; treated as first-class (§5) |
| Tamil (+Tanglish) | Tamil | P1 | |
| Telugu (+code-mix) | Telugu | P1 | |
| Bengali | Bengali | P1 | |
| Marathi | Devanagari | P1 | Shares Devanagari normalization with Hindi |
| Kannada (+Kanglish) | Kannada | P2 | |
| Malayalam (+Manglish) | Malayalam | P2 | |
| Gujarati | Gujarati | P2 | |
| Punjabi | Gurmukhi | P2 | |
| Odia | Odia | P2 | |
| Assamese | Bengali–Assamese | P2 | Lowest ASR resource availability of the set |

## 2. ASR model strategy (L2)

- Runtime: sherpa-onnx (TECHNICAL_ARCHITECTURE.md D4). Per-language **model packs** downloaded on demand (bundled: English+Hindi bilingual pack if size budget ≤ 250 MB allows; else download-on-first-run with Manual Mode until ready).
- Candidate sources per language (to be benchmarked, not assumed): AI4Bharat IndicConformer/IndicWhisper family (open, Indic-focused), Whisper small/base multilingual (strong code-mix, batch), OpenAI-community fine-tunes. **NO VERIFIED EVIDENCE FOUND** at doc time for exact current model names/checkpoints suitable for streaming in every listed language — the M1 bake-off (§4) resolves this per language; docs must not hardcode checkpoints.
- Language selection at runtime: user's configured call language(s) (up to 2 active, e.g., Hindi + English) → if bilingual/multilingual model available use it; else primary-language model and rely on romanized patterns for embedded English.

## 3. Text normalization (critical, owned by `core:common`)

All pattern matching happens on normalized text:
1. Unicode NFC; strip ZWJ/ZWNJ where non-semantic.
2. Indic digit → ASCII digit mapping.
3. Script-aware lowercase (Latin only).
4. **Romanization channel:** every transcript additionally transliterated to a Latin folding (ISO-15919-derived, lossy-normalized: drop diacritics, collapse aa/ā) so one romanized pattern list can match speech transcribed in native script and vice versa. Library candidate: indic-transliteration ports / custom table (small, testable). This halves pattern-list maintenance.
5. Common ASR confusion folding per language (e.g., Hindi श/स, Tamil ழ/ள handled by fuzzy match distance, not folding — keep folding minimal and documented).

## 4. Language bake-off protocol (M1, per language)

1. Build 30-minute scripted corpus: 3 scam dialogues + 2 benign dialogues, native-speaker voiced, mixed-register.
2. Render through the two-phone rig (AUDIO_PIPELINE.md §8).
3. Measure: WER (informational), **signal-recall** (binding: engine catches ≥ 90% of planted signals), latency (partial ≤ 1.5 s), model size/RAM.
4. Pick smallest model meeting gates; record in `docs/decisions/asr-<lang>.md` (decision-record dir created at implementation).
5. A language ships only when L1–L4 all pass; otherwise it ships as "Manual Mode + UI only" with honest labeling.

**Why signal-recall over WER as the binding gate:** VAARTA doesn't need perfect transcription; it needs "CBI", "arrest", "UPI", "don't tell family" to survive transcription. A 40% WER transcript can still yield 95% signal recall — optimize for the product, not the benchmark.

## 5. Code-mixed speech (Hinglish/Tanglish/Manglish/Kanglish)

Reality: scam calls in urban India are heavily code-mixed ("Aapke naam pe ek parcel seize hua hai, Mumbai police ko transfer kar raha hoon").
- ASR: prefer multilingual models that emit mixed output naturally (Whisper-class); where the streaming model is monolingual, embedded-English words come out as phonetic native-script — the romanization channel (§3.4) + fuzzy match recovers them ("पार्सल" → "parsal" ~ "parcel", fuzzy(1–2)).
- Patterns: every signal carries `xx` (native), `xx_latn` (romanized), and `mix` variant lists; `mix` entries are written from real observed phrasing during corpus building, not invented.
- Eval: code-mixed dialogues are mandatory in every language's corpus (≥ 40% of scam lines, matching field reality).

## 6. Output language (L4)

- Independent setting: `output_language` (UI/prompts/debrief/complaint narrative) decoupled from ASR language and device locale (UX §5).
- Complaint export special case: NCRP/police complaints are commonly filed in English or Hindi → complaint editor offers "Narrative language: [user language] / English / Hindi" with template-based generation for each (never machine-translated on the fly in v1; each language's templates are human-reviewed).
- Question bank entries are *localized*, not translated: each language's questions rewritten by native speakers for natural spoken register (a Tamil grandmother must be able to read the question aloud verbatim and sound natural).

## 7. Rendering & typography

- Noto family fallbacks bundled per shipped script (subset to app languages to control APK size).
- Bubble text: line-height and font-size verified per script (Devanagari/Tamil need more vertical space than Latin at same sp) — part of the per-screen accessibility gate (UX §7).
- Never render romanized text to users who chose native script.

## 8. Failure cases

| Case | Behavior |
|---|---|
| Model pack for user's language missing | Manual Mode + download prompt; UI/questions still in user's language (L1/L3/L4 don't depend on ASR) |
| User's language not in ASR-supported set | Honest setting label: "Live listening not yet available in Odia — Manual Mode + all other features work" |
| Mixed language beyond configured pair | signals from romanization channel still fire; debrief notes reduced confidence |
| Wrong auto-detected language | v1: no auto-detect; explicit user setting only (auto-detect = silent failure risk) |

## 9. Testing
Per-language regression corpus locked into CI after bake-off; native-speaker signoff checklist per release for L1/L4 string changes; fuzzy-match unit tests per script (confusion pairs). See TESTING_STRATEGY.md §7.

## 10. Roadmap
- M2: P1 languages through bake-off; complaint narrative in P1 languages.
- M3: P2 languages; investigate single multilingual Indic streaming model to replace per-language packs (RAM/size win).
- M4: dialect/register deltas per state fed from field data (SCAM_INTELLIGENCE.md R3).
