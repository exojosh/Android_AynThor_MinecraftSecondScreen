package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import android.graphics.Matrix as AndroidMatrix

/**
 * Vanilla's enchantment glint, over an item icon.
 *
 * The mod renders icons **without** the glint (see `ItemIconRenderer`), because
 * an icon is a still PNG and this effect is animated. Everything that makes it
 * read as vanilla's glint rather than as a coloured wash happens here:
 *
 * - **It scrolls.** Vanilla's `TextureTransform.GLINT_TEXTURING` builds a
 *   texture matrix per frame from the wall clock, which is what those numbers
 *   below are.
 * - **It's masked to the item.** The glint is drawn into an offscreen layer and
 *   then cut down by the icon's own alpha, so a sword glints along the blade
 *   instead of the whole 16x16 square lighting up. The previous version was an
 *   unmasked translucent purple gradient across the box, which read as a
 *   *highlighted slot* rather than an enchanted item.
 * - **It's added, not blended over.** Vanilla's glint pipeline is additive; a
 *   translucent overlay just washes the icon out.
 */

/** Vanilla samples the 128px glint texture 8 times across a GUI item --
 *  `GLINT_TEXTURING = getGlintTransformation(8.0F)`. */
private const val TILES_ACROSS_ITEM = 8f

/** `matrix4f.rotateZ((float)(Math.PI / 18))` — 10 degrees. */
private const val ANGLE_DEGREES = 10f

/**
 * The two scroll periods, and the 8x that multiplies the clock. Straight from
 * `getGlintTransformation`:
 *
 * ```
 * long l = (long)(Util.getMeasuringTimeMs() * glintSpeed * 8.0);
 * float f = (l % 110000L) / 110000.0F;
 * float g = (l % 30000L)  / 30000.0F;
 * new Matrix4f().translation(-f, g, 0).rotateZ(PI/18).scale(8)
 * ```
 *
 * Two periods that don't divide into each other are what stop the pattern
 * looking like it's sliding in one direction — it drifts. `glintSpeed` is a
 * video option we can't see from here, so it's taken at its default of 1.
 */
private const val CLOCK_MULTIPLIER = 8.0
private const val PERIOD_X_MS = 110_000L
private const val PERIOD_Y_MS = 30_000L

/**
 * How strong the shimmer is. Vanilla's `GlintAlpha` uniform, which isn't
 * readable from here; this is tuned by eye on the device to sit where vanilla's
 * does — bright enough to notice at a glance, dim enough that the item is still
 * identifiable underneath.
 */
private const val GLINT_ALPHA = 0.55f

/**
 * Draws [icon]'s glint, filling the box it's given.
 *
 * Expects to be stacked **over** an already-drawn copy of the same icon at the
 * same size, since the icon is both what's being decorated and the mask.
 *
 * [glintTexture] null (an older mod build that doesn't serve it) draws nothing
 * rather than falling back to the old purple wash — no glint reads better than
 * a wrong one, and the item itself is still correct either way.
 */
@Composable
fun ItemGlint(
    icon: Bitmap?,
    glintTexture: Bitmap?,
    modifier: Modifier = Modifier
) {
    if (icon == null || glintTexture == null) return

    val iconImage = remember(icon) { icon.asImageBitmap() }
    val glintImage = remember(glintTexture) { glintTexture.asImageBitmap() }

    // Read inside the draw lambda, not during composition: a State read in
    // Canvas's block invalidates only the draw phase, so an animating glint
    // never re-runs composition for the slot (or anything above it).
    val frameMillis = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameMillis.longValue = it }
        }
    }

    // Linear filtering, matching the texture's own .mcmeta ("blur": true).
    // This is the one texture in the app that *isn't* drawn with FilterQuality
    // .None -- the glint is a soft gradient, and point-sampling it at this
    // scale turns the shimmer into visible blocky steps.
    val shader = remember(glintImage) {
        ImageShader(glintImage, TileMode.Repeated, TileMode.Repeated)
    }
    val glintPaint = remember(shader) {
        Paint().apply {
            this.shader = shader
            filterQuality = FilterQuality.Low
        }
    }
    val localMatrix = remember { AndroidMatrix() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val scrolled = (frameMillis.longValue * CLOCK_MULTIPLIER).toLong()
        val offsetX = (scrolled % PERIOD_X_MS) / PERIOD_X_MS.toFloat()
        val offsetY = (scrolled % PERIOD_Y_MS) / PERIOD_Y_MS.toFloat()

        // One full tile of the texture, in screen pixels. Vanilla's scale of 8
        // means eight tiles span the item, so the translation -- which vanilla
        // applies in whole-texture units -- is in these units too.
        val tilePx = size.width / TILES_ACROSS_ITEM

        localMatrix.reset()
        localMatrix.postScale(tilePx / glintImage.width, tilePx / glintImage.height)
        localMatrix.postRotate(ANGLE_DEGREES)
        localMatrix.postTranslate(-offsetX * tilePx, offsetY * tilePx)
        shader.setLocalMatrix(localMatrix)

        drawIntoCanvas { canvas ->
            // The layer's own blend mode is what makes the composite additive.
            // Doing it per-draw instead wouldn't work: the mask pass below has
            // to combine with the glint *inside* the layer, not with the item.
            canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height),
                Paint().apply {
                    blendMode = BlendMode.Plus
                    alpha = GLINT_ALPHA
                }
            )

            canvas.drawRect(0f, 0f, size.width, size.height, glintPaint)

            // DstIn keeps the destination (the glint just drawn) only where the
            // source (the icon) is opaque. This is the masking step.
            drawImage(
                image = iconImage,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(iconImage.width, iconImage.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
                blendMode = BlendMode.DstIn
            )

            canvas.restore()
        }
    }
}
