package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import android.graphics.Matrix as AndroidMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.exojosh.minecraftsecondscreen.net.HudIcon
import com.exojosh.minecraftsecondscreen.net.HudState
import com.exojosh.minecraftsecondscreen.net.ResourcePackIconProvider
import com.exojosh.minecraftsecondscreen.net.HudRepository
import kotlin.math.ceil
import kotlin.math.min
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/** Matches the 25.dp used by the heart/armor/food rows, so bubbles read as
 *  part of the same stack of status icons. */
private val BUBBLE_SIZE = 25.dp



@Composable
fun HudScreen(
    state: HudState?,
    hudRepository: HudRepository,
    iconProvider: ResourcePackIconProvider? = null
) {
    if (state == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for game data...")
        }
        return
    }

    val context = LocalContext.current

    // Safely load the texture from assets
    val dirtBitmap = remember {
        try {
            context.assets.open("minecraft/textures/block/dirt.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception in Logcat
            null
        }
    }

    if (dirtBitmap != null) {
        RepeatingTextureBackground(
            texture = dirtBitmap,
            modifier = Modifier.fillMaxSize()
        ) {
            HudContent(state = state, hudRepository = hudRepository, iconProvider = iconProvider)
        }
    } else {
        // Fallback with a dark charcoal background if asset fails to load
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            HudContent(state = state, hudRepository = hudRepository, iconProvider = iconProvider)
        }
    }
}

