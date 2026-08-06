package com.exojosh.minecraftsecondscreen.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "input_layout"

/** One pref key holding all nine slots, so "never configured" is a single
 *  missing key rather than nine ambiguous empties. */
private const val SLOTS_KEY = "slots"

/** The Input tab's grid. 3 columns x 3 rows, fixed. */
const val INPUT_COLUMNS = 3
const val INPUT_SLOT_COUNT = 9

/**
 * What the grid starts as: the seven actions it used to have hardcoded, in the
 * same order, with the last two slots empty.
 *
 * These are vanilla binding ids, which is the whole point of the change — the
 * grid used to send opaque codes that a table in the mod had to recognise, and
 * that table drifted. An id the game doesn't have (a version rename, a removed
 * mod) degrades to a button showing the raw id that logs a miss when pressed,
 * rather than to a button that silently does nothing.
 */
val DEFAULT_INPUT_SLOTS = listOf(
    "key.inventory", "key.drop", "key.swapOffhand",
    "key.use", "key.attack", "key.jump",
    "key.sneak", null, null
)

/**
 * Which key binding each button on the Input tab presses, persisted across
 * restarts.
 *
 * Slots hold a **binding id**, not a label and not a key name. The id is what
 * the mod resolves through `KeyBinding.byId`, so this survives the player
 * rebinding that action to a different key in game — the button follows the
 * action, which is what someone configuring "put Drop on the second screen"
 * actually meant.
 *
 * `null` is an empty slot, and it's a real state rather than a placeholder: the
 * grid is a fixed 3x3 so that buttons don't move around under the player's
 * thumb when one is cleared.
 *
 * Backed by [android.content.SharedPreferences] and held in a
 * [mutableStateListOf] for the same reasons as [HudSettings] — nine strings
 * don't justify DataStore, and Compose observing the list means the grid
 * updates the moment a slot is reassigned.
 */
class InputSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val slots = mutableStateListOf<String?>().apply {
        addAll(InputSlotCodec.decode(prefs.getString(SLOTS_KEY, null), DEFAULT_INPUT_SLOTS))
    }

    /** The binding id in [index], or null if that slot is empty. */
    fun bindingAt(index: Int): String? = slots.getOrNull(index)

    fun setBindingAt(index: Int, bindingId: String?) {
        if (index !in 0 until INPUT_SLOT_COUNT) return
        slots[index] = bindingId
        persist()
    }

    fun clearSlot(index: Int) = setBindingAt(index, null)

    /** Back to the original seven actions -- the state a fresh install has. */
    fun resetToDefaults() {
        slots.clear()
        slots.addAll(InputSlotCodec.decode(null, DEFAULT_INPUT_SLOTS))
        persist()
    }

    val isDefault: Boolean
        get() = slots.toList() == InputSlotCodec.decode(null, DEFAULT_INPUT_SLOTS)

    private fun persist() {
        prefs.edit().putString(SLOTS_KEY, InputSlotCodec.encode(slots)).apply()
    }
}

/** The single [InputSettings] for this window, keyed on the application
 *  context for the same reason [rememberHudSettings] is. */
@Composable
fun rememberInputSettings(): InputSettings {
    val context = LocalContext.current
    return remember(context.applicationContext) { InputSettings(context) }
}
