# VAARTA — "Calm Guardian" Premium UI + Consistency Pass

**Date:** 2026-07-16
**Status:** Design — awaiting owner review before planning
**Relationship to prior specs:** refines the *presentation layer* of the v2 shell
(`2026-07-14-vaarta-v2-intelligence-ux-design.md`). It does **not** change the product's
information architecture, features, or engine — it makes the existing surface clean, consistent,
sleek, and genuinely premium, and fixes presentation bugs.
**Scope-lock (ADR-0001):** unchanged — strict $0, no new paid deps, offline, sideload,
hackathon/portfolio MVP.

---

## 1. Why this exists (the problem)

The design *foundation* is strong — semantic color tokens (`Color.kt`), a WCAG-AA risk ramp, a
restrained Apple-style type scale (`Type.kt`), and the "color is reserved for risk" rule. But the
layer on top drifted, and it reads as unpolished / "hackathon". Five concrete defects:

1. **Emojis used as UI icons** — bottom nav (🛡️ 💬 🆘), every primary button (📞 🌐 📝 🔔 🎙️ 🎧),
   the chat composer (🎤 🖼️ 🎧), Home cards, source links (🔗). They render differently per OEM,
   sit at inconsistent baselines, and carry cartoonish color that fights the "color = risk only"
   rule. ~80% of the cheap feeling.
2. **The type scale is defined but ignored.** `Type.kt` ships a full scale
   (`displayLarge`…`labelSmall`), yet nearly every screen hardcodes `fontSize = 15.sp` /
   `fontWeight = …` inline. Sizes, weights, and line-heights are re-invented per screen — the root
   cause of the inconsistent, un-sleek feel.
3. **Raw markdown leaks into the UI (bug).** AI free-text (`AssistantBubble` `ChatView.kt`, the
   article summary `ArticleScreen.kt`, coach warnings) is rendered in a plain `Text()`. Gemini
   emits markdown, so users see literal `**bold**`, `#`, `- `, `1.` characters.
4. **Duplicated, inconsistent micro-components.** The source-link row is copy-pasted 4× (three in
   `ChatView`, one in `ArticleScreen`) at different sizes with different emoji; back affordances
   differ per screen ("‹ Back" text vs others); section eyebrows vary (10 vs 11sp, uppercase or
   not).
5. **Ad-hoc spacing & flat components** — arbitrary `Spacer(8/10/12.dp)`, no press feedback, no
   depth story, empty states rendered as "text in a box".

## 2. Goals / Non-goals

**Goals**
- Zero emoji in the *interface*; one coherent vector icon system.
- Every piece of text flows through the `Type.kt` scale — no hardcoded `fontSize`/`fontWeight` in
  screens.
- AI output renders cleanly (formatted, no stray markdown characters) — bug fixed at the render
  layer, not just prompted away.
- One set of shared micro-components (source link, back bar, eyebrow, icon-chip card, buttons) so
  the look cannot drift.
- One named aesthetic — **Calm Guardian** — executed on every screen: quiet monochrome chrome,
  color only for risk, generous space, soft depth, gentle motion, elder-legible.
- Keep **only what the user actually needs** — simplify/consolidate overlapping surfaces (§9).
- No regressions: build + existing ~70 unit tests green; both light and dark verified on device.

**Non-goals**
- No change to information architecture (3 tabs), navigation, or which features exist (subject to
  the small, owner-confirmed consolidations in §9).
- No copy rewrites (beyond removing markdown), no engine/AI/data-layer changes.
- No new color *ramp* (may add 1–2 neutral elevation/shadow tokens only).

## 3. The aesthetic — "Calm Guardian"

Chrome is near-monochrome and quiet; **color appears only for risk**; content outranks chrome.
Apple-style restraint with elder-first overrides: ≥48dp tap targets, ≥15–17sp body, contrast above
iOS defaults, no thin low-contrast text. Motion is subtle and never flashes (existing safety rule).

## 4. Iconography

### 4.1 Decision: hand-author in the existing house style
Standardize on the **existing Lucide-style house system** already in `res/drawable`: 24dp
viewport, `strokeWidth ≈ 1.75`, round caps/joins, `fillColor="#00000000"`, stroke color overridden
by `Icon(painter, tint = …)`. Author the missing glyphs as vector XML in this exact convention.
**Rejected:** `material-icons-extended` — adds a dependency and its heavier/filled style clashes
with the shipped thin-line signal glyphs; a mixed set reads worse than the emoji.

### 4.2 New glyphs (~20)
Nav: `ic_nav_shield`, `ic_nav_chat`, `ic_nav_help` (life-buoy). Actions: `ic_phone`, `ic_globe`,
`ic_file_text`, `ic_bell`, `ic_headphones`, `ic_image`, `ic_download`, `ic_alert_triangle`,
`ic_sparkle` (AI/VAARTA mark), `ic_check`, `ic_chevron_right`, `ic_arrow_left` (back), `ic_close`,
`ic_link_external`, `ic_siren` (panic). Reuse `ic_mic`, `ic_history`, `ic_shield_x`, `ic_act_*`,
`ic_sig_*`. Each verified legible at 20/24/28dp.

### 4.3 Emoji that stays (content, not chrome)
Only in **user-authored strings shared out of the app** — the "⚠️ Scam alert from VAARTA…"
WhatsApp warning and the "📷 Photo" / "🎧 Audio clip" attachment content markers. Those are message
copy, idiomatic in WhatsApp, not interface.

## 5. Text rendering & AI output (bug fix + polish)

