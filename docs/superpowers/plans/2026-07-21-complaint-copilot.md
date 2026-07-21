# Complaint Co-pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Help tab's inert "report a scam" links into an intelligent complaint co-pilot that routes to the right portal, assembles the whole complaint from the call context + the user's saved details, and pre-fills the real portal inside the app — leaving the user only the OTP, CAPTCHA, and final Submit.

**Architecture:** A curated `complaint-playbook-v1.json` (deterministic knowledge: routing, fields, documents, procedure, best-effort selectors) drives a pure-Kotlin `ComplaintRouter`. The app assembles a `ComplaintPacket` from the existing `ComplaintDraft` + an encrypted on-device `IdentityVault`. A three-step Compose flow (Prepare → Review → File) opens the *live* portal in a WebView; an `AutofillBridge` injects values when the DOM matches and always offers tap-to-fill chips as the floor. AI web-grounding adds an advisory freshness check. VAARTA never auto-submits.

**Tech Stack:** Kotlin, Jetpack Compose, Room + SQLCipher (`net.zetetic`), kotlinx.serialization, Android WebView (`evaluateJavascript`), JUnit5 (core JVM tests) + AndroidX instrumented tests, Gemini via existing `GeminiClient`.

## Global Constraints

- **Strict $0.** No paid APIs, no backend/server, no new paid dependency (ADR-0001). Pack ships bundled; any refresh uses a free static host.
- **VAARTA never auto-submits, never fills/touches OTP or CAPTCHA fields, never programmatically clicks Submit.** The final Submit, OTP, and CAPTCHA are always the user's action (`SCAM_INTELLIGENCE.md` §9).
- **Never submit a real complaint during testing.** `AutofillBridge` is exercised only against bundled mock HTML; live-portal manual testing loads read-only and stops before Submit.
- **We render the real live portal** and assist on top — never replace/fabricate government content.
- **No new AI backend** — reuse `GeminiClient` (assembly + existing web-grounding). AI is additive, never on the critical path; every AI path fails silent to the deterministic result.
- **Module rule:** `core:complaint` depends only on `core:common`; `core:reasoning` holds the playbook. The packet assembler that needs both lives in **app** (`ai.vaarta.complaint`). Keep the module graph acyclic.
- **Provenance preserved:** every pre-filled field keeps its `SlotSource` (DETECTED / USER / DEFAULT) "auto-filled — verify" marker.
- **i18n:** every new user-facing string goes through `strings.xml` with HI + Hinglish; safety-critical copy is added to the native-review checklist in `PROJECT_STATUS.md`.
- **Build/verify (Git Bash):** `export JAVA_HOME="/c/Users/Meges/AppData/Local/Programs/jdk17/jdk-17.0.19+10"` then `./gradlew ...`. Emulator AVD `vaarta_test`; package `ai.vaarta.debug`; APK `app/build/outputs/apk/debug/app-debug.apk`.

---

## File Structure

**New files**
- `core/reasoning/src/main/resources/packs/complaint-playbook-v1.json` — the curated playbook.
- `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybook.kt` — pack model + loader.
- `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintRouter.kt` — routing rule.
- `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybookParityTest.kt`
- `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintRouterTest.kt`
- `core/data/src/main/kotlin/ai/vaarta/core/data/db/IdentityDao.kt`
- `core/data/src/androidTest/kotlin/ai/vaarta/core/data/db/IdentityDaoTest.kt`
- `app/src/main/java/ai/vaarta/complaint/ComplaintPacket.kt` — packet + assembler.
- `app/src/main/java/ai/vaarta/complaint/IdentityStore.kt` — vault facade (mirrors `GuardianStore`).
- `app/src/main/java/ai/vaarta/complaint/AutofillBridge.kt` — JS builder + tap-to-fill value provider.
- `app/src/main/java/ai/vaarta/complaint/ComplaintFlowViewModel.kt` — flow state + freshness.
- `app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt` — Prepare/Review/File Compose UI.
- `app/src/main/java/ai/vaarta/ui/SettingsScreen.kt` — trimmed settings (language, guardian, identity, privacy).
- `app/src/test/java/ai/vaarta/complaint/ComplaintPacketAssemblerTest.kt`
- `app/src/test/java/ai/vaarta/complaint/AutofillBridgeTest.kt`
- `app/src/androidTest/assets/mock_ncrp.html`, `app/src/androidTest/assets/mock_chakshu.html`
- `app/src/androidTest/java/ai/vaarta/complaint/AutofillBridgeWebViewTest.kt`

**Modified files**
- `core/data/.../db/Entities.kt` — add `IdentityEntity`.
- `core/data/.../db/VaartaDatabase.kt` — version 4→5, `MIGRATION_4_5`, `identityDao()`.
- `app/.../ui/VaartaNav.kt` — new `SubScreen.Complaint` + `SubScreen.Settings`; wire entry points.
- `app/.../ui/HelpScreen.kt` — replace report/tools links with the "Report this scam" entry; move config rows to Settings.
- `app/.../MainActivity.kt` — pass session→router inputs; open complaint/settings sub-screens.
- `app/src/main/res/values/strings.xml` (+ `values-hi`, `values-b+hi+Latn`) — new strings.
- `PROJECT_STATUS.md` — status matrix, Next Up, change log, native-review checklist.

---

## Phase 1 — Deterministic knowledge core (pure JVM, no device)

### Task 1: Complaint playbook pack + model + parity test

**Files:**
- Create: `core/reasoning/src/main/resources/packs/complaint-playbook-v1.json`
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybook.kt`
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybookParityTest.kt`

**Interfaces:**
- Produces: `ComplaintPlaybook`, `ComplaintDestination`, `PlaybookField`, `PlaybookDocument`, `PlaybookStep`; `ComplaintPlaybookLoader.bundled(): ComplaintPlaybook`.

- [ ] **Step 1: Write the model + loader**

