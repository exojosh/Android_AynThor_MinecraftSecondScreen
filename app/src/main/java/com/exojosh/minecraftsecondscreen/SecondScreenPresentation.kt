package com.exojosh.minecraftsecondscreen

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.exojosh.minecraftsecondscreen.net.HudRepository
import com.exojosh.minecraftsecondscreen.SecondScreenApp
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider
import com.exojosh.minecraftsecondscreen.ui.HudScreen

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