- **Lightweight markdown renderer, no dependency.** A pure parser in `core` (unit-tested, TDD)
  that converts common markdown to a clean `AnnotatedString` + block list: inline `**bold**` /
  `*italic*`, bullet lists (`- ` / `* `), numbered lists (`1.`), and links; headings (`#`) become
  a bold line; stray markers (`>`, backticks, residual `*`/`#`) are dropped. A `MarkdownText`
  composable renders it with the type scale.
- Applied to every AI free-text surface: `AssistantBubble`, coach warning, `ArticleScreen`
  summary. Deterministic/engine text (replies, verdicts) is already clean and left as plain text.
- **Defense-in-depth:** also nudge the Gemini prompts to answer in plain text without markdown, so
  the renderer is a guaranteed floor, not the only line of defense.

## 6. Type & spacing system (consistency backbone)

- **Route all text through `MaterialTheme.typography.*`.** Remove inline `fontSize`/`fontWeight`
  from screens; map each usage to a scale role (screen title → `headlineMedium`, section title →
  `titleLarge`, body → `bodyLarge`/`bodyMedium`, eyebrow → `labelSmall`, caption → `bodySmall`).
  Extend the scale only if a genuine role is missing.
- **Spacing scale** (`4/8/12/16/20/24/32`, e.g. `VSpace`) replacing ad-hoc `Spacer`s. Screen
  padding 16→20dp; card inner padding 20dp; section gaps 24dp. Radii already in `Shapes` — applied
  consistently (cards 20, chips full, buttons 14).

## 7. Shared components (build once, reuse)

- **`VaartaButton` / `VaartaSecondaryButton`** — 52–56dp, leading tinted icon + label, real
  pressed state, no emoji. Filled = indigo (brand); destructive/panic = `scam` red.
- **Icon-chip card** — replace bare emoji with a leading rounded tinted square holding the line
  icon. Guardrail so it never breaks "color = risk only":
  - Action/brand cards → chip = **indigo tint bg + indigo icon** (indigo is chrome, on-system).
  - Content rows (feed, conversation list) → chip = **neutral tint**, keeping the feed quiet.
  - `scam` red never used as chip decoration — reserved for the panic/scam context only.
  Soft 1–2dp shadow on light / hairline border on dark; trailing chevron via `ic_chevron_right`.
- **`SourceLink`** — one component (link icon + title, tappable) replacing the 4 duplicated rows.
- **`VaartaBackBar`** — one back affordance (`ic_arrow_left` + optional title) for all sub-screens.
- **`Eyebrow`** — one small label-caps component for section/category labels.
- **Empty state** — centered icon + one muted line, replacing "text in a box".

## 8. Motion

Subtle press-scale (~0.97) + tonal shift on tappable cards/buttons; fade + slight slide on
sub-screen enter/exit. The risk-ring sweep/breathe animation and the "no flashing" rule untouched.

## 9. Simplification — "only what the user needs" (confirm before cutting)

Candidates I want the owner to confirm before removing/merging anything (I will not silently delete
features):
1. **Overlapping emergency guidance in 3 places** — Home's PANIC card, its panic bottom sheet, and
   Help's "If this is happening now". Proposal: the panic sheet and Help share one canonical
   "right-now steps" component; Home's card opens it. Removes duplicated, slightly-divergent copy.
2. **Duplicate web-grounded scam-ID** — `ScamIdCard` and `CoachBubble`'s `scamType` header both
   present the same web-grounded identification. Proposal: keep one presentation.
3. **Article back vs global back** — fold into the shared `VaartaBackBar`.
Anything the owner wants kept stays; this section is a proposal, not a mandate.

## 10. Where it lands (screen inventory)

Home (`HomeScreen.kt`), Conversations (`HistoryScreen` in `MainActivity.kt`), Help
(`HelpScreen.kt`), Article (`ArticleScreen.kt`), Chat (`ConversationScreen.kt` + `ChatView.kt`),
Live hero (`RiskHero.kt` + `MainActivity` live controls), Analyze (`AnalyzeScreen`), Panic sheet,
permission screen (`MainActivity`), bottom nav (`VaartaNav.kt`), overlay (`OverlayService.kt`).
Each: emoji→icon, text→type scale, icon-chip cards, shared components, spacing scale, markdown
render where AI text appears.

## 11. Verification (evidence before "done")

- `gradlew :app:assembleDebug` succeeds; unit tests (~70 + new markdown-parser tests) green.
- Install on the `vaarta_test` emulator; screenshots of **every screen in both light and dark**,
  before/after, as completion evidence.
- **Emoji-free grep** over `*.kt` interface strings (only §4.3 whitelist allowed).
- **No-hardcoded-size check** — spot-grep for `fontSize =` in screen files (should be gone except
  inside the type/theme definitions).
- Markdown: verify a known markdown-heavy AI answer renders clean (no `**`/`#`) on device.
- TalkBack: icon-only controls keep `contentDescription`; decorative icons are null.

## 12. Rollout order (feeds the implementation plan)

1. **Foundation** — author glyphs; add spacing tokens, `VaartaIcon`, `MarkdownText` + parser
   (TDD), `VaartaButton`/`VaartaSecondaryButton`, `SourceLink`, `VaartaBackBar`, `Eyebrow`,
   icon-chip card.
2. **Home + bottom nav** (highest-visibility surface).
3. **Help + Article** (+ markdown in Article summary).
4. **Conversations list + Chat** (+ markdown in AssistantBubble, source links).
5. **Live hero + Analyze + Panic sheet + permission screen + ChatView markers.**
6. **Overlay.**
7. **Type-scale sweep** — ensure no screen still hardcodes sizes.
8. **Full emulator verification** (light + dark), emoji/size/markdown checks, status + commit.
