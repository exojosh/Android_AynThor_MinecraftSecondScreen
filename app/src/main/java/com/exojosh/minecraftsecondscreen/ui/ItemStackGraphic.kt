package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.net.HotbarSlot

/**
 * One item stack, with vanilla's decorations: glint, durability bar, count.
 *
 * Shared by the hotbar and the inventory grid rather than duplicated, because
 * they are the same thing on screen — a player shouldn't have to re-learn what
 * a durability bar looks like one tab over, and a fix to any of these should
 * land in both places at once.
 *
 * Drawn inside a box that maps 1:1 onto vanilla's 16x16 item area, so the
 * decoration offsets below can be vanilla's own pixel numbers. [unit] is one of
 * those pixels; the caller sizes the box to `16 * unit`.
 */

/** Durability bar: 13x2 at +2,+13 within the 16x16 item, per `drawItemBar`. */
private const val BAR_INSET_X = 2f
private const val BAR_INSET_Y = 13f
private const val BAR_WIDTH = 13f
private const val BAR_HEIGHT = 2f

/** Vanilla's item area, in its own pixels. */
const val ITEM_AREA_PIXELS = 16f

@Composable
fun BoxScope.ItemStackGraphic(
    stack: HotbarSlot,
    icon: Bitmap?,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap? = null,
    unit: Dp
) {
    if (stack.itemId == "minecraft:air") return

    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (stack.hasGlint) {
        // Vanilla's real glint: scrolling, additive, masked to the item's own
        // alpha. See ItemGlint for why it's drawn here rather than baked into
        // the icon the mod sends.
        ItemGlint(icon = icon, glintTexture = glintTexture)
    }

    stack.durabilityFraction?.let { fraction ->
        // Vanilla only shows the bar once an item has taken damage.
        if (fraction < 1f) {
            DurabilityBar(
                fraction = fraction,
                unit = unit,
                modifier = Modifier.offset(x = unit * BAR_INSET_X, y = unit * BAR_INSET_Y)
            )
        }
    }

    // Vanilla only labels a stack holding more than one, bottom-right with a
    // drop shadow, overhanging the item box by 1px on both edges.
    if (stack.count > 1) {
        val countModifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = unit, y = unit)

        if (fontSheet != null) {
            MinecraftText(
                text = stack.count.toString(),
                fontSheet = fontSheet,
                pixelSize = unit,
                modifier = countModifier
            )
        } else {
            // No font sheet delivered -- a system-font label beats no count.
            Text(
                text = stack.count.toString(),
                fontSize = 10.sp,
                color = Color.White,
                modifier = countModifier
            )
        }
    }
}

@Composable
private fun DurabilityBar(fraction: Float, unit: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.size(width = unit * BAR_WIDTH, height = unit * BAR_HEIGHT)
    ) {
        // Vanilla steps through discrete colours (green -> yellow -> red) as
        // durability drops rather than using a smooth gradient; roughly
        // reproduced here with two thresholds.
        val color = when {
            fraction > 0.5f -> Color(0xFF4CD137)
            fraction > 0.2f -> Color(0xFFE1B12C)
            else -> Color(0xFFE84118)
        }
        // Black backing is the full 2px; the coloured fill is the top 1px,
        // same as drawItemBar.
        drawRect(color = Color.Black)
        drawRect(
            color = color,
            size = size.copy(
                width = size.width * fraction.coerceIn(0f, 1f),
                height = size.height / 2f
            )
        )
    }
}
