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
import androidx.compose.foundation.layout.Spacer
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
import com.exojosh.minecraftsecondscreen.settings.HudElement
import com.exojosh.minecraftsecondscreen.settings.HudSettings
import kotlin.math.ceil
import kotlin.math.min
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The status rows (armor, hearts, hunger, bubbles) all lay out on this one
 * grid, so they stack in alignment. Vanilla achieves the same thing with 9px
 * sprites on an 8px pitch; this HUD draws them edge to edge instead, so the
 * only thing that matters is that every row uses the same numbers.
 */
private val STATUS_ICON_SIZE = 25.dp
private const val STATUS_ICON_COUNT = 10

/** Fallback colour when no absorbing-heart sprite is available. */
private val ABSORPTION_HEART_COLOR = Color(0xFFE8B62F)

/** Swap-hands. Must match a key in `CommandDispatcher.COMMANDS` on the mod
 *  side, which names actions rather than keyboard keys. */
private const val OFFHAND_SWAP_COMMAND = "SWAP"



/**
 * The player's vital signs: armor/bubbles, hearts/hunger, XP bar, hotbar.
 *
 * Sits at the top of every tab and **wraps its own height** -- the tab's own
 * panel gets whatever is left underneath. It deliberately doesn't paint a
 * background or fill the screen: [com.exojosh.minecraftsecondscreen.SecondScreenApp]
 * owns the tiled backdrop for the whole window, and having this fill too meant
 * the dirt was drawn twice, once over the other.
 *
 * [offhandOverflowsBelow] lets the off-hand box hang past the bottom of that
 * wrapped height instead of extending it, handing the row it would have cost
 * to the panel underneath. The caller decides, because only the caller knows
 * what's in that corner of the panel.
 */
@Composable
fun HudScreen(
    state: HudState?,
    hudRepository: HudRepository,
    settings: HudSettings,
    iconProvider: ResourcePackIconProvider? = null,
    offhandOverflowsBelow: Boolean = false
) {
    if (state == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for game data...", color = Color(0xFFBBBBBB))
        }
        return
    }

    HudContent(
        state = state,
        hudRepository = hudRepository,
        settings = settings,
        iconProvider = iconProvider,
        offhandOverflowsBelow = offhandOverflowsBelow
    )
}

