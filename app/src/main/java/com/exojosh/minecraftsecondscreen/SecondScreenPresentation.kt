package com.exojosh.minecraftsecondscreen

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.exojosh.minecraftsecondscreen.net.HudRepository
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider

/**
 * A Presentation is Android's standard mechanism for rendering different
 * content on a secondary display than what's showing on the primary one --
 * the same API used for HDMI-out, Chromecast, or a car's second display.
 * This is the class that actually shows up on the Thor's bottom panel.
 *
 * A ComposeView needs a lifecycle/viewmodel/saved-state owner attached
 * manually here, since a Presentation's window isn't part of the normal
 * Activity view hierarchy -- without these three lines Compose throws at
 * runtime rather than just rendering. ComponentActivity conveniently
 * implements all three owner interfaces itself, so we just point at it.
 */
class SecondScreenPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val hudRepository: HudRepository,
    private val iconProvider: ResourcePackIconProvider
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The bottom screen is a HUD -- it's looked at constantly but never
        // touched, so Android's idle timer has no reason to think it's in use
        // and blanks it mid-session. The player is holding the device and
        // playing the whole time.
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)

            setContent {
                SecondScreenApp(
                    hudRepository = hudRepository,
                    iconProvider = iconProvider
                )
            }
        }

        setContentView(composeView)
    }
}