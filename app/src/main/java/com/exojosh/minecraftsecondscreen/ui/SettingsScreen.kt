package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.settings.HudElement
import com.exojosh.minecraftsecondscreen.settings.HudSettings

private val ROW_BACKGROUND = Color.Black.copy(alpha = 0.35f)

/**
 * The Settings tab: which HUD elements are on screen.
 *
 * Deliberately visibility-only rather than drag-to-place. The status rows all
 * share one alignment grid (see `STATUS_ICON_SIZE`/`STATUS_ICON_COUNT` in
 * [HudScreen]), and free placement would either break that grid or need a
 * layout engine to preserve it -- for a fraction of the value. Hiding the
 * elements you don't care about is most of what "customisable" means here.
 *
 * Changes apply instantly: [HudSettings] holds its state in a Compose state
 * map, so the HUD above this panel recomposes as each switch is flipped, and
 * the player can see what they're turning off while they turn it off.
 */
@Composable
fun SettingsScreen(settings: HudSettings) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Show on this screen",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                // Says where a switched-off element goes. Without this the
                // toggle reads as "hide", and nobody would guess the element
                // reappears in the game itself.
                Text(
                    text = "Anything switched off goes back to the game's HUD on the top screen",
                    color = Color(0xFF9A9A9A),
                    fontSize = 11.sp
                )
            }
            if (!settings.isDefault) {
                TextButton(onClick = { settings.resetToDefaults() }) {
                    Text("Reset", color = Color(0xFFBBBBBB))
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(HudElement.entries) { element ->
                ElementToggle(
                    element = element,
                    checked = settings.isVisible(element),
                    onToggle = { settings.toggle(element) }
                )
            }
        }
    }
}

/**
 * One row. The whole row is the tap target, not just the switch -- this is a
 * handheld being poked with a thumb, and a bare `Switch` is a small target
 * against a 1240px-wide panel.
 */
@Composable
private fun ElementToggle(
    element: HudElement,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ROW_BACKGROUND)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = element.label, color = Color.White, fontSize = 14.sp)
            Text(text = element.description, color = Color(0xFF9A9A9A), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            // null: the Row above already handles the tap, and letting the
            // switch handle it too gives the row two competing ripples.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF5B8A3A)
            )
        )
    }
}
