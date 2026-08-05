package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Minecraft's in-game font is a bitmap sheet, not a TTF -- so matching it
 * exactly means drawing from that sheet rather than shipping a lookalike
 * font file. This does that, which has the side benefit that a resource pack
 * restyling the font comes through automatically, same as the HUD sprites.
 *
 * The sheet (assets/minecraft/textures/font/ascii.png) is a 16x16 grid of
 * equally-sized cells covering codepoints 0x00-0xFF. Vanilla renders it at
 * 8x8 logical pixels per cell regardless of the file's actual resolution, so
 * a hi-res pack's 256x256 sheet lays out identically to the 128x128 default.
 * Everything below is in those 8-per-cell "font pixel" units.
 *
 * Glyphs are not fixed-width: vanilla derives each one's advance by scanning
 * its cell right-to-left for the last column containing any non-transparent
 * pixel, then adds 1px of letter spacing.
 */
private const val GRID = 16
private const val GLYPH_FONT_PIXELS = 8

/** Space has no pixels to measure. Vanilla supplies its advance separately
 *  (the "space" provider in default.json), so hardcode the same value. */
private const val SPACE_ADVANCE = 4

/** Vanilla darkens text shadow to a quarter brightness, keeping alpha. */
private fun Color.toShadowColor(): Color = Color(red / 4f, green / 4f, blue / 4f, alpha)

/**
 * A parsed font sheet: the image plus each codepoint's measured advance.
 * Build via [rememberMinecraftFont] -- measuring walks every pixel of the
 * sheet, which is cheap but pointless to repeat per recomposition.
 */
class MinecraftFontSheet(
    val image: ImageBitmap,
    val cellSize: Int,
    private val advances: IntArray
) {
    fun advanceOf(char: Char): Int =
        advances[if (char.code in 0..255) char.code else '?'.code]

    /** Total advance of [text] in font pixels, including the trailing 1px gap. */
    fun measure(text: String): Int = text.sumOf { advanceOf(it) }

    companion object {
        fun from(bitmap: Bitmap): MinecraftFontSheet? {
            val cellSize = bitmap.width / GRID
            if (cellSize <= 0 || bitmap.height < cellSize * GRID) return null

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            // Scale measured cell-pixel widths back down to vanilla's 8px grid
            // so hi-res sheets don't render wider than the default one.
            val toFontPixels = GLYPH_FONT_PIXELS.toFloat() / cellSize
            val advances = IntArray(256)

            for (code in 0 until 256) {
                if (code == ' '.code) {
                    advances[code] = SPACE_ADVANCE
                    continue
                }

                val cellX = (code % GRID) * cellSize
                val cellY = (code / GRID) * cellSize

                var lastInkColumn = -1
                for (x in cellSize - 1 downTo 0) {
                    var hasInk = false
                    for (y in 0 until cellSize) {
                        if (pixels[(cellY + y) * bitmap.width + (cellX + x)] ushr 24 != 0) {
                            hasInk = true
                            break
                        }
                    }
                    if (hasInk) {
                        lastInkColumn = x
                        break
                    }
                }

                // +1 for the single-pixel gap vanilla puts between glyphs.
                advances[code] = (0.5f + (lastInkColumn + 1) * toFontPixels).toInt() + 1
            }

            return MinecraftFontSheet(bitmap.asImageBitmap(), cellSize, advances)
        }
    }
}

@Composable
fun rememberMinecraftFont(bitmap: Bitmap?): MinecraftFontSheet? =
    remember(bitmap) { bitmap?.let { MinecraftFontSheet.from(it) } }

/**
 * Draws [text] in Minecraft's font, with vanilla's 1px offset drop shadow.
 *
 * @param pixelSize size of one *font* pixel -- a glyph is 8 * pixelSize tall.
 */
@Composable
fun MinecraftText(
    text: String,
    fontSheet: MinecraftFontSheet,
    pixelSize: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shadow: Boolean = true
) {
    val shadowPixels = if (shadow) 1 else 0

    // The trailing +1 of the last glyph's advance is inter-glyph spacing, not
    // ink, so it isn't part of the drawn width.
    val widthPixels = (fontSheet.measure(text) - 1).coerceAtLeast(0) + shadowPixels
    val heightPixels = GLYPH_FONT_PIXELS + shadowPixels

    Canvas(
        modifier = modifier.size(
            width = pixelSize * widthPixels,
            height = pixelSize * heightPixels
        )
    ) {
        // Derive scale from the laid-out size rather than the Dp so the glyphs
        // still fill the box exactly if a parent constrains it.
        val scale = size.height / heightPixels
        val cell = fontSheet.cellSize
        val glyphSize = (GLYPH_FONT_PIXELS * scale).roundToInt().coerceAtLeast(1)
        val dstSize = IntSize(glyphSize, glyphSize)

        fun drawPass(offsetFontPixels: Float, passColor: Color) {
            var penX = offsetFontPixels
            val y = (offsetFontPixels * scale).roundToInt()

            for (char in text) {
                val code = if (char.code in 0..255) char.code else '?'.code
                drawImage(
                    image = fontSheet.image,
                    srcOffset = IntOffset((code % GRID) * cell, (code / GRID) * cell),
                    srcSize = IntSize(cell, cell),
                    dstOffset = IntOffset((penX * scale).roundToInt(), y),
                    dstSize = dstSize,
                    colorFilter = ColorFilter.tint(passColor),
                    filterQuality = FilterQuality.None
                )
                penX += fontSheet.advanceOf(char)
            }
        }

        // Shadow first so the main pass sits on top of it, as vanilla does.
        if (shadow) drawPass(1f, color.toShadowColor())
        drawPass(0f, color)
    }
}
