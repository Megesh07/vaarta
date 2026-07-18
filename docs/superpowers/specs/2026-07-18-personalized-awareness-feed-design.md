# Personalized Awareness Feed — Design Spec

**Date:** 2026-07-18
**Status:** Approved direction (this doc is the written spec for user review)
**Owner branch:** `vaarta-v2-ux`
**Depends on:** nothing from the live-call spec; independently shippable. Build order: after
2026-07-18-live-call-core-hardening-design.md.

## 1. Goal

Make the Home awareness feed feel personal instead of one-size-fits-all, MS-Edge-daily-briefing
style, with **zero manual work for the user**. Two signals, both automatic:

1. **Exposure** — scam types this user has actually encountered (their own call/chat history).
2. **Region + language** — scams currently trending where the user is, in their language
   (language already exists via `AppLanguage`).

## 2. Privacy boundary (hard rule)

The user's history **never leaves the device**. Exposure-based ranking is computed locally over
the Room DB. Only two coarse, low-sensitivity values may appear in the Gemini feed prompt:
UI language (already sent today) and state/region name (e.g. "Tamil Nadu"). Never session
contents, scam-type history, timestamps, or counts.

## 3. Signals

### 3.1 Exposure (on-device)

New DAO query on the existing `CallSessionEntity` table (no schema change):

```sql
SELECT scam_type, COUNT(*) AS n, MAX(started_at_ms) AS last_seen
FROM call_sessions
WHERE scam_type IS NOT NULL AND final_level != 'OBSERVING'
GROUP BY scam_type
```

Only sessions that actually raised concern count (`final_level != 'OBSERVING'`), so a benign call
mentioning "parcel" doesn't personalize the feed forever.

### 3.2 Region (one-time automatic, overridable)

No GPS, no permission prompt. Default region = SIM/network country + `TelephonyManager` network
operator region where resolvable; fallback = unset. One optional row in Help ("Your state — for
local scam alerts") with a state picker; stored in `SharedPreferences`
(`vaarta_awareness_prefs`, existing file). Unset region = today's national feed, no nagging.

## 4. Ranking (deterministic, on-device)

Feed cards (seed or live-fetched, unchanged pipeline) are scored locally at render time:

```
rank = base_order
     + 3 if card.scamType fuzzy-matches an exposure row      (personal relevance)
     + 1 extra if that exposure's last_seen < 30 days        (recency)
```

Stable sort, ties keep fetch order. The featured (top) card = highest rank. Pure function
`rankAwarenessCards(cards, exposure, nowMs)` in `core/reasoning` or `app/feed` — unit-testable
with no Android deps. No LLM in the ranking path (invariant: personalization can't hallucinate).

Fuzzy match = the existing `coverKeyForScamType` normalization (already maps free-text scam types
to canonical families) — reuse, don't reinvent.

## 5. Region-aware live fetch

`AwarenessPrompt` gains one optional line when region is known:

```
Prioritize scams currently reported in <STATE>, India, but include nationally trending ones too.
```

Cache key extends from per-language to per-language+region (`AwarenessStore`), so switching state
invalidates correctly. Staleness window stays 12 h. Seed fallback unchanged (national, English —
existing known limitation).

## 6. UI

- No new screens. The feed simply reorders; the featured card may show a small "Seen in your area"
  or "Because you encountered this" one-line attribution (localized, from `strings.xml`) so the
  personalization is legible, not creepy-silent.
- Help gains the single optional state-picker row (§3.2).
- Empty history + unset region ⇒ feed identical to today (no regression for new users).

## 7. Edge cases

| Case | Outcome |
|---|---|
| Fresh install, no history | Ranking adds 0 everywhere → fetch order → today's feed. |
| History exists but all OBSERVING | Exposure query returns nothing → today's feed. |
| scamType free-text variants ("UPI fraud" vs "UPI refund scam") | Normalized via `coverKeyForScamType` before matching. |
| User clears history | Exposure recomputed live from DB → personalization fades automatically. |
| Region picker set then unset | Cache key reverts; national feed on next refresh. |
| Language switch | Existing per-language cache behavior; region carries across languages. |
| Live fetch fails | Seed cards still ranked by exposure (personalization works offline). |

## 8. Testing

- `rankAwarenessCards`: TDD unit tests — empty exposure (identity order), single match boost,
  recency bonus, stable ties, normalization matching.
- DAO query test (existing Room test infra): OBSERVING sessions excluded; grouping correct.
- Prompt test: region line present iff region set; never contains history data (assert the prompt
  string contains no scam-type values sourced from the DB).
- Emulator: fresh install shows today's feed; after a HIGH_RISK demo call with scamType set, the
  matching card is featured with attribution line; in all three languages.

## 9. Non-goals

- No GPS/location permission, no behavioral profiles, no server-side anything, no per-user LLM
  prompt containing history, no notification/badge system, no A/B logic.
