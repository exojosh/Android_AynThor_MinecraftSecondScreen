package com.exojosh.minecraftsecondscreen.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backoff bug never crashes. It either hammers the socket forever or waits
 * so long the app looks dead, and neither shows up in a screenshot — which is
 * why this policy is a plain function with tests rather than arithmetic buried
 * in the reconnect loop.
 */
class ReconnectPolicyTest {

    /** The case that matters most: a game restart should be picked up at once,
     *  not after a backoff earned by the minutes of waiting before it. */
    @Test
    fun `the first few retries stay fast`() {
        for (attempt in 0 until ReconnectPolicy.FAST_ATTEMPTS) {
            assertEquals(ReconnectPolicy.FAST_DELAY_MS, ReconnectPolicy.delayMs(attempt))
        }
    }

    @Test
    fun `it backs off after a sustained failure`() {
        val first = ReconnectPolicy.delayMs(ReconnectPolicy.FAST_ATTEMPTS)
        val second = ReconnectPolicy.delayMs(ReconnectPolicy.FAST_ATTEMPTS + 1)
        assertTrue("should grow past the fast window", first > ReconnectPolicy.FAST_DELAY_MS)
        assertTrue("should keep growing", second > first)
    }

    @Test
    fun `it never waits longer than the cap`() {
        for (attempt in 0..1000) {
            assertTrue(ReconnectPolicy.delayMs(attempt) <= ReconnectPolicy.MAX_DELAY_MS)
        }
    }

    /**
     * The failure mode a naive doubling has: after enough attempts the shift
     * overflows and the delay wraps back round to something tiny, turning a
     * long outage into a hot loop. This is the specific thing the exponent
     * clamp exists for.
     */
    @Test
    fun `a very long outage does not wrap back to a short delay`() {
        for (attempt in listOf(50, 100, 1_000, Int.MAX_VALUE - 1, Int.MAX_VALUE)) {
            val delay = ReconnectPolicy.delayMs(attempt)
            assertEquals(
                "attempt $attempt should sit at the cap",
                ReconnectPolicy.MAX_DELAY_MS,
                delay
            )
        }
    }

    @Test
    fun `delays never decrease as failures accumulate`() {
        var previous = 0L
        for (attempt in 0..200) {
            val delay = ReconnectPolicy.delayMs(attempt)
            assertTrue("attempt $attempt went backwards: $previous -> $delay", delay >= previous)
            previous = delay
        }
    }
}
