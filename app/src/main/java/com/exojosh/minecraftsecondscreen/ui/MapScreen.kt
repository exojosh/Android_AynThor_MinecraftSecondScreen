package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.exojosh.minecraftsecondscreen.net.MapTile
import kotlin.math.min
import kotlin.math.roundToInt

private val MARKER_FILL = Color.White
private val MARKER_OUTLINE = Color.Black

/**
 * The paper border around the map, in map pixels per side.
 *
 * Vanilla draws `map_background.png` as a quad from -7 to 135 while the map
 * data itself covers 0 to 128 (HeldItemRenderer's first-person map), so the
 * sheet overhangs the data by exactly 7 on every side and the whole thing is
 * 142 across. Both numbers are reproduced here rather than eyeballed, so the
 * proportions match a map held in game at any scale.
 */
private const val MAP_BORDER = 7

/**
 * The paper field of vanilla's `map_background.png` (it's a 3-colour indexed
 * PNG; this is the light one). Only used if the sprite never arrives, so the
 * map still sits on paper rather than on bare dirt.
 */
private val MAP_PAPER_FALLBACK = Color(0xFFD6BE96)

/**
 * The live map, shown below the hotbar on the HUD tab.
 *
 * The tile arrives pre-coloured by the mod using vanilla's own map palette, so
 * there's no colour work here -- this scales it up, lays it on the paper sheet
 * a held map is drawn on, and puts a marker on it.
 *
 * Scaling is snapped to whole pixels and drawn with [FilterQuality.None]: the
 * tile is one pixel per block, so any smoothing turns terrain into mush. Same
 * rule as every other pixel-art surface in this app. The paper underneath is a
 * 64x64 sprite stretched to 142 and is the one thing here that *doesn't* land
 * on whole pixels -- vanilla stretches it the same way, and it's a soft
 * gradient, so there's no pixel grid to preserve.
 *
 * The paper is also load-bearing beyond decoration: [com.exojosh.minecraftsecondscreen.net.MapTile]
 * pixels for unloaded chunks arrive fully transparent, so they now read as
 * blank paper -- exactly what an unexplored region looks like on a real map --
 * instead of as a hole showing the dirt backdrop.
 */
@Composable
fun MapScreen(tile: MapTile?, isConnected: Boolean, backgroundBitmap: Bitmap? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (tile == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isConnected) "Waiting for map data..." else "Not connected",
                    color = Color(0xFFBBBBBB)
                )
                if (isConnected) {
                    Text(
                        text = "Join a world to see the map",
                        color = Color(0xFF777777),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            return@Box
        }

        val image = remember(tile.bitmap) { tile.bitmap.asImageBitmap() }
        val paper = remember(backgroundBitmap) { backgroundBitmap?.asImageBitmap() }

        // No padding: the map is height-bound in this panel and the scale below
        // is quantised to whole pixels, so a few dp of inset can cost an entire
        // step of map size. The paper border is the margin.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Whole-pixel scale only. A fractional scale would put block edges
            // between screen pixels and make the whole map shimmer as the
            // player walks. The fit is taken against the *sheet* -- map plus
            // both borders -- so the paper is what's sized to the panel and
            // the map data still lands on an integer scale inside it.
            val footprint = tile.size + MAP_BORDER * 2
            val fit = min(size.width / footprint, size.height / footprint)
            val scale = fit.toInt().coerceAtLeast(1)

            val drawn = tile.size * scale
            val border = MAP_BORDER * scale
            val sheet = drawn + border * 2

            val sheetLeft = ((size.width - sheet) / 2f).roundToInt()
            val sheetTop = ((size.height - sheet) / 2f).roundToInt()
            val left = sheetLeft + border
            val top = sheetTop + border

            if (paper != null) {
                drawImage(
                    image = paper,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(paper.width, paper.height),
                    dstOffset = IntOffset(sheetLeft, sheetTop),
                    dstSize = IntSize(sheet, sheet),
                    filterQuality = FilterQuality.None
                )
            } else {
                drawRect(
                    color = MAP_PAPER_FALLBACK,
                    topLeft = Offset(sheetLeft.toFloat(), sheetTop.toFloat()),
                    size = Size(sheet.toFloat(), sheet.toFloat())
                )
            }

            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(tile.size, tile.size),
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(drawn, drawn),
                filterQuality = FilterQuality.None
            )

            val markerX = left + tile.playerPixelX * scale
            val markerZ = top + tile.playerPixelZ * scale

            translate(left = markerX, top = markerZ) {
                // Minecraft yaw is 0 = south (+Z) and increases clockwise; the
                // marker path below points up (-Z on screen), so it needs a
                // half turn to line up with the world.
                rotate(degrees = tile.yaw + 180f, pivot = Offset.Zero) {
                    drawPlayerMarker(scale)
                }
            }
        }
    }
}

/**
 * A small arrowhead pointing along -Y, drawn about the origin so the caller
 * can translate/rotate it into place.
 *
 * Sized off the map scale so it stays readable when the tile is drawn large,
 * but clamped so it never swamps the terrain underneath.
 */
private fun DrawScope.drawPlayerMarker(scale: Int) {
    val r = (scale * 3f).coerceIn(9f, 20f)

    val path = Path().apply {
        moveTo(0f, -r)
        lineTo(r * 0.62f, r * 0.72f)
        lineTo(0f, r * 0.34f)
        lineTo(-r * 0.62f, r * 0.72f)
        close()
    }

    // Outline first, fill on top: map colours run the full range from sand to
    // deepslate, so a plain white arrow disappears over pale terrain.
    drawPath(path, color = MARKER_OUTLINE, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.34f))
    drawPath(path, color = MARKER_FILL)
}
