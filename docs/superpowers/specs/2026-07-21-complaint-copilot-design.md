# Complaint co-pilot — intelligent, assisted scam reporting — design

**Date:** 2026-07-21
**Status:** Approved in principle (design), pending spec review
**Areas:** Help tab "report" area, new complaint flow, settings cleanup, `core:reasoning` (router + pack), `core:complaint`, `core:data` (identity vault), in-app WebView autofill

## 1. Problem

The Help tab's "Get help & report" area is inert **information**: "Call 1930", a link to Sanchar Saathi,
a link to cybercrime.gov.in, and a static 7-step "if you've already lost money" list. Anyone can Google
that. It hands the user homework at the worst possible moment and delivers no product value — there is no
USP in a list of links.

VAARTA already *knows what happened* (it heard the call / has the chat / classified the scam) but does
nothing with that knowledge when the user wants to act. The owner's directive: **collapse the manual work
to its irreducible core.** For filing a complaint that means: the app understands the *procedure and the
required documents* for the right destination, assembles the whole packet from the call context + the
user's saved details, opens the real portal *inside the app*, and pre-fills everything it safely can —
leaving the user only the steps the law requires (register/OTP, CAPTCHA, and the final Submit).

Goal: the Help "report" area becomes a **complaint co-pilot** — intelligent routing + prepared packet +
assisted in-app filing — and Settings is trimmed to only the options that matter.

## 2. Non-goals & hard rails

