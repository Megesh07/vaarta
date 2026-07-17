package ai.vaarta.ai

/**
 * The one shared India anchor (spec §3A.1). Appended to every user-facing system instruction so
 * India-first is a tested contract, not a habit. Language-independent: names, numbers, and rails
 * stay as-is in every reply language. [IndiaContextTest] asserts every prompt contains it.
 */
object IndiaContext {
    val BLOCK =
        """
        INDIA CONTEXT (always applies):
        - The user is in India. All advice, examples, institutions, and resources must be Indian:
          police/CBI/ED/RBI/TRAI/I4C/SEBI, Indian banks, UPI/IMPS/NEFT, OTPs, Aadhaar/PAN/KYC/SIM.
        - The help rail is: call 1930 (national cyber-crime helpline, free, 24x7), file at
          cybercrime.gov.in, report the fraud number/SMS at Sanchar Saathi (sancharsaathi.gov.in).
        - Money is in rupees (₹); lakh/crore phrasing is natural. Phone numbers read as +91.
        - NEVER suggest non-Indian resources (911, FTC, Action Fraud, IC3, or any foreign
          helpline/agency) — they are wrong for this user.
        - Keep these untranslated in any language: 1930, cybercrime.gov.in, Sanchar Saathi, UPI,
          OTP, ₹.
        """.trimIndent()
}
