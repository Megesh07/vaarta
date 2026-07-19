# Portfolio Polish to 10/10 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every code-closable gap between VAARTA's current state and a genuine portfolio-10 MVP, per spec `docs/superpowers/specs/2026-07-19-portfolio-polish-to-10-design.md`.

**Architecture:** Small, independent increments over the existing modular Android codebase (`core:common`, `core:reasoning`, `core:complaint`, `core:data`, `core:voice`, `app`). Deterministic engine stays the sole score owner; every AI/network add is raise-only and fails closed. No new native dependencies.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit5 (JVM unit tests), kotlinx.serialization, `HttpURLConnection` (matching existing `GeminiClient`), Gradle wrapper.

## Global Constraints

- **$0 to build** — no paid APIs, no backend, sideload only. Google Safe Browsing v4 is used under its free **non-commercial** tier only.
- **Build/test commands** (Git Bash): `export JAVA_HOME="/c/Users/Meges/AppData/Local/Programs/jdk17/jdk-17.0.19+10"` then `./gradlew <task>`.
- **Test count baseline (this session, fresh XML):** 159 unit tests, 0 failures. Every task keeps the full suite green.
- **Safety invariants (verbatim, from spec §3):** `RiskEngine` is sole score owner; `HybridAlert` ratchet raises-only; `SuggestionSafetyFilter` enforces HARD RULES; all LLM/network calls fail closed → deterministic-only; `PackParityTest` (every signal has a `manualCue`) and `EvalTest` (zero false-SCAM_PATTERN on benign) stay green.
- **New Hindi/Hinglish strings are machine-drafted** — add to the native-review checklist in `PROJECT_STATUS.md`, never mark "reviewed".
- **Execution order:** Task 1→9 as numbered (A, H, K, B, C, D, E, then F/G gates). Increment J (voice-anomaly) is spike-gated and intentionally NOT planned as code here — see §"Deferred".

---

### Task 1 (Increment A): Fix the `"fir"` fuzzy false-positive

**Files:**
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/TextMatcherTest.kt` (create)
- Modify: `core/reasoning/src/main/resources/packs/core-scam-v1.json` (line 76, `SIG_LEGAL_THREAT` `hi_latn`)

**Interfaces:**
- Consumes: `TextMatcher.matches(normalizedText, signal, activeLanguages)`, `PackLoader.fromResource(path)`, `ai.vaarta.core.common.Signal`.
- Produces: nothing new — behavior fix only.

- [ ] **Step 1: Write the failing test**

Create `TextMatcherTest.kt`:

```kotlin
package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the fuzzy matcher against short-token false positives. "fir" (3 chars, FUZZY1) used to
 * substring-match "for"/"from"/"Sir" via edit-distance 1 on whitespace-stripped text, firing
 * SIG_LEGAL_THREAT on benign English speech.
 */
class TextMatcherTest {

    private val pack = PackLoader.fromResource("/packs/core-scam-v1.json")
    private val legalThreat = pack.signals.first { it.id == "SIG_LEGAL_THREAT" }
    private val langs = listOf("en", "hi_latn", "hi")

    private fun matches(text: String): Boolean =
        TextMatcher.matches(ai.vaarta.core.common.Normalization.normalize(text), legalThreat, langs)

    @Test
    fun `benign english words do not fire the legal-threat signal`() {
        assertFalse(matches("this is for you"), "'for' must not fuzzy-match")
        assertFalse(matches("i am calling from the bank"), "'from' must not fuzzy-match")
        assertFalse(matches("yes sir how can i help"), "'Sir' must not fuzzy-match")
    }

