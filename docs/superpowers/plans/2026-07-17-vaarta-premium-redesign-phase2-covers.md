# VAARTA Premium Redesign — Phase 2: Cover Illustrations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The bundled, $0, offline imagery system (spec §5.1): 11 India-scam vector covers + a
tested scam-type→cover mapping + a reusable `ScamCover` composable, wired into the Home feed rows
so the change is visible this phase.

**Architecture:** Pure mapping (`coverKeyForScamType`) in `core:reasoning` (TDD). Drawables +
key→resource mapping + composable in `app`. Feed rows swap the gray alert-triangle chip for the
cover thumbnail; the featured card + article banner come in phases 3–4.

**Tech Stack:** VectorDrawable XML (gradient fills, minSdk 29 OK), Compose `painterResource`.

## Global Constraints

- Covers are **text-free**, faceless, flat duotone in the **indigo family + white alphas** — never
  risk red (spec §3A.6, §5.1). One visual contract for all 11 (see Task 2).
- Every cover must read at **56dp thumbnail** and crop acceptably to a 16:7 banner.
- Mapping priority is a **fixed order** (digital-arrest keywords outrank parcel, etc.) pinned by
  tests; unknown/blank → `cover_generic`, never a crash.
- Build/test commands and commit style: same as Phase 1 plan.

---

### Task 1: `coverKeyForScamType` (core, TDD)

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/CoverKey.kt`
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/CoverKeyTest.kt`

**Interfaces:**
- Produces: `fun coverKeyForScamType(scamType: String?): String` — one of:
  `digital_arrest, upi, parcel, kyc_bank, investment, job, loan_app, lottery, romance, utility, generic`.

Steps: write the test class below → run (`:core:reasoning:test`, expect unresolved reference) →
implement the ordered keyword table → run to green → commit.

```kotlin
class CoverKeyTest {
    @Test fun `digital arrest keywords win`() {
        for (s in listOf("Digital Arrest", "fake police call", "CBI impersonation", "ED court threat"))
            assertEquals("digital_arrest", coverKeyForScamType(s))
    }
    @Test fun `police plus courier is digital arrest, not parcel`() =
        assertEquals("digital_arrest", coverKeyForScamType("Police courier parcel scam"))
    @Test fun `upi and qr map to upi`() {
        for (s in listOf("UPI fraud", "QR code scam", "PhonePe payment request"))
            assertEquals("upi", coverKeyForScamType(s))
    }
    @Test fun `parcel and customs map to parcel`() {
        for (s in listOf("Parcel scam", "FedEx courier", "customs seizure"))
            assertEquals("parcel", coverKeyForScamType(s))
    }
    @Test fun `bank identity keywords map to kyc_bank`() {
        for (s in listOf("KYC expiry", "bank account fraud", "Aadhaar misuse", "SIM swap"))
            assertEquals("kyc_bank", coverKeyForScamType(s))
    }
    @Test fun `investment job loan lottery romance utility`() {
        assertEquals("investment", coverKeyForScamType("stock trading app fraud"))
        assertEquals("job", coverKeyForScamType("work-from-home task scam"))
        assertEquals("loan_app", coverKeyForScamType("instant loan app harassment"))
        assertEquals("lottery", coverKeyForScamType("KBC lucky draw prize"))
        assertEquals("romance", coverKeyForScamType("matrimonial dating fraud"))
        assertEquals("utility", coverKeyForScamType("electricity bill disconnection"))
    }
    @Test fun `case-insensitive`() = assertEquals("upi", coverKeyForScamType("uPi FRAUD"))
    @Test fun `blank null unknown fall back to generic`() {
        assertEquals("generic", coverKeyForScamType(null))
        assertEquals("generic", coverKeyForScamType("  "))
        assertEquals("generic", coverKeyForScamType("something new"))
    }
}
```

Implementation shape: an ordered `List<Pair<String, List<String>>>` scanned first-match on the
lowercased input.

