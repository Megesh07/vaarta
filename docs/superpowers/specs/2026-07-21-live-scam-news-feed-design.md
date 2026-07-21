# Live scam-news feed — design

**Date:** 2026-07-21
**Status:** Approved (design), pending implementation plan
**Area:** Home "Trending scams in India" feed + article view

## 1. Problem

The current "Trending scams in India" feed shows a fixed set of AI-*synthesized* category
cards (Digital arrest, Courier, KYC, Utility, Investment, Job/Task), each expanded on tap into
an AI-written summary. It is not what was intended:

- The cards are a **fixed default list**, not real-time trending content.
- They are **AI summaries about scam categories**, not **real news articles** from the internet.
- Tapping in gives another **summary**, never the actual article.
- The cards use a flat category illustration and **feel generic/boring**.

The intended experience (Google-Discover style): a **live feed of real, current scam-related news
articles** — real headline, real source, the article's own eye-catching preview image — refreshed
each time the user opens Home. Tapping a card opens the **real article** inside the app, with an AI
layer that **summarizes it and answers questions about that specific article**, plus a row of
**related articles**.

## 2. Goal & non-goals

**Goal:** Replace the synthesized-category feed with a real, live, refreshing feed of genuine
current scam news articles (real URL + real preview image + real source + short AI hook), and an
article view that shows the real article with AI summarize/ask + related articles.

**Non-goals (YAGNI / out of scope):**
- No paid news API. Everything stays on the existing $0 Gemini free-tier + web grounding.
- No user accounts, no server, no push notifications for new articles.
- No offline article caching beyond the existing instant-paint cache + bundled seed fallback.
- No per-article comment/like/save features.
- The live-call copilot, chat, and recorded-call paths are unchanged by this work.

## 3. Constraints

- **$0:** no paid APIs, no API keys beyond the existing Gemini key.
- **Fail-closed & no fabrication (existing safety spine):** never surface a URL or an image the
  model merely *typed*. Real URLs come only from Gemini grounding metadata; real images come only
  from the real article page's own `og:image`.
- **Deadline:** single focused implementation; reuse existing pieces
  (`summarizeArticle`, `chat`, `ScamCover`, `AwarenessStore`) wherever possible.

## 4. Approved decisions

1. **Feed card = real article** — real preview photo (`og:image`) + real headline + source +
   one-line AI hook + scam category; tapping opens the real article.
2. **Images via `og:image` scrape** — fetch the real article page, read `og:image`, load with Coil.
   ~80-90% coverage; cards without an image **fall back to the category illustration**
   (`ScamCover`). Images load **async** (text first, photo fills in).
3. **Tap-in = real article in-app (WebView)** + a bottom **AI bar** ("Summarize" / "Ask about this
   article") + a **"Related scams" row** of other current real articles.
4. **Refresh** on every Home open + pull-to-refresh; cache is only for instant first paint.

## 5. Architecture & components

### 5.1 Data model (`core:reasoning/AwarenessModels.kt`)

`AwarenessCard` gains real-article fields, all nullable/blank-safe so the bundled seed and the
fail-closed paths still construct a valid card:

```
data class AwarenessCard(
    val title: String,            // real headline when live; category title for seed
    val oneLine: String,          // short AI hook
    val scamType: String = "",    // category tag (drives the illustration + Related grouping)
    val sourceName: String = "",  // publisher/domain (from grounding chunk)
    val url: String? = null,      // REAL grounding-cited article URL; null for seed cards
    val imageUrl: String? = null, // REAL og:image; null → fall back to ScamCover illustration
)
```

Invariant: `url`/`imageUrl` are non-null **only** when sourced from grounding metadata / the real
page. A seed or fail-closed card has `url == null` and renders exactly as today (illustration +
AI-summary page on tap).

### 5.2 Feed fetch (`GeminiClient.awarenessFeed()` + `AwarenessWireParser`)

1. One grounded call (`google_search`, no response schema — unchanged transport) asking for the
   scams Indians are being hit with **right now**, requesting a JSON array of
   `{headline, oneLine, scamType, sourceName}` items for **real, recent, cited** articles.
2. Extract the grounding articles from `candidates[0].groundingMetadata.groundingChunks[].web`
   (real `uri` + `title`) — the existing `extractSources()` path.
3. **Attach real URLs from grounding, never from model text.** Pair each model item to a grounding
   chunk (best-effort match on source/title). Rules:
   - Match found → card gets that chunk's real `uri` + `title`-derived `sourceName`.
   - No confident match → **surface the grounding article directly**: real `title` as headline,
     `scamType` inferred/blank, no fabricated caption. (Real article wins over a pretty caption.)
   - A model item with **no** backing grounding chunk is **dropped** (would be a typed/fake link).
4. Result: a list of cards each backed by a real grounding URL. Fail-closed to empty → caller
   falls back to cache, then seed (both render as today).

### 5.3 Image resolution (`OgImageResolver`, new, app module)

- Input: a card's real `url`. Follows redirects (the grounding `uri` is a Google redirect that
  resolves to the publisher), GETs the HTML (small read cap), parses
  `<meta property="og:image" content="…">` (and `twitter:image` fallback).
