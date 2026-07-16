# VAARTA — "Calm Guardian" Premium UI Pass

**Date:** 2026-07-16
**Status:** Design — awaiting owner review before planning
**Relationship to prior specs:** refines the *presentation layer* of the v2 shell
(`2026-07-14-vaarta-v2-intelligence-ux-design.md`). It does **not** change information
architecture, navigation, copy, or any logic — purely visual/UX polish.
**Scope-lock (ADR-0001):** unchanged — strict $0, no new paid deps, offline, sideload,
hackathon/portfolio MVP.

---

## 1. Why this exists (the problem)

The design *foundation* is strong — the semantic color tokens (`Color.kt`), the WCAG-AA risk
ramp, the restrained Apple-style type scale (`Type.kt`), and the "color is reserved for risk"
rule are all done right. But a thin, pervasive layer on top reads as unpolished / "hackathon":

1. **Emojis are used as UI icons throughout** — bottom nav (🛡️ 💬 🆘), every primary button
   (📞 🌐 📝 🔔 🎙️ 🎧), the chat composer (🎤 🖼️ 🎧), Home cards, source links (🔗). Emojis
   render differently per OEM, sit at inconsistent baselines, and carry cartoonish color that
   fights the "color = risk only" discipline. This is ~80% of the cheap feeling.
2. **No finished icon system** — the project already ships 17 hand-drawn line glyphs
   (`res/drawable/ic_*`), but the emoji spots never got real glyphs, so the system is half-built.
3. **Ad-hoc spacing** — arbitrary `Spacer(8/10/12.dp)` instead of a scale; buttons and cards lean
   on stock Material with no press feedback or depth story.

## 2. Goals / Non-goals

**Goals**
- Zero emoji in the *interface*. Every UI glyph is a real vector icon in one coherent style.
- One named aesthetic — **Calm Guardian** — executed on every screen.
- A premium, trustworthy, elder-legible feel: quiet monochrome chrome, color only for risk,
  generous space, soft depth, gentle motion.
- No regressions: build + existing 70 unit tests stay green; both light and dark verified.

**Non-goals**
- No change to information architecture (3 tabs: Home / Conversations / Help), navigation flow,
  or sub-screen structure.
- No copy rewrites, no behavior/logic changes.
- No new color *ramp* (tokens are good; may add 1–2 neutral elevation/shadow tokens only).
- Not touching the deterministic engine, AI, or data layers.

## 3. The aesthetic — "Calm Guardian"

Chrome is near-monochrome and quiet; **color appears only for risk**; content outranks chrome
(deference). Apple-style restraint, but with elder-first overrides layered on top: ≥48dp tap
targets, ≥15–17sp body text, contrast above iOS defaults, no thin low-contrast text. Motion is
subtle and never flashes (respects the existing safety rule).

## 4. Iconography (the core change)

### 4.1 Decision: hand-author in the existing house style
Standardize on the **existing Lucide-style house system** already in `res/drawable`:
`android:width/height="24dp"`, `viewportWidth/Height="24"`, `strokeWidth="1.75"`,
`strokeLineCap/Join="round"`, `fillColor="#00000000"`, and the stroke color overridden at the
call site by `Icon(painter, tint = …)`. New glyphs are authored as vector XML in this exact
convention.

**Rejected:** `androidx.compose.material:material-icons-extended`. It adds a dependency and its
filled/heavier optical style clashes with the shipped thin-line signal icons — a mixed set reads
worse than the emoji. Hand-authoring keeps $0/offline, gives full control of weight/optical size,
and guarantees the nav/button glyphs look like the same hand that drew the risk-signal glyphs.

### 4.2 New glyphs to author (~20)
Chrome/nav: `ic_nav_shield` (Home), `ic_nav_chat` (Conversations), `ic_nav_help` (life-buoy).
Actions: `ic_phone`, `ic_globe`, `ic_file_text`, `ic_bell` (or megaphone), `ic_headphones`,
`ic_image`, `ic_download`, `ic_alert_triangle`, `ic_sparkle` (AI), `ic_check`, `ic_chevron_right`,
`ic_close`, `ic_link_external`, `ic_siren` (panic). Reuse existing `ic_mic`, `ic_history`,
`ic_shield_x`, `ic_act_*`, `ic_sig_*` where they already fit.
Every glyph verified for legibility at 20/24/28dp.

### 4.3 Tinting helper
A small `VaartaIcon(res, tint, size)` wrapper (or a shared convention) so no icon is ever drawn
with a raw color — tint always comes from tokens (`ink` / `muted` / an intent color), keeping
theming and dark mode correct.

