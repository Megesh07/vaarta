package ai.vaarta.core.reasoning

/**
 * Maps a free-text scam type/category (AI-labelled or user-visible) to one of the 11 bundled
 * cover-illustration keys (redesign spec §5.1 — India's fraud landscape per I4C/1930 reporting).
 * Ordered first-match: digital-arrest cues outrank parcel/UPI ("police courier scam" is a
 * digital-arrest story), and anything unrecognized falls back to the safe generic cover.
 */
private val COVER_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "digital_arrest" to listOf("digital arrest", "arrest", "police", "cbi", "ed", "court", "impersonat\\w*", "fir", "narcotic\\w*"),
    "upi" to listOf("upi", "qr", "phonepe", "gpay", "google pay", "paytm", "payment request", "cashback"),
    "parcel" to listOf("parcel", "courier", "fedex", "customs", "shipment", "package"),
    "kyc_bank" to listOf("kyc", "bank", "account", "sim", "aadhaar", "aadhar", "pan", "net-banking", "netbanking", "card", "identity"),
    "investment" to listOf("invest\\w*", "trading", "stock", "crypto", "ipo", "ponzi", "sebi", "share market"),
    "job" to listOf("job", "jobs", "task", "work-from-home", "work from home", "recruit\\w*", "part-time", "part time"),
    "loan_app" to listOf("loan", "recovery", "harassment"),
    "lottery" to listOf("lottery", "prize", "lucky draw", "kbc", "festival offer", "gift"),
    "romance" to listOf("romance", "matrimonial", "dating", "honeytrap"),
    "utility" to listOf("electricity", "utility", "bill", "disconnect\\w*", "gas", "meter"),
)

// Word-boundary regexes, compiled once. Substring matching burned us: "Task-Based" contains
// "ed " (Enforcement Directorate) — \b keeps short agency tokens from firing inside words.
private val COVER_PATTERNS: List<Pair<String, Regex>> = COVER_KEYWORDS.map { (key, words) ->
    key to Regex("\\b(?:" + words.joinToString("|") + ")\\b")
}

fun coverKeyForScamType(scamType: String?): String {
    val t = scamType?.trim()?.lowercase() ?: return "generic"
    if (t.isBlank()) return "generic"
    for ((key, pattern) in COVER_PATTERNS) {
        if (pattern.containsMatchIn(t)) return key
    }
    return "generic"
}
