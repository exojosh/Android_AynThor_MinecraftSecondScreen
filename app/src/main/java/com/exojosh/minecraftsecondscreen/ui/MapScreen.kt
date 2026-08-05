package com.exojosh.minecraftsecondscreen.ui

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

private val MAP_BACKGROUND = Color(0xFF14100C)
private val MARKER_FILL = Color.White
private val MARKER_OUTLINE = Color.Black

/**
 * The live map tab.
 *
 * The tile arrives pre-coloured by the mod using vanilla's own map palette, so
 * there's no colour work here -- this scales it up and puts a marker on it.
 *
 * Scaling is snapped to whole pixels and drawn with [FilterQuality.None]: the
 * tile is one pixel per block, so any smoothing turns terrain into mush. Same
 * rule as every other pixel-art surface in this app.
 */
@Composable
fun MapScreen(tile: MapTile?, isConnected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MAP_BACKGROUND),
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

        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Whole-pixel scale only. A fractional scale would put block edges
            // between screen pixels and make the whole map shimmer as the
            // player walks.
            val fit = min(size.width / tile.size, size.height / tile.size)
            val scale = fit.toInt().coerceAtLeast(1)
            val drawn = tile.size * scale

            val left = ((size.width - drawn) / 2f).roundToInt()
            val top = ((size.height - drawn) / 2f).roundToInt()

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
