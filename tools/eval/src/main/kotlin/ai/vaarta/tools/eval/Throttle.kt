package ai.vaarta.tools.eval

/** Enforces a minimum gap between calls so a provider's RPM ceiling is never exceeded.
 *  minIntervalMs = 60_000 / requestsPerMinute. Clock + sleep are injectable for testing. */
class Throttle(
    private val minIntervalMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private var lastCallAt: Long? = null

    fun acquire() {
        val last = lastCallAt
        if (last != null) {
            val elapsed = now() - last
            val wait = minIntervalMs - elapsed
            if (wait > 0) sleep(wait)
        }
        lastCallAt = now()
    }
}