@Composable
fun HudContent(
    state: HudState,
    hudRepository: HudRepository,
    settings: HudSettings,
    iconProvider: ResourcePackIconProvider?,
    offhandOverflowsBelow: Boolean = false
) {
    val fontSheet = rememberMinecraftFont(iconProvider?.getFontSheet())

    val showArmor = settings.isVisible(HudElement.ARMOR)
    val showBubbles = settings.isVisible(HudElement.BUBBLES)
    val showHearts = settings.isVisible(HudElement.HEARTS)
    val showHunger = settings.isVisible(HudElement.HUNGER)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    )
    {
        // Armor and breathing bubbles share one line, mirroring vanilla's
        // stacking: armor sits directly above health on the left, bubbles
        // directly above hunger on the right.
        //
        // The height is pinned to STATUS_ICON_SIZE rather than inherited from
        // whichever child happens to be present. It used to come from the
        // armor icons, which quietly made armor load-bearing for the layout --
        // fine while it was always drawn, but now that it can be switched off
        // the row would collapse and re-expand every time the player dipped
        // underwater, dragging everything below it up and down.
        //
        // The row itself is drawn even when *both* halves are empty, which is
        // why the armor test below is on the icons rather than on the row: an
        // unarmoured player picking up a chestplate should see the sockets
        // appear in place, not see the whole HUD jump up 25dp and the map
        // below it resize. Vanilla gets that for free by anchoring its HUD to
        // the bottom of the screen; this stack is top-anchored, so the space
        // has to be held open deliberately.
        if (showArmor || showBubbles) {
            Row(
                modifier = Modifier.fillMaxWidth().height(STATUS_ICON_SIZE),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Vanilla wraps renderArmor in `if (j > 0)`, so an unarmoured
                // player never sees a row of empty sockets in game -- drawing
                // them here made the second screen show something the top
                // screen wouldn't.
                if (showArmor && state.armor > 0) {
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
                }

                // Holds the gap open so the bubbles stay hard right even when
                // the armor row beside them is switched off -- SpaceBetween
                // pushes a lone child to the *start*, which would slide the
                // bubbles across to the left edge.
                Spacer(modifier = Modifier.weight(1f))

                if (showBubbles && state.isDrowning) {
                    BubbleRow(
                        air = state.air,
                        maxAir = state.maxAir,
                        bubbleBitmap = iconProvider?.getIcon(HudIcon.AIR),
                        burstingBitmap = iconProvider?.getIcon(HudIcon.AIR_BURSTING)
                    )
                }
            }
        }

        // Health and Hunger share one line. Bottom-aligned because absorption
        // stacks extra heart rows *upward*: the hunger row has to stay level
        // with the bottom heart row, not get pushed down or centred against a
        // taller neighbour.
        if (showHearts || showHunger) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                if (showHearts) {
                    HeartRow(
                        health = state.health,
                        maxHealth = state.maxHealth,
                        absorption = state.absorption,
                        fullIcon = iconProvider?.getIcon(HudIcon.HEART_FULL),
                        halfIcon = iconProvider?.getIcon(HudIcon.HEART_HALF),
                        containerIcon = iconProvider?.getIcon(HudIcon.HEART_CONTAINER),
                        absorbFullIcon = iconProvider?.getIcon(HudIcon.HEART_ABSORBING_FULL),
                        absorbHalfIcon = iconProvider?.getIcon(HudIcon.HEART_ABSORBING_HALF)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (showHunger) {
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
            }
        }

        if (settings.isVisible(HudElement.XP_BAR)) {
            XpBar(
                level = state.xpLevel,
                progress = state.xpProgress,
                backgroundBitmap = iconProvider?.getIcon(HudIcon.EXPERIENCE_BAR_BACKGROUND),
                progressBitmap = iconProvider?.getIcon(HudIcon.EXPERIENCE_BAR_PROGRESS),
                fontSheet = fontSheet
            )
        }

        // Hotbar, with the off-hand box on its own line beneath it. The
        // off-hand is drawn by HotbarRow, so hiding the hotbar necessarily
        // hides it too -- said plainly in HudElement.HOTBAR's description
        // rather than left for the player to discover.
        if (settings.isVisible(HudElement.HOTBAR)) {
            HotbarRow(
                slots = state.hotbar,
                selectedIndex = state.selectedSlot,
                backgroundBitmap = iconProvider?.getIcon(HudIcon.HOTBAR_BACKGROUND),
                selectionBitmap = iconProvider?.getIcon(HudIcon.HOTBAR_SELECTION),
                offhandSlot = state.offhand.takeIf { settings.isVisible(HudElement.OFFHAND) },
                offhandBitmap = iconProvider?.getIcon(HudIcon.HOTBAR_OFFHAND),
                offhandOverflowsBelow = offhandOverflowsBelow,
                glintTexture = iconProvider?.getIcon(HudIcon.ENCHANTED_GLINT),
                fontSheet = fontSheet,
                onSlotClick = { slot -> hudRepository.sendCommand(slot.toString()) },
                // Tapping the box trades the held item with whatever is in the
                // off-hand, which is also how you put something *into* an empty
                // off-hand from here.
                onOffhandClick = { hudRepository.sendCommand(OFFHAND_SWAP_COMMAND) },
                itemIcon = { itemId -> hudRepository.requestIcon(itemId) }
            )
        }
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
/**
 * Health, with absorption ("golden") hearts.
 *
 * The slot maths lives in [HeartLayout], away from Compose, so it can be unit
 * tested -- that separation is the point, not incidental tidying. This
 * composable only turns slots into sprites.
 *
 * Rows stack upward the way vanilla's do: [HeartLayout.compute] returns the
 * bottom row first, so the list is reversed for the `Column`. Absorption
 * hearts therefore appear *above* the normal row, and the bottom row stays put
 * (and stays level with the hunger row beside it).
 */
@Composable
private fun HeartRow(
    health: Float,
    maxHealth: Float,
    absorption: Float,
    fullIcon: Bitmap?,
    halfIcon: Bitmap?,
    containerIcon: Bitmap?,
    absorbFullIcon: Bitmap?,
    absorbHalfIcon: Bitmap?,
    iconSize: Dp = STATUS_ICON_SIZE
) {
    val rows = HeartLayout.compute(health = health, maxHealth = maxHealth, absorption = absorption)

    Column(horizontalAlignment = Alignment.Start) {
        // Bottom-first from the layout, top-first for the Column.
        rows.asReversed().forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                row.forEach { slot ->
                    Box(modifier = Modifier.size(iconSize)) {
                        if (containerIcon != null) {
                            PixelIcon(containerIcon, iconSize)
                        } else {
                            Canvas(modifier = Modifier.size(iconSize)) {
                                drawHeart(filled = false, half = false)
                            }
                        }

                        if (slot.fill != HeartFill.EMPTY) {
                            val isHalf = slot.fill == HeartFill.HALF
                            val icon = when {
                                slot.absorption && isHalf -> absorbHalfIcon
                                slot.absorption -> absorbFullIcon
                                isHalf -> halfIcon
                                else -> fullIcon
                            }
                            if (icon != null) {
                                PixelIcon(icon, iconSize)
                            } else {
                                val fallbackColor =
                                    if (slot.absorption) ABSORPTION_HEART_COLOR else Color(0xFFD84A3A)
                                Canvas(modifier = Modifier.size(iconSize)) {
                                    drawHeart(filled = true, half = isHalf, color = fallbackColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixelIcon(bitmap: Bitmap, size: Dp) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        filterQuality = FilterQuality.None,
        modifier = Modifier.size(size)
    )
}

/**
 * Armor and hunger: a flat run of half/full slots with no wrapping.
 *
 * Sized off [STATUS_ICON_SIZE]/[STATUS_ICON_COUNT] rather than its own
 * literals. Those constants exist so every status row lands on the same grid
 * and the rows stack in alignment; hardcoding 25.dp and 10 here quietly opted
 * this row out of the invariant it was supposed to be following.
 */
@Composable
private fun IconRow(
    currentPoints: Int,
    maxPoints: Int,
    fullIcon: Bitmap?,
    halfIcon: Bitmap?,
    emptyIcon: Bitmap?,
    drawFallbackFull: DrawScope.() -> Unit,
    drawFallbackHalf: DrawScope.() -> Unit,
    drawFallbackEmpty: DrawScope.() -> Unit,
    iconSize: Dp = STATUS_ICON_SIZE
) {
    val totalSlots = min(ceil(maxPoints / 2f).toInt(), STATUS_ICON_COUNT)

    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(totalSlots) { index ->
            val pointsForThisSlot = currentPoints - (index * 2)

            Box(modifier = Modifier.size(iconSize)) {
                if (emptyIcon != null) {
                    PixelIcon(emptyIcon, iconSize)
                } else {
                    Canvas(modifier = Modifier.size(iconSize)) { drawFallbackEmpty() }
                }

                val overlay: Bitmap? = when {
                    pointsForThisSlot >= 2 -> fullIcon
                    pointsForThisSlot == 1 -> halfIcon
                    else -> null
                }
                val fallback: (DrawScope.() -> Unit)? = when {
                    pointsForThisSlot >= 2 -> drawFallbackFull
                    pointsForThisSlot == 1 -> drawFallbackHalf
                    else -> null
                }

                if (overlay != null) {
                    PixelIcon(overlay, iconSize)
                } else if (fallback != null) {
                    Canvas(modifier = Modifier.size(iconSize)) { fallback() }
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
        val unit = maxWidth / XP_BAR_WIDTH
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
                    // Crop the *source* to the filled fraction and draw it
                    // into the same fraction of the destination, so the
                    // sprite stays at 1:1 scale and its internal segment
                    // boundaries line up with the unfilled bar behind it.
                    //
                    // Doing this by clipping a full-width Image doesn't work:
                    // Modifier.width() is only a preferred size, so a parent
                    // narrowed to the fill fraction squeezes the Image and
                    // ContentScale.FillBounds stretches the bitmap into it.
                    val progressImage = progressBitmap.asImageBitmap()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val srcWidth = (progressImage.width * fillFraction)
                            .roundToInt().coerceIn(1, progressImage.width)
                        val dstWidth = (size.width * fillFraction)
                            .roundToInt().coerceAtLeast(1)

                        drawImage(
                            image = progressImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(srcWidth, progressImage.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(dstWidth, size.height.roundToInt()),
                            filterQuality = FilterQuality.None
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
 * Vanilla's breathing bubbles, following `renderAirBubbles`.
 *
 * **Ordering:** vanilla positions bubble `n` (1-based) at
 * `left - (n - 1) * 8 - 9`, so n=1 is the *rightmost*, and full bubbles are
 * those with `n <= k`. As air drops, the highest-numbered bubble goes first
 * -- meaning the row empties from its **left** end inward, not the right.
 * Laying these out in naive left-to-right index order gets that backwards,
 * which is why the index is mirrored below.
 *
 * **Width:** this uses the same fixed grid as the hunger/health/armor rows
 * ([STATUS_ICON_COUNT] slots of [STATUS_ICON_SIZE]) rather than vanilla's
 * 9px-sprite-on-8px-pitch overlap, so the bubble row lines up exactly with
 * the hunger row it sits above. Vanilla gets that alignment for free because
 * *all* its status rows share the 8px pitch; this HUD's rows don't overlap,
 * so bubbles have to follow the same grid to match.
 *
 * Only shown while air is below maximum, matching vanilla -- there's no
 * "full row of bubbles" state on screen when you're not underwater. Vanilla
 * also briefly draws `air_empty` sprites as bubbles pop, timed off a
 * per-bubble delay; that animation isn't reproduced, so an emptied slot
 * renders nothing. Same simplification as the enchant glint.
 */
@Composable
private fun BubbleRow(
    air: Int,
    maxAir: Int,
    bubbleBitmap: Bitmap?,
    burstingBitmap: Bitmap?,
    bubbleSize: Dp = STATUS_ICON_SIZE
) {
    if (maxAir <= 0) return

    val counts = BubbleLayout.compute(air = air, maxAir = maxAir)

    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(STATUS_ICON_COUNT) { index ->
            // Mirror: leftmost cell holds the highest-numbered bubble, so
            // the row empties from the left as vanilla's does.
            val position = STATUS_ICON_COUNT - index

            val bitmap = when {
                position <= counts.full -> bubbleBitmap
                counts.hasBursting && position == counts.throughBursting -> burstingBitmap
                else -> null
            }

            Box(modifier = Modifier.size(bubbleSize)) {
                if (bitmap != null) PixelIcon(bitmap, bubbleSize)
            }
        }
    }
}

private fun DrawScope.drawHeart(
    filled: Boolean,
    half: Boolean,
    color: Color = if (filled) Color(0xFFD84A3A) else Color(0xFF4A2A26)
) {
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