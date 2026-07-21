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
