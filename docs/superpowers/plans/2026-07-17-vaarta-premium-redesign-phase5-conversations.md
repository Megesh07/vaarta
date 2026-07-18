# VAARTA Premium Redesign — Phase 5: Conversations v2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Conversations v2 (spec §6.6): clean header + result-count eyebrow + kebab sheet
(retention + Delete all move off the prime scroll space), an extended "New chat" FAB, the shared
row grammar (single-line title + verdict pill + relative time, chevron only), swipe-to-delete
with an Undo snackbar, and the encryption note shrunk to a lock caption at the list foot.

**Architecture:** `HistoryScreen` (in `MainActivity.kt`) rewritten; two new glyphs
(`ic_more_vert`, `ic_lock`); strings extracted. No ViewModel change (`delete`/`deleteAll`/
`retentionDays`/`setRetentionDays` already exist). Swipe uses M3 `SwipeToDismissBox`; Undo defers
the actual DB delete via a pending-id set so restore needs no re-insert.

**Global Constraints:** as prior phases. Risk red never used as row decoration (verdict pill uses
the risk ramp only when a call is actually scored).

---

### Task 1: Glyphs + strings

- `ic_more_vert.xml`, `ic_lock.xml` (house style: 24dp, stroke 1.75, round, fill-none).
- strings: `conv_title`, `conv_count` (plural-ish "%1$d conversations"), `conv_new_chat`,
  `conv_empty_title`, `conv_empty_body`, `conv_encrypted`, `conv_menu_title`, `conv_autodelete`,
  `conv_keep`, `conv_7d`, `conv_30d`, `conv_delete_all`, `conv_deleted`, `conv_undo`,
  `conv_section_week`, `conv_section_earlier`.

### Task 2: Conversations v2 screen

- Root `Box`; header row (title + count eyebrow + kebab `IconButton`); `LazyColumn` (This week /
  Earlier sections) each row wrapped in `SwipeToDismissBox` (`key(session.id)`); extended FAB
  bottom-end; `SnackbarHost` bottom-center; lock caption as the final list item.
- **Swipe/undo:** a `remember { mutableStateListOf<Long>() }` of pending ids; displayed list =
  `sessions - pending`; on dismiss → add id, show snackbar; `ActionPerformed` (Undo) → remove id
  (row returns); else → `historyVm.delete(id)`.
- **Kebab sheet:** `ModalBottomSheet` with retention chips + a destructive "Delete all" row.
- Row grammar: 44dp tinted source circle (chat=indigo, recording=neutral, live=verify-blue),
  title `titleMedium` `maxLines=1` ellipsis, meta line (verdict pill + relative time) `maxLines=1`,
  trailing chevron.

### Task 3: Verify + wrap

Build; emulator screenshots (populated list, swipe reveal, undo snackbar, kebab sheet, empty
state), light + dark; commit; PROJECT_STATUS.
