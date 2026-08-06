package com.exojosh.minecraftsecondscreen.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the persisted-layout format.
 *
 * The behaviour these lock down is all of the "quietly wrong" kind — a player
 * gets back a grid that isn't the one they configured, or the app throws on a
 * slot index that used to exist. None of it is visible from reading the call
 * site, which is why it's tested rather than trusted.
 */
class InputSlotCodecTest {

    private val defaults = listOf("a", "b", null, "d", null, null, null, null, null)

    @Test
    fun `no stored value falls back to the defaults`() {
        assertEquals(defaults, InputSlotCodec.decode(null, defaults))
    }

    /**
     * The distinction the single-string format exists to preserve: a player who
     * clears every button has *configured* an empty grid, and must not be
     * handed the defaults back on the next launch.
     */
    @Test
    fun `an all-empty stored value is kept, not treated as unset`() {
        val allEmpty = List<String?>(INPUT_SLOT_COUNT) { null }
        assertEquals(allEmpty, InputSlotCodec.decode(InputSlotCodec.encode(allEmpty), defaults))
    }

    @Test
    fun `round trips a mixed layout`() {
        val slots = listOf("key.inventory", null, "key.drop", null, null, "key.jump", null, null, null)
        assertEquals(slots, InputSlotCodec.decode(InputSlotCodec.encode(slots), defaults))
    }

    @Test
    fun `a short stored value is padded with empty slots`() {
        assertEquals(
            listOf("key.jump", "key.sneak") + List<String?>(INPUT_SLOT_COUNT - 2) { null },
            InputSlotCodec.decode("key.jump,key.sneak", defaults)
        )
    }

    @Test
    fun `a long stored value is truncated to the slot count`() {
        val stored = (1..INPUT_SLOT_COUNT + 4).joinToString(",") { "key.$it" }
        val decoded = InputSlotCodec.decode(stored, defaults)

        assertEquals(INPUT_SLOT_COUNT, decoded.size)
        assertEquals("key.1", decoded.first())
        assertEquals("key.$INPUT_SLOT_COUNT", decoded.last())
    }

    @Test
    fun `decode always returns exactly the slot count`() {
        listOf(null, "", ",", "key.a", "key.a,key.b,key.c").forEach { stored ->
            assertEquals(
                "wrong size for stored=$stored",
                INPUT_SLOT_COUNT,
                InputSlotCodec.decode(stored, defaults).size
            )
        }
    }

    /** The grid is 3x3; the two constants have to agree or the last row is
     *  either short or absent, with nothing to say so. */
    @Test
    fun `the slot count fills whole rows of the grid`() {
        assertEquals(0, INPUT_SLOT_COUNT % INPUT_COLUMNS)
    }

    /** The defaults are what a fresh install gets; a list of the wrong length
     *  would silently drop buttons off the end of the grid. */
    @Test
    fun `the shipped defaults fill the grid exactly`() {
        assertEquals(INPUT_SLOT_COUNT, DEFAULT_INPUT_SLOTS.size)
    }
}