### Task 2: The 11 cover drawables

**Files:** Create `app/src/main/res/drawable/cover_<key>.xml` × 11.

**Visual contract (all 11):** 120×120 viewport; full-bleed linear-gradient background in the
indigo band (start `#3B35A8` → end `#4B45C6`, angle may vary per cover for variety); motif built
from 2–3 simple geometric paths in white at 90%/35% alpha (`#E6FFFFFF` / `#59FFFFFF`); optional
small accent in light indigo `#8F8AF0`; motif centered inside the middle 60% so both the square
thumbnail and a 16:7 center-crop keep it whole. No text, no faces, no red.

Reference implementation (parcel) — the other ten follow the identical structure with their motif
paths (motifs: peaked cap+phone; QR corner squares+₹ stroke; box+tag; card+shield; snapping
chart line; briefcase+hook; phone+chain links; gift box+coins; heart+mask; gauge+bolt;
shield+waves):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="120dp" android:height="120dp"
    android:viewportWidth="120" android:viewportHeight="120">
    <path android:pathData="M0,0h120v120h-120z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="0" android:startY="0" android:endX="120" android:endY="120" android:type="linear">
                <item android:offset="0" android:color="#FF3B35A8"/>
                <item android:offset="1" android:color="#FF4B45C6"/>
            </gradient>
        </aapt:attr>
    </path>
    <!-- box body -->
    <path android:fillColor="#E6FFFFFF" android:pathData="M36,52 L60,40 L84,52 L84,80 L60,92 L36,80 Z"/>
    <!-- box lid shading -->
    <path android:fillColor="#59FFFFFF" android:pathData="M36,52 L60,64 L84,52 L60,40 Z"/>
    <!-- warning tag -->
    <path android:fillColor="#FF8F8AF0" android:pathData="M74,34 m-9,0 a9,9 0 1,1 18,0 a9,9 0 1,1 -18,0"/>
</vector>
```

Verify each renders: build + the Task 4 emulator screenshot (thumbnails) + Android Studio-free
check via `aapt2`-implicit build validation (a malformed vector fails `mergeDebugResources`).

### Task 3: `ScamCover` composable + key→drawable map

**Files:** Create `app/src/main/java/ai/vaarta/ui/Covers.kt`.

**Interfaces:**
- Produces: `@Composable fun ScamCover(scamType: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp)` — resolves `coverKeyForScamType`, renders the drawable `ContentScale.Crop` inside a rounded clip; caller sizes it (56dp square thumb now; 16:7 banner in phases 3–4). `contentDescription = null` (decorative, spec §11).

```kotlin
@Composable
fun ScamCover(scamType: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val res = when (coverKeyForScamType(scamType)) {
        "digital_arrest" -> R.drawable.cover_digital_arrest
        "upi" -> R.drawable.cover_upi
        "parcel" -> R.drawable.cover_parcel
        "kyc_bank" -> R.drawable.cover_kyc_bank
        "investment" -> R.drawable.cover_investment
        "job" -> R.drawable.cover_job
        "loan_app" -> R.drawable.cover_loan_app
        "lottery" -> R.drawable.cover_lottery
        "romance" -> R.drawable.cover_romance
        "utility" -> R.drawable.cover_utility
        else -> R.drawable.cover_generic
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(corner)),
    )
}
```

### Task 4: Wire the feed thumbnails + verify on emulator

**Files:** Modify `app/src/main/java/ai/vaarta/ui/HomeScreen.kt` (`AwarenessCardRow`).

Replace the 44dp gray `Surface`+alert-triangle chip with `ScamCover(card.scamType, Modifier.size(56.dp))`.
Build, install, screenshot Home: every feed row shows its category cover (seed feed covers
digital-arrest/parcel/KYC/investment at minimum); different categories show visibly different art.
Commit, update PROJECT_STATUS.
