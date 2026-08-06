package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.net.GameBinding
import com.exojosh.minecraftsecondscreen.settings.INPUT_COLUMNS
import com.exojosh.minecraftsecondscreen.settings.INPUT_SLOT_COUNT
import com.exojosh.minecraftsecondscreen.settings.InputSettings

private val GRID_SPACING = 12.dp
private val GRID_PADDING = 16.dp

private val EMPTY_SLOT_BORDER = Color(0xFF444444)
private val EMPTY_SLOT_TEXT = Color(0xFF777777)

/**
 * Prettifies a binding id the mod hasn't told us about yet.
 *
 * Reached in two situations, both real: before the binding list has arrived
 * (the app can be launched with the game shut), and when a persisted id no
 * longer exists in the game (a removed mod, a renamed vanilla key). Showing
 * `swapOffhand` beats showing `key.swapOffhand`, and both beat a blank button
 * that gives the player nothing to recognise the slot by.
 */
private fun fallbackLabel(bindingId: String): String =
    bindingId.substringAfterLast('.').ifEmpty { bindingId }

/**
 * The Input tab: a 3x3 grid of buttons, each pressing one of the game's key
 * bindings.
 *
 * **Every button is data now.** It used to be a hardcoded list of command codes
 * matched against a hardcoded table in the mod — two lists in two repos meant
 * to say the same thing, and they drifted (an "R" button that did an F key's
 * job, an "L" button wired to nothing). The button carries a binding id that
 * came from the game itself and sends it straight back, so there's nothing left
 * for either side to get wrong, and another mod's bindings work with no entry
 * anywhere.
 *
 * The grid stays a fixed 3x3 with empty slots drawn as empty. Compacting it
 * would move every button after the gap, and these are thumb targets on a
 * handheld — muscle memory is worth more than filling the space.
 */
@Composable
fun InputGridScreen(
    settings: InputSettings,
    bindings: List<GameBinding>,
    onPressBinding: (String) -> Unit
) {
    val byId = remember(bindings) { bindings.associateBy(GameBinding::id) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Buttons used to be forced square with aspectRatio(1f), which was fine
        // when this tab owned the whole screen. It now shares the panel with
        // the status stack above it, and square buttons at this width overflow
        // into a scroll -- so height comes from the space actually available.
        val rows = (INPUT_SLOT_COUNT + INPUT_COLUMNS - 1) / INPUT_COLUMNS
        val available = maxHeight - GRID_PADDING * 2 - GRID_SPACING * (rows - 1)
        val buttonHeight = (available / rows).coerceAtLeast(48.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(INPUT_COLUMNS),
            modifier = Modifier.fillMaxSize().padding(GRID_PADDING),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING)
        ) {
            items((0 until INPUT_SLOT_COUNT).toList()) { index ->
                val bindingId = settings.bindingAt(index)

                if (bindingId == null) {
                    EmptySlot(modifier = Modifier.height(buttonHeight))
                } else {
                    val binding = byId[bindingId]
                    Button(
                        onClick = { onPressBinding(bindingId) },
                        modifier = Modifier.height(buttonHeight)
                    ) {
                        Text(
                            text = binding?.label ?: fallbackLabel(bindingId),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * A slot with nothing assigned. Drawn rather than skipped so the other buttons
 * keep their positions, and labelled so it reads as configurable rather than
 * as something that failed to load.
 */
@Composable
private fun EmptySlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .border(1.dp, EMPTY_SLOT_BORDER, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Empty", color = EMPTY_SLOT_TEXT, fontSize = 12.sp)
    }
}
