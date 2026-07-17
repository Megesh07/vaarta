# VAARTA — Premium Redesign to the Core ("Calm Guardian 2.0")

**Date:** 2026-07-17
**Status:** Design — awaiting owner review before planning/implementation
**Relationship to prior specs:** supersedes the presentation-layer scope of
`2026-07-16-vaarta-ui-premium-pass-design.md`. That pass fixed tokens/icons/markdown but kept the
information architecture and layouts untouched — and the owner's verdict is that the result still
reads overloaded, repetitive, text-heavy, and not premium. This spec redesigns **structure**, not
just skin: what appears on each screen, where, once, and how content earns visual richness.
**Scope-lock (ADR-0001):** unchanged — strict $0, no paid deps, offline-first, sideload, MVP.
**Engine/AI/data layers:** untouched, except one prompt-shape change (§7) that is additive and
fails closed.

---

## 1. Diagnosis — what is actually wrong (evidence: live emulator screenshots, 2026-07-17)

Screenshots captured this session (`01-home` … `07`): Home, Live, Help, Conversations, panic
sheet, Article loading + loaded.

### 1.1 The same action lives in 3–4 places (the "overloaded, repeated" feeling)

| Action | Where it appears today |
|---|---|
| Start live help | Home card · panic sheet · Live screen ("Start live protection" **and** "Use as a floating window") |
| Analyze a recording | Home card · panic sheet · Live screen · Analyze screen |
| Call 1930 | panic sheet · Help "If this is happening now" · Help step 2 of the lost-money list |
| Warn family / share warning | Help section · Article button · Live "Alert my family" · Analyze "Share this warning" |
| Ask VAARTA (new chat) | Home card · Conversations "New chat" · Article "Ask VAARTA about this" |
| Emergency steps copy | panic sheet (4 steps) · Help "happening now" · Help "already lost money" (7 steps) — three overlapping scripts |

The Live screen alone stacks **five** buttons (Start, Floating window, Try a demo, Reset, Analyze
a recorded call) plus a five-line AI-consent paragraph. Every duplicated entry point costs a card,
and every card costs a title + a subtitle sentence — this is where "text text text" comes from.

### 1.2 Text does all the work; imagery does none
- Home: 11 text blocks above the fold; every card is the identical white rounded row.
- Feed cards all use the **same gray alert-triangle** — a news feed with no news imagery.
- Article: one undifferentiated wall of body text. The AI's preamble ("Here is an explanation
  of…") leaks into the UI; "What it is / How to spot it / What to do" render as plain body lines,
  not sections. While loading, the screen is a blank void with a disabled gray button.
- Help: five cards × (title + paragraph + full-width button) + a 7-step text wall.

### 1.3 Concrete alignment/placement defects (all reproduced on the emulator)
1. **Article screen ignores the status bar** — the back arrow overlaps the clock
   (`ArticleScreen` has no `statusBarsPadding()`).
2. **Status-bar icons are white on a light background** (invisible clock/wifi on Home) — system
   bar appearance is never set to dark-icons-on-light.
3. **System back exits the app from any sub-screen** — sub-screens only wire their own arrow;
   there is no `BackHandler`, so predictive/system back kills the Activity instead of popping.
4. **Conversations rows break their grid** — the trailing date wraps to three lines
   ("16 Jul / 2026, / 11:41 PM"), the red verdict line and date fight for one row.
5. **"Delete all" + retention chips sit above the content** — destructive/settings chrome
   occupies the prime first-scroll zone of a content list.
6. **Live idle state lies** — shows "Listening & checking" + score 0 before anything started.
7. Article pins **two stacked full-width buttons** permanently over the content (~20% of screen).

### 1.4 Why it still doesn't feel premium
Every surface is the same flat white 16dp-radius card on the same lavender ground with the same
44dp icon-chip — no hero, no featured content, no depth story, no brand moment. The default MD3
`NavigationBar`, an `OutlinedTextField` + text "Send" button composer, and `❝ ❞` quote glyphs
read stock. Premium apps have **one dominant element per screen and visible restraint everywhere
else**; VAARTA currently has six equal elements per screen.

## 2. Goals / Non-goals

**Goals**
1. **One action, one home** — every capability has exactly one canonical entry point; every other
   surface links to it instead of cloning it (§4).
2. **Images where content lives** — a bundled, $0, offline illustration system gives every scam
   category a distinctive cover; the feed becomes a magazine, the article gets a real header (§5).
