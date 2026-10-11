package ai.vaarta.tools.eval

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ThrottleTest {
    @Test
    fun `first acquire does not sleep, second sleeps the remaining interval`() {
        var clock = 1000L
        val sleeps = mutableListOf<Long>()
        val throttle = Throttle(
            minIntervalMs = 5000,
            now = { clock },
            sleep = { ms -> sleeps.add(ms); clock += ms },
        )
        throttle.acquire()                 // first call: no wait
        clock += 1000                      // 1s later
        throttle.acquire()                 // needs 4s more to reach the 5s interval
        assertEquals(listOf(4000L), sleeps)
    }
}
