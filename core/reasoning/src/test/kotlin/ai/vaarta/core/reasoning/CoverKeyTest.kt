package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spec §5.1: the scam-type → cover-illustration mapping. Priority order is part of the contract
 * (digital-arrest keywords outrank parcel, so "police courier scam" gets the police cover).
 */
class CoverKeyTest {

    @Test
    fun `digital arrest keywords win`() {
        for (s in listOf("Digital Arrest", "fake police call", "CBI impersonation", "ED court threat")) {
            assertEquals("digital_arrest", coverKeyForScamType(s), s)
        }
    }

    @Test
    fun `police plus courier is digital arrest, not parcel`() =
        assertEquals("digital_arrest", coverKeyForScamType("Police courier parcel scam"))

    @Test
    fun `upi and qr map to upi`() {
        for (s in listOf("UPI fraud", "QR code scam", "PhonePe payment request")) {
            assertEquals("upi", coverKeyForScamType(s), s)
        }
    }

    @Test
    fun `parcel and customs map to parcel`() {
        for (s in listOf("Parcel scam", "FedEx courier", "customs seizure")) {
            assertEquals("parcel", coverKeyForScamType(s), s)
        }
    }

    @Test
    fun `bank identity keywords map to kyc_bank`() {
        for (s in listOf("KYC expiry", "bank account fraud", "Aadhaar misuse", "SIM swap")) {
            assertEquals("kyc_bank", coverKeyForScamType(s), s)
        }
    }

    @Test
    fun `investment job loan lottery romance utility`() {
        assertEquals("investment", coverKeyForScamType("stock trading app fraud"))
        assertEquals("job", coverKeyForScamType("work-from-home task scam"))
        assertEquals("loan_app", coverKeyForScamType("instant loan app harassment"))
        assertEquals("lottery", coverKeyForScamType("KBC lucky draw prize"))
        assertEquals("romance", coverKeyForScamType("matrimonial dating fraud"))
        assertEquals("utility", coverKeyForScamType("electricity bill disconnection"))
    }

    @Test
    fun `case-insensitive`() = assertEquals("upi", coverKeyForScamType("uPi FRAUD"))

    @Test
    fun `short agency tokens only match whole words`() {
        // Regression: "Task-Based" must not trigger the "ed" (Enforcement Directorate) keyword.
        assertEquals("job", coverKeyForScamType("Job Fraud Task-Based Job Scams"))
        assertEquals("digital_arrest", coverKeyForScamType("ED officer video call"))
    }

    @Test
    fun `category plus title combined input maps on either part`() {
        // Callers pass "category title" so a vague tag still resolves via the headline.
        assertEquals("kyc_bank", coverKeyForScamType("Identity Theft KYC Update Fraud"))
        assertEquals("investment", coverKeyForScamType("Financial Fraud Investment & Trading Scams"))
    }

    @Test
    fun `blank null unknown fall back to generic`() {
        assertEquals("generic", coverKeyForScamType(null))
        assertEquals("generic", coverKeyForScamType("  "))
        assertEquals("generic", coverKeyForScamType("something new"))
    }
}
