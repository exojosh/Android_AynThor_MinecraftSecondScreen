    package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
 * together. A radial layout (matching the 3DS reference image more closely)
 * is a reasonable follow-up once this grid version is working end-to-end;
 * swapping the layout doesn't change anything below the onSendCommand call.
 */
private val COMMAND_CODES = listOf("R", "G", "H", "K", "L")

@Composable
fun InputGridScreen(onSendCommand: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(COMMAND_CODES) { code ->
            Button(
                onClick = { onSendCommand(code) },
                modifier = Modifier.aspectRatio(1f)
            ) {
                Text(code)
            }
        }
    }
}
