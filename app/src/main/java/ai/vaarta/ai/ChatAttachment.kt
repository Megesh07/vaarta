package ai.vaarta.ai

/**
 * A file the user attached to a chat message (v2 Phase 3): a screenshot of a suspicious SMS/WhatsApp,
 * or a short call recording. Sent to Gemini inline (base64); NOT persisted (only a [label] marker is
 * saved with the turn — the DB stays lean and no media is stored, matching ADR-0004's minimalism).
 */
data class ChatAttachment(
    val mimeType: String,
    val bytes: ByteArray,
    /** Short human marker shown in the sent bubble + saved as text, e.g. "📷 Photo" / "🎧 Audio clip". */
    val label: String,
) {
    // ByteArray needs value-based equals/hashCode for Compose state correctness.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatAttachment) return false
        return mimeType == other.mimeType && label == other.label && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
