package com.exojosh.minecraftsecondscreen.ui

import com.exojosh.minecraftsecondscreen.net.HudIcon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Heart sprites are picked on two independent axes — the status set from the
 * player, the hardcore variant from the world — and getting the combination
 * wrong draws a plausible-looking heart of the wrong kind, which is the sort
 * of thing nobody notices until it matters.
 */
class HeartSpritesTest {

    @Test
    fun `a plain survival player gets the ordinary red hearts`() {
        val s = heartSprites("NORMAL", hardcore = false)
        assertEquals(HudIcon.HEART_FULL, s.full)
        assertEquals(HudIcon.HEART_HALF, s.half)
        assertEquals(HudIcon.HEART_CONTAINER, s.container)
    }

    @Test
    fun `each status effect selects its own set`() {
        assertEquals(HudIcon.HEART_POISONED_FULL, heartSprites("POISONED", false).full)
        assertEquals(HudIcon.HEART_WITHERED_FULL, heartSprites("WITHERED", false).full)
        assertEquals(HudIcon.HEART_FROZEN_FULL, heartSprites("FROZEN", false).full)
    }

    /** The axes are independent: hardcore has a variant of *every* status set,
     *  not just of the normal one. */
    @Test
    fun `hardcore has a variant of every status set`() {
        assertEquals(HudIcon.HEART_HARDCORE_FULL, heartSprites("NORMAL", true).full)
        assertEquals(HudIcon.HEART_POISONED_HARDCORE_FULL, heartSprites("POISONED", true).full)
        assertEquals(HudIcon.HEART_WITHERED_HARDCORE_FULL, heartSprites("WITHERED", true).full)
        assertEquals(HudIcon.HEART_FROZEN_HARDCORE_FULL, heartSprites("FROZEN", true).full)
    }

    @Test
    fun `the container follows the hardcore axis`() {
        assertEquals(HudIcon.HEART_CONTAINER, heartSprites("POISONED", false).container)
        assertEquals(HudIcon.HEART_CONTAINER_HARDCORE, heartSprites("POISONED", true).container)
    }

    /** Absorption hearts stay golden through poison and freezing — they follow
     *  the hardcore axis only. */
    @Test
    fun `absorption hearts ignore the status axis`() {
        for (type in listOf("NORMAL", "POISONED", "FROZEN", "WITHERED")) {
            assertEquals(HudIcon.HEART_ABSORBING_FULL, heartSprites(type, false).absorbFull)
            assertEquals(HudIcon.HEART_ABSORBING_HARDCORE_FULL, heartSprites(type, true).absorbFull)
        }
    }

    /** A heart type from a newer game version must degrade to a plain red
     *  heart, not to an empty row. */
    @Test
    fun `an unknown heart type falls back to normal`() {
        assertEquals(HudIcon.HEART_FULL, heartSprites("SOME_FUTURE_TYPE", false).full)
        assertEquals(HudIcon.HEART_HARDCORE_FULL, heartSprites("SOME_FUTURE_TYPE", true).full)
    }
}
