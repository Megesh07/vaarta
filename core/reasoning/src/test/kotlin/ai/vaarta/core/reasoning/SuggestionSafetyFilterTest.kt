package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SuggestionSafetyFilterTest {

    private fun accepted(t: String) = SuggestionSafetyFilter.check(t) is SuggestionSafetyFilter.Result.Accepted
    private fun rejected(t: String) = SuggestionSafetyFilter.check(t) is SuggestionSafetyFilter.Result.Rejected

    @Test
    fun `rejects advising the victim to pay or comply`() {
        assertTrue(rejected("You should pay the fine to avoid arrest."))
        assertTrue(rejected("Just pay them and it will be resolved."))
        assertTrue(rejected("It's safe to transfer the money to clear your name."))
        assertTrue(rejected("Go ahead and pay the deposit."))
        assertTrue(rejected("Make the payment to the RBI account."))
        assertTrue(rejected("You should comply with the officer."))
    }

    @Test
    fun `rejects legal-advice phrasing`() {
        assertTrue(rejected("This is illegal and you must respond."))
        assertTrue(rejected("You have the right to remain silent."))
        assertTrue(rejected("You should refuse to answer."))
    }

    @Test
    fun `rejects accusing the specific caller`() {
        assertTrue(rejected("This caller is a scammer, hang up."))
        assertTrue(rejected("He is a criminal."))
    }

    @Test
    fun `rejects empty or blank suggestions`() {
        assertTrue(rejected(""))
        assertTrue(rejected("   "))
    }

    @Test
    fun `accepts legitimate verification questions even when they mention money`() {
        // These are exactly what we WANT the user to say — must not be over-blocked.
        assertTrue(accepted("Why would a government agency need me to pay money to avoid arrest?"))
        assertTrue(accepted("Which police station are you calling from? I will call them directly."))
        assertTrue(accepted("I will verify this with the 1930 helpline and call back."))
        assertTrue(accepted("I am adding my son to this call now."))
        assertTrue(accepted("Please send the FIR copy to my official email."))
    }

    @Test
    fun `sanitizedOrNull returns text when safe and null when unsafe`() {
        val safe = LiveSuggestion("Which police station are you from?", why = "verify", confidence = 0.9)
        val unsafe = LiveSuggestion("You should pay the fine now.", why = "", confidence = 0.9)
        assertTrue(SuggestionSafetyFilter.sanitizedOrNull(safe) == safe.suggestedReply)
        assertNull(SuggestionSafetyFilter.sanitizedOrNull(unsafe))
    }
}
