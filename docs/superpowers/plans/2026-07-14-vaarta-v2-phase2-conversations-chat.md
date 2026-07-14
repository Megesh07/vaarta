# VAARTA v2 — Phase 2: Unified Conversations + text chat

> Executed against the committed spec `docs/superpowers/specs/2026-07-14-vaarta-v2-intelligence-ux-design.md`
> (§4.2, §4.4, §6.5). Compact plan — the spec carries the rationale. TDD for pure logic; build +
> on-device verification for UI/DB (Room+SQLCipher migration needs a device, not a JVM unit test).

**Goal:** Turn the History tab into a unified **Conversations** store+list (live / recording / chat),
and make a real **text** ChatGPT-style conversation work end-to-end (grounded, safety-filtered,
persisted). Multimodal composer (voice/image/audio) + call-context header + auto-save are **Phase 3**.

## Global constraints
- $0 (free Gemini + web grounding); fail closed on every AI/network/parse error.
- Additive Room migration only — never wipe existing saved calls.
- Every AI reply passes `SuggestionSafetyFilter` before display.
- MD3 + Calm Guardian; ≥48dp targets; large legible text.

---

### P2-T1 — Unified Conversation store (core:data v2)
**Files:** `core/data/.../db/Entities.kt`, `Converters.kt` (tolerant defaults already handle new enum
values), `db/VaartaDatabase.kt` (version 2 + `MIGRATION_1_2`), `db/HistoryDao.kt`, `HistoryRepository.kt`.
- `enum SessionSource { LIVE, RECORDING, CHAT }` (+CHAT). `enum TurnKind { CALLER, USER, COACH, ASSISTANT }` (+ASSISTANT).
- `CallSessionEntity` += `@ColumnInfo("title") val title: String? = null`.
- `VaartaDatabase`: `version = 2`; `MIGRATION_1_2 = Migration(1,2){ db.execSQL("ALTER TABLE call_session ADD COLUMN title TEXT") }`; `.addMigrations(MIGRATION_1_2)`.
- DAO: `@Query("UPDATE call_session SET title=:title WHERE id=:id") suspend fun setTitle(id, title)`.
- Repo: `createConversation(startedAtMs, source, title?)` → id; `setTitle(id, title)`; keep the rest.
- **Verify:** `:app:assembleDebug` green; a saved call from the v1 DB still loads after upgrade (on-device T4).

### P2-T2 — Conversations list + Home reorder
**Files:** `MainActivity.kt` (HistoryScreen → grouped Conversations + New chat), `ui/VaartaNav.kt`
(tab label/route), `ui/HomeScreen.kt` (add "Ask VAARTA" card; news already lowest — keep).
- Tab label "History"→"Conversations"; `VaartaTab.HISTORY` stays (internal enum name unchanged).
- List rows: derived title (`title` ?: scamType ?: kind label), type glyph (📞/🎧/💬), risk ring when
  score>0, time; grouped **This week / Earlier** by `startedAtMs`.
- **＋ New chat** button → creates a CHAT conversation (empty) and opens the Conversation screen (T3).
- Home: add **"Ask VAARTA"** action card (💬) between live and recording → opens a new CHAT conversation.
- **Verify:** on-device — tab reads "Conversations", grouping renders, New chat + Ask VAARTA open the chat.

### P2-T3 — Text chat conversation screen (the heart, text-only this phase)
**Files:** new `core/reasoning/.../ChatModels.kt` (`ChatMessage(role, text)`, `ChatAnswer(text, sources)`),
new `app/ai/ChatPrompt.kt`, `app/ai/GeminiClient.kt` (+`chat()`), new `app/conversation/ConversationViewModel.kt`,
new `ui/ConversationScreen.kt`, `ChatItem.kt` (+`Assistant`), `ChatView.kt` (render Assistant bubble),
`history/ChatHistoryMapping.kt` (ASSISTANT ↔ Assistant).
- `GeminiClient.chat(context: String?, history: List<ChatMessage>, userText: String): ChatAnswer?` —
  `google_search` grounded, prose answer + `extractSources`, fails closed. Mirrors `classify()` mechanics
  (no responseSchema with tools). System prompt = `ChatPrompt` (VAARTA scam-help assistant: plain
  language, India context, never advises paying/OTP disclosure, refuses off-topic, answers in user's
  language). Attachments/voice = Phase 3.
- **TDD (pure):** `ChatModels` + any parsing helper get a JVM test (well-formed, empty→null).
- `ConversationViewModel(app)`: holds `id`, `kind`, `context?`, `turns: List<ChatItem>`, `sending`.
  `send(text)`: append `You`, persist (create row on first send, set title = first ~40 chars), call
  `chat()` on IO, run `SuggestionSafetyFilter.sanitize`, append `Assistant`, persist. Fail closed →
  a safe "I couldn't reach the assistant — never pay or share an OTP; call 1930 if unsure."
- `ConversationScreen`: optional context header (none for blank chat), `ChatThread`, bottom text
  composer (TextField + send), sending indicator. MD3/Calm Guardian.
- **Verify:** build green + on-device (T4).

### P2-T4 — Verify end-to-end + status
- On-device: New chat → "Is a call from someone saying they're CBI a scam?" → grounded, safe answer with
  source(s); appears in Conversations with a title; reopen replays it; app restart → still there
  (migration + persistence). Screenshot each.
- `:app:assembleDebug` + `test` green (new core tests included).
- Update `PROJECT_STATUS.md` (built/next), commit.

## Self-review
- Spec §4.2 Conversations ✓ T2; §4.4 conversation screen (chat half) ✓ T3; §6.5 chat text half ✓ T3;
  store ✓ T1. Deferred to Phase 3 (explicitly): multimodal composer, call-context header population
  from live/recording, download, auto-save of live/recording. Article summarizer = Phase 5.
- No placeholders; types: `ChatMessage`/`ChatAnswer`/`ChatItem.Assistant`/`SessionSource.CHAT`/
  `TurnKind.ASSISTANT` used consistently across T1/T3.
