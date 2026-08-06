package com.exojosh.minecraftsecondscreen.settings

/**
 * How the input grid's slots are written to and read back from a single
 * preference string.
 *
 * Free of Android and Compose types on purpose, so it can be unit tested — the
 * same split [HeartLayout] uses for the heart-row maths, and for the same
 * reason. This is persistence code: a bug here doesn't crash, it quietly gives
 * the player back a different set of buttons than they configured, or throws on
 * an index that used to be valid. That is exactly the class of bug nobody
 * notices until it has already happened to them.
 *
 * The format is deliberately dull: the ids joined by commas, an empty field for
 * an empty slot. Binding ids are translation keys (`key.inventory`), which
 * never contain a comma, so there is nothing to escape.
 *
 * **A missing string and an empty one mean different things.** No stored value
 * at all is "never configured" and yields the defaults; a stored string of
 * commas is "configured, and every slot cleared", which is a state a player can
 * reach and must survive a restart.
 */
object InputSlotCodec {

    private const val SEPARATOR = ","

    fun encode(slots: List<String?>): String = slots.joinToString(SEPARATOR) { it.orEmpty() }

    /**
     * Reads [stored] back, or falls back to [defaults] when nothing was saved.
     *
     * Always returns exactly [slotCount] entries, padding with nulls or
     * truncating as needed. Neither case happens today, but both would if
     * [INPUT_SLOT_COUNT] ever changed, and a stored layout from before the
     * change would otherwise index out of bounds against a UI built for the
     * new size.
     */
    fun decode(
        stored: String?,
        defaults: List<String?>,
        slotCount: Int = INPUT_SLOT_COUNT
    ): List<String?> {
        val parsed = stored?.split(SEPARATOR)?.map { it.takeIf(String::isNotEmpty) } ?: defaults
        return List(slotCount) { parsed.getOrNull(it) }
    }
}
