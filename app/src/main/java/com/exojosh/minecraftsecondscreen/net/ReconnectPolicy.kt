package com.exojosh.minecraftsecondscreen.net

/**
 * How long to wait before the next connection attempt.
 *
 * **Android- and Compose-free so it can be unit tested**, same line as
 * `HeartLayout`, `InputSlotCodec`, `ChatLineWrapper` and `InventoryLayout`.
 * A backoff bug doesn't crash — it either hammers the socket forever or waits
 * so long the app looks dead — and neither shows up in a screenshot.
 *
 * The old behaviour was a flat 1.5s retry with no backoff. That's fine for the
 * common case (the game isn't running yet, and you want it to notice the moment
 * it is) and wasteful for the case that actually persists: the app left running
 * for hours with no Minecraft, waking the radio-free loopback stack every 1.5
 * seconds all night.
 *
 * So the first few retries stay fast — a player alt-tabbing back into the game
 * should reconnect immediately — and only a *sustained* failure backs off.
 */
object ReconnectPolicy {

    /** Retries at this delay before any backoff starts. Covers a game restart,
     *  which is the case where fast reconnection actually matters. */
    const val FAST_ATTEMPTS = 5

    const val FAST_DELAY_MS = 1_500L

    /** Never wait longer than this, so clearing the problem is noticed within
     *  a reasonable time without the player having to restart the app. */
    const val MAX_DELAY_MS = 30_000L

    /**
     * @param failedAttempts how many consecutive attempts have failed, 0 for
     *                       the first retry after a working connection
     */
    fun delayMs(failedAttempts: Int): Long {
        if (failedAttempts < FAST_ATTEMPTS) return FAST_DELAY_MS

        // Double once per attempt past the fast window. Computed as a shift on
        // a bounded exponent rather than by repeated multiplication, so a long
        // outage can't overflow its way back to a tiny delay.
        val steps = (failedAttempts - FAST_ATTEMPTS + 1).coerceAtMost(16)
        val delay = FAST_DELAY_MS shl steps
        return delay.coerceAtMost(MAX_DELAY_MS)
    }
}