### 4.4 Emoji that stays (content, not chrome)
Emoji remains **only in user-authored content strings that get shared out** of the app — the
"⚠️ Scam alert from VAARTA…" WhatsApp warning (`ArticleScreen`, `WARN_FAMILY_MESSAGE`) and the
attachment content markers ("📷 Photo" / "🎧 Audio clip" saved into a sent bubble). Those are
message copy, idiomatic in WhatsApp, not interface elements.

## 5. Spacing & rhythm

Introduce a spacing scale (`4 / 8 / 12 / 16 / 20 / 24 / 32`, e.g. `VSpace.xs…xxl`) and replace
ad-hoc `Spacer` values with it. Screen horizontal padding 16→20dp; card inner padding 20dp;
section gaps 24dp. Radii already exist in `Shapes` — apply consistently: cards 20, chips full,
buttons 14.

## 6. Components (refine, do not rebuild)

- **Buttons** — `VaartaButton` (filled) + `VaartaSecondaryButton` (tonal/outlined): 52–56dp tall,
  leading tinted icon + centered label, real pressed state, no emoji. Filled = indigo (brand);
  destructive/panic = `scam` red.
- **Cards + icon chip** — the biggest visual lever. Replace bare 28sp emoji with a leading
  **icon chip**: a rounded tinted square holding the line icon. Guardrail so it never breaks
  "color = risk only":
  - Action/brand cards (Help me on a call, Ask VAARTA, Check a recording) → chip = **indigo tint
    bg + indigo icon** (indigo is chrome, on-system).
  - Content rows (trending-scam feed, conversation list) → chip = **neutral tint** (faint), so the
    feed stays quiet.
  - `scam` red is never used as a chip decoration — reserved for the panic/scam context only.
  Cards get a soft 1–2dp shadow on light / hairline border on dark; trailing `›` becomes
  `ic_chevron_right`.
- **Bottom nav** — real line icons; Material3's selected pill + indigo tint for the active tab,
  `muted` for the rest.
- **Empty states** — centered icon + one muted line, replacing the current "text in a box".

## 7. Motion

Subtle press-scale (~0.97) + tonal shift on tappable cards/buttons; fade + slight slide on
sub-screen enter/exit. The risk-ring sweep/breathe animation and the "no flashing" rule are
untouched.

## 8. Where it lands (screen inventory)

Each screen gets: emoji→icon, icon-chip cards where cards exist, refined buttons, spacing scale.
- **Home** (`HomeScreen.kt`) — header, panic card (siren icon, red kept), 3 action cards (icon
  chips), trending feed rows (neutral chips + external-link), panic bottom sheet.
- **Conversations** (`HistoryScreen` in `MainActivity.kt`) — list rows with source icon chips
  (chat/recording/call), new-chat button, empty state.
- **Help** (`HelpScreen.kt`) — section cards, all four buttons, numbered step badges (keep), the
  "call 1930" red button.
- **Article** (`ArticleScreen.kt`) — source links, "Ask VAARTA" / "Warn my family" buttons.
- **Chat** (`ConversationScreen.kt`) — composer icons (mic/image/audio), context header
  (globe/download), empty-state shield.
- **Live** (`RiskHero.kt`, `MainActivity` live controls) — "flagged from web" alert-triangle,
  start/family/analyze buttons.
- **Analyze** (`AnalyzeScreen`), **Panic sheet**, **permission screen** (`MainActivity`),
  **overlay** (`OverlayService.kt`) — icon + button/spacing pass.
- **ChatView.kt** — inline source/flag/verdict markers (🔗 ⚠ 🌐 🛡️ ❝❞) → icons / typographic
  quotes.

## 9. Verification

- `gradlew :app:assembleDebug` succeeds; existing unit tests (~70) stay green.
- Install on the `vaarta_test` emulator; capture screenshots of **every screen in both light and
  dark**, before/after, as the completion evidence.
- Confirm **zero emoji remain in interface strings** (grep the emoji ranges over `*.kt`, allowing
  only the whitelisted content strings in §4.4).
- Spot-check TalkBack content descriptions still read (icons are decorative where a text label is
  adjacent; `contentDescription` preserved on icon-only controls).

## 10. Rollout order (feeds the implementation plan)

1. Foundation: author the ~20 glyphs; add spacing tokens + `VaartaIcon`; build `VaartaButton` /
   `VaartaSecondaryButton` / icon-chip card primitives.
2. Home + bottom nav (highest-visibility surface).
3. Help + Article.
4. Conversations list + Chat composer.
5. Live hero + Analyze + Panic sheet + permission screen + ChatView inline markers.
6. Overlay.
7. Full emulator verification (light + dark) + emoji-free grep + status/commit.