`ComplaintPlaybook.kt`:
```kotlin
package ai.vaarta.core.reasoning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ComplaintPlaybook(
    val packId: String,
    val verifiedOn: String, // ISO date the procedure facts were last confirmed
    val destinations: List<ComplaintDestination>,
)

@Serializable
data class ComplaintDestination(
    val id: String,                 // "ncrp" | "chakshu"
    val name: String,
    val url: String,                // the real form/landing URL rendered in the WebView
    val phone: String? = null,      // "1930" for NCRP financial fraud
    val scamCodes: List<String>,    // routing: which SC-xx codes reach this destination
    val requiresMoneyLost: Boolean = false, // NCRP financial-fraud path ranks first when money moved
    val categoryValue: String,      // the portal category the user should pick
    val fields: List<PlaybookField>,
    val documents: List<PlaybookDocument>,
    val procedure: List<PlaybookStep>,
)

@Serializable
data class PlaybookField(
    val key: String,     // logical id, e.g. "incident.description"
    val label: String,
    val slot: String,    // maps to a packet source: narrative|callerNumber|category|incidentDate|
                         // platform|identity.name|identity.address|identity.mobile|identity.email|
                         // loss.amount|loss.txnId|loss.txnDate
    val selector: String? = null, // best-effort CSS selector for autofill (may be null)
    val minChars: Int? = null,
)

@Serializable
data class PlaybookDocument(
    val key: String,
    val label: String,
    val providedByVaarta: Boolean, // transcript/signals = true; ID proof/bank proof = false
    val note: String,              // format/size, or where to get it
)

@Serializable
data class PlaybookStep(
    val order: Int,
    val text: String,
    val userOnly: Boolean = false, // register/OTP/CAPTCHA/submit — the user's own action
)

object ComplaintPlaybookLoader {
    private val json = Json { ignoreUnknownKeys = true }
    fun fromJson(text: String): ComplaintPlaybook =
        json.decodeFromString(ComplaintPlaybook.serializer(), text)
    fun bundled(): ComplaintPlaybook {
        val stream = ComplaintPlaybookLoader::class.java
            .getResourceAsStream("/packs/complaint-playbook-v1.json")
            ?: error("Complaint playbook resource not found")
        return stream.bufferedReader(Charsets.UTF_8).use { fromJson(it.readText()) }
    }
}
```

- [ ] **Step 2: Write the pack JSON** (`complaint-playbook-v1.json`) — content grounded in the spec §3 research:
```json
{
  "packId": "complaint-playbook@2026.07.1",
  "verifiedOn": "2026-07-21",
  "destinations": [
    {
      "id": "ncrp",
      "name": "National Cyber Crime Reporting Portal",
      "url": "https://cybercrime.gov.in/Webform/Accept.aspx",
      "phone": "1930",
      "scamCodes": ["SC-01", "SC-02", "SC-03", "SC-04", "SC-05"],
      "requiresMoneyLost": true,
      "categoryValue": "Online Financial Fraud",
      "fields": [
        { "key": "incident.category", "label": "Category of complaint", "slot": "category", "selector": null },
        { "key": "incident.datetime", "label": "Approx. date & time of incident", "slot": "incidentDate", "selector": null },
        { "key": "incident.platform", "label": "Where did the incident occur?", "slot": "platform", "selector": null },
        { "key": "incident.description", "label": "Incident description", "slot": "narrative", "selector": "textarea#incidentDesc", "minChars": 200 },
        { "key": "suspect.mobile", "label": "Suspect mobile number", "slot": "callerNumber", "selector": "input#suspectMobile" },
        { "key": "complainant.name", "label": "Your name", "slot": "identity.name", "selector": "input#complainantName" },
        { "key": "complainant.mobile", "label": "Your mobile", "slot": "identity.mobile", "selector": "input#complainantMobile" },
        { "key": "complainant.email", "label": "Your email", "slot": "identity.email", "selector": "input#complainantEmail" },
        { "key": "complainant.address", "label": "Your address", "slot": "identity.address", "selector": "textarea#complainantAddress" }
      ],
      "documents": [
        { "key": "transcript", "label": "Call transcript", "providedByVaarta": true, "note": "VAARTA has this from the call." },
        { "key": "signals", "label": "Detected scam signals (timeline)", "providedByVaarta": true, "note": "VAARTA has this from the analysis." },
        { "key": "id_proof", "label": "Your national ID proof", "providedByVaarta": false, "note": "PAN / Aadhaar / DL / Voter / Passport as .jpg/.jpeg/.png, max 5 MB." },
        { "key": "txn_proof", "label": "Bank transaction proof", "providedByVaarta": false, "note": "Only if money was lost: statement / receipt showing the 12-digit Transaction ID / UTR." }
      ],
      "procedure": [
        { "order": 1, "text": "Register with your mobile + email and verify the OTP", "userOnly": true },
        { "order": 2, "text": "Accept the terms, then choose category: Online Financial Fraud" },
        { "order": 3, "text": "Fill the incident, suspect and your details (VAARTA fills these)" },
        { "order": 4, "text": "Attach your ID proof and any transaction proof" },
        { "order": 5, "text": "Solve the CAPTCHA", "userOnly": true },
        { "order": 6, "text": "Preview everything and check it is correct" },
        { "order": 7, "text": "Submit — you get a 14-digit acknowledgement by SMS", "userOnly": true }
      ]
    },
    {
      "id": "chakshu",
      "name": "Sanchar Saathi — Chakshu (report the number)",
      "url": "https://sancharsaathi.gov.in/sfc/",
      "phone": null,
      "scamCodes": ["SC-02", "SC-03", "SC-08"],
      "requiresMoneyLost": false,
      "categoryValue": "Impersonation as Government official / Agency",
      "fields": [
        { "key": "medium", "label": "Communication medium", "slot": "platform", "selector": null },
        { "key": "sender.number", "label": "Sender / caller number", "slot": "callerNumber", "selector": "input#senderNumber" },
        { "key": "description", "label": "Description", "slot": "narrative", "selector": "textarea#description", "minChars": 30 }
      ],
      "documents": [
        { "key": "screenshot", "label": "Screenshot of the call/SMS", "providedByVaarta": false, "note": "Mandatory: a screenshot of the fraud call log or SMS." },
        { "key": "transcript", "label": "Call transcript", "providedByVaarta": true, "note": "VAARTA has this — useful for the description." }
      ],
      "procedure": [
        { "order": 1, "text": "Choose medium (Call / SMS / WhatsApp) and the fraud category" },
        { "order": 2, "text": "Enter the sender number and description (VAARTA fills these)" },
        { "order": 3, "text": "Attach the mandatory screenshot", "userOnly": true },
        { "order": 4, "text": "Enter your number, solve the CAPTCHA and verify the OTP", "userOnly": true },
        { "order": 5, "text": "Accept the declaration, preview and Submit", "userOnly": true }
      ]
    }
  ]
}
```

- [ ] **Step 3: Write the failing parity test** (`ComplaintPlaybookParityTest.kt`) — mirrors `PackParityTest`'s data-invariant style:
```kotlin
package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ComplaintPlaybookParityTest {

    private val validSlots = setOf(
        "narrative", "callerNumber", "category", "incidentDate", "platform",
        "identity.name", "identity.address", "identity.mobile", "identity.email",
        "loss.amount", "loss.txnId", "loss.txnDate",
    )

    @Test
    fun `bundled playbook loads and every destination is well-formed`() {
        val pb = ComplaintPlaybookLoader.bundled()
        assertFalse(pb.destinations.isEmpty(), "playbook has no destinations")
        for (d in pb.destinations) {
            assertTrue(d.categoryValue.isNotBlank(), "${d.id} missing categoryValue")
            assertTrue(d.procedure.isNotEmpty(), "${d.id} has no procedure steps")
            assertTrue(d.scamCodes.isNotEmpty(), "${d.id} routes from no scam codes")
        }
        assertTrue(pb.verifiedOn.isNotBlank(), "playbook missing verifiedOn")
    }

    @Test
    fun `every mapped field slot is a known packet slot`() {
        val pb = ComplaintPlaybookLoader.bundled()
        val bad = pb.destinations.flatMap { it.fields }.map { it.slot }.filter { it !in validSlots }
        assertTrue(bad.isEmpty(), "unknown field slots (would silently fail autofill): $bad")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.ComplaintPlaybookParityTest" --console=plain`