3. **Structure over prose** — the article renders as designed sections (checklists, steps), never
   one markdown blob; AI preamble never shows (§7).
4. **Alignment perfection** — a single row/tile grammar with hard rules (one-line trailing meta,
   baseline grid, edge-to-edge with correct system-bar treatment, BackHandler) (§6, §8).
5. **A premium read at first glance** — one hero per screen, featured-vs-compact card hierarchy,
   quiet chrome, real motion (§8, §9).
6. Elder-legible throughout: ≥48dp targets, ≥15sp body, AA contrast — unchanged and re-verified.

**Non-goals**
- No feature additions or removals (consolidation of *entry points* only — every capability
  stays reachable).
- No engine, scoring, safety-rail, or data-layer changes.
- No new dependencies; no network imagery in the core path (an OG-image fetch is a flagged
  stretch, §5.4).
- The risk color ramp and "loud color = risk only" rule stay locked.

## 3. The aesthetic — Calm Guardian 2.0

Same named aesthetic, executed to the core instead of veneered:

- **Quiet chrome, loud danger.** Ground and cards stay near-monochrome; the indigo family is the
  only brand voice; the warm risk ramp appears **only** when risk is real. Unchanged, now actually
  enforced by layout (the panic banner is the single red element on Home).
- **One hero per screen.** Home = panic + featured story; Live = the ring; Article = the cover;
  Conversations = the list itself; Help = the 1930 action. Everything else visually recedes.
- **Editorial content, instrumental actions.** Content (feed, article) gets imagery, big type,
  and generous space — like a well-set magazine. Actions get compact, consistent, boring rows —
  like a well-made tool. The current design treats both identically; that's the sameness.
- **Depth via layers, not shadows.** Ground `bg` → card `panel` → chip `track` + hairlines; a
  single soft elevation on light. Gradients allowed **only** inside illustration covers and the
  brand header (indigo family, never risk colors).

## 4. Information architecture — the "one home" rule

Every row below is a *move or delete of an entry point*, not a feature change.

### 4.1 Canonical homes

| Capability | Canonical home | Everywhere else |
|---|---|---|
| Live in-call help | **Live screen** (from Home tile) | Panic sheet gets a compact "Get live help" **link row**, not a button clone |
| Analyze a recording | **Analyze screen** (from Home tile) | Removed from Live screen and panic sheet |
| Ask VAARTA chat | **Chat** (from Home tile + Conversations FAB) | Article keeps "Ask about this" (it seeds context — genuinely different) |
| Call 1930 | **Panic sheet** (in the moment) + **Help** (reference) | Removed from inside the lost-money steps card (step text links instead) |
| Warn family | **Help** | Article/Live/Analyze keep *contextual* shares (they share specific content, not the canned text) — but as a top-bar share icon, not a stacked button |
| Emergency steps | **One shared component** (`RightNowSteps`) | Panic sheet and Help render the same component; the copy exists once |
| Demo call | **Live screen, as a text link** "Watch how it works" | No longer a peer button |
| Reset | **Gone from idle Live** (nothing to reset); appears only during/after a session |
| AI consent toggle | **One compact row on Live** (icon + one line + switch + "Learn more" expander) | The 5-line paragraph moves into the expander |
| Retention + Delete all | **Overflow sheet on Conversations** (kebab in header) | Out of the list's prime space |

### 4.2 Screen inventory after the cut

- **Home:** panic banner · 3 action tiles · feed. Nothing else.
- **Panic sheet:** `RightNowSteps` (4 steps) · "Call 1930 now" · one quiet "Get live help" link.
- **Live:** ring hero · thread/question · Start (primary) · Floating window (secondary) ·
  demo text-link · compact AI row.
- **Analyze:** unchanged flow, restyled states (§6.6).
- **Conversations:** header + kebab · list · FAB "New chat".
- **Chat:** unchanged flow, new composer (§6.7).
- **Article:** cover header · structured sections · sources · one primary action (§6.4).
- **Help:** 1930 · report online · lost-money steps (collapsed) · complaint · warn family (§6.5).

## 5. The imagery system (the "images in news and retrieval" ask, at $0)

### 5.1 Category covers (bundled, offline, deterministic)
Hand-author **9 vector cover illustrations** — flat duotone scenes in the indigo/neutral family
(consistent style: 2–3 shapes, one accent, no faces, no text) — one per scam family:

| Key | Motif | Matches (case-insensitive, in `coverForScamType()`) |
|---|---|---|
| `cover_digital_arrest` | police cap + phone silhouette | digital arrest, police, CBI, ED, impersonation, courier+police |
| `cover_parcel` | parcel box + warning tag | parcel, courier, FedEx, customs |
| `cover_kyc_bank` | bank card + shield | KYC, bank, account, SIM, Aadhaar, PAN |
| `cover_investment` | rising chart that snaps | investment, trading, stock, crypto, Ponzi |
| `cover_job` | briefcase + hook | job, task, work-from-home, recruitment |
| `cover_lottery` | gift box + coins | lottery, prize, lucky draw, cashback |
| `cover_romance` | heart + mask | romance, matrimonial, dating |
| `cover_utility` | bolt/meter | electricity, utility, bill, disconnection |
| `cover_generic` | shield + waves | anything else (safe default) |

Each cover ships in a **wide banner crop** (article header, featured feed card) and reads
correctly at **56dp thumbnail** (compact feed rows). One drawable per cover, scaled — vectors
crop via `ContentScale`. Mapping is a pure function in `core:reasoning`
(`coverKeyForScamType(scamType: String): String`) so it's unit-testable; the app maps key →
drawable.

### 5.2 Feed becomes a magazine (Home)
- **Featured card** (first item): full-width, cover banner on top (16:7), category chip overlaid,
  title in `titleLarge`, source line. One per refresh.
