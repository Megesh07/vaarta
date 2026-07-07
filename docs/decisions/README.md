# Architecture Decision Records (ADRs)

This directory is the prescribed home for every **measured or scope-level decision** in
VAARTA. Its creation was required before implementation by
[ARCHITECTURE_FREEZE_REVIEW.md](../ARCHITECTURE_FREEZE_REVIEW.md) Task 3, and it is
enforced by [IMPLEMENTATION_GUARDRAILS.md](../IMPLEMENTATION_GUARDRAILS.md) (NEVER #8,
ALWAYS #1) and [CLAUDE_CODE_RULES.md](../CLAUDE_CODE_RULES.md) §1, §10.

An ADR is **required** for: ASR checkpoint selection per language, AEC defaults per OEM
class, engine weight tuning, milestone scope locks, and any reopening of a LOCKED
decision (FOUNDATION_AUDIT.md §2) or a decision in ARCHITECTURE_FREEZE_REVIEW.md.
A code comment or a PR description is **not** an ADR.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-mvp-scope-lock.md) | MVP scope lock — hackathon / portfolio build, $0 constraint | Accepted |
| [0002](0002-live-ai-voice-assist.md) | Live AI voice-assist — mic+speakerphone, Gemini Live, amends NEVER #3 | Accepted |

## Process

1. Copy `0000-adr-template.md` to `NNNN-short-title.md` (next free number).
2. Fill **Context** with evidence, not opinion; then **Decision**, **Consequences**, **Alternatives**.
3. Status flows `Proposed → Accepted` on merge. A superseded ADR stays in the tree, is
   marked `Superseded by ADR-XXXX`, and links forward. ADRs are never deleted.
4. Add a row to the index above in the same change.
