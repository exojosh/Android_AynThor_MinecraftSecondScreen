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
 * Grid of custom keybind buttons. Codes here must match the keys in
 * CommandDispatcher.COMMANDS on the mod side -- add/rename in both places
 * together.
 *
 * Known gap: "L" has no entry in COMMANDS, so it currently logs "Unknown
 * command code" and does nothing. Fixing that belongs with the input-remapping
 * work, which turns both sides into data instead of two hand-maintained lists.
 */
private val COMMAND_CODES = listOf("R", "G", "H", "K", "L")

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
        val rows = (COMMAND_CODES.size + COLUMNS - 1) / COLUMNS
        val available = maxHeight - GRID_PADDING * 2 - GRID_SPACING * (rows - 1)
        val buttonHeight = (available / rows).coerceAtLeast(48.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxSize().padding(GRID_PADDING),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING)
        ) {
            items(COMMAND_CODES) { code ->
                Button(
                    onClick = { onSendCommand(code) },
                    modifier = Modifier.height(buttonHeight)
                ) {
                    Text(code)
                }
            }
        }
    }
}
