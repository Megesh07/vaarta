# VAARTA Premium Redesign — Phase 3: Home v2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home v2 (spec §6.1 + §5.2): brand header with an honest status chip, a slim panic
banner, the wide-primary + two-half-tiles action grammar (subtitle text cut), and the magazine
feed — featured cover-banner card + compact thumbnail rows with the body-preview line removed.

**Architecture:** `HomeScreen.kt` is restructured (not re-skinned); one new shared component
(`ActionTile`) in `VaartaComponents.kt`; Home's copy moves to `strings.xml` per the Phase-1
convention. `VaartaNav` additionally passes the feed `origin`.

**Tech Stack / Global Constraints:** as Phase 1 plan. Red stays panic-only; tiles are
indigo/neutral chrome.

---

### Task 1: Strings + `ActionTile` component

- `app/src/main/res/values/strings.xml`: add `home_*` strings (status chip, panic, actions,
  feed header/origin/empty) — exact set in the implementation.
- `VaartaComponents.kt`: add
  `@Composable fun ActionTile(@DrawableRes icon: Int, title: String, onClick: () -> Unit, modifier: Modifier = Modifier)`
  — panel card, 40dp icon chip over a title (`titleMedium`, maxLines 2), min height 104dp,
  press-scale like `IconChipCard`.

### Task 2: HomeScreen v2

Structure top→bottom (all copy via `stringResource`):
1. Header row: "VAARTA" `headlineMedium` + status pill (`indigoTint` surface, sparkle icon +
   "AI ready" when `aiConfigured`, shield icon + "On-device" otherwise). Tagline deleted.
2. Panic banner: red card, `heightIn(min = 72.dp)`, single-line headline + white chevron, no
   subtitle; full instruction kept in `contentDescription`.
3. Actions: `IconChipCard` (wide, live help, keeps its one-line subtitle) + `Row` of two
   `ActionTile`s (Ask VAARTA / Check a recording), `weight(1f)` each. "Check a recording" tile
   hidden when `!aiConfigured` (Ask tile widens to full row).
4. Feed: header row ("Trending scams in India" + refresh spinner) + quiet origin caption
   (LIVE → "Live from the web · just now", CACHED → "From your last refresh", SEED → built-in
   note); **featured card** for the first item (16:7 `ScamCover` banner with an overlaid category
   pill, title `titleLarge`, source line) and **compact rows** for the rest (56dp thumb, eyebrow,
   title max 2 — the `oneLine` body is deleted from compact rows).
5. `VaartaNav.kt`: pass `feedOrigin = feed.origin`.

### Task 3: Verify + wrap

Build; emulator screenshots of Home top + feed (light); TalkBack semantics unchanged for panic;
commit; PROJECT_STATUS entry.
