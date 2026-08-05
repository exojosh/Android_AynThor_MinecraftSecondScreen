package com.exojosh.minecraftsecondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.exojosh.minecraftsecondscreen.net.HudRepository
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider
import com.exojosh.minecraftsecondscreen.ui.HudScreen
import com.exojosh.minecraftsecondscreen.ui.InputGridScreen
import com.exojosh.minecraftsecondscreen.ui.MapScreen
import com.exojosh.minecraftsecondscreen.ui.RepeatingTextureBackground
import com.exojosh.minecraftsecondscreen.ui.SettingsScreen
import com.exojosh.minecraftsecondscreen.settings.rememberHudSettings

/**
 * Height reserved at the bottom for the tab strip.
 *
 * Kept tight on purpose: everything not spent here goes to the tab's panel,
 * and the map scales in whole-pixel steps, so ~16dp reclaimed off this strip
 * can be worth a full step of map size rather than a sliver.
 */
private val TAB_STRIP_HEIGHT = 44.dp

private val TAB_SELECTED_COLOR = Color(0xFF6750A4)

/**
 * What fills the panel *below* the hotbar.
 *
 * The status stack (armor, hearts, hunger, XP, hotbar) is not part of this --
 * it's always on screen, on every tab. Switching tabs only swaps the panel
 * underneath it, because the whole point of the second screen is that vital
 * signs stay visible while you do something else with the rest of the space.
 *
 * A Settings tab belongs here later; adding it is one entry plus one `when`
 * branch.
 */
enum class SecondScreenTab(val label: String) {
    HUD("HUD"),
    INPUT("Input"),
    SETTINGS("Settings")
}

@Composable
fun SecondScreenApp(
    hudRepository: HudRepository,
    iconProvider: ResourcePackIconProvider
) {
    val hudState by hudRepository.hudState.collectAsState()
    val mapTile by hudRepository.mapTile.collectAsState()
    val isConnected by hudRepository.isConnected.collectAsState()
    var selectedTab by remember { mutableStateOf(SecondScreenTab.HUD) }
    val settings = rememberHudSettings()

    // Same source as every other texture: the mod pushes it over the socket,
    // resolved through Minecraft's resource manager, so a resource pack applies
    // here too. This used to read the bundled copy directly while HudScreen
    // used the socket one, which meant a pack changed the HUD's background but
    // not the background behind it.
    val dirtBitmap = remember(iconProvider.getBackground()) {
        iconProvider.getBackground()?.asImageBitmap()
    }

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = TAB_STRIP_HEIGHT)
            ) {
                // Always on top, on every tab. Wraps its own height.
                HudScreen(
                    state = hudState,
                    hudRepository = hudRepository,
                    settings = settings,
                    iconProvider = iconProvider
                )

                // Everything left over goes to the tab's panel.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        SecondScreenTab.HUD -> MapScreen(
                            tile = mapTile,
                            isConnected = isConnected,
                            backgroundBitmap = iconProvider.getMapBackground()
                        )

                        SecondScreenTab.INPUT -> InputGridScreen(
                            onSendCommand = hudRepository::sendCommand
                        )

                        SecondScreenTab.SETTINGS -> SettingsScreen(settings = settings)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    // 2dp + a 40dp button + 2dp has to come to TAB_STRIP_HEIGHT
                    // exactly: the strip is overlaid on the Box, and the panel
                    // above only avoids it via a bottom padding of that height,
                    // so a taller strip silently covers the panel's last rows.
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Driven off the enum so adding a tab is one entry, not another
                // copy-pasted Button that can drift from its neighbours.
                SecondScreenTab.entries.forEach { tab ->
                    Button(
                        onClick = { selectedTab = tab },
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (selectedTab == tab) TAB_SELECTED_COLOR else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(tab.label)
                    }
                }
            }
        }
    }

    if (dirtBitmap != null) {
        RepeatingTextureBackground(
            texture = dirtBitmap,
            scaleFactor = 16f,
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF222222)),
            content = { content() }
        )
    }
}