Expected: 2 tests PASS. (If a `slot` typo exists in the JSON, the second test fails with the offending slot — fix the JSON.)

- [ ] **Step 5: Commit**
```bash
git add core/reasoning/src/main/resources/packs/complaint-playbook-v1.json \
        core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybook.kt \
        core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintPlaybookParityTest.kt
git commit -m "core:reasoning — complaint playbook pack + model + parity test"
```

---

### Task 2: ComplaintRouter

**Files:**
- Create: `core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintRouter.kt`
- Test: `core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintRouterTest.kt`

**Interfaces:**
- Consumes: `ComplaintPlaybook`, `ComplaintDestination` (Task 1).
- Produces: `ComplaintRouter.route(playbook, scamCode: String?, moneyLost: Boolean): List<ComplaintDestination>`.

- [ ] **Step 1: Write the failing test** (`ComplaintRouterTest.kt`):
```kotlin
package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComplaintRouterTest {

    private val pb = ComplaintPlaybookLoader.bundled()

    @Test
    fun `digital arrest with money lost routes NCRP first`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-01", moneyLost = true)
        assertEquals("ncrp", r.first().id)
    }

    @Test
    fun `spam-number scam routes to Chakshu, never NCRP`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-08", moneyLost = false)
        assertEquals(listOf("chakshu"), r.map { it.id })
    }

    @Test
    fun `feeder code SC-02 reaches both, NCRP first when money lost`() {
        val r = ComplaintRouter.route(pb, scamCode = "SC-02", moneyLost = true)
        assertTrue(r.map { it.id }.containsAll(listOf("ncrp", "chakshu")))
        assertEquals("ncrp", r.first().id)
    }

    @Test
    fun `unknown or null scam code returns all destinations for the user to pick`() {
        val r = ComplaintRouter.route(pb, scamCode = null, moneyLost = false)
        assertEquals(pb.destinations.size, r.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.ComplaintRouterTest" --console=plain`
Expected: FAIL — `ComplaintRouter` unresolved.

- [ ] **Step 3: Write the implementation** (`ComplaintRouter.kt`):
```kotlin
package ai.vaarta.core.reasoning

/**
 * Deterministic complaint routing. Given the matched scam code (or null when there is no session),
 * returns the destinations to offer, most-relevant first. Money-lost pushes the NCRP financial-fraud
 * path to the top; a null/unknown code returns everything so the user picks. No AI, fully testable.
 */
object ComplaintRouter {
    fun route(
        playbook: ComplaintPlaybook,
        scamCode: String?,
        moneyLost: Boolean,
    ): List<ComplaintDestination> {
        val matches =
            if (scamCode == null) playbook.destinations
            else playbook.destinations.filter { scamCode in it.scamCodes }
                .ifEmpty { playbook.destinations }
        // Stable: keep pack order, but when money moved lift the money-lost destination(s) to the front.
        return matches.sortedByDescending { it.requiresMoneyLost && moneyLost }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:reasoning:test --tests "ai.vaarta.core.reasoning.ComplaintRouterTest" --console=plain`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**
```bash
git add core/reasoning/src/main/kotlin/ai/vaarta/core/reasoning/ComplaintRouter.kt \
        core/reasoning/src/test/kotlin/ai/vaarta/core/reasoning/ComplaintRouterTest.kt
git commit -m "core:reasoning — deterministic ComplaintRouter (scam code + money-lost -> destinations)"
```

---

## Phase 2 — Encrypted IdentityVault (core:data, instrumented)

### Task 3: IdentityEntity + IdentityDao + migration 4→5

**Files:**
- Modify: `core/data/src/main/kotlin/ai/vaarta/core/data/db/Entities.kt` (append entity)
- Create: `core/data/src/main/kotlin/ai/vaarta/core/data/db/IdentityDao.kt`
- Modify: `core/data/src/main/kotlin/ai/vaarta/core/data/db/VaartaDatabase.kt` (version, migration, dao)
- Test: `core/data/src/androidTest/kotlin/ai/vaarta/core/data/db/IdentityDaoTest.kt`

**Interfaces:**
- Produces: `IdentityEntity(id, name, address, mobile, email, idType)`; `IdentityDao.get()/set()/clear()`; `VaartaDatabase.identityDao()`.

- [ ] **Step 1: Append the entity to `Entities.kt`**:
```kotlin
/**
 * The user's reusable complaint-filing details (spec §4). Single-row table — [id] is fixed at 1 so
 * INSERT OR REPLACE always updates the same row. Deliberately holds NO national-ID number or image —
 * only the ID *type* — so the most sensitive data (the number/scan) is never at rest; the user attaches
 * those directly on the portal. Encrypted at rest by the same SQLCipher database; cleared by [IdentityDao.clear].
 */
@Entity(tableName = "identity")
data class IdentityEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "mobile") val mobile: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "id_type") val idType: String,
)
```

- [ ] **Step 2: Create `IdentityDao.kt`** (mirrors `GuardianDao`):
```kotlin
package ai.vaarta.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity LIMIT 1")
    suspend fun get(): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: IdentityEntity)

    @Query("DELETE FROM identity")
    suspend fun clear()
}
```

- [ ] **Step 3: Wire into `VaartaDatabase.kt`** — three edits:
  1. `entities = [... , GuardianEntity::class, IdentityEntity::class]` and `version = 5`.
  2. Add `abstract fun identityDao(): IdentityDao`.
  3. Add the migration and register it:
```kotlin
/** v4 -> v5 (complaint co-pilot): the identity table for reusable filing details. New table only —
 *  no existing data touched. Holds only the ID *type*, never the number/image (privacy, spec §9). */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `identity` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `name` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `mobile` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `id_type` TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}
```
  and `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.

- [ ] **Step 4: Write the instrumented test** (`IdentityDaoTest.kt`, mirrors `GuardianDaoTest`):
```kotlin
package ai.vaarta.core.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityDaoTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        VaartaDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun sample(name: String = "Asha", mobile: String = "+919000000000") =
        IdentityEntity(name = name, address = "12 MG Road, Pune", mobile = mobile, email = "a@b.in", idType = "PAN")

    @Test fun getReturnsNullWhenEmpty() = runBlocking {
        val d = db(); assertNull(d.identityDao().get()); d.close()
    }

    @Test fun setThenGetRoundTrips() = runBlocking {
        val d = db(); d.identityDao().set(sample())
        val s = d.identityDao().get()
        assertEquals("Asha", s?.name); assertEquals("PAN", s?.idType); d.close()
    }

    @Test fun setCalledTwiceReplacesNotDuplicates() = runBlocking {
        val d = db(); d.identityDao().set(sample())
        d.identityDao().set(sample(name = "Ravi", mobile = "+919111111111"))
        val s = d.identityDao().get(); assertEquals("Ravi", s?.name); d.close()
    }

    @Test fun clearEmptiesTheTable() = runBlocking {
        val d = db(); d.identityDao().set(sample()); d.identityDao().clear()
        assertNull(d.identityDao().get()); d.close()
    }
}
```

- [ ] **Step 5: Run the instrumented test on the emulator**

Run: `./gradlew :core:data:connectedDebugAndroidTest --tests "ai.vaarta.core.data.db.IdentityDaoTest" --console=plain`
Expected: 4 tests PASS. (Emulator `vaarta_test` must be booted.)

- [ ] **Step 6: Commit**
```bash
git add core/data/src/main/kotlin/ai/vaarta/core/data/db/Entities.kt \
        core/data/src/main/kotlin/ai/vaarta/core/data/db/IdentityDao.kt \
        core/data/src/main/kotlin/ai/vaarta/core/data/db/VaartaDatabase.kt \
        core/data/src/androidTest/kotlin/ai/vaarta/core/data/db/IdentityDaoTest.kt
