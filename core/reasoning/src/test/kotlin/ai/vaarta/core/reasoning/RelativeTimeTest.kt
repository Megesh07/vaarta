package ai.vaarta.core.reasoning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Locale

class RelativeTimeTest {

    private fun ms(y: Int, mo: Int, d: Int, h: Int, min: Int): Long =
        Calendar.getInstance(Locale.ENGLISH).apply {
            clear(); set(y, mo - 1, d, h, min)
        }.timeInMillis

    private val now = ms(2026, 7, 17, 14, 0) // 17 Jul 2026, 2:00 pm

    @Test
    fun `same day shows the time`() =
        assertEquals("11:41 am", relativeTimeLabel(ms(2026, 7, 17, 11, 41), now))

    @Test
    fun `previous calendar day shows Yesterday`() =
        assertEquals("Yesterday", relativeTimeLabel(ms(2026, 7, 16, 23, 30), now))

    @Test
    fun `same year shows day and month`() =
        assertEquals("2 Mar", relativeTimeLabel(ms(2026, 3, 2, 9, 0), now))

    @Test
    fun `older shows day month year`() =
        assertEquals("16 Jul 2025", relativeTimeLabel(ms(2025, 7, 16, 9, 0), now))

    @Test
    fun `future timestamps fall back to full date`() =
        assertEquals("18 Jul 2026", relativeTimeLabel(ms(2026, 7, 18, 9, 0), now))
}