@Composable
fun HudContent(state: HudState, hudRepository: HudRepository, iconProvider: ResourcePackIconProvider?) {
    val fontSheet = rememberMinecraftFont(iconProvider?.getFontSheet())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    )
    {
        // Armor row
        IconRow(
            currentPoints = state.armor,
            maxPoints = 20,
            fullIcon = iconProvider?.getIcon(HudIcon.ARMOR_FULL),
            halfIcon = iconProvider?.getIcon(HudIcon.ARMOR_HALF),
            emptyIcon = iconProvider?.getIcon(HudIcon.ARMOR_EMPTY),
            drawFallbackFull = { drawShield(filled = true, half = false) },
            drawFallbackHalf = { drawShield(filled = true, half = true) },
            drawFallbackEmpty = { drawShield(filled = false, half = false) }
        )

        // Breathing bubbles sit directly above hunger, as in vanilla, and are
        // right-aligned to match the hunger row they sit over. The row keeps
        // its height when empty so the layout below doesn't jump each time
        // the player surfaces.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(modifier = Modifier.height(BUBBLE_SIZE)) {
                if (state.isDrowning) {
                    BubbleRow(
                        air = state.air,
                        maxAir = state.maxAir,
                        bubbleBitmap = iconProvider?.getIcon(HudIcon.AIR),
                        burstingBitmap = iconProvider?.getIcon(HudIcon.AIR_BURSTING),
                        bubbleSize = BUBBLE_SIZE
                    )
                }
            }
        }

        // Health and Hunger share one line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Health row
            IconRow(
                currentPoints = state.health.toInt(),
                maxPoints = state.maxHealth.coerceAtLeast(20.0f).toInt(),
                fullIcon = iconProvider?.getIcon(HudIcon.HEART_FULL),
                halfIcon = iconProvider?.getIcon(HudIcon.HEART_HALF),
                emptyIcon = iconProvider?.getIcon(HudIcon.HEART_CONTAINER),
                drawFallbackFull = { drawHeart(filled = true, half = false) },
                drawFallbackHalf = { drawHeart(filled = true, half = true) },
                drawFallbackEmpty = { drawHeart(filled = false, half = false) }
            )

            // Hunger row
            IconRow(
                currentPoints = state.food,
                maxPoints = 20,
                fullIcon = iconProvider?.getIcon(HudIcon.FOOD_FULL),
                halfIcon = iconProvider?.getIcon(HudIcon.FOOD_HALF),
                emptyIcon = iconProvider?.getIcon(HudIcon.FOOD_EMPTY),
                drawFallbackFull = { drawDrumstick(filled = true, half = false) },
                drawFallbackHalf = { drawDrumstick(filled = true, half = true) },
                drawFallbackEmpty = { drawDrumstick(filled = false, half = false) }
            )


        }

        XpBar(
            level = state.xpLevel,
            progress = state.xpProgress,
            backgroundBitmap = iconProvider?.getIcon(HudIcon.EXPERIENCE_BAR_BACKGROUND),
            progressBitmap = iconProvider?.getIcon(HudIcon.EXPERIENCE_BAR_PROGRESS),
            fontSheet = fontSheet
        )

        // Hotbar Row
        HotbarRow(
            slots = state.hotbar,
            selectedIndex = state.selectedSlot,
            backgroundBitmap = iconProvider?.getIcon(HudIcon.HOTBAR_BACKGROUND),
            selectionBitmap = iconProvider?.getIcon(HudIcon.HOTBAR_SELECTION),
            fontSheet = fontSheet,
            onSlotClick = { slot -> hudRepository.sendCommand(slot.toString()) },
            itemIcon = { itemId -> hudRepository.requestIcon(itemId) }
        )
    }
}
@Composable
fun RepeatingTextureBackground(
    texture: ImageBitmap,
    scaleFactor: Float = 16f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val paint = remember(texture, scaleFactor) {
        val matrix = AndroidMatrix().apply {
            postScale(scaleFactor, scaleFactor)
        }

        val imageShader = ImageShader(
            image = texture,
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated
        )

        imageShader.setLocalMatrix(matrix)

        Paint().apply {
            shader = imageShader
            filterQuality = FilterQuality.None
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Draw tiled dirt texture
            drawIntoCanvas { canvas ->
                canvas.drawRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    paint = paint
                )
            }

            // 2. Overlay top black gradient (fades to transparent)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.65f), // Darker at top
                        Color.Transparent                // Fades out entirely
                    ),
                    startY = 0f,
                    endY = size.height * 0.4f // Adjust how far down the shadow extends
                )
            )
        }

        // 3. Render HUD UI on top
        content()
    }
}
@Composable
private fun IconRow(
    currentPoints: Int,
    maxPoints: Int,
    fullIcon: Bitmap?,
    halfIcon: Bitmap?,
    emptyIcon: Bitmap?,
    drawFallbackFull: DrawScope.() -> Unit,
    drawFallbackHalf: DrawScope.() -> Unit,
    drawFallbackEmpty: DrawScope.() -> Unit
) {
    val totalSlots = min(ceil(maxPoints / 2f).toInt(), 10)

    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(totalSlots) { index ->
            val pointsForThisSlot = currentPoints - (index * 2)

            Box(modifier = Modifier.size(25.dp)) {
                if (emptyIcon != null) {
                    Image(
                        bitmap = emptyIcon.asImageBitmap(),
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(25.dp)
                    )
                } else {
                    Canvas(modifier = Modifier.size(25.dp)) {
                        drawFallbackEmpty()
                    }
                }

                when {
                    pointsForThisSlot >= 2 -> {
                        if (fullIcon != null) {
                            Image(
                                bitmap = fullIcon.asImageBitmap(),
                                contentDescription = null,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.size(25.dp)
                            )
                        } else {
                            Canvas(modifier = Modifier.size(25.dp)) {
                                drawFallbackFull()
                            }
                        }
                    }
                    pointsForThisSlot == 1 -> {
                        if (halfIcon != null) {
                            Image(
                                bitmap = halfIcon.asImageBitmap(),
                                contentDescription = null,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.size(25.dp)
                            )
                        } else {
                            Canvas(modifier = Modifier.size(25.dp)) {
                                drawFallbackHalf()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vanilla's XP bar, in texture pixels of the 182x5 sprite pair (see
 * ExperienceBar.renderBar). The progress sprite is drawn as a left-cropped
 * sub-rect of the full-width sprite rather than being squashed, so the
 * bar's end cap stays the right shape as it fills.
 *
 * Vanilla computes the filled width as progress * 183 -- one wider than the
 * sprite -- so a nearly-full bar reaches the end rather than leaving a
 * 1px gap. Kept as-is and clamped.
 */
private const val XP_BAR_WIDTH = 182f
private const val XP_BAR_HEIGHT = 5f
private const val XP_BAR_FILL_WIDTH = 183f

/** Vanilla's XP green (Bar.drawExperienceLevel's -8323296). */
private val XP_LEVEL_COLOR = Color(0xFF80FF20)

@Composable
private fun XpBar(
    level: Int,
    progress: Float,
    backgroundBitmap: Bitmap?,
    progressBitmap: Bitmap?,
    fontSheet: MinecraftFontSheet?
) {
    if (backgroundBitmap == null) {
        // No sprites bundled -- keep a plain drawn bar rather than nothing.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                drawRect(color = Color(0xFF303030))
                drawRect(
                    color = Color(0xFF3DBE3D),
                    size = size.copy(width = size.width * progress.coerceIn(0f, 1f))
                )
            }
            Text("Lv $level", color = Color.White)
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Captured here because the nested Boxes below shadow this scope's
        // maxWidth with their own.
        val availableWidth = maxWidth
        val unit = availableWidth / XP_BAR_WIDTH
        val barHeight = unit * XP_BAR_HEIGHT
        val fillFraction = (progress.coerceIn(0f, 1f) * XP_BAR_FILL_WIDTH / XP_BAR_WIDTH)
            .coerceAtMost(1f)

        // The level number overhangs the bar upwards, so the row has to be
        // tall enough for both. Vanilla puts the text's baseline such that its
        // bottom 2px overlap the bar's top.
        val textPixels = 10f // 8px glyph + 1px outline on each side
        val overlap = 2f
        val totalHeight = unit * (textPixels - overlap + XP_BAR_HEIGHT)

        Box(
            modifier = Modifier.fillMaxWidth().height(totalHeight),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(barHeight)) {
                Image(
                    bitmap = backgroundBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize()
                )

                if (progressBitmap != null && fillFraction > 0f) {
                    // Clip to the filled fraction while drawing the sprite at
                    // full width, reproducing vanilla's sub-rect draw.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .fillMaxHeight()
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = progressBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .width(availableWidth)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Vanilla hides the number at level 0.
            if (level > 0 && fontSheet != null) {
                MinecraftText(
                    text = level.toString(),
                    fontSheet = fontSheet,
                    pixelSize = unit,
                    color = XP_LEVEL_COLOR,
                    style = MinecraftTextStyle.OUTLINE,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * Vanilla's breathing bubbles: 9x9 sprites on an 8px pitch, so each overlaps
 * its neighbour by a pixel. Ten of them represent the full air supply.
 *
 * Only shown while air is below maximum, matching vanilla -- there's no
 * "full row of bubbles" state on screen when you're not underwater.
 *
 * Vanilla also briefly draws `air_empty` sprites as bubbles pop, timed off a
 * per-bubble delay; that animation isn't reproduced here, so an emptied slot
 * simply renders nothing. Same simplification as the enchant glint.
 */
private const val BUBBLE_COUNT = 10
private const val BUBBLE_SPRITE_SIZE = 9f
private const val BUBBLE_PITCH = 8f

@Composable
private fun BubbleRow(
    air: Int,
    maxAir: Int,
    bubbleBitmap: Bitmap?,
    burstingBitmap: Bitmap?,
    bubbleSize: Dp
) {
    if (maxAir <= 0) return

    // Vanilla's own rounding (InGameHud.getAirBubbles): `full` lags by two
    // ticks of air so the last bubble visibly bursts before it disappears.
    val clamped = air.coerceIn(0, maxAir)
    val full = ceil((clamped - 2).toFloat() * BUBBLE_COUNT / maxAir).toInt()
    val throughBursting = ceil(clamped.toFloat() * BUBBLE_COUNT / maxAir).toInt()
    val hasBursting = throughBursting != full

    val unit = bubbleSize / BUBBLE_SPRITE_SIZE

    Box(modifier = Modifier.size(width = unit * (BUBBLE_PITCH * BUBBLE_COUNT + 1f), height = bubbleSize)) {
        for (index in 0 until BUBBLE_COUNT) {
            val position = index + 1
            val bitmap = when {
                position <= full -> bubbleBitmap
                hasBursting && position == throughBursting -> burstingBitmap
                else -> null
            } ?: continue

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .offset(x = unit * BUBBLE_PITCH * index)
                    .size(bubbleSize)
            )
        }
    }
}

private fun DrawScope.drawHeart(filled: Boolean, half: Boolean) {
    val color = if (filled) Color(0xFFD84A3A) else Color(0xFF4A2A26)
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w / 2f, h * 0.85f)
        cubicTo(w * -0.1f, h * 0.5f, w * 0.15f, h * 0.05f, w / 2f, h * 0.3f)
        if (!half) {
            cubicTo(w * 0.85f, h * 0.05f, w * 1.1f, h * 0.5f, w / 2f, h * 0.85f)
        }
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawDrumstick(filled: Boolean, half: Boolean) {
    val color = if (filled) Color(0xFFC98A4B) else Color(0xFF4A3A26)
    drawCircle(color = color, radius = size.minDimension / 2.5f, center = Offset(size.width * 0.4f, size.height * 0.4f))
    if (!half) {
        drawCircle(color = color, radius = size.minDimension / 4f, center = Offset(size.width * 0.75f, size.height * 0.75f))
    }
}

private fun DrawScope.drawShield(filled: Boolean, half: Boolean) {
    val color = if (filled) Color(0xFFB0B8C0) else Color(0xFF3A3E42)
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w / 2f, h * 0.05f)
        lineTo(if (half) w / 2f else w * 0.9f, h * 0.25f)
        if (!half) lineTo(w * 0.9f, h * 0.55f)
        if (!half) cubicTo(w * 0.9f, h * 0.85f, w * 0.7f, h * 0.98f, w / 2f, h * 0.98f)
        cubicTo(w * 0.3f, h * 0.98f, w * 0.1f, h * 0.85f, w * 0.1f, h * 0.55f)
        lineTo(w * 0.1f, h * 0.25f)
        close()
    }
    drawPath(path, color = color)
}