- **Compact rows** (rest): 56dp rounded cover thumbnail (replaces the gray alert-triangle chip),
  category eyebrow, title (max 2 lines), source — **no body preview line** (the one-liner moves
  to the article screen; it's the main text-noise cut on Home).
- Sub-caption "Tap a card — VAARTA explains…" is deleted; a featured card invites tapping by
  itself. Section header gains a "Refreshed today" quiet timestamp instead.

### 5.3 Article gets a real header
Cover banner full-width under the back bar (edge-to-edge, ~200dp), category chip + title +
source **on** the banner's lower scrim (indigo-tinted gradient scrim, AA-checked). Content below
in structured sections (§7).

### 5.4 Stretch (flagged, not in scope unless owner opts in)
Fetch each source's real OG/preview image at feed-refresh time with the bundled cover as the
guaranteed fallback. Free but adds network variance + mixed-quality images; the bundled system
must land first and stand alone.

## 6. Screen-by-screen redesign

### 6.1 Home
Top → bottom:
1. **Brand header:** small "VAARTA" wordmark row + a status chip ("Protected · AI ready" /
   "On-device only") — gives the screen a live, product-grade opening instead of a plain title +
   tagline. Tagline deleted (the app explains itself through content).
2. **Panic banner:** slimmer (72dp min) full-width red banner — headline + chevron, no
   subtitle (the subtitle "Tap for what to do this second" is implied by the chevron and read by
   TalkBack via contentDescription). Still the only red on screen, still thumb-first.
3. **Actions — a 3-tile grammar** replacing three identical rows: one **wide primary tile**
   ("Help me on a call" — mic glyph, short one-line support) + **two half-width tiles**
   ("Ask VAARTA", "Check a recording" — icon + title only, no subtitles). Cuts Home's action
   text from 6 blocks to 4 short lines and finally creates hierarchy (live help is the headline
   feature).
4. **Feed:** §5.2. Featured + compacts.

### 6.2 Panic sheet
`RightNowSteps` (shared component, 4 steps, red numbered badges) → **Call 1930 now** (the only
button) → one quiet link-row "Get live help from VAARTA →". Removes the two secondary buttons
that duplicated Home tiles one tap away.

### 6.3 Live
- **Idle:** ring at rest, **"Ready to protect"** (fixes the "Listening & checking"/0 lie), one
  short line ("Put the call on speaker when you start"). Primary **Start live protection**;
  secondary **Float over your call**; text-link **Watch how it works** (demo). Compact AI row
  (§4.1). Controls sit in a bottom-anchored group; the hero owns the upper half. Nothing else.
- **Active:** ring + state line; thread grows between hero and a bottom **Stop** bar;
  "● Live" text marker becomes a small pulsing dot chip next to the state line.
- **Post-session:** "Saved to your conversations ✓" row + **Done** + **Start again** — this is
  where Reset lives now.

### 6.4 Article
Cover header (§5.3) → **structured sections** (§7): "What it is" (short prose), "How to spot it"
(check-glyph list rows), "What to do" (numbered steps — same `StepRow` as Help) → sources as
compact chips → **one** primary action "Ask VAARTA about this". "Warn my family" becomes a share
icon in the top bar. **Loading state:** cover + title render instantly (they come from the card),
with shimmer placeholder lines below — never a blank void; the screen is already worth looking at
before the AI answers.

### 6.5 Help
- **Emergency:** one red-tinted card — "Scam happening now?" + **Call 1930** + a link that opens
  the same panic sheet (shared steps, zero copy drift).
- **Report online:** compact link-row (globe icon + "cybercrime.gov.in" + chevron) — a
  full-width outlined button is more chrome than this needs.
- **"Lost money?" steps:** collapsed to the first 3 steps + "Show all 7 steps" expander; steps
  keep the numbered `StepRow` look.
- **Complaint + Warn family:** two compact rows in one "Tools" section card.
Help drops from ~5 screens of scroll to ~2.

### 6.6 Conversations
- Header: title + result count eyebrow + **kebab** (retention + Delete all live in its sheet).
- **FAB "New chat"** (extended, indigo) bottom-right — the standard, thumb-reachable home for
  creation; the header button goes away.
- **Row grammar (hard rules):** 44dp leading tinted circle (glyph per source; tint per source
  family — indigo=chat, neutral=recording, verify-blue=live call; risk red never used here),
  title `titleMedium` **maxLines=1 ellipsize**, second line = verdict pill (compact, tinted,
  only when scored) + **relative time** ("11:41 PM", "Yesterday", "16 Jul") `maxLines=1`.
  Trailing: chevron only. **Delete moves to swipe-to-delete** (with undo snackbar) — no per-row
  X, no accidental destructive tap target next to every open target.
- Encryption note shrinks to a lock-glyph caption at the **bottom** of the list.

### 6.7 Chat
- Composer rebuilt: one rounded **pill field** with mic/attach as trailing icons inside the
  field + a circular indigo **send FAB-let**; attachment pickers collapse behind one "+"
  (paperclip) that opens a small sheet (photo / audio) — 3 always-visible gray icons become 1.
- Empty state: shield + "Ask me anything about a suspicious call or message" + **3 tappable
  starter chips** ("Is this message a scam?", "They're asking for an OTP", "Check a number for
  me") — replaces the two-sentence paragraph and gives instant, judge-friendly value.
- Bubbles: quote glyphs `❝ ❞` dropped; coach replies restyled as clean "SAY THIS" chips
  (existing tint system, straight quotes).

### 6.8 Bottom nav
Custom quiet bar: `panel` bg + top hairline, 3 items, active = indigo icon + label + 3dp dot
(no tonal pill), labels **Home · Chats · Help** ("Conversations" overflows at large font
scale). Height/insets per MD3; this is a restyle, not a nav change.

### 6.9 Overlay
Unchanged this pass (rebuilt + verified in v2 Phase 5) **except** inheriting the new thread
bubble styles automatically via ChatView. Explicitly out of scope for re-verification beyond
a smoke check.

## 7. Structured article data (the one AI-shape change)

`summarizeArticle` today returns one markdown blob; the screen can only render prose. Change the
prompt to request strict JSON:

```json
{ "whatItIs": "2–3 sentences, plain language",
  "howToSpot": ["sign 1", "sign 2", "sign 3"],
  "whatToDo": ["step 1", "step 2", "step 3"],
  "sources": [ … unchanged … ] }
```

- Parsed by a new tolerant `AwarenessWireParser.parseStructuredSummary` (same fail-closed
  discipline + TDD as `parseFeed`; skips citation markers the same way).
- **Fail-closed ladder:** structured parse fails → render the raw text through the existing
  `MarkdownText` path (today's behavior) → that fails → the card's one-liner. No new failure
  modes, strictly additive.
- Preamble defense: prompt instructs "JSON only"; the parser locks onto the first `{…}` so any
  "Here is…" preamble is structurally ignored — the bug in `06b-article-loaded.png` cannot
  recur.
- Chat answers (`ChatPrompt`) stay free-form prose — conversation should read like conversation.

## 8. Structural correctness (the "everything aligned" contract)

1. **Edge-to-edge done right:** `enableEdgeToEdge()` + status-bar icons dark-on-light /
   light-on-dark (fixes invisible clock); every top-level surface handles insets through one
   shared `VaartaScreenScaffold` (screen padding, `statusBarsPadding`, scroll) so a screen
   *cannot* forget them — fixes the Article overlap by construction.
2. **System back:** `BackHandler` on every sub-screen mirrors its back arrow; predictive back
   pops Article→Home, Chat→Conversations, Live→Home. System back never exits the app except
   from a tab root.
3. **Row grammar (one implementation, reused):** leading visual = 44dp; text column
   start-aligned; trailing meta ≤1 line each, never wraps (`maxLines=1`, text column gets
   `weight(1f)`); vertical rhythm on the 4dp grid; list gaps 8dp, section gaps 24dp, screen
   padding 20dp. The Conversations 3-line-date breakage becomes impossible in the shared row.
4. **Type roles:** unchanged scale; headline sizes bump for hierarchy (screen title 26→28sp
   `headlineMedium`; featured card title `titleLarge`); still zero hardcoded sizes outside
   `theme/`.
5. **Loading states:** shimmer skeletons (feed cards, article sections) replace
   spinner-next-to-text; content that is already known (cover, title) always renders
   immediately.

## 9. Motion

- Sub-screen enter: 220ms fade + 8dp slide-up (MD3 emphasized-decelerate); exit reverse-fast.
- Feed cards: staggered 40ms fade-in on first load only.
- Press: existing 0.98 scale + tonal shift, now on every tappable row/tile.
- Ring: existing sweep/breathe kept; panic banner gets **no** animation (no flashing rule).
- All motion respects reduced-motion (`LocalAccessibilityManager` / animator duration scale 0).

## 10. Alternatives considered

1. **Skin-only polish again** (spacing/color tweaks, keep IA): rejected — the 07-16 pass proved
   the sameness and repetition are structural; another veneer cannot fix "5 buttons on Live" or
   a text-only feed.
2. **Real web images (OG fetch) as the primary imagery**: rejected as core — network-dependent,
   uncontrollable quality/aspect, privacy surface, and empty on first/offline run; kept as a
   stretch layer over the bundled covers (§5.4).
3. **AI-generated cover images**: rejected — free-tier image generation is rate-limited and
   unreliable, breaks $0 discipline in spirit, and style drifts; hand-authored vectors are
   controlled, offline, and permanent.
4. **4-tab nav (separate Learn tab for the feed)**: rejected — the feed is Home's content spine;
   a fourth tab dilutes the 3-tab clarity the v2 spec locked and adds an empty-feeling screen.

## 11. Verification (evidence before "done")

- Build green; all existing unit tests green + new tests: `coverKeyForScamType` mapping,
  `parseStructuredSummary` (well-formed / preamble / malformed→fail-closed).
- **Emulator screenshot matrix, light + dark:** Home (seed + live feed), panic sheet, Live
  (idle/active/post), Analyze (idle/running/done), Conversations (empty + populated + swipe
  state), Chat (empty + thread + composer states), Article (loading skeleton + loaded + each
  section type), Help (collapsed + expanded).
- **Dedup grep:** each canonical action string appears in exactly one screen file.
- **Alignment checks:** status-bar icons legible on both themes; no trailing text wraps at
  font-scale 1.3; system back from every sub-screen pops (verified via adb `input keyevent 4`
  sequence, app never exits except from a root).
- TalkBack pass on Home + Article (cover images `contentDescription = null` as decorative;
  featured card reads title + category once).
- Same-day owner walkthrough — this redesign's bar is the owner's eye, not just the checklist.

## 12. Build order (feeds the implementation plan)

1. **Foundation:** `VaartaScreenScaffold` (insets/back/scroll) + edge-to-edge/system-bar fix +
   BackHandlers; shared row/tile components v2; shimmer primitive. *(unblocks every screen)*
2. **Covers:** author 9 vectors + `coverKeyForScamType` (TDD) + wide/thumb rendering.
3. **Home v2:** header, slim panic, tile grammar, magazine feed (featured + compact).
4. **Article v2:** cover header + structured summary (prompt + parser TDD + fail-closed ladder)
   + skeleton loading + single-action footer.
5. **Conversations v2:** row grammar, swipe-to-delete + undo, kebab sheet, FAB.
6. **Live v2 + panic sheet:** idle/active/post states, control regrouping, shared
   `RightNowSteps`, AI-consent row.
7. **Help v2 + Chat composer v2 + nav restyle.**
8. **Sweep:** motion, dark mode, TalkBack, font-scale 1.3, full screenshot matrix, docs/status.

Each phase is independently shippable and emulator-verifiable; nothing blocks on the physical
phone.
