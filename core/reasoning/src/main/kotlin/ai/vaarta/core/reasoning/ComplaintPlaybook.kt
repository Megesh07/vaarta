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
