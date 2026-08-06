package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An on-screen keyboard drawn by this app, rather than Android's IME.
 *
 * **This is deliberate, not a reinvention.** The whole UI lives inside a
 * `Presentation` on a secondary display, and Android only shows an IME on a
 * secondary display when that display reports system decoration support —
 * which presentation-category displays generally don't. Even where it does
 * come up, it comes up on the *primary* screen, which on this device is the one
 * running Minecraft: the keyboard would cover the game while the text box it
 * was typing into sat on the other panel.
 *
 * Drawing our own keeps every pixel of chat on the bottom screen, which is the
 * point of the bottom screen. It also means the layout can be the one a game
 * actually needs — `/`, `~`, `^`, `@`, `:` and `{}` are one tap away, because
 * commands and coordinates are most of what gets typed here.
 *
 * The symbol layer is a pragmatic subset, not the full ASCII set: `# $ % & * +
 * < > \ |` have no key. They're the ones that appear in neither a command nor
 * ordinary chat, and a fifth row costs more panel height than they're worth.
 */

/** Keys that do something other than insert their own label. */
private enum class KeyAction { SHIFT, BACKSPACE, SEND, LAYER }

private sealed interface Key {
    val weight: Float

    /** Inserts [lower], or [upper] while shift is held. */
    data class Glyph(
        val lower: String,
        val upper: String = lower.uppercase(),
        override val weight: Float = 1f
    ) : Key

    data class Action(
        val label: String,
        val action: KeyAction,
        override val weight: Float = 1.5f
    ) : Key
}

private fun row(vararg glyphs: String): List<Key> = glyphs.map { Key.Glyph(it) }

private val SHIFT_KEY = Key.Action("⇧", KeyAction.SHIFT)
private val BACKSPACE_KEY = Key.Action("⌫", KeyAction.BACKSPACE)
private val SEND_KEY = Key.Action("Send", KeyAction.SEND, weight = 2.5f)
private val SPACE_KEY = Key.Glyph(" ", " ", weight = 4f)

private val LETTER_ROWS: List<List<Key>> = listOf(
    row("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    row("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf(SHIFT_KEY) + row("z", "x", "c", "v", "b", "n", "m") + listOf(BACKSPACE_KEY),
    listOf(
        Key.Action("?123", KeyAction.LAYER),
        // A leading slash is what turns a message into a command, so it earns
        // a key on the letters layer rather than living behind the toggle.
        Key.Glyph("/"),
        SPACE_KEY,
        Key.Glyph("."),
        SEND_KEY
    )
)

private val SYMBOL_ROWS: List<List<Key>> = listOf(
    row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    row("-", "_", ":", ";", "'", "\"", "(", ")", "[", "]"),
    // ~ and ^ are Minecraft's relative and local coordinate prefixes, @ starts
    // a target selector, {} wrap NBT -- all command staples.
    row("@", "~", "^", "{", "}", ",", "!", "?") + listOf(BACKSPACE_KEY),
    listOf(
        Key.Action("ABC", KeyAction.LAYER),
        Key.Glyph("/"),
        SPACE_KEY,
        Key.Glyph("="),
        SEND_KEY
    )
)

private val ACTION_KEY_COLOR = Color(0xFF3A3A44)
private val SHIFT_ACTIVE_COLOR = Color(0xFF6750A4)
private val GLYPH_KEY_COLOR = Color(0xFF55555F)

@Composable
fun ChatKeyboard(
    onType: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shifted by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }

    val rows = if (symbols) SYMBOL_ROWS else LETTER_ROWS

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEach { keys ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                keys.forEach { key ->
                    when (key) {
                        is Key.Glyph -> KeyButton(
                            label = if (shifted && !symbols) key.upper else key.lower,
                            weight = key.weight,
                            color = GLYPH_KEY_COLOR
                        ) {
                            onType(if (shifted && !symbols) key.upper else key.lower)
                            // One-shot, like a phone keyboard: shift applies to
                            // the next character and then releases.
                            shifted = false
                        }

                        is Key.Action -> KeyButton(
                            label = key.label,
                            weight = key.weight,
                            color = if (key.action == KeyAction.SHIFT && shifted) {
                                SHIFT_ACTIVE_COLOR
                            } else {
                                ACTION_KEY_COLOR
                            }
                        ) {
                            when (key.action) {
                                KeyAction.SHIFT -> shifted = !shifted
                                KeyAction.BACKSPACE -> onBackspace()
                                KeyAction.SEND -> onSend()
                                KeyAction.LAYER -> {
                                    symbols = !symbols
                                    shifted = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One key. Sized entirely by weight so a row always spans the full width
 * whatever it contains, and by [Modifier.fillMaxHeight] so every row is the
 * same height regardless of label length.
 */
@Composable
private fun RowScope.KeyButton(
    label: String,
    weight: Float,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(weight).fillMaxHeight(),
        shape = RoundedCornerShape(6.dp),
        // Keys are narrow; the default padding would clip a two-character label
        // long before the key itself ran out of room.
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White
        )
    ) {
        // A Button already centres its content, so the label needs no sizing of
        // its own -- only a cap on wrapping, since "Send" in a narrow key would
        // otherwise break across two lines and grow the row.
        Text(
            text = label,
            fontSize = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