git commit -m "core:data — encrypted IdentityVault table (migration 4->5) + DAO test"
```

---

### Task 4: IdentityStore facade (app)

**Files:**
- Create: `app/src/main/java/ai/vaarta/complaint/IdentityStore.kt`

**Interfaces:**
- Consumes: `IdentityDao`, `IdentityEntity`, `VaartaDatabase` (Task 3).
- Produces: `IdentityDetails(name, address, mobile, email, idType)`; `IdentityStore.create(context)`, `get()`, `set(details)`, `clear()`.

- [ ] **Step 1: Write the facade** (mirrors `GuardianStore` exactly):
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.data.db.IdentityDao
import ai.vaarta.core.data.db.IdentityEntity
import ai.vaarta.core.data.db.VaartaDatabase
import android.content.Context

data class IdentityDetails(
    val name: String,
    val address: String,
    val mobile: String,
    val email: String,
    val idType: String,
)

/** Thin suspend-fun facade over [IdentityDao] so callers never touch Room types (mirrors GuardianStore). */
class IdentityStore private constructor(private val dao: IdentityDao) {
    suspend fun get(): IdentityDetails? = dao.get()?.let {
        IdentityDetails(it.name, it.address, it.mobile, it.email, it.idType)
    }
    suspend fun set(d: IdentityDetails) =
        dao.set(IdentityEntity(name = d.name, address = d.address, mobile = d.mobile, email = d.email, idType = d.idType))
    suspend fun clear() = dao.clear()

    companion object {
        fun create(context: Context): IdentityStore =
            IdentityStore(VaartaDatabase.get(context).identityDao())
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/ai/vaarta/complaint/IdentityStore.kt
git commit -m "app — IdentityStore facade over the encrypted identity DAO"
```

---

## Phase 3 — Packet assembly + autofill bridge

### Task 5: ComplaintPacket + assembler (app, pure JVM test)

**Files:**
- Create: `app/src/main/java/ai/vaarta/complaint/ComplaintPacket.kt`
- Test: `app/src/test/java/ai/vaarta/complaint/ComplaintPacketAssemblerTest.kt`

**Interfaces:**
- Consumes: `ComplaintDestination`, `PlaybookField`, `PlaybookDocument`, `PlaybookStep` (Task 1); `ComplaintDraft` + `SlotSource` (`core:complaint`); `IdentityDetails` (Task 4).
- Produces: `FilledField`, `ChecklistItem`, `ComplaintPacket`; `ComplaintPacketAssembler.assemble(dest, draft, identity, loss): ComplaintPacket`.

