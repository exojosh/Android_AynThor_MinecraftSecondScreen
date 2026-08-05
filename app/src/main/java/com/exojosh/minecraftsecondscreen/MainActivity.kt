package com.exojosh.minecraftsecondscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.exojosh.minecraftsecondscreen.ui.theme.AYNThorSecondScreenMinecraftDisplayTheme
import com.exojosh.minecraftsecondscreen.ui.HudScreen
import com.exojosh.minecraftsecondscreen.ui.InputGridScreen
import com.exojosh.minecraftsecondscreen.net.HudRepository
import com.exojosh.minecraftsecondscreen.net.HudState
import com.exojosh.minecraftsecondscreen.SecondScreenApp
import com.exojosh.minecraftsecondscreen.SecondScreenPresentation
import android.hardware.display.DisplayManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.exojosh.minecraftsecondscreen.net.GameDirectoryAccess
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider

/*
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AYNThorSecondScreenMinecraftDisplayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
*/
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AYNThorSecondScreenMinecraftDisplayTheme {
        Greeting("Android")
    }
}



/**
 * Primary-screen activity. Its own UI is a minimal status view -- the real
 * content renders on the Thor's second screen via SecondScreenPresentation.
 *
 * Display detection: the Thor exposes its bottom panel as a standard Android
 * "presentation" category external display, discoverable through
 * DisplayManager like any other secondary display (same mechanism used for
 * HDMI-out or Chromecast). If no such display is found (e.g. running on a
 * regular single-screen phone during development), we skip showing the
 * Presentation and just show status here instead.
 */
class MainActivity : ComponentActivity() {

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            GameDirectoryAccess.persist(this, it)
            iconProvider.refresh()
        }
    }
    private lateinit var iconProvider: ResourcePackIconProvider



    private lateinit var hudRepository: HudRepository
    private var presentation: SecondScreenPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hudRepository = HudRepository(lifecycleScope)

        // The mod pushes every HUD texture over the socket on connect, so the
        // provider reads from the repository first and only falls back to
        // bundled files. Constructed after the repository for that reason.
        iconProvider = ResourcePackIconProvider(this, hudRepository)
        // pickFolderLauncher.launch(null)

        hudRepository.start()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val connected by hudRepository.isConnected.collectAsState()
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (connected) "Connected to Minecraft mod -- second screen active"
                            else "Waiting for mod on 127.0.0.1:48291..."
                        )
                    }
                }
            }
        }

        showPresentationIfAvailable()
    }

    private fun showPresentationIfAvailable() {
        val displayManager = getSystemService(DisplayManager::class.java)
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        val secondDisplay = presentationDisplays.firstOrNull() ?: run {
            // Not on a dual-screen device, or the second display isn't
            // exposed as a presentation display -- nothing to attach to.
            return
        }

        presentation = SecondScreenPresentation(
            activity = this,
            display = secondDisplay,
            hudRepository = hudRepository,
            iconProvider = iconProvider
        ).also { it.show() }
    }

    override fun onDestroy() {
        presentation?.dismiss()
        presentation = null
        super.onDestroy()
    }
}