- **VAARTA never auto-submits.** The final Submit, the OTP, and the CAPTCHA are always the user's own
  action. Honors the existing ethical red line (`SCAM_INTELLIGENCE.md` §9: "Never auto-contact
  police/1930 on the user's behalf") and the platform safety rules on CAPTCHAs / financial-legal actions.
- **We render the *real, live* portal** in the WebView and assist on top of it — we never replace or
  fabricate government content. The authoritative form and current rules are always whatever the site
  shows today.
- **No paid APIs, no backend/server** (strict $0, ADR-0001). Pack refresh, if built, uses a free static
  host (e.g. GitHub raw JSON); the bundled pack + live site work fully offline.
- **No new AI backend** — reuse `GeminiClient` (assembly + the existing web-grounding used by the feed).
- The deterministic engine, scoring, and safety filters are unchanged. The AI is additive and never on
  the critical path.
- Out of scope this pass: portals beyond NCRP + Chakshu (router is extensible; RBI Sachet / bank portals
  are future rows), document *generation* of ID proof (user attaches their own), and true end-to-end
  auto-filing.

## 3. Verified portal knowledge (research, 2026 sources)

Confirmed against current public sources — not assumed. This is the factual basis for the playbook pack.

### 3.1 NCRP — cybercrime.gov.in  (digital-arrest / financial fraud → SC-01/02/03/04/05)
- **Access (user-only steps):** register with mobile + email → OTP → CAPTCHA → login. For financial
  fraud, **call 1930 first** within the golden hour (RBI Limited Liability Circular 2017 fund-freeze).
- **4-part form:**
  1. **Incident Details** — *Category of complaint* (dropdown); approx. date & time; *delay reason*
     (optional); *"Where did the incident occur?"* (Email / WhatsApp / Website URL / Telegram / Instagram
     / Facebook …); **description — min 200 characters, no special characters**; evidence upload.
  2. **Suspect Details** — all optional: name, ID type + number, photo, mobile, email, address.
  3. **Complainant / Victim Details** — name, email, mobile, DOB, gender, address, national ID.
  4. **Preview & Submit** — declaration → **14-digit acknowledgement (starts with "2")** by SMS.
- **Financial-fraud data to have ready:** bank/wallet/merchant name; 12-digit Transaction ID/UTR; txn
  date; fraud amount.
- **Documents:** national ID (PAN/Aadhaar/DL/Voter/Passport) as **.jpeg/.jpg/.png ≤ 5 MB**; evidence =
  screenshots, bank statements, receipts, chat transcripts, page URLs.

### 3.2 Chakshu — sancharsaathi.gov.in/sfc  (report the number/SMS → SC-02/03/08 spam path)
- **Access:** public — recipient's 10-digit number + CAPTCHA + OTP, **no account**. Report within 30 days.
- **Fields:** medium (Call / SMS / WhatsApp); *fraud category* dropdown (KYC-bank/electricity/gas,
  impersonation of police/CBI/customs/Aadhaar/RBI/DoT, fake customer care, job/lottery, investment,
  malicious links, sextortion, IVR/robo …); **screenshot (mandatory)**; sender number; SMS type + header;
  date & time; **description — min 30 characters**; declaration → preview → submit.

### 3.3 Honest gap
Exact CSS/DOM selectors for autofill can only be finalized by inspecting each live form at build time
(NCRP's real form is behind login). This is *why* tap-to-fill chips + the live form are the guaranteed
floor: the **knowledge** (fields, docs, procedure) above is confirmed; the **selectors** are best-effort
and captured/verified during implementation, degrading gracefully when a portal's HTML shifts.

## 4. Architecture (Approach A — curated pack + AI assembly, tap-to-fill floor)

Deterministic, accurate knowledge in a curated pack; AI only personalizes/assembles; the live site is
ground truth; assistance degrades gracefully. Mirrors VAARTA's existing "deterministic core + AI on top,
never hallucinate" design.

| Component | Module | Responsibility |
|---|---|---|
| **`complaint-playbook-v1.json`** | `core:reasoning` resources (beside `core-scam-v1.json`) | Curated knowledge: per destination — routing rule, complaint category value, required fields (each mapped to a `ComplaintDraft`/vault slot), required documents, best-effort form selectors, ordered procedure steps (flagged `userOnly` where the user must act), and a `verifiedOn` date. |
| **`ComplaintRouter`** | `core:reasoning` (pure Kotlin, unit-tested) | Deterministic: given `scamCode`/`scamName` (from a session) **or** a user-picked issue (no session), returns ranked destination(s). e.g. money-loss/digital-arrest → NCRP (+1930); spam call/SMS number → Chakshu. |
| **`IdentityVault` (`IdentityEntity`/`IdentityDao`)** | `core:data` (SQLCipher, mirrors `GuardianDao` exactly) | Encrypted on-device store of reusable filing details (name, address, mobile, email, optional national-ID type). Single-row upsert, editable, clearable. Never leaves device except when the user fills it into the portal. |
| **`ComplaintPacket` + assembler** | `core:complaint` (extends existing `ComplaintDraft`) | Bundle for the flow: routed destination + field values (from `ComplaintDraft` + vault) + a document checklist (what VAARTA already has vs. what the user must attach). Pure, testable. |
| **`PlaybookFreshness`** | app (reuses `GeminiClient` grounding) | Advisory-only "has the procedure/docs/rules changed?" check when online; fails silent. Optional free-static-host pack refresh with `verifiedOn`/`review_by` discipline (like the intel-pack lifecycle). |
| **`ComplaintFlow` UI + `AutofillBridge`** | `app` | The Prepare → Review → File screens and the WebView JS-inject + tap-to-fill chip assist over the live portal. Reuses the `WebViewArticle` pattern. |

All deterministic units (router, vault, packet) are pure and unit-testable. `ComplaintRouter` and the
packet assembler depend only on `core:common`/existing `core:complaint` types (module rule preserved).

### 4.1 Field mapping (the value)
VAARTA already holds: narrative (`ComplaintBuilder`), timestamped `detectedSignals`, `callerNumber`,
`scamCode`/`scamName`, call start/end, and (for saved calls) the transcript. These map onto:

| Portal field | VAARTA source |
|---|---|
| Incident category | `scamCode` → pack's `categoryValue` per portal |
| Incident date/time | `callStartEpochMs` |
| "Where did it occur" / medium | `CHANNEL_SWITCH` signal → WhatsApp/Skype, else "Phone call" |
| Description (≥200 / ≥30 chars) | `Narrative.text` (already exceeds 200) |
| Suspect mobile | `callerNumber` |
| Evidence | transcript + `detectedSignals` (offered as "copy / save to attach") |
| Complainant name/address/mobile/email/ID | `IdentityVault` |
| Loss amount / Transaction ID / UTR | user-entered in Review (`Loss`) |

Each pre-filled field keeps its existing `SlotSource` provenance marker (DETECTED / USER / DEFAULT) so the
"auto-filled — verify before filing" honesty is preserved.

## 5. Experience — Prepare → Review → File

**Entry points (context carried automatically):** Help tab's new primary **"Report this scam"**; a
**"Report this"** action on any saved conversation / recording / live verdict; and from the panic sheet
after the instant steps.

### Step 1 · Prepare (instant, no waiting)
State where to report and **why**: a routed destination card — e.g. *"Digital-arrest / financial fraud →
National Cyber Crime Portal + call 1930 now if money moved,"* with the matched scam type as the reason.
When there's no session or routing is ambiguous, a 2–4-tap **"What happened?"** picker routes instead.

### Step 2 · Review (where manual work collapses)
- **Your complaint** — pre-filled narrative + fields from `ComplaintBuilder`, each editable, each tagged
  with the existing auto-filled/verify provenance marker.
- **Your details** — from `IdentityVault`; if empty, one inline "add once" prompt (writes to the vault).
- **Money lost?** — amount + Transaction ID/UTR + txn date (feeds `Loss`), shown only for the NCRP path.
- **Documents checklist** — crisp: *✓ Call transcript (VAARTA has it) · ✓ Detected signals (VAARTA has
  it) · ▢ Your ID proof — PAN/Aadhaar as .jpg ≤5MB (you attach) · ▢ Bank transaction proof (you attach)*;
  each "you attach" row says what it is and where to get it.
- Any `PlaybookFreshness` advisory shows here. Primary: **"Continue to file →"**.

### Step 3 · File (in-app WebView over the live portal)
- The real portal opens in-app (existing WebView pattern; JS + DOM storage on).
- A persistent bottom **step rail** from the pack's procedure, e.g. NCRP: *1 Register / OTP (you) · 2
  Choose category X · 3 Fill details · 4 Attach docs · 5 CAPTCHA (you) · 6 Review · 7 Submit (you)*.
- **`AutofillBridge`**: a **"Fill this page"** button injects mapped values via evaluated JS when the DOM
  matches the pack's selectors; **tap-to-fill chips are always present** as the floor (tap → value copied
  to clipboard → toast "paste into '<field>'"). One-tap **"Copy complaint text."**
- Hard banner throughout: *"You review and submit — VAARTA only fills."* VAARTA never targets the OTP,
  CAPTCHA, or Submit controls with autofill and never programmatically clicks Submit.

The magic moment: a confusing ~30-minute government form becomes a guided ~3-minute assisted fill, with
the paperwork already assembled.

## 6. Settings cleanup

Split the current grab-bag Help tab so **Help = action only** (Emergency 1930 + panic steps · **Report a
scam** [the new flow] · If you've already lost money [steps] · Warn family), and a single clean
**Settings** section holds **only**: *App language · Guardian contact · Your filing details (IdentityVault
add / edit / clear) · Privacy (clear conversations / clear voice data, both with confirm)*. Remove
redundant rows and visual clutter. No new capabilities beyond the IdentityVault row.

## 7. Freshness & correctness strategy

Three layers, none able to silently mislead on a legal filing (§2 rails):
1. **Live ground truth** — the real portal is always what the user sees and submits.
2. **AI web-grounded freshness check** (`PlaybookFreshness`, reuses feed grounding) — advisory delta when
   online; fails silent offline.
3. **Updatable pack** — `verifiedOn`/`review_by` dates; optional refresh from a free static host, no app
   update and still $0. Offline/never-refreshed → bundled pack + live site still work.

## 8. Testing strategy (and the owner's rule: never submit fake complaints)

- **`AutofillBridge` is tested only against a bundled local mock HTML page** (in `app` test assets) that
  mimics each portal's field structure — never the live site. Verifies: JS fill populates fields;
  tap-to-fill copies the correct value; **VAARTA never triggers Submit**.
- **Live portal testing loads read-only and stops before Submit** — no OTP, no CAPTCHA, no real
  submission, ever.
- **Pure-JVM unit tests:** `ComplaintRouter` (scamCode → destination, ambiguity, no-session fallback);
  **playbook-pack parity** (every mapped field ↔ a real `ComplaintDraft`/vault slot; every destination has
  category + procedure + `verifiedOn`) — a `PackParityTest`-style invariant so the pack can't silently
  drift; `ComplaintPacket` assembler (checklist correctness, provenance preserved).
- **Instrumented (emulator):** `IdentityVault` DAO round-trip / replace / clear (mirrors
  `GuardianDaoTest`); DB migration additive-only.
- **Manual emulator pass:** full Prepare → Review → File against the mock page + a read-only load of the
  real portal (no submit); screenshots in light/dark, EN + HI + Hinglish.
- **Full gate:** `./gradlew clean test assembleDebug lintDebug` green; new strings translated (HI +
  Hinglish) with the native-review checklist appended for safety-critical copy.

## 9. Open decisions folded in
- **Router scope this pass:** NCRP + Chakshu only; extensible pack rows for the rest.
- **Pack refresh host:** deferred to a follow-up (bundled pack ships day-one); the `verifiedOn` date and
  the AI freshness advisory are the correctness guarantees until then.
- **IdentityVault fields:** name, address, mobile, email, national-ID *type* only (never the ID number or
  image — the user attaches those directly on the portal, minimizing sensitive data at rest).