- [ ] **Step 1: Write the failing test** (`ComplaintPacketAssemblerTest.kt`):
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintBuilder
import ai.vaarta.core.complaint.ComplaintInput
import ai.vaarta.core.complaint.DetectedSignal
import ai.vaarta.core.common.SignalCategory
import ai.vaarta.core.common.Stage
import ai.vaarta.core.reasoning.ComplaintPlaybookLoader
import ai.vaarta.core.reasoning.ComplaintRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplaintPacketAssemblerTest {

    private fun draft() = ComplaintBuilder.assemble(
        ComplaintInput(
            callerNumber = "+919812345678",
            callStartEpochMs = 1_720_000_000_000L,
            callEndEpochMs = 1_720_000_180_000L,
            languages = listOf("en"),
            matchedScamCode = "SC-01",
            matchedScamName = "Digital Arrest - police/CBI impersonation",
            finalScore = 100,
            detectedSignals = listOf(
                DetectedSignal("SIG_LEGAL_THREAT", SignalCategory.LEGAL_THREAT, Stage.AUTHORITY, 5_000, "arrest threat"),
            ),
        ),
        generatedAtEpochMs = 1_720_000_200_000L,
    )

    private val ncrp = ComplaintRouter.route(ComplaintPlaybookLoader.bundled(), "SC-01", moneyLost = true).first()

    @Test
    fun `narrative field is filled from the draft and clears the min-char floor`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        val desc = packet.fields.first { it.key == "incident.description" }
        assertTrue("narrative too short for portal floor", desc.value.length >= 200)
    }

    @Test
    fun `caller number maps to suspect field`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertEquals("+919812345678", packet.fields.first { it.key == "suspect.mobile" }.value)
    }

    @Test
    fun `identity fills complainant fields when present`() {
        val id = IdentityDetails("Asha", "12 MG Road", "+919000000000", "a@b.in", "PAN")
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = id, loss = null)
        assertEquals("Asha", packet.fields.first { it.key == "complainant.name" }.value)
    }

    @Test
    fun `checklist marks transcript as provided and ID proof as user-supplied`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertTrue(packet.checklist.first { it.label.contains("transcript", true) }.providedByVaarta)
        assertTrue(packet.checklist.any { !it.providedByVaarta })
    }

    @Test
    fun `procedure steps carry the you-only marker text`() {
        val packet = ComplaintPacketAssembler.assemble(ncrp, draft(), identity = null, loss = null)
        assertTrue(packet.procedureSteps.any { it.contains("(you)") })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.complaint.ComplaintPacketAssemblerTest" --console=plain`
Expected: FAIL — `ComplaintPacketAssembler` unresolved.

- [ ] **Step 3: Write the implementation** (`ComplaintPacket.kt`):
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.complaint.SlotSource
import ai.vaarta.core.reasoning.ComplaintDestination

/** Money the user reports lost — entered in the Review step (spec §5, NCRP path only). */
data class LossInput(val amountInr: Long?, val txnId: String?, val txnDate: String?)

data class FilledField(
    val key: String,
    val label: String,
    val value: String,
    val source: SlotSource, // DETECTED / USER / DEFAULT — drives the "auto-filled, verify" marker
    val selector: String?,
    val minChars: Int?,
)

data class ChecklistItem(val label: String, val providedByVaarta: Boolean, val note: String)

data class ComplaintPacket(
    val destinationId: String,
    val destinationName: String,
    val url: String,
    val phone: String?,
    val categoryValue: String,
    val fields: List<FilledField>,
    val checklist: List<ChecklistItem>,
    val procedureSteps: List<String>,
)

object ComplaintPacketAssembler {

    fun assemble(
        dest: ComplaintDestination,
        draft: ComplaintDraft,
        identity: IdentityDetails?,
        loss: LossInput?,
    ): ComplaintPacket {
        val platform = draft.incident.platforms.firstOrNull() ?: "Phone call"
        val fields = dest.fields.mapNotNull { f ->
            val (value, source) = when (f.slot) {
                "narrative" -> draft.narrative.text to SlotSource.DETECTED
                "callerNumber" -> (draft.incident.callerNumbers.firstOrNull() ?: "") to
                    (if (draft.incident.callerNumbers.isNotEmpty()) SlotSource.DETECTED else SlotSource.DEFAULT)
                "category" -> dest.categoryValue to SlotSource.DEFAULT
                "incidentDate" -> draft.incident.startIso to SlotSource.DETECTED
                "platform" -> platform to SlotSource.DETECTED
                "identity.name" -> (identity?.name ?: "") to SlotSource.USER
                "identity.address" -> (identity?.address ?: "") to SlotSource.USER
                "identity.mobile" -> (identity?.mobile ?: "") to SlotSource.USER
                "identity.email" -> (identity?.email ?: "") to SlotSource.USER
                "loss.amount" -> (loss?.amountInr?.toString() ?: "") to SlotSource.USER
                "loss.txnId" -> (loss?.txnId ?: "") to SlotSource.USER
                "loss.txnDate" -> (loss?.txnDate ?: "") to SlotSource.USER
                else -> return@mapNotNull null
            }
            FilledField(f.key, f.label, value, source, f.selector, f.minChars)
        }
        val checklist = dest.documents.map { ChecklistItem(it.label, it.providedByVaarta, it.note) }
        val steps = dest.procedure.sortedBy { it.order }
            .map { if (it.userOnly) "${it.text} (you)" else it.text }
        return ComplaintPacket(
            destinationId = dest.id,
            destinationName = dest.name,
            url = dest.url,
            phone = dest.phone,
            categoryValue = dest.categoryValue,
            fields = fields,
            checklist = checklist,
            procedureSteps = steps,
        )
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.complaint.ComplaintPacketAssemblerTest" --console=plain`
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/ai/vaarta/complaint/ComplaintPacket.kt \
        app/src/test/java/ai/vaarta/complaint/ComplaintPacketAssemblerTest.kt
git commit -m "app — ComplaintPacket + assembler (draft + identity + loss -> filled fields, checklist, steps)"
```

---

### Task 6: AutofillBridge — JS builder + tap-to-fill, tested against a mock page

**Files:**
- Create: `app/src/main/java/ai/vaarta/complaint/AutofillBridge.kt`
- Test: `app/src/test/java/ai/vaarta/complaint/AutofillBridgeTest.kt`
- Create: `app/src/androidTest/assets/mock_ncrp.html`
- Create: `app/src/androidTest/java/ai/vaarta/complaint/AutofillBridgeWebViewTest.kt`

**Interfaces:**
- Consumes: `FilledField` (Task 5).
- Produces: `AutofillBridge.buildFillJs(fields): String`; `AutofillBridge.fillableFields(fields): List<FilledField>` (those with a non-null selector and non-blank value).

- [ ] **Step 1: Write the failing pure-JVM test** (`AutofillBridgeTest.kt`):
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.complaint.SlotSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillBridgeTest {

    private fun f(key: String, value: String, selector: String?) =
        FilledField(key, key, value, SlotSource.DETECTED, selector, null)

    @Test
    fun `only fields with a selector and a value are fillable`() {
        val fields = listOf(
            f("a", "x", "input#a"),
            f("b", "", "input#b"),      // no value
            f("c", "y", null),          // no selector
        )
        assertEquals(listOf("a"), AutofillBridge.fillableFields(fields).map { it.key })
    }

    @Test
    fun `generated JS targets the selector and escapes the value`() {
        val js = AutofillBridge.buildFillJs(listOf(f("a", "he said \"stop\"\nnow", "input#a")))
        assertTrue(js.contains("querySelector(\"input#a\")"))
        assertFalse("raw newline would break the script", js.contains("\n\"stop\"\nnow\n"))
        assertTrue(js.contains("dispatchEvent"))
    }

    @Test
    fun `JS never references submit, otp or captcha controls`() {
        val js = AutofillBridge.buildFillJs(listOf(f("a", "x", "input#a"))).lowercase()
        assertFalse(js.contains("submit")); assertFalse(js.contains("otp")); assertFalse(js.contains("captcha"))
    }
}
```
(Add `import org.junit.Assert.assertEquals`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.complaint.AutofillBridgeTest" --console=plain`
Expected: FAIL — `AutofillBridge` unresolved.

- [ ] **Step 3: Write the implementation** (`AutofillBridge.kt`):
```kotlin
package ai.vaarta.complaint

import org.json.JSONObject

/**
 * Best-effort autofill over the LIVE portal in the WebView. Builds a JS snippet that sets values on
 * the pack's known selectors and fires input/change events so the page's own validation runs. It never
 * targets a submit, OTP or CAPTCHA control and never calls .click()/.submit() — VAARTA fills, the user
 * submits (Global Constraints). When a selector is missing/stale, the field simply isn't filled here;
 * the UI's tap-to-fill chips are the always-present fallback.
 */
object AutofillBridge {

    fun fillableFields(fields: List<FilledField>): List<FilledField> =
        fields.filter { !it.selector.isNullOrBlank() && it.value.isNotBlank() }

    fun buildFillJs(fields: List<FilledField>): String {
        val ops = fillableFields(fields).joinToString("\n") { field ->
            // JSONObject.quote gives a safe, fully-escaped JS string literal (handles quotes/newlines).
            val sel = JSONObject.quote(field.selector)
            val value = JSONObject.quote(field.value)
            """
            (function(){
              var el = document.querySelector($sel);
              if (el) {
                el.value = $value;
                el.dispatchEvent(new Event('input', {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
              }
            })();
            """.trimIndent()
        }
        return "(function(){$ops})();"
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ai.vaarta.complaint.AutofillBridgeTest" --console=plain`
Expected: 3 tests PASS.

- [ ] **Step 5: Create the mock portal page** (`app/src/androidTest/assets/mock_ncrp.html`) — field ids match the pack selectors; a Submit button sets a flag the test asserts is never tripped:
```html
<!doctype html><html><head><meta charset="utf-8"><title>Mock NCRP</title></head>
<body>
  <textarea id="incidentDesc"></textarea>
  <input id="suspectMobile"/>
  <input id="complainantName"/>
  <input id="complainantMobile"/>
  <input id="complainantEmail"/>
  <textarea id="complainantAddress"></textarea>
  <button id="submitBtn" onclick="window.__submitted=true">Submit</button>
  <script>window.__submitted=false;</script>
</body></html>
```

- [ ] **Step 6: Write the instrumented WebView test** (`AutofillBridgeWebViewTest.kt`) — loads the mock asset, injects, reads values back, asserts Submit never fired:
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.complaint.SlotSource
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class AutofillBridgeWebViewTest {

    private fun field(key: String, value: String, selector: String) =
        FilledField(key, key, value, SlotSource.DETECTED, selector, null)

    @Test
    fun fillsMappedFieldsAndNeverSubmits() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val instr = InstrumentationRegistry.getInstrumentation()
        lateinit var web: WebView

        // WebView must be created + driven on the main thread.
        instr.runOnMainSync { web = WebView(ctx).apply { settings.javaScriptEnabled = true } }
        loadAndWait(instr, web, "file:///android_asset/mock_ncrp.html")

        val fields = listOf(
            field("incident.description", "A".repeat(200), "textarea#incidentDesc"),
            field("suspect.mobile", "+919812345678", "input#suspectMobile"),
        )
        val js = AutofillBridge.buildFillJs(fields)
        eval(instr, web, js)

        assertEquals("\"+919812345678\"", eval(instr, web, "document.querySelector('input#suspectMobile').value"))
        assertEquals("false", eval(instr, web, "String(window.__submitted)"))
    }

    private suspend fun loadAndWait(instr: android.app.Instrumentation, web: WebView, url: String) =
        withTimeout(10_000) {
            suspendCancellableCoroutine<Unit> { cont ->
                instr.runOnMainSync {
                    web.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, u: String?) { if (cont.isActive) cont.resume(Unit) }
                    }
                    web.loadUrl(url)
                }
            }
        }

    private suspend fun eval(instr: android.app.Instrumentation, web: WebView, js: String): String =
        withTimeout(10_000) {
            suspendCancellableCoroutine { cont ->
                instr.runOnMainSync { web.evaluateJavascript(js) { if (cont.isActive) cont.resume(it) } }
            }
        }
}
```

- [ ] **Step 7: Run the instrumented test on the emulator**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "ai.vaarta.complaint.AutofillBridgeWebViewTest" --console=plain`
Expected: PASS — mapped field reads back the injected value; `window.__submitted` stays `false`.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/ai/vaarta/complaint/AutofillBridge.kt \
        app/src/test/java/ai/vaarta/complaint/AutofillBridgeTest.kt \
        app/src/androidTest/assets/mock_ncrp.html \
        app/src/androidTest/java/ai/vaarta/complaint/AutofillBridgeWebViewTest.kt
git commit -m "app — AutofillBridge (safe JS fill + tap-to-fill) verified against a mock portal, never submits"
```

---

## Phase 4 — UI flow + IA/settings

> UI tasks follow existing patterns: `VaartaSubScreen`, `VaartaButton`/`VaartaSecondaryButton`, `LinkRow`/`TextLinkRow`, `VaartaTheme.colors`, `VSpace`, `stringResource`, and the `WebViewArticle` pattern in `ArticleScreen.kt`. Every visible string uses `stringResource` (added in Task 10). Reduced-motion + dark mode come for free from the theme.

### Task 7: ComplaintFlowViewModel + nav wiring + Prepare step

**Files:**
- Create: `app/src/main/java/ai/vaarta/complaint/ComplaintFlowViewModel.kt`
- Create: `app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/VaartaNav.kt`
- Modify: `app/src/main/java/ai/vaarta/MainActivity.kt`

**Interfaces:**
- Consumes: `ComplaintPlaybookLoader`, `ComplaintRouter` (Task 1–2), `ComplaintPacketAssembler`, `IdentityStore` (Task 4–5), session's `complaintDraft`/`scamType`.
- Produces: `ComplaintFlowViewModel` with `state: StateFlow<ComplaintFlowState>`; `ComplaintFlowState(step, destinations, selected, packet, identity, loss, freshness)`; `SubScreen.Complaint`.

- [ ] **Step 1: Write the ViewModel** — holds flow state; on open, builds draft → routes → assembles packet. All deterministic; the freshness field is filled in Task 11.
```kotlin
package ai.vaarta.complaint

import ai.vaarta.core.complaint.ComplaintDraft
import ai.vaarta.core.reasoning.ComplaintDestination
import ai.vaarta.core.reasoning.ComplaintPlaybookLoader
import ai.vaarta.core.reasoning.ComplaintRouter
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ComplaintStep { PREPARE, REVIEW, FILE }

data class ComplaintFlowState(
    val step: ComplaintStep = ComplaintStep.PREPARE,
    val destinations: List<ComplaintDestination> = emptyList(),
    val selected: ComplaintDestination? = null,
    val packet: ComplaintPacket? = null,
    val identity: IdentityDetails? = null,
    val loss: LossInput? = null,
    val freshnessNote: String? = null, // Task 11
)

class ComplaintFlowViewModel(app: Application) : AndroidViewModel(app) {
    private val playbook = ComplaintPlaybookLoader.bundled()
    private val identityStore = IdentityStore.create(app)
    private val _state = MutableStateFlow(ComplaintFlowState())
    val state: StateFlow<ComplaintFlowState> = _state.asStateFlow()

    private var draft: ComplaintDraft? = null

    /** Open with the session's draft + matched scam code (either may be null → user picks). */
    fun open(draft: ComplaintDraft?, scamCode: String?, moneyLost: Boolean) {
        this.draft = draft
        val dests = ComplaintRouter.route(playbook, scamCode, moneyLost)
        _state.value = ComplaintFlowState(destinations = dests, selected = dests.firstOrNull())
        viewModelScope.launch { _state.value = _state.value.copy(identity = identityStore.get()) }
    }

    fun selectDestination(d: ComplaintDestination) { _state.value = _state.value.copy(selected = d) }

    fun toReview() {
        val d = _state.value.selected ?: return
        val dr = draft ?: return
        _state.value = _state.value.copy(
            step = ComplaintStep.REVIEW,
            packet = ComplaintPacketAssembler.assemble(d, dr, _state.value.identity, _state.value.loss),
        )
    }

    fun setLoss(loss: LossInput) { _state.value = _state.value.copy(loss = loss); reassemble() }

    fun saveIdentity(details: IdentityDetails) {
        _state.value = _state.value.copy(identity = details)
        viewModelScope.launch { identityStore.set(details) }
        reassemble()
    }

    fun toFile() { _state.value = _state.value.copy(step = ComplaintStep.FILE) }
    fun back() {
        _state.value = when (_state.value.step) {
            ComplaintStep.FILE -> _state.value.copy(step = ComplaintStep.REVIEW)
            ComplaintStep.REVIEW -> _state.value.copy(step = ComplaintStep.PREPARE)
            ComplaintStep.PREPARE -> _state.value
        }
    }

    private fun reassemble() {
        val d = _state.value.selected ?: return
        val dr = draft ?: return
        _state.value = _state.value.copy(
            packet = ComplaintPacketAssembler.assemble(d, dr, _state.value.identity, _state.value.loss),
        )
    }
}
```

- [ ] **Step 2: Add the nav sub-screen** — in `VaartaNav.kt`: extend `SubScreen` with `data object Complaint : SubScreen` and `data object Settings : SubScreen`; add the `ComplaintFlowViewModel` param to `VaartaNav`; render `ComplaintFlowScreen` in the `AnimatedContent when`. Wire the Help entry point (`onReport = { complaintVm.open(draft, scamCode, moneyLost=false); sub = SubScreen.Complaint }`). Pass `complaintVm` from `MainActivity` (create it with `viewModels()` alongside `panicVm`).

- [ ] **Step 3: Write the Prepare step of `ComplaintFlowScreen.kt`** — `VaartaSubScreen` with a `when(state.step)`; PREPARE shows the routed destination card(s) (name + why + 1930 chip if `phone != null`), a destination picker when >1, and a primary "Continue →" calling `vm.toReview()`. (REVIEW/FILE added in Tasks 8–9 — leave `TODO`-free stubs that render an empty Column for now so it compiles.)

- [ ] **Step 4: Build + launch, verify Prepare renders**

Run: `./gradlew :app:assembleDebug --console=plain` then install + launch (see Global Constraints), tap Help → Report → screenshot.
Expected: APK builds; Prepare screen shows the routed destination and Continue.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/ai/vaarta/complaint/ComplaintFlowViewModel.kt \
        app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt \
        app/src/main/java/ai/vaarta/ui/VaartaNav.kt app/src/main/java/ai/vaarta/MainActivity.kt
git commit -m "app — complaint flow VM + nav wiring + Prepare step"
```

---

### Task 8: Review step

**Files:**
- Modify: `app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt`

- [ ] **Step 1: Implement the REVIEW branch** — render from `state.packet`:
  - **Your complaint** — each `FilledField` as an editable `OutlinedTextField` (label + value), with a small "auto-filled — verify" caption when `source != SlotSource.USER`, and a red hint when `minChars != null && value.length < minChars`.
  - **Your details** — if `state.identity == null`, an "Add your details once" card that opens an identity form sheet (name/address/mobile/email/ID-type dropdown) → `vm.saveIdentity(...)`; else a summary + "Edit".
  - **Money lost?** — shown only when `selected.requiresMoneyLost`: amount + Transaction ID + txn date fields → `vm.setLoss(...)`.
  - **Documents** — `state.packet.checklist` as rows: check-glyph + label + note; `providedByVaarta` rows styled as done.
  - `state.freshnessNote?.let { advisory banner }` (populated in Task 11).
  - Primary "Continue to file →" → `vm.toFile()`.

- [ ] **Step 2: Build, launch, drive Prepare→Review**

Run: `./gradlew :app:assembleDebug` → install → tap through. Screenshot Review with a demo-call draft (run the demo call first so a draft exists).
Expected: fields pre-filled from the draft; identity prompt shows; doc checklist correct.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt
git commit -m "app — complaint flow Review step (editable fields, identity vault, loss, doc checklist)"
```

---

### Task 9: File step (WebView + step rail + autofill + tap-to-fill + safety banner)

**Files:**
- Modify: `app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt`

**Interfaces:**
- Consumes: `AutofillBridge` (Task 6), `state.packet` (Task 5).

- [ ] **Step 1: Implement the FILE branch**:
  - A `WebView` (same setup as `WebViewArticle`: JS + DOM storage on, own scrolling) holding a `remember`ed reference, loading `state.packet.url`.
  - A persistent safety banner: `stringResource(R.string.complaint_you_submit_banner)` ("You review and submit — VAARTA only fills").
  - A bottom bar with: **"Fill this page"** → `web.evaluateJavascript(AutofillBridge.buildFillJs(packet.fields), null)`; **"Copy complaint text"** → copies the narrative field value to clipboard; and a horizontally-scrolling row of **tap-to-fill chips** — one per `AutofillBridge.fillableFields(packet.fields)` — each tap copies that field's value to the clipboard and shows a toast "Copied — paste into <label>".
  - A collapsible **step rail** listing `state.packet.procedureSteps` (the `(you)` marker already embedded).
  - **No** control that targets Submit/OTP/CAPTCHA or calls `.click()`; VAARTA never auto-submits (Global Constraints).

- [ ] **Step 2: Build, launch, drive to File against the LIVE portal read-only**

Run: `./gradlew :app:assembleDebug` → install → Prepare→Review→File. The real portal loads; tap "Fill this page" on any visible public field; tap a chip.
Expected: page renders; visible mapped fields fill / chip copies value; **do not** register, solve CAPTCHA, or submit anything (Global Constraints). Screenshot.

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/ai/vaarta/ui/ComplaintFlowScreen.kt
git commit -m "app — complaint flow File step (live portal WebView, autofill + tap-to-fill, step rail, never submits)"
```

---

### Task 10: Help IA restructure + Settings screen + strings

**Files:**
- Modify: `app/src/main/java/ai/vaarta/ui/HelpScreen.kt`
- Create: `app/src/main/java/ai/vaarta/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/ai/vaarta/ui/VaartaNav.kt`, `MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add all new strings** to `values/strings.xml` (English), e.g.: `complaint_report_title`, `complaint_report_sub`, `complaint_prepare_why`, `complaint_continue`, `complaint_continue_file`, `complaint_you_submit_banner`, `complaint_fill_page`, `complaint_copy_text`, `complaint_copied_toast`, `complaint_add_details`, `complaint_money_lost_title`, `complaint_amount`, `complaint_txn_id`, `complaint_txn_date`, `identity_name`, `identity_address`, `identity_mobile`, `identity_email`, `identity_id_type`, `settings_title`, `settings_your_details`, `settings_your_details_sub`, `settings_privacy_title`. (Reuse existing language/guardian/clear-voice strings.)

- [ ] **Step 2: Restructure `HelpScreen.kt`** — Help becomes action-only:
  - Keep the Emergency card (1930 + panic steps) and "If you've already lost money".
  - **Replace** the `help_report_title` link section and the `help_tools_*` complaint/warn-family rows with **one** primary `LinkRow` "Report a scam" (→ `onReport()` wired in Task 7) plus the "Warn your family" row.
  - **Move** the language row, the guardian rows, and the "Clear voice data" row **out** to the new Settings screen. Add a single "Settings" `LinkRow` at the bottom → `onOpenSettings()`.

- [ ] **Step 3: Create `SettingsScreen.kt`** — a `VaartaSubScreen` with only: **App language** (opens the existing `LanguageOptionsList` sheet), **Guardian contact** (the existing picker + clear), **Your filing details** (summary of `IdentityStore.get()`; row opens the same identity form as Review; "Clear" wipes it via `IdentityStore.clear()`), and **Privacy** (Clear conversations + Clear voice data, both behind the existing `ConfirmDialog`).

- [ ] **Step 4: Wire `SubScreen.Settings`** in `VaartaNav.kt` (`onOpenSettings = { sub = SubScreen.Settings }`), render `SettingsScreen`.

- [ ] **Step 5: Build, launch, verify Help is trimmed and Settings holds only the four groups**

Run: `./gradlew :app:assembleDebug` → install → screenshot Help + Settings.
Expected: Help = emergency · report · lost-money · warn-family · Settings link. Settings = language · guardian · your details · privacy. Nothing else.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/ai/vaarta/ui/HelpScreen.kt app/src/main/java/ai/vaarta/ui/SettingsScreen.kt \
        app/src/main/java/ai/vaarta/ui/VaartaNav.kt app/src/main/java/ai/vaarta/MainActivity.kt \
        app/src/main/res/values/strings.xml
git commit -m "app — Help IA trimmed to actions; clean Settings screen (language, guardian, your details, privacy)"
```

---

## Phase 5 — Freshness, i18n, full verification

### Task 11: PlaybookFreshness advisory (reuse Gemini grounding, fail-silent)

**Files:**
- Modify: `app/src/main/java/ai/vaarta/complaint/ComplaintFlowViewModel.kt`
- Modify: `app/src/main/java/ai/vaarta/ai/GeminiClient.kt` (add a `checkPlaybookFreshness` call, same grounding pattern as the feed)

**Interfaces:**
- Produces: `GeminiClient.checkPlaybookFreshness(destinationName, url, verifiedOn): String?` — a one-line advisory or null; `ComplaintFlowViewModel` sets `state.freshnessNote`.

- [ ] **Step 1: Add the grounded check** to `GeminiClient` mirroring the existing web-grounded call used by the feed/scam-ID: prompt = "As of today, has the complaint procedure or required documents for <destinationName> (<url>) changed since <verifiedOn>? If materially changed, reply with one short sentence naming the change; else reply exactly 'NONE'." Return `null` on `NONE`, blank, not-configured, offline, or any exception (fail-silent — never blocks the flow).

- [ ] **Step 2: Call it from `open()`** in a `viewModelScope.launch { withContext(Dispatchers.IO){...} }`, setting `freshnessNote` when non-null. The Review banner (Task 8) already renders it.

- [ ] **Step 3: Build + manual check** — with AI configured + online, open the flow; if the model flags a change, the advisory shows; offline, nothing shows and the flow is unaffected.

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL; flow works online and offline (advisory optional).

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/ai/vaarta/complaint/ComplaintFlowViewModel.kt app/src/main/java/ai/vaarta/ai/GeminiClient.kt
git commit -m "app — advisory AI freshness check on the complaint playbook (fail-silent, never blocks)"
```

---

### Task 12: Hindi + Hinglish translations

**Files:**
- Modify: `app/src/main/res/values-hi/strings.xml`, `app/src/main/res/values-b+hi+Latn/strings.xml`
- Modify: `PROJECT_STATUS.md` (append the new safety-critical strings to the native-review checklist)

- [ ] **Step 1: Translate every new string** from Task 10 (+ any freshness string) into both files. Machine-drafted, marked pending native review per the existing house rule.

- [ ] **Step 2: Add to the native-review checklist** in `PROJECT_STATUS.md`: `complaint_you_submit_banner`, `complaint_prepare_why`, `complaint_money_lost_title`, and the identity field labels (safety/PII-sensitive copy).

- [ ] **Step 3: Verify no missing translations**

Run: `./gradlew :app:lintDebug --console=plain`
Expected: BUILD SUCCESSFUL, zero `MissingTranslation`.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/res/values-hi/strings.xml app/src/main/res/values-b+hi+Latn/strings.xml PROJECT_STATUS.md
git commit -m "app — HI + Hinglish translations for the complaint co-pilot (pending native review)"
```

---

### Task 13: Full verification pass + status update

**Files:**
- Modify: `PROJECT_STATUS.md` (status matrix, Next Up, change log)

- [ ] **Step 1: Full clean gate**

Run: `./gradlew clean test assembleDebug lintDebug --console=plain`
Expected: all unit tests PASS (new: playbook parity ×2, router ×4, packet ×5, autofill ×3), `assembleDebug` SUCCESS, `lintDebug` zero errors. Record the fresh total test count from `*/build/test-results/**/*.xml`.

- [ ] **Step 2: Instrumented gate on the emulator**

Run: `./gradlew :core:data:connectedDebugAndroidTest --tests "*IdentityDaoTest" :app:connectedDebugAndroidTest --tests "*AutofillBridgeWebViewTest" --console=plain`
Expected: IdentityDao ×4 PASS; AutofillBridge WebView PASS (fills mock, never submits).

- [ ] **Step 3: Manual emulator pass** — run the demo call (to create a draft), then Help → Report → Prepare → Review → File. Verify: routing correct; fields pre-filled; identity add/edit/clear; doc checklist; File loads the live portal and "Fill this page"/chips work on public fields. **Do not submit anything.** Capture screenshots (light + dark, EN + HI + Hinglish) to the scratchpad. Also confirm the `AutofillBridge` mock-page test is the only place a "fill" runs to completion — no real submission occurred.

- [ ] **Step 4: Update `PROJECT_STATUS.md`** — move the complaint co-pilot to "Built and verified" with evidence (test counts + screenshots + the read-only live-portal note); add a dated change-log entry describing router/pack/vault/packet/autofill/flow/settings and the "never-submitted-a-real-complaint" testing discipline; add any follow-ups (e.g. real selector capture for NCRP's logged-in form, pack-refresh host) to the Open follow-ups tracker.

- [ ] **Step 5: Commit**
```bash
git add PROJECT_STATUS.md
git commit -m "docs — PROJECT_STATUS: complaint co-pilot built + verified (mock-page autofill, no real submissions)"
```

---

## Self-Review

**Spec coverage:** §3 portal knowledge → Task 1 pack. §4 components → router (T2), vault (T3–4), packet (T5), autofill (T6), freshness (T11), flow+bridge (T7–9). §4.1 field mapping → T5 assembler. §5 Prepare/Review/File → T7/T8/T9. §6 settings cleanup → T10. §7 freshness (3 layers) → live WebView (T9) + AI advisory (T11) + `verifiedOn` in pack (T1); remote refresh host explicitly deferred (spec §9, noted in T13 follow-ups). §8 testing incl. "never submit fake complaints" → mock page + read-only live (T6, T9 step 2, T13). §2 rails → Global Constraints + enforced by AutofillBridge tests (T6). All covered.

**Placeholder scan:** No TBD/TODO; every code step shows real code; test bodies are concrete. Task 7 step 3 intentionally leaves compiling empty REVIEW/FILE branches filled in T8/T9 (sequenced, not a placeholder).

**Type consistency:** `ComplaintDestination`/`PlaybookField.slot` (T1) ↔ `ComplaintPacketAssembler` slot switch (T5) ↔ parity `validSlots` (T1) — same slot vocabulary. `FilledField`(T5) ↔ `AutofillBridge`(T6) ↔ File step (T9). `IdentityDetails`(T4) ↔ VM/assembler/Settings. `ComplaintRouter.route(playbook, scamCode, moneyLost)` signature identical in T2 test, T5 test, T7 VM. Consistent.
