package com.exojosh.minecraftsecondscreen.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "chat_settings"
private const val KEY_SYSTEM_KEYBOARD = "system_keyboard"

/**
 * How chat is typed, persisted across restarts.
 *
 * Same shape and same reasoning as [HudSettings]: one boolean read at startup
 * and written on a tap doesn't justify a DataStore dependency, and holding the
 * live copy in Compose state means the Chat tab swaps keyboards the instant the
 * switch is flipped.
 *
 * **The default is the app's own keyboard, and that isn't timidity.** Android
 * only shows an IME on a secondary display when that display reports system
 * decoration support, which presentation-category displays generally don't;
 * where it does appear, it appears on the *primary* screen — which on this
 * device is the one running Minecraft, so the keyboard covers the game while
 * the field it types into sits on the other panel. The app-drawn keyboard was
 * built for that reason and stays the default. This switch exists because the
 * behaviour is a property of the *device*, not something the app can determine
 * in advance: where the system keyboard does come up on the bottom screen, it
 * is much nicer to type on than a grid of drawn keys, and only the person
 * holding the Thor can see which happens.
 */
class ChatSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Set through [useTheSystemKeyboard], which persists as well as assigns —
     *  the two must not drift, so the setter is private. */
    var useSystemKeyboard by mutableStateOf(prefs.getBoolean(KEY_SYSTEM_KEYBOARD, false))
        private set

    fun useTheSystemKeyboard(enabled: Boolean) {
        useSystemKeyboard = enabled
        prefs.edit().putBoolean(KEY_SYSTEM_KEYBOARD, enabled).apply()
    }

    fun toggleSystemKeyboard() = useTheSystemKeyboard(!useSystemKeyboard)
}

/**
 * The single [ChatSettings] for this window.
 *
 * Keyed on the *application* context for the same reason [rememberHudSettings]
 * is: a `Presentation` runs against its own display context, but the
 * preferences it reads are app-scoped.
 */
@Composable
fun rememberChatSettings(): ChatSettings {
    val context = LocalContext.current
    return remember(context.applicationContext) { ChatSettings(context) }
}