    @Test
    fun `genuine legal-threat phrases still fire`() {
        // NOTE: TextMatcher's fuzzy match is a whitespace-stripped CONTIGUOUS substring check
        // (see TextMatcher.kt matchPhrase) — the matched words must be adjacent in the transcript,
        // not merely present in the same sentence. "your fir registered" keeps "fir" and
        // "registered" adjacent so it matches the fixed "fir registered" pattern; a sentence like
        // "an fir has been registered" would NOT match (words separated) and must not be used here.
        assertTrue(matches("your fir registered against you"))
        assertTrue(matches("you are under digital arrest"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.TextMatcherTest"`
Expected: FAIL on `benign english words...` ("for"/"from"/"Sir" match the bare `"fir"` pattern).

- [ ] **Step 3: Fix the pack pattern**

In `core-scam-v1.json`, `SIG_LEGAL_THREAT` (line ~76), replace the bare `"fir"` token in `hi_latn` with the distinctive phrase already used in `en`. Change:

```json
        "hi_latn": ["arrest warrant", "digital arrest", "fir", "giraftari", "money laundering"],
```
to:
```json
        "hi_latn": ["arrest warrant", "digital arrest", "fir registered", "fir darj", "giraftari", "money laundering"],
```

Rationale: `"fir registered"` (en-mixed, common in Hinglish) and `"fir darj"` (Hindi "FIR filed") are both long enough that FUZZY1 cannot collide with 3-letter English words, while still catching the real phrase. The bare 3-char token is removed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.TextMatcherTest" --tests "ai.vaarta.core.reasoning.EvalTest" --tests "ai.vaarta.core.reasoning.PackParityTest"`
Expected: PASS (both new tests; EvalTest's scam-script still reaches SCAM_PATTERN — `"digital arrest"` and other AUTHORITY signals carry it; benign still zero).

- [ ] **Step 5: Commit**

```bash
git add core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/TextMatcherTest.kt core/reasoning/src/main/resources/packs/core-scam-v1.json
git commit -m "core:reasoning — fix SIG_LEGAL_THREAT 'fir' fuzzy false-positive (matched for/from/Sir); regression-pinned"
```

---

### Task 2 (Increment H): Intel-pack breadth — KYC-expiry + family-emergency impersonation

**Files:**
- Modify: `core/common/src/main/kotlin/ai/vaarta/core/common/IntelPack.kt` (add `KINSHIP_IMPERSONATION` to `SignalCategory`)
- Modify: `core/reasoning/src/main/resources/packs/core-scam-v1.json` (bump `packId`; add 3 signals)
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/KinshipEvalTest.kt` (create)

**Interfaces:**
- Consumes: `RiskEngine(pack, langs)`, `RiskEvent.Transcript`, `RiskLevel`, `PackLoader.fromResource`.
- Produces: 3 new signals (`SIG_HOOK_KYC_EXPIRY`, `SIG_HOOK_FAMILY_EMERGENCY`, `SIG_ISOLATION_NEW_NUMBER`) and 1 new category `KINSHIP_IMPERSONATION`.

- [ ] **Step 1: Add the new category enum value**

In `IntelPack.kt`, extend `SignalCategory` (currently ends `PARCEL_PRETEXT, FINANCIAL_LURE, SERVICE_THREAT, BENIGN`):

```kotlin
@Serializable
enum class SignalCategory {
    AUTHORITY_CLAIM, LEGAL_THREAT, ISOLATION_ORDER, CHANNEL_SWITCH,
    URGENCY_PRESSURE, IDENTITY_PHISH, EXTRACTION_MOVE, LEGITIMACY_THEATER,
    PARCEL_PRETEXT, FINANCIAL_LURE, SERVICE_THREAT, KINSHIP_IMPERSONATION, BENIGN,
}
```

Also update the doc comment above the enum to note `KINSHIP_IMPERSONATION` was added for pack v3 (2026-07-19), the family-emergency ("it's me, I'm in trouble, send money, new number") pattern.

- [ ] **Step 2: Write the failing eval test**

Create `KinshipEvalTest.kt`:

```kotlin
package ai.vaarta.core.reasoning

import ai.vaarta.core.common.RiskEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Eval for the SC-09 family-emergency impersonation family (pack v3). A scam that claims kinship,
 * a new number, secrecy and urgent money must reach SCAM_PATTERN; a GENUINE family emergency that
 * never demands secret urgent money to an unfamiliar account must NOT (zero-tolerance, mirrors
 * EvalTest's benign-police gate).
 */
class KinshipEvalTest {

    private val langs = listOf("en", "hi_latn", "hi")
    private fun engine() = RiskEngine(PackLoader.fromResource("/packs/core-scam-v1.json"), langs)
    private fun line(text: String, atMs: Long) =
        RiskEvent.Transcript(text, atMs, atMs + 3_000, isFinal = true, confidence = 0.9f)

    private val kinshipScam = listOf(
        line("Hello beta it's me, I am in serious trouble please listen carefully", 5_000),
        line("This is my new number, my old phone is broken, do not call the old one", 25_000),
        line("I had an accident and the police are involved, please do not tell papa or anyone", 55_000),
        line("You must transfer the money to this account right now to settle it", 90_000),
    )

    // A REAL family emergency: kinship + distress, but no secrecy, no new-number misdirection,
    // no demand to wire money to an unfamiliar account. Must stay out of SCAM_PATTERN.
    private val genuineEmergency = listOf(
        line("Hi it's me, I'm okay but I had a small accident on the way home", 5_000),
        line("Can you come pick me up near the hospital, I am with the doctor now", 40_000),
        line("Tell mom I'll be a bit late, nothing serious, see you soon", 80_000),
    )

    @Test
    fun `kinship impersonation scam reaches SCAM_PATTERN`() {
        val e = engine()
        var last: RiskState? = null
        for (ev in kinshipScam) last = e.ingest(ev)
        assertEquals(
            RiskLevel.SCAM_PATTERN, last!!.level,
            "final ${last.score}/${last.level}, top=${last.topSignals.map { it.signalId }}",
        )
    }

    @Test
    fun `genuine family emergency never reaches SCAM_PATTERN`() {
        val e = engine()
        var maxLevel = RiskLevel.OBSERVING
        for (ev in genuineEmergency) {
            val s = e.ingest(ev)
            if (s.level.ordinal > maxLevel.ordinal) maxLevel = s.level
        }
        assertTrue(
            maxLevel.ordinal < RiskLevel.SCAM_PATTERN.ordinal,
            "genuine emergency peaked at $maxLevel — false SCAM_PATTERN is zero-tolerance",
        )
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.KinshipEvalTest"`
Expected: FAIL — `kinship impersonation scam reaches SCAM_PATTERN` fails (the kinship/new-number signals don't exist yet, so the ladder never builds).

- [ ] **Step 4: Add the three signals to the pack**

In `core-scam-v1.json`: (a) bump the top-level `"packId"` to `"core-scam@2026.07.3"`; (b) add these three objects to the `signals` array (after `SIG_HOOK_COURIER_COD`, before the closing `]` of `signals`). Follow the exact field schema of existing signals (see `SIG_HOOK_ELECTRICITY` for reference):

```json
    ,{
      "id": "SIG_HOOK_KYC_EXPIRY",
      "category": "SERVICE_THREAT",
      "stage": "HOOK",
      "weight": 12,
      "patterns": {
        "en": ["kyc will expire", "kyc expired update now", "account will be blocked", "verify kyc to avoid block", "pan card kyc update"],
        "hi_latn": ["kyc expire ho jayega", "account block ho jayega", "kyc update karo", "kyc band ho jayega"],
        "hi": ["केवाईसी एक्सपायर", "खाता ब्लॉक हो जाएगा", "केवाईसी अपडेट करें"]
      },
      "match": "FUZZY1",
      "explain": {
        "en": "Caller threatens the account will be blocked unless KYC is updated now",
        "hi": "कॉलर केवाईसी अपडेट न करने पर खाता ब्लॉक होने की धमकी दे रहा है"
      },
      "manualCue": "CUE_KYC_EXPIRY_THREAT"
    },
    {
      "id": "SIG_HOOK_FAMILY_EMERGENCY",
      "category": "KINSHIP_IMPERSONATION",
      "stage": "HOOK",
      "weight": 12,
      "patterns": {
        "en": ["it's me your son", "it is me beta", "mom it's me i'm in trouble", "i had an accident please help", "i am in police custody"],
        "hi_latn": ["beta main bol raha hoon", "mera accident ho gaya", "main musibat mein hoon", "police ne pakad liya"],
        "hi": ["बेटा मैं बोल रहा हूँ", "मेरा एक्सीडेंट हो गया", "मैं मुसीबत में हूँ"]
      },
      "match": "FUZZY1",
      "explain": {
        "en": "Caller claims to be a family member in sudden trouble",
        "hi": "कॉलर अचानक मुसीबत में फंसे परिवार के सदस्य होने का दावा कर रहा है"
      },
      "manualCue": "CUE_FAMILY_EMERGENCY_CLAIM"
    },
    {
      "id": "SIG_ISOLATION_NEW_NUMBER",
      "category": "KINSHIP_IMPERSONATION",
      "stage": "ISOLATION",
      "weight": 14,
      "patterns": {
        "en": ["this is my new number", "my old phone is broken", "do not call my old number", "save this new number"],
        "hi_latn": ["ye mera naya number hai", "purana phone kharab", "purane number pe mat call karna"],
        "hi": ["यह मेरा नया नंबर है", "पुराना फोन खराब", "पुराने नंबर पर मत करना"]
      },
      "match": "FUZZY1",
      "explain": {
        "en": "Caller redirects to a new number and discourages calling the known one back",
        "hi": "कॉलर नए नंबर पर भेजता है और पुराने नंबर पर वापस कॉल करने से रोकता है"
      },
      "manualCue": "CUE_NEW_NUMBER_MISDIRECTION"
    }
```

Note: the `SIG_ISOLATION_NEW_NUMBER` weight (14) is at ISOLATION stage so that kinship-claim (HOOK) + new-number (ISOLATION) + the existing generic `SIG_EXTRACTION_TRANSFER` (EXTRACTION) build the stage ladder to SCAM_PATTERN, while the genuine-emergency script (HOOK kinship only, no ISOLATION/EXTRACTION) stays below. If Step 5 shows the benign script still crosses, lower `SIG_HOOK_FAMILY_EMERGENCY` weight first (never raise the benign-safety bar by weakening real detection without re-checking both tests).

- [ ] **Step 5: Run the full reasoning suite**

Run: `./gradlew :core:reasoning:test`
Expected: PASS — `KinshipEvalTest` both cases green, `PackParityTest` green (all 3 new signals have `manualCue`), `EvalTest` unchanged-green, and the `SignalCategory` enum deserializes the new value.

- [ ] **Step 6: Record the machine-drafted strings for native review + commit**

Add a checklist line under the native-review section of `PROJECT_STATUS.md` (§8 checklist): `SIG_HOOK_KYC_EXPIRY / SIG_HOOK_FAMILY_EMERGENCY / SIG_ISOLATION_NEW_NUMBER hi + hi_latn patterns (pack v3)`.

```bash
git add core/common/src/main/kotlin/ai/vaarta/core/common/IntelPack.kt core/reasoning/src/main/resources/packs/core-scam-v1.json core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/KinshipEvalTest.kt PROJECT_STATUS.md
git commit -m "core:reasoning — pack v3: KYC-expiry + family-emergency impersonation (SC-08/SC-09), benign-emergency guarded"
```

---

### Task 3 (Increment K): Scam-link checker (URL extraction + threat lookup)

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/UrlExtractor.kt`
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/UrlExtractorTest.kt`
- Create: `app/src/main/java/ai/vaarta/ai/LinkChecker.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/ConversationScreen.kt` (inline warning row)
- Modify: `app/src/main/res/values/strings.xml` (+ `values-hi`, `values-b+hi+Latn`)

**Interfaces:**
- Consumes: `BuildConfig.GEMINI_API_KEY` pattern (for the Safe Browsing key BuildConfig field), `HttpURLConnection` (as `GeminiClient` uses).
- Produces: `UrlExtractor.extract(text: String): List<String>`; `LinkChecker.check(url: String): LinkChecker.Verdict` (`MALICIOUS` / `CLEAN_SO_FAR` / `UNKNOWN`).

- [ ] **Step 1: Write the failing URL-extractor test**

Create `UrlExtractorTest.kt`:

```kotlin
package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlExtractorTest {
    @Test
    fun `extracts http and https urls`() {
        assertEquals(
            listOf("http://bit.ly/x", "https://sbi-kyc.example.com/login"),
            UrlExtractor.extract("click http://bit.ly/x or https://sbi-kyc.example.com/login now"),
        )
    }

    @Test
    fun `extracts bare domain with path`() {
        assertTrue(UrlExtractor.extract("go to sbi-verify.co/kyc please").contains("sbi-verify.co/kyc"))
    }

    @Test
    fun `returns empty for plain text`() {
        assertTrue(UrlExtractor.extract("please call me back tomorrow").isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.UrlExtractorTest"`
Expected: FAIL ("Unresolved reference: UrlExtractor").

- [ ] **Step 3: Implement `UrlExtractor`**

Create `UrlExtractor.kt`:

```kotlin
package ai.vaarta.core.reasoning

/** Pure, Android-free URL extraction for scam-link checking (spec §13). */
object UrlExtractor {
    // http(s):// links, or bare domain+path like "sbi-verify.co/kyc". Deliberately conservative:
    // requires a dotted host, and (for bare hosts) a following path so plain "call me at 5.30" is
    // not treated as a domain.
    private val WITH_SCHEME = Regex("""https?://[^\s]+""")
    private val BARE_WITH_PATH = Regex("""\b(?:[a-z0-9-]+\.)+[a-z]{2,}/[^\s]*""", RegexOption.IGNORE_CASE)

    fun extract(text: String): List<String> {
        val out = LinkedHashSet<String>()
        WITH_SCHEME.findAll(text).forEach { out.add(it.value.trimEnd('.', ',', ')')) }
        BARE_WITH_PATH.findAll(text).forEach { m ->
            if (out.none { it.contains(m.value) }) out.add(m.value.trimEnd('.', ',', ')'))
        }
        return out.toList()
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.UrlExtractorTest"`
Expected: PASS.

- [ ] **Step 5: Add the Safe Browsing BuildConfig key (mirrors GEMINI_API_KEY)**

In `app/build.gradle.kts`, wherever `GEMINI_API_KEY` is injected into `buildConfigField`, add a sibling `SAFE_BROWSING_API_KEY` read from the same `local.properties`/env source (empty-string default so absence = fail-closed, exactly like the Gemini key). Match the existing pattern verbatim; do not invent a new mechanism.

- [ ] **Step 6: Implement `LinkChecker` (fail-closed, URLhaus first, Safe Browsing second)**

Create `LinkChecker.kt`. Uses `HttpURLConnection` like `GeminiClient`; blocking, call from `Dispatchers.IO`:

```kotlin
package ai.vaarta.ai

import ai.vaarta.BuildConfig
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Checks a URL against free threat intelligence. Fails closed: any network/parse error → UNKNOWN
 * (the UI says nothing). Never reassures — a not-listed URL is CLEAN_SO_FAR, phrased as "not on
 * known threat lists", never "safe". Never touches the risk score (spec §13 safety invariants).
 */
object LinkChecker {
    enum class Verdict { MALICIOUS, CLEAN_SO_FAR, UNKNOWN }

    private const val TIMEOUT_MS = 6_000
    private const val URLHAUS = "https://urlhaus-api.abuse.ch/v1/url/"
    private const val SAFE_BROWSING =
        "https://safebrowsing.googleapis.com/v4/threatMatches:find?key="

    fun check(url: String): Verdict {
        val viaHaus = urlhaus(url)
        if (viaHaus == Verdict.MALICIOUS) return Verdict.MALICIOUS
        val viaSb = safeBrowsing(url)
        return when {
            viaSb == Verdict.MALICIOUS -> Verdict.MALICIOUS
            viaHaus == Verdict.CLEAN_SO_FAR || viaSb == Verdict.CLEAN_SO_FAR -> Verdict.CLEAN_SO_FAR
            else -> Verdict.UNKNOWN
        }
    }

    private fun urlhaus(url: String): Verdict = try {
        val conn = (URL(URLHAUS).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write("url=${URLEncoder.encode(url, "UTF-8")}".toByteArray()) }
        if (conn.responseCode != 200) return Verdict.UNKNOWN
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val status = JSONObject(body).optString("query_status")
        when {
            JSONObject(body).optString("threat").isNotEmpty() -> Verdict.MALICIOUS
            status == "no_results" -> Verdict.CLEAN_SO_FAR
            else -> Verdict.UNKNOWN
        }
    } catch (e: Exception) {
        Log.w("LinkChecker", "urlhaus failed: ${e.javaClass.simpleName}"); Verdict.UNKNOWN
    }

    private fun safeBrowsing(url: String): Verdict {
        val key = BuildConfig.SAFE_BROWSING_API_KEY
        if (key.isBlank()) return Verdict.UNKNOWN
        return try {
            val conn = (URL(SAFE_BROWSING + key).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """
                {"client":{"clientId":"vaarta","clientVersion":"1.0"},
                 "threatInfo":{"threatTypes":["MALWARE","SOCIAL_ENGINEERING"],
                 "platformTypes":["ANY_PLATFORM"],"threatEntryTypes":["URL"],
                 "threatEntries":[{"url":${JSONObject.quote(url)}}]}}
            """.trimIndent()
            conn.outputStream.use { it.write(payload.toByteArray()) }
            if (conn.responseCode != 200) return Verdict.UNKNOWN
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (JSONObject(body).has("matches")) Verdict.MALICIOUS else Verdict.CLEAN_SO_FAR
        } catch (e: Exception) {
            Log.w("LinkChecker", "safebrowsing failed: ${e.javaClass.simpleName}"); Verdict.UNKNOWN
        }
    }
}
```

- [ ] **Step 7: Add warning strings (all three locales)**

`values/strings.xml`:
```xml
<string name="link_warning_malicious">⚠️ This link is on a known scam/malware list. Do not open it.</string>
```
`values-hi/strings.xml`:
```xml
<string name="link_warning_malicious">⚠️ यह लिंक ज्ञात स्कैम/मैलवेयर सूची में है। इसे न खोलें।</string>
```
`values-b+hi+Latn/strings.xml`:
```xml
<string name="link_warning_malicious">⚠️ Yeh link ek known scam/malware list mein hai. Ise mat kholo.</string>
```
(Add all three to the native-review checklist in `PROJECT_STATUS.md`.)

- [ ] **Step 8: Wire the inline warning into chat**

In `ConversationScreen.kt`, where a chat message's text is rendered, extract URLs via `UrlExtractor.extract(message.text)`; for each, launch a `Dispatchers.IO` check (in the screen's `ViewModel` / a `LaunchedEffect` keyed by message id — do NOT block Compose), and when any returns `MALICIOUS`, render a red warning row (`stringResource(R.string.link_warning_malicious)`) below the message. `CLEAN_SO_FAR`/`UNKNOWN` render nothing. Follow the existing per-message composable structure; keep the check off the main thread and memoized per message id so it runs once.

- [ ] **Step 9: Verify build + live check**

Run: `./gradlew :core:reasoning:test :app:assembleDebug`
Then live on the emulator: send a chat message containing `http://testsafebrowsing.appspot.com/s/malware.html` (Google's deterministic test-malicious URL) → the red warning row appears. Send a message with a normal URL → no warning. Toggle airplane mode, resend → no warning, no crash (fail-closed).

- [ ] **Step 10: Commit**

```bash
git add core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/UrlExtractor.kt core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/UrlExtractorTest.kt app/src/main/java/ai/vaarta/ai/LinkChecker.kt app/src/main/java/ai/vaarta/ui/ConversationScreen.kt app/build.gradle.kts app/src/main/res/values/strings.xml app/src/main/res/values-hi/strings.xml "app/src/main/res/values-b+hi+Latn/strings.xml" PROJECT_STATUS.md
git commit -m "app+core — scam-link checker: URL extraction (TDD) + URLhaus/SafeBrowsing lookup (fail-closed, raise-only, never scores)"
```

---

### Task 4 (Increment B): Confirmation dialogs on destructive rows

**Files:**
- Create: `app/src/main/java/ai/vaarta/ui/components/ConfirmDialog.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (delete-all onClick, ~line 603)
- Modify: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt` (clear-voice-data onClick, ~line 219)
- Modify: `strings.xml` (+ hi, hi+Latn) — 4 strings

**Interfaces:**
- Produces: `ConfirmDialog(visible, title, body, confirmLabel, onConfirm, onDismiss)` composable.

- [ ] **Step 1: Create the reusable dialog**

`ConfirmDialog.kt`:

```kotlin
package ai.vaarta.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ai.vaarta.R

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
```

- [ ] **Step 2: Add strings (three locales)**

`values/strings.xml`:
```xml
<string name="action_cancel">Cancel</string>
<string name="confirm_delete_all_title">Delete all conversations?</string>
<string name="confirm_delete_all_body">This permanently deletes every saved conversation. This cannot be undone.</string>
<string name="confirm_clear_voice_title">Clear voice data?</string>
<string name="confirm_clear_voice_body">VAARTA will forget the voice it learned from your calls. This cannot be undone.</string>
```
Add Hindi (`values-hi`) and Hinglish (`values-b+hi+Latn`) translations of all five, and list them on the native-review checklist.

- [ ] **Step 3: Gate the delete-all row**

In `MainActivity.kt`, near the kebab sheet (~line 600), add `var showDeleteAll by remember { mutableStateOf(false) }`, change the row's `onClick` from `{ historyVm.deleteAll(); showMenu = false }` to `{ showDeleteAll = true }`, and render:

```kotlin
ConfirmDialog(
    visible = showDeleteAll,
    title = stringResource(R.string.confirm_delete_all_title),
    body = stringResource(R.string.confirm_delete_all_body),
    confirmLabel = stringResource(R.string.conv_delete_all),
    onConfirm = { historyVm.deleteAll(); showMenu = false },
    onDismiss = { showDeleteAll = false },
)
```

- [ ] **Step 4: Gate the clear-voice-data row**

In `HelpScreen.kt` (~line 217), add `var showClearVoice by remember { mutableStateOf(false) }`, change the button `onClick` from `{ vm.session.clearVoiceData() }` to `{ showClearVoice = true }`, and render a `ConfirmDialog` with the `confirm_clear_voice_*` strings and `onConfirm = { vm.session.clearVoiceData() }`.

- [ ] **Step 5: Build + live-verify + commit**

Run: `./gradlew :app:assembleDebug`. On the emulator: tap "Delete all" → dialog appears → Cancel keeps data → tap again → Confirm deletes. Same for "Clear voice data".

```bash
git add app/src/main/java/ai/vaarta/ui/components/ConfirmDialog.kt app/src/main/java/ai/vaarta/MainActivity.kt app/src/main/java/ai/vaarta/ui/HelpScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-hi/strings.xml "app/src/main/res/values-b+hi+Latn/strings.xml" PROJECT_STATUS.md
git commit -m "app — confirmation dialogs before destructive delete-all + clear-voice-data (closes task_517a16be)"
```

---

### Task 5 (Increment C): Real guardian contact picker

**Files:**
- Create: `app/src/main/java/ai/vaarta/guardian/GuardianStore.kt`
- Test: `app/src/test/java/ai/vaarta/guardian/GuardianStoreTest.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt` (picker launcher + share branch)
- Modify: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt` (guardian settings row)
- Modify: `strings.xml` (+ hi, hi+Latn)

**Interfaces:**
- Consumes: `SharedPreferences` (as `AwarenessStore.kt` uses), `ActivityResultContracts.PickContact`.
- Produces: `GuardianStore(context)` with `get(): Guardian?`, `set(name, number)`, `clear()`; `data class Guardian(name, number)`.

- [ ] **Step 1: Write the failing store test**

`GuardianStoreTest.kt` (Robolectric or a fake `SharedPreferences`; if the app module has no Robolectric, use an in-memory `SharedPreferences` fake — check existing app tests like `IndiaContextTest` for the established pattern and match it):

```kotlin
package ai.vaarta.guardian

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GuardianStoreTest {
    @Test
    fun `set then get round-trips, clear removes`() {
        val store = GuardianStore(FakePrefs())
        assertNull(store.get())
        store.set("Amma", "+919000000000")
        assertEquals(Guardian("Amma", "+919000000000"), store.get())
        store.clear()
        assertNull(store.get())
    }
}
```

(Define `FakePrefs` as a minimal in-memory `SharedPreferences` in the test, or reuse the app's existing test double if one exists. If `GuardianStore` must take a `Context`, refactor it to take a `SharedPreferences` so it is unit-testable — the app-side caller passes `context.getSharedPreferences(...)`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.guardian.GuardianStoreTest"`
Expected: FAIL (unresolved `GuardianStore`).

- [ ] **Step 3: Implement `GuardianStore`**

```kotlin
package ai.vaarta.guardian

import android.content.SharedPreferences

data class Guardian(val name: String, val number: String)

/** Stores the one chosen guardian contact locally (spec §7). Mirrors AwarenessStore's
 *  SharedPreferences use — no new persistence layer, nothing leaves the device. */
class GuardianStore(private val prefs: SharedPreferences) {
    fun get(): Guardian? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val number = prefs.getString(KEY_NUMBER, null) ?: return null
        return Guardian(name, number)
    }
    fun set(name: String, number: String) =
        prefs.edit().putString(KEY_NAME, name).putString(KEY_NUMBER, number).apply()
    fun clear() = prefs.edit().remove(KEY_NAME).remove(KEY_NUMBER).apply()

    companion object {
        private const val KEY_NAME = "guardian_name"
        private const val KEY_NUMBER = "guardian_number"
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.guardian.GuardianStoreTest"`
Expected: PASS.

- [ ] **Step 5: Add the picker + settings row + share branch**

- In `HelpScreen.kt`, add a "Guardian contact" `LinkRow` (reuse the existing `LinkRow` component) showing the stored guardian name or "Not set", tapping it launches the contact picker (`rememberLauncherForActivityResult(ActivityResultContracts.PickContact())`), which resolves the display name + number and calls `GuardianStore.set(...)`; a "Clear" affordance calls `clear()`. Verify the exact `PickContact` result-URI → name/number query against current Android docs at implementation time (needs `READ_CONTACTS` only for reading the number back — request it at pick time; do not hold it otherwise).
- In `MainActivity.kt`, in the warn-family share flow, branch: if `GuardianStore.get()` is non-null, offer a direct SMS via `Intent(ACTION_SENDTO, "smsto:<number>")` with the message pre-filled; else fall back to the existing `shareText(...)`/chooser path unchanged.

- [ ] **Step 6: Add strings (three locales)**

`guardian_row_title` ("Guardian contact"), `guardian_not_set` ("Not set — tap to choose"), `guardian_clear` ("Remove guardian"). Hindi + Hinglish + native-review checklist.

- [ ] **Step 7: Build + live-verify + commit**

Run: `./gradlew :app:assembleDebug`. On the emulator (with a seeded contact): Help → Guardian contact → pick → name shows; warn-family now offers direct SMS to that contact; clear resets to chooser fallback.

```bash
git add app/src/main/java/ai/vaarta/guardian/ app/src/test/java/ai/vaarta/guardian/ app/src/main/java/ai/vaarta/MainActivity.kt app/src/main/java/ai/vaarta/ui/HelpScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-hi/strings.xml "app/src/main/res/values-b+hi+Latn/strings.xml" PROJECT_STATUS.md
git commit -m "app — real guardian contact picker (SharedPreferences + PickContact), direct-SMS with chooser fallback"
```

---

### Task 6 (Increment D): Overlay speaker-off nudge parity

**Files:**
- Modify: `app/src/main/java/ai/vaarta/OverlayService.kt` (`PanelContent`)

**Interfaces:**
- Consumes: `session.showSpeakerNudge: StateFlow<Boolean>` (already exists on `CopilotSession`) and `R.string.live_active_caption` (already translated).

- [ ] **Step 1: Render the nudge in the overlay panel**

In `OverlayService.kt`'s `PanelContent`, after the `StatusBanner(...)` line (~line 480), add:

```kotlin
val showSpeakerNudge by session.showSpeakerNudge.collectAsState()
var speakerNudgeDismissed by remember { mutableStateOf(false) }
if (showSpeakerNudge && !speakerNudgeDismissed) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.indigoTint, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        ai.vaarta.ui.VaartaIcon(R.drawable.ic_mic, contentDescription = null, tint = c.indigo, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.live_active_caption),
            style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { speakerNudgeDismissed = true }) {
            Text(stringResource(R.string.overlay_hide), style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
    }
}
```

(Reuses the exact `showSpeakerNudge` flow and `live_active_caption` string the in-app `SpeakerNudgeBanner` uses — no new state, no drift. Confirm `c.indigoTint`/`c.indigo`/`c.ink` exist on `VaartaTheme.colors`; they are used in the in-app banner already.)

- [ ] **Step 2: Build + live-verify + commit**

Run: `./gradlew :app:assembleDebug`. Verify the overlay panel renders (screenshot); the nudge row shows when `showSpeakerNudge` is true (can be forced in a debug run by the same path the in-app banner uses).

```bash
git add app/src/main/java/ai/vaarta/OverlayService.kt
git commit -m "app — surface the speaker-off nudge in the floating overlay panel too (parity with in-app Live; closes task_0682d091)"
```

---

### Task 7 (Increment E): Documentation correction + real follow-up tracking

**Files:**
- Modify: `PROJECT_STATUS.md`
- Create: `docs/decisions/0005-call-audio-access.md` (ADR-0005 from spec §15)

- [ ] **Step 1: Correct the Manual Mode entry**

In `PROJECT_STATUS.md`, find the 2026-07-19 changelog note stating the Manual Mode cue UI "was never actually built ... tracked as a follow-up (task_6a52885f)". Replace it with a correction: the Manual Mode UI's absence is **intentional and resolved** — it was deliberately deleted in the v2 pivot (`docs/superpowers/specs/2026-07-14-vaarta-v2-intelligence-ux-design.md`: "Manual Mode is dead weight ... DELETED") because it gave every user the same canned answer, the exact anti-pattern the v2 redesign exists to remove. Not an open gap.

- [ ] **Step 2: Add an "Open follow-ups" table**

In `PROJECT_STATUS.md` §5 area, add a small table tracking the four `task_*` items and their status after this plan: `task_ecd0ce74` (fir bug) → **closed, Task 1**; `task_517a16be` (destructive confirm) → **closed, Task 4**; `task_0682d091` (overlay nudge) → **closed, Task 6**; `task_6a52885f` (Manual Mode) → **not a gap, see correction above**. This becomes the single tracked home for follow-ups, not changelog prose.

- [ ] **Step 3: Write ADR-0005 (call-audio access is closed)**

Create `docs/decisions/0005-call-audio-access.md` following the template in `docs/decisions/0000-adr-template.md`: decision = third-party apps cannot access the `VOICE_CALL` stream (OS-level block since Android 10, not Play policy — sideloading does not help); accessibility-service and Shizuku workarounds rejected (fragile/OEM-dependent; ADB re-arm per reboot, unusable for the audience); consequence = speakerphone + mic is the only sanctioned capture path, verified conclusion. Cite the sources from the research (GrapheneOS issue tracker, The Register 2022 Play ban, ACR/Shizuku).

- [ ] **Step 4: Commit**

```bash
git add PROJECT_STATUS.md docs/decisions/0005-call-audio-access.md
git commit -m "docs — correct Manual Mode mischaracterization, add open-follow-ups table, ADR-0005 (call-audio access closed)"
```

---

### Task 8 (Increment F): Verification pass — emulator then physical phone

Not automatable; the agent prepares, the owner drives the device half.

- [ ] **Step 1: Emulator regression of Tasks 1–6**

Boot `vaarta_test`, install the fresh APK, and drive: (a) demo scam call still reaches SCAM_PATTERN; (b) a benign line with "for/from/sir" does not raise legal-threat; (c) a kinship-scam demo line surfaces the new signals; (d) both confirm dialogs block-then-proceed; (e) guardian pick → direct SMS; (f) link warning on the Safe Browsing test URL; (g) overlay nudge renders. Capture screenshots.

- [ ] **Step 2: Physical-phone live-call test (owner-driven, wireless adb)**

Prepare and hand the owner exact steps: `adb pair <phone-ip:port>` (Wireless debugging → Pair device with code), `adb connect <phone-ip:port>`, `./gradlew :app:assembleDebug`, `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`, launch, grant mic+overlay, start live listening, place a real call on speakerphone (or a second phone reading a scam script). Owner reports: does the risk score move from real caller speech? Record the outcome (works → flagship proven; doesn't transcribe cleanly → R-01 confirmed-hard, pivot demo to recorded-analyzer). Update `PROJECT_STATUS.md` with the result.

---

### Task 9 (Increment G): Definition-of-done hardening pass

- [ ] **Step 1:** Run `security-and-hardening` + `code-simplification` review over the full diff from Tasks 1–7 (agent-driven review skills). Fix findings.
- [ ] **Step 2:** `./gradlew clean test assembleDebug lintDebug` — full suite green, 0 lint issues, from a clean build.
- [ ] **Step 3:** `git-cleanup` — confirm no dead code, no orphan branches; the branch is ready to finish (`superpowers:finishing-a-development-branch`).

---

## Deferred (tracked, not planned as code here)

- **Increment J — voice-anomaly detection:** spike-gated per spec §14. Do NOT write integration code until the spike measures score separation of real vs. synthetic speech on the real speaker→air→mic path. The spike is the gate; a passing result gets its own small follow-up plan.
- **Native-speaker review** of all machine-drafted Hindi/Hinglish strings (now including Tasks 2/3/4/5 additions) — human task, gates "shipping" those languages.
- **Demo video + deck** — done once at the very end, describing the final state.

## Self-Review Notes

- **Spec coverage:** A→Task 1, H→Task 2, K→Task 3, B→Task 4, C→Task 5, D→Task 6, E→Task 7, F→Task 8, G→Task 9, ADR-0005→Task 7 Step 3, J→Deferred. All spec increments mapped.
- **Type consistency:** `LinkChecker.Verdict`, `GuardianStore.get()/set()/clear()`, `Guardian(name, number)`, `UrlExtractor.extract()`, `ConfirmDialog(...)`, `SignalCategory.KINSHIP_IMPERSONATION` used consistently across tasks.
- **Fail-closed preserved:** LinkChecker and the Safe Browsing key both default to UNKNOWN/silent on absence/error; guardian absent → existing chooser; none touch `RiskEngine`.
