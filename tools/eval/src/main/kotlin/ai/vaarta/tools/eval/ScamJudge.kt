package ai.vaarta.tools.eval

interface ScamJudge {
    val id: String
    /** Bumped when the judge's prompt changes, so the cache invalidates only affected entries. */
    val promptVersion: String
    fun assess(case: TranscriptCase): Verdict
}
