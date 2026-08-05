package com.exojosh.minecraftsecondscreen.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "hud_layout"

/**
 * A HUD element the player can move off the second screen.
 *
 * Switching one off doesn't discard it -- the mod puts it back on the game's
 * own HUD on the top screen, so between the two displays the full HUD is
 * always drawn exactly once. That's the whole point of the toggle: it chooses
 * *which screen* an element lives on, not whether you get to see it.
 *
 * The granularity is per *element*, not per row, even though several of these
 * share a row on screen -- armor sits beside the bubbles, health beside
 * hunger. Tying the setting to the row would mean you couldn't drop the armor
 * sockets without also losing your breath meter, which is the pairing people
 * are most likely to want to break: armor is permanently on screen and mostly
 * static, bubbles only appear when they matter.
 *
 * [key] is both the persisted preference name **and** the wire name the mod
 * matches in `GameHudVisibility.Element` — renaming one silently resets that
 * toggle *and* stops the game ever drawing it. Rename the constant, not the
 * key.
 */
enum class HudElement(val key: String, val label: String, val description: String) {
    ARMOR("armor", "Armor", "The armor sockets, top left"),
    BUBBLES("bubbles", "Breath", "Bubbles, shown only underwater"),
    HEARTS("hearts", "Health", "Hearts, including golden absorption hearts"),
    HUNGER("hunger", "Hunger", "The drumstick row"),
    XP_BAR("xp_bar", "XP bar", "The bar and the level number above it"),
    HOTBAR("hotbar", "Hotbar", "The 9 slots. Turning this off also hides the off-hand."),

    // The one element with no vanilla counterpart: the game draws the off-hand
    // box as part of the hotbar, so there's nothing separate to switch back on
    // up top. The mod ignores this key by design.
    OFFHAND(
        "offhand",
        "Off-hand slot",
        "The tappable swap-hands box. Stays off the top screen either way."
    )
}

/**
 * Which HUD elements are on screen, persisted across restarts.
 *
 * Backed by [android.content.SharedPreferences] rather than DataStore: this is
 * seven booleans read once at startup and written on a tap, so DataStore's
 * coroutine/Flow machinery would be a new dependency bought for nothing.
 *
 * The in-memory copy is a [mutableStateMapOf] so Compose observes reads and
 * recomposes the HUD the instant a switch is flipped -- same trick as
 * `HudRepository.assetCache`. Everything defaults to visible, so a fresh
 * install looks exactly like it did before this setting existed.
 */
class HudSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val visibility = mutableStateMapOf<HudElement, Boolean>().apply {
        HudElement.entries.forEach { element ->
            put(element, prefs.getBoolean(element.key, true))
        }
    }

    fun isVisible(element: HudElement): Boolean = visibility[element] ?: true

    fun setVisible(element: HudElement, visible: Boolean) {
        visibility[element] = visible
        prefs.edit().putBoolean(element.key, visible).apply()
    }

    fun toggle(element: HudElement) = setVisible(element, !isVisible(element))

    /** Back to everything on -- the state a fresh install starts in. */
    fun resetToDefaults() {
        HudElement.entries.forEach { setVisible(it, true) }
    }

    /** True when nothing has been turned off, i.e. there's nothing to reset. */
    val isDefault: Boolean
        get() = HudElement.entries.all { isVisible(it) }
}

/**
 * The single [HudSettings] for this window.
 *
 * Keyed on the *application* context: a `Presentation` runs against its own
 * display context, but the preferences it reads are app-scoped, so keying on
 * the local context would rebuild the store for no reason if the Presentation
 * is ever recreated.
 */
@Composable
fun rememberHudSettings(): HudSettings {
    val context = LocalContext.current
    return remember(context.applicationContext) { HudSettings(context) }
}
