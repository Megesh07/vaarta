package ai.vaarta.core.reasoning

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val EN_IN = Locale("en", "IN")

/**
 * One relative-time label for list rows (redesign spec §3A.5/§6.6): short, single-line, en-IN
 * forms — "11:41 am" (same day), "Yesterday", "16 Jul" (same year), "16 Jul 2025" (older/future).
 * Pure JVM so it's unit-testable; the caller passes `now` (no hidden clock).
 */
fun relativeTimeLabel(thenMs: Long, nowMs: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = thenMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        thenMs <= nowMs && sameDay(then, now) ->
            SimpleDateFormat("h:mm a", EN_IN).format(Date(thenMs)).lowercase(EN_IN)
        thenMs <= nowMs && sameDay(then, yesterday) -> "Yesterday"
        thenMs <= nowMs && then.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            SimpleDateFormat("d MMM", EN_IN).format(Date(thenMs))
        else -> SimpleDateFormat("d MMM yyyy", EN_IN).format(Date(thenMs))
    }
}
