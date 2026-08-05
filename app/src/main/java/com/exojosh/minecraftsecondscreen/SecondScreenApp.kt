package com.exojosh.minecraftsecondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Height reserved at the bottom for the tab strip. */
private val TAB_STRIP_HEIGHT = 60.dp

private val TAB_SELECTED_COLOR = Color(0xFF6750A4)

enum class SecondScreenTab(val label: String) {
    HUD("HUD"),
    MAP("Map"),
    INPUT("Input")
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = TAB_STRIP_HEIGHT)
            ) {
                when (selectedTab) {
                    SecondScreenTab.HUD -> HudScreen(
                        state = hudState,
                        hudRepository = hudRepository,
                        iconProvider = iconProvider
                    )

                    SecondScreenTab.MAP -> MapScreen(
                        tile = mapTile,
                        isConnected = isConnected
                    )

                    SecondScreenTab.INPUT -> InputGridScreen(
                        onSendCommand = hudRepository::sendCommand
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Driven off the enum so adding a tab is one entry, not another
                // copy-pasted Button that can drift from its neighbours.
                SecondScreenTab.entries.forEach { tab ->
                    Button(
                        onClick = { selectedTab = tab },
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
