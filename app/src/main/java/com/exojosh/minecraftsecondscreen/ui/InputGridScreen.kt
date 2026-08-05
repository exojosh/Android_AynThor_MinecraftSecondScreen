package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One button on the input grid.
 *
 * [code] is the wire code and must match a key in `CommandDispatcher.COMMANDS`
 * on the mod side; [label] is what the player reads. Keeping them separate is
 * the fix for the bug this replaced: the grid used to render the raw code as
 * its own label, so a button that sent `"R"` -- which the mod had bound to
 * swap-hands, vanilla's F -- displayed as an "R" key that did an F key's job.
 * A code can now be as literal as the mod needs without lying to the player.
 */
private data class InputCommand(val code: String, val label: String)

/**
 * Grid of custom keybind buttons.
 *
 * Codes must match `CommandDispatcher.COMMANDS` on the mod side -- add or
 * rename in both places together. Until the settings work (item 3 in TODO.md)
 * turns both sides into data, this is a hand-maintained list and drift between
 * the two is only caught by pressing the button.
 *
 * The mod keeps the old single-letter codes as aliases, so this list moving to
 * action names doesn't strand a mod build that predates the rename.
 */
private val COMMANDS = listOf(
    InputCommand("INVENTORY", "Inventory"),
    InputCommand("DROP", "Drop"),
    InputCommand("SWAP", "Swap"),
    InputCommand("USE", "Use"),
    InputCommand("ATTACK", "Attack"),
    InputCommand("JUMP", "Jump"),
    InputCommand("SNEAK", "Sneak")
)

private const val COLUMNS = 3
private val GRID_SPACING = 12.dp
private val GRID_PADDING = 16.dp

@Composable
fun InputGridScreen(onSendCommand: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Buttons used to be forced square with aspectRatio(1f), which was fine
        // when this tab owned the whole screen. It now shares the panel with
        // the status stack above it, and square buttons at this width overflow
        // into a scroll -- so height comes from the space actually available.
        val rows = (COMMANDS.size + COLUMNS - 1) / COLUMNS
        val available = maxHeight - GRID_PADDING * 2 - GRID_SPACING * (rows - 1)
        val buttonHeight = (available / rows).coerceAtLeast(48.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxSize().padding(GRID_PADDING),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING)
        ) {
            items(COMMANDS) { command ->
                Button(
                    onClick = { onSendCommand(command.code) },
                    modifier = Modifier.height(buttonHeight)
                ) {
                    Text(command.label)
                }
            }
        }
    }
}
