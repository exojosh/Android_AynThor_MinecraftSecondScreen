package com.exojosh.minecraftsecondscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down `HeartLayout` against vanilla's `InGameHud.renderHealthBar`.
 *
 * The case that matters most is [fullHealthWithAbsorptionSpillsToSecondRow]:
 * the shipped implementation clamped the slot run to 10, so a normal-max-health
 * player could never show a golden heart. That was unreachable code, not an
 * edge case, and it survived review because nothing here existed.
 */
class HeartLayoutTest {

    private fun flatten(rows: List<List<HeartSlot>>) = rows.flatten()

    @Test
    fun fullHealthNoAbsorptionIsOneFullRow() {
        val rows = HeartLayout.compute(health = 20f, maxHealth = 20f, absorption = 0f)

        assertEquals(1, rows.size)
        assertEquals(10, rows[0].size)
        assertTrue(rows[0].all { it.fill == HeartFill.FULL && !it.absorption })
    }

    @Test
    fun halfHeartIsTheLastFilledSlot() {
        val rows = HeartLayout.compute(health = 19f, maxHealth = 20f, absorption = 0f)
        val slots = flatten(rows)

        assertEquals(10, slots.size)
        assertTrue(slots.take(9).all { it.fill == HeartFill.FULL })
        assertEquals(HeartFill.HALF, slots[9].fill)
    }

    @Test
    fun emptySlotsAppearPastCurrentHealth() {
        val rows = HeartLayout.compute(health = 6f, maxHealth = 20f, absorption = 0f)
        val slots = flatten(rows)

        assertEquals(10, slots.size)
        assertTrue(slots.take(3).all { it.fill == HeartFill.FULL })
        assertTrue(slots.drop(3).all { it.fill == HeartFill.EMPTY })
    }

    /** The regression this whole branch exists for. */
    @Test
    fun fullHealthWithAbsorptionSpillsToSecondRow() {
        // A golden apple gives 4 absorption -> 2 extra slots.
        val rows = HeartLayout.compute(health = 20f, maxHealth = 20f, absorption = 4f)

        assertEquals("absorption must wrap to a second row", 2, rows.size)
        assertEquals(10, rows[0].size)
        assertEquals(2, rows[1].size)

        assertTrue("bottom row is normal hearts", rows[0].none { it.absorption })
        assertTrue("top row is absorption hearts", rows[1].all { it.absorption })
        assertTrue("both absorption hearts are full", rows[1].all { it.fill == HeartFill.FULL })
    }

    @Test
    fun oddAbsorptionEndsInAHalfGoldenHeart() {
        val rows = HeartLayout.compute(health = 20f, maxHealth = 20f, absorption = 3f)
        val absorptionSlots = flatten(rows).filter { it.absorption }

        assertEquals(2, absorptionSlots.size)
        assertEquals(HeartFill.FULL, absorptionSlots[0].fill)
        assertEquals(HeartFill.HALF, absorptionSlots[1].fill)
    }

    @Test
    fun fractionalAbsorptionRoundsUpLikeVanilla() {
        // Vanilla does ceil(absorptionAmount) before halving, so 0.5 -> 1 point
        // -> one half golden heart.
        val absorptionSlots = flatten(HeartLayout.compute(20f, 20f, 0.5f)).filter { it.absorption }

        assertEquals(1, absorptionSlots.size)
        assertEquals(HeartFill.HALF, absorptionSlots[0].fill)
    }

    @Test
    fun reducedMaxHealthDrawsFewerContainers() {
        // No floor at 20: vanilla uses ceil(maxHealth / 2) directly.
        val rows = HeartLayout.compute(health = 6f, maxHealth = 12f, absorption = 0f)

        assertEquals(1, rows.size)
        assertEquals(6, rows[0].size)
    }

    @Test
    fun raisedMaxHealthWrapsWithoutAbsorption() {
        val rows = HeartLayout.compute(health = 30f, maxHealth = 30f, absorption = 0f)

        assertEquals(2, rows.size)
        assertEquals(10, rows[0].size)
        assertEquals(5, rows[1].size)
        assertTrue(flatten(rows).none { it.absorption })
    }

    @Test
    fun rowCountIsCapped() {
        val rows = HeartLayout.compute(health = 20f, maxHealth = 20f, absorption = 500f)

        assertEquals(HeartLayout.MAX_ROWS, rows.size)
        assertEquals(HeartLayout.SLOTS_PER_ROW * HeartLayout.MAX_ROWS, flatten(rows).size)
    }

    @Test
    fun deadPlayerShowsEmptyContainers() {
        val rows = HeartLayout.compute(health = 0f, maxHealth = 20f, absorption = 0f)

        assertEquals(1, rows.size)
        assertTrue(rows[0].all { it.fill == HeartFill.EMPTY })
    }

    @Test
    fun noMaxHealthProducesNoRows() {
        assertTrue(HeartLayout.compute(0f, 0f, 0f).isEmpty())
    }
}

class BubbleLayoutTest {

    @Test
    fun fullAirIsTenBubbles() {
        val counts = BubbleLayout.compute(air = 300, maxAir = 300)
        assertEquals(10, counts.throughBursting)
    }

    @Test
    fun emptyAirIsNoBubbles() {
        val counts = BubbleLayout.compute(air = 0, maxAir = 300)
        assertEquals(0, counts.full)
        assertEquals(0, counts.throughBursting)
    }

    @Test
    fun halfAirIsHalfTheBubbles() {
        val counts = BubbleLayout.compute(air = 150, maxAir = 300)
        assertEquals(5, counts.throughBursting)
    }

    @Test
    fun noBurstingBubbleAtFullAir() {
        // full = ceil(298 * 10 / 300) = 10 and throughBursting = 10, so they
        // agree and nothing is mid-burst.
        assertFalse(BubbleLayout.compute(air = 300, maxAir = 300).hasBursting)
    }

    @Test
    fun burstingBubbleAppearsAtABoundary() {
        // full lags throughBursting by two ticks of air, so just past a
        // boundary the two disagree and vanilla draws the bursting sprite.
        // air=271: full = ceil(269*10/300) = 9, throughBursting = ceil(271*10/300) = 10.
        val counts = BubbleLayout.compute(air = 271, maxAir = 300)

        assertEquals(9, counts.full)
        assertEquals(10, counts.throughBursting)
        assertTrue(counts.hasBursting)
    }

    @Test
    fun respirationRaisesMaxAirWithoutChangingBubbleCount() {
        // Respiration III pushes maxAir to 600; the row is still 10 bubbles.
        val counts = BubbleLayout.compute(air = 600, maxAir = 600)
        assertEquals(10, counts.throughBursting)
    }

    @Test
    fun zeroMaxAirIsHandled() {
        val counts = BubbleLayout.compute(air = 0, maxAir = 0)
        assertEquals(0, counts.full)
        assertEquals(0, counts.throughBursting)
    }
}
