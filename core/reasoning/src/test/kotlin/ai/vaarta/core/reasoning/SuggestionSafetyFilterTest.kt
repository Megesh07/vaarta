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

    @Test
    fun `sanitizedOrNull also gates on the why field`() {
        val unsafeWhy = LiveSuggestion("Which police station are you from?", why = "Just pay them to be safe.", confidence = 0.9)
        assertNull(SuggestionSafetyFilter.sanitizedOrNull(unsafeWhy))
    }

    // --- ADR-0003 expansion: OTP/PIN/Aadhaar/bank-detail disclosure ---

    @Test
    fun `rejects advising the victim to disclose OTP PIN or identity numbers`() {
        assertTrue(rejected("Sure, I'll share my OTP with you."))
        assertTrue(rejected("Okay, I will give them the PIN."))
        assertTrue(rejected("I will tell you my Aadhaar number."))
        assertTrue(rejected("I can send my PAN card details now."))
        assertTrue(rejected("Yes, I will provide my bank details."))
    }

    @Test
    fun `accepts refusing to disclose OTP PIN or identity numbers`() {
        assertTrue(accepted("I will not share my OTP with anyone."))
        assertTrue(accepted("I won't give you my Aadhaar number."))
        assertTrue(accepted("I will never share my PIN with anyone."))
    }

    // --- ADR-0003 expansion: isolation compliance (stay on the line / don't tell family) ---

    @Test
    fun `rejects complying with isolation instructions`() {
        assertTrue(rejected("I will not tell my family about this call."))
        assertTrue(rejected("I won't tell anyone until this is resolved."))
        assertTrue(rejected("Okay, I will stay on the line."))
        assertTrue(rejected("Sure, I will keep this secret."))
    }

    @Test
    fun `accepts breaking isolation`() {
        assertTrue(accepted("I am adding my son to this call right now."))
        assertTrue(accepted("I will tell my family about this call immediately."))
        assertTrue(accepted("I will not stay on the line without verifying first."))
    }

    // --- ADR-0003 expansion: remote access / app install / screen share ---

    @Test
    fun `rejects agreeing to install remote-access software or share screen`() {
        assertTrue(rejected("Okay, I will install the app now."))
        assertTrue(rejected("Sure, I'll share my screen with you."))
        assertTrue(rejected("I will download AnyDesk."))
    }

    @Test
    fun `accepts refusing remote access`() {
        assertTrue(accepted("I will not install any app on my phone."))
        assertTrue(accepted("I won't share my screen with anyone."))
    }

    // --- ADR-0003 expansion: generic compliance verbs ---

    @Test
    fun `rejects generic compliance phrasing`() {
        assertTrue(rejected("You should cooperate with the officer."))
        assertTrue(rejected("Just do what the officer says."))
        assertTrue(rejected("I will follow their instructions."))
    }

    // --- ADR-0003 expansion: Hindi and Hinglish ---

    @Test
    fun `rejects unsafe compliance in Hindi and Hinglish`() {
        assertTrue(rejected("Theek hai, main paisa transfer kar doonga."))
        assertTrue(rejected("main otp bata doonga"))
        assertTrue(rejected("kisi ko mat batao"))
    }

    @Test
    fun `accepts safe replies that happen to be in Hinglish`() {
        assertTrue(accepted("Main 1930 helpline se confirm karunga."))
        assertTrue(accepted("Main apne bete ko is call mein add kar raha hoon."))
    }

    // --- Regression guard: the interrogative "why would they need X" framing must never trip ---

    @Test
    fun `does not over-block questions that merely mention sensitive nouns`() {
        assertTrue(accepted("Why would a government agency need my OTP over the phone?"))
        assertTrue(accepted("Why do you need me to install any app?"))
        assertTrue(accepted("Why should I stay on the line if you're really from the CBI?"))
    }

    @Test
    fun `filterReplies keeps only safe replies`() {
        val replies = listOf(
            Reply("I will not share my OTP with anyone.", ReplyKind.REFUSE),
            Reply("Okay, I will share my OTP.", ReplyKind.REFUSE),
            Reply("Which police station are you calling from?", ReplyKind.VERIFY),
        )
        val kept = SuggestionSafetyFilter.filterReplies(replies)
        assertTrue(kept.size == 2)
        assertTrue(kept.none { it.text.contains("Okay, I will share") })
    }

    @Test
    fun `sanitize discards the whole turn when the warning is unsafe`() {
        val response = CoachingResponse(
            warning = "You should just pay them to be safe.",
            replies = listOf(Reply("I will not transfer any money.", ReplyKind.REFUSE)),
        )
        assertNull(SuggestionSafetyFilter.sanitize(response))
    }

    @Test
    fun `sanitize discards the whole turn when no reply survives`() {
        val response = CoachingResponse(
            warning = "The caller is claiming to be from the CBI.",
            replies = listOf(Reply("Okay, I will pay the fine.", ReplyKind.REFUSE)),
        )
        assertNull(SuggestionSafetyFilter.sanitize(response))
    }

    @Test
    fun `sanitize returns the coaching response when everything is safe`() {
        val response = CoachingResponse(
            warning = "The caller is claiming to be from the CBI — a real agency would never call like this.",
            replies = listOf(
                Reply("Which police station are you calling from?", ReplyKind.VERIFY),
                Reply("I will not transfer any money.", ReplyKind.REFUSE),
            ),
        )
        val result = SuggestionSafetyFilter.sanitize(response)
        assertTrue(result != null && result.replies.size == 2)
    }
}
