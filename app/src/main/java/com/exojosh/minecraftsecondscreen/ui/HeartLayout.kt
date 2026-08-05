package com.exojosh.minecraftsecondscreen.ui

import kotlin.math.ceil

/**
 * Which sprite a heart slot draws on top of its container.
 *
 * Every slot always gets a container behind it; this is what goes over it.
 */
enum class HeartFill { EMPTY, HALF, FULL }

/**
 * One heart slot. [absorption] picks the golden sprite over the red one --
 * vanilla treats absorption as extra slots appended after the normal ones,
 * not as a separate bar.
 */
data class HeartSlot(val absorption: Boolean, val fill: HeartFill)

/**
 * Vanilla's health-bar slot layout, lifted from `InGameHud.renderHealthBar`
 * (1.21.11, verified against the decompiled source rather than recalled).
 *
 * Vanilla walks a single continuous run of `ceil(maxHealth/2)` normal slots
 * followed by `ceil(absorption/2)` absorption slots, and wraps it into stacked
 * rows every 10 with `l / 10`. Rows stack *upward*, so row 0 is the bottom one
 * -- the one that stays put as absorption pushes extra hearts above it.
 *
 * This is kept free of Compose and Android types on purpose: it's the part
 * that had the bug, and it's the part a JVM unit test can actually pin down.
 * The previous version clamped the whole run to 10 slots, which meant a
 * normal-max-health player (already 10 slots) could never show an absorption
 * heart at all -- the feature was unreachable code, and nothing caught it
 * because nothing tested it.
 */
object HeartLayout {

    /** Vanilla wraps every 10 slots. */
    const val SLOTS_PER_ROW = 10

    /**
     * Vanilla has no row limit here (it compresses the vertical pitch instead,
     * via `max(10 - (rows - 2), 3)`, so rows overlap). At this HUD's icon size
     * overlapping rows would be unreadable, so rows are drawn at full height
     * and capped instead. Three rows covers 60 points of health-plus-absorption,
     * far past anything reachable without commands.
     */
    const val MAX_ROWS = 3

    /**
     * Slot rows, **bottom row first** -- index 0 is vanilla's `m == 0`.
     *
     * @param health current health in points (2 per heart)
     * @param maxHealth maximum health in points
     * @param absorption absorption ("golden heart") points on top of health
     */
    fun compute(health: Float, maxHealth: Float, absorption: Float): List<List<HeartSlot>> {
        // Vanilla rounds each of these up to whole points before laying out.
        val healthPoints = ceil(health.coerceAtLeast(0f)).toInt()
        val absorptionPoints = ceil(absorption.coerceAtLeast(0f)).toInt()

        // `i` and `j` in renderHealthBar. No floor at 20: an attribute-reduced
        // max health draws fewer containers, as vanilla does.
        val healthSlots = ceil(maxHealth.coerceAtLeast(0f) / 2f).toInt()
        val absorptionSlots = ceil(absorptionPoints / 2f).toInt()

        val totalSlots = (healthSlots + absorptionSlots).coerceAtMost(SLOTS_PER_ROW * MAX_ROWS)
        if (totalSlots <= 0) return emptyList()

        // `k` in renderHealthBar -- where the absorption run starts, in points.
        val absorptionStartPoints = healthSlots * 2

        val rows = ArrayList<MutableList<HeartSlot>>()
        for (slot in 0 until totalSlots) {
            val row = slot / SLOTS_PER_ROW
            while (rows.size <= row) rows.add(mutableListOf())

            val points = slot * 2
            val heartSlot = if (slot >= healthSlots) {
                // Absorption slot. `covered` is how much absorption the slots
                // before this one already accounted for (vanilla's `r`).
                val covered = points - absorptionStartPoints
                when {
                    covered >= absorptionPoints -> HeartSlot(absorption = true, fill = HeartFill.EMPTY)
                    covered + 1 == absorptionPoints -> HeartSlot(absorption = true, fill = HeartFill.HALF)
                    else -> HeartSlot(absorption = true, fill = HeartFill.FULL)
                }
            } else {
                when {
                    points >= healthPoints -> HeartSlot(absorption = false, fill = HeartFill.EMPTY)
                    points + 1 == healthPoints -> HeartSlot(absorption = false, fill = HeartFill.HALF)
                    else -> HeartSlot(absorption = false, fill = HeartFill.FULL)
                }
            }
            rows[row].add(heartSlot)
        }
        return rows
    }
}

/**
 * Vanilla's breathing-bubble counts, from `InGameHud.renderAirBubbles` and its
 * `getAirBubbles(air, maxAir, delay) = ceil((air + delay) * 10 / maxAir)`.
 *
 * [full] lags [throughBursting] by two ticks of air, which is what makes the
 * last bubble visibly burst before it vanishes.
 */
data class BubbleCounts(val full: Int, val throughBursting: Int) {
    val hasBursting: Boolean get() = throughBursting != full
}

object BubbleLayout {

    const val BUBBLE_COUNT = 10

    fun compute(air: Int, maxAir: Int): BubbleCounts {
        if (maxAir <= 0) return BubbleCounts(0, 0)
        val clamped = air.coerceIn(0, maxAir)
        val full = ceil((clamped - 2).toFloat() * BUBBLE_COUNT / maxAir).toInt().coerceAtLeast(0)
        val throughBursting = ceil(clamped.toFloat() * BUBBLE_COUNT / maxAir).toInt().coerceAtLeast(0)
        return BubbleCounts(full = full, throughBursting = throughBursting)
    }
}
