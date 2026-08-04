package com.exojosh.minecraftsecondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exojosh.minecraftsecondscreen.ui.HudScreen
import com.exojosh.minecraftsecondscreen.ui.InputGridScreen
import com.exojosh.minecraftsecondscreen.net.HudRepository
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.exojosh.minecraftsecondscreen.ui.RepeatingTextureBackground

private enum class Tab { HUD, INPUT }

enum class SecondScreenTab { HUD, INPUT }
@Composable
fun SecondScreenApp(
    hudRepository: HudRepository,
    iconProvider: ResourcePackIconProvider
) {
    val context = LocalContext.current
    val hudState by hudRepository.hudState.collectAsState()
    var selectedTab by remember { mutableStateOf(SecondScreenTab.HUD) }

    // Load dirt texture at root level
    val dirtBitmap = remember {
        try {
            context.assets.open("minecraft/textures/block/dirt.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. MAIN SCREEN CONTENT (Takes full space, top-aligned)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp) // Leave room so buttons don't overlap lower HUD elements
            ) {
                when (selectedTab) {
                    SecondScreenTab.HUD -> HudScreen(state = hudState,  hudRepository = hudRepository, iconProvider = iconProvider)
                    SecondScreenTab.INPUT -> InputGridScreen(
                        onSendCommand = { command ->
                            // Wire this up to your socket or repository sender:
                            hudRepository.sendCommand(command)
                        }
                    )
                }
            }

            // 2. TAB BUTTONS (Pinned directly to the bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f)) // Semi-transparent dark strip behind tab buttons
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HUD Tab Button
                Button(
                    onClick = { selectedTab = SecondScreenTab.HUD },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == SecondScreenTab.HUD) Color(0xFF6750A4) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("HUD")
                }

                // Input Tab Button
                Button(
                    onClick = { selectedTab = SecondScreenTab.INPUT },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == SecondScreenTab.INPUT) Color(0xFF6750A4) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Input")
                }
            }
        }
    }

    // Wrap the entire app view in dirt texture
    if (dirtBitmap != null) {
        RepeatingTextureBackground(
            texture = dirtBitmap,
            scaleFactor = 16f, // Adjust scale factor as needed
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

@Composable
private fun tabButtonColors(selected: Boolean) =
    if (selected) ButtonDefaults.buttonColors()
    else ButtonDefaults.outlinedButtonColors()