- Returns an absolute image URL or null. **Fail-closed:** any error/timeout/missing tag → null →
  card keeps the illustration.
- Runs **per card, in parallel, off the main thread**, after the feed text is already shown, and is
  **cached** (keyed by article URL) so re-opening Home doesn't re-scrape.
- Pure HTML→og:image parsing lives in `core:reasoning` (unit-testable, no network); the network
  fetch wrapper lives in the app module.

### 5.4 Feed refresh (`AwarenessViewModel`)

- Keep instant-paint from cache/seed. Change staleness so a Home open triggers a fresh grounded
  fetch (short freshness window) and expose an explicit `refresh()` for pull-to-refresh.
- On a successful live fetch: kick off async og:image resolution for the new cards, updating each
  card's `imageUrl` as it resolves.

### 5.5 Home feed UI (`HomeScreen`)

- Card renders: `imageUrl` via Coil when present, else `ScamCover(scamType title)`; headline;
  `sourceName` line; AI one-line hook. Add pull-to-refresh + a subtle refreshing indicator.
- Replace the momentary empty-state text during first fetch with a **loading skeleton** (fixes the
  "stories will appear once you're online" flash observed on the emulator).

### 5.6 Article view (`ArticleScreen` → v3)

- **Card has a real `url`** → in-app **WebView** renders the real article. Bottom **AI bar**:
  - "Summarize" → `GeminiClient.summarizeArticle(headline, scamType)` shown in a sheet/expandable.
  - "Ask about this article" → existing `onAskAbout(seedContext)` → `chat()` seeded with the
    article's headline + summary.
  - WebView load failure → "Open in browser" (existing `onOpenUrl`).
- **"Related scams" row** — other current feed cards, **same `scamType` first**, then the rest;
  excludes the current article. Pure selection function (unit-testable). No extra network call.
- **Card has no `url`** (seed/offline) → keep today's AI-summary page unchanged.

## 6. Data flow

```
Home open
  → AwarenessViewModel.load(): show cache/seed instantly
  → awarenessFeed() (grounded)            [real headlines + grounding URLs]
  → cards with real url, imageUrl=null    [render text immediately]
  → OgImageResolver per card (parallel)   [imageUrl fills in async, cached]
Tap card (real url)
  → ArticleScreen v3: WebView(real url) + AI bar + Related(other cards)
      Summarize → summarizeArticle(); Ask → chat(seedContext)
Tap card (no url / seed)
  → ArticleScreen (today's AI summary page)
```

## 7. Error handling (all fail-closed, never empty, never fake)

| Failure | Behavior |
|---|---|
| Grounded feed call fails / empty | Keep cache, else bundled seed (illustrated category cards). |
| Model item has no grounding-backed URL | Drop that item (never show a typed/fake link). |
| `og:image` missing / blocked / timeout | Card keeps the category illustration. |
| WebView fails to load the article | "Open in browser" fallback (external). |
| `summarizeArticle` / `chat` fail | Existing fallbacks (card one-line / safe chat message). |

## 8. Testing

- **`core:reasoning` (pure, no network):**
  - Feed parse: real URL attached only from grounding; model-typed-only item dropped;
    unmatched grounding article surfaced with its real title.
  - `og:image` HTML parse: extracts `og:image`, falls back to `twitter:image`, returns null when
    absent; ignores tags inside comments/strings.
  - Related-article selection: same-category-first ordering, current article excluded, bounded count.
- **Emulator drive-through:** feed shows real photos + headlines; tap opens a real article in-app;
  Summarize/Ask/Related work; a no-image article falls back to illustration cleanly.

## 9. Primary risk

**Model-hook ↔ grounding-article alignment.** Grounding reliably gives real URLs + titles, but
matching each to the model's one-line caption/category is imperfect. Mitigation (§5.2): when a
confident match is missing, show the **real grounding article using its real title** rather than a
pretty-but-unbacked caption. Worst case = fewer perfectly-captioned cards, never a fake one.

Secondary: `og:image` coverage and in-app WebView rendering of heavy/paywalled sites — both have
explicit fail-closed fallbacks (illustration / open-in-browser), so neither can break the feed.

## 10. Scope note

This is the largest single item in the current push. It reuses `summarizeArticle`, `chat`,
`ScamCover`, and `AwarenessStore`; the genuinely new parts are: real-URL plumbing in the feed
parser, `OgImageResolver` + Coil, the WebView reader, the Related row, and refresh-on-open. The
implementation plan will sequence these so the feed (real headlines + links) lands first and
images/WebView build on top.
