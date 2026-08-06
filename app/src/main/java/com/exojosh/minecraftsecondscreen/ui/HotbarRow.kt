package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.net.HotbarSlot

/**
 * Vanilla's hotbar geometry, in texture pixels of the 182x22 hotbar sprite.
 * Taken from InGameHud.renderHotbar rather than eyeballed, because the slot
 * cells are painted into the background texture -- if our slot positions
 * don't match these exactly, icons sit visibly off-centre in their boxes.
 *
 * The texture is always 182x22 *logically* regardless of the file's real
 * resolution (a hi-res pack ships 364x44, 728x88, ...), so everything here
 * scales cleanly: pick a dp-per-logical-pixel `unit` and multiply.
 *
 *   background  0,0    182x22
 *   slot cell   1+20i, 1     20x20   (9 cells, 1px border each side)
 *   item icon   3+20i, 3     16x16   (centred in the cell)
 *   selection  -1+20i, -1    24x23   (deliberately overhangs the strip)
 */
private const val TEXTURE_WIDTH = 182f
private const val TEXTURE_HEIGHT = 22f
private const val SLOT_PITCH = 20f
private const val SLOT_INSET = 1f
private const val ICON_SIZE = 16f
private const val SELECTION_OFFSET = -1f
private const val SELECTION_WIDTH = 24f
private const val SELECTION_HEIGHT = 23f

/** Durability bar: 13x2 at +2,+13 within the 16x16 item, per drawItemBar. */
private const val BAR_INSET_X = 2f
private const val BAR_INSET_Y = 13f
private const val BAR_WIDTH = 13f
private const val BAR_HEIGHT = 2f

/**
 * The off-hand box, measured off vanilla's own `hotbar_offhand_right.png`
 * rather than recalled: 29x24, with its 20x20 cell at (8,2) and the 16x16 item
 * area at (10,4). The sprite is 29 wide because its *left* 8 columns are empty
 * -- vanilla's spacer between the box and the hotbar strip it normally sits
 * beside.
 *
 * **The right-hand variant is deliberate, and so is the placement.** Vanilla
 * hangs this box off the side of the hotbar (left variant for a right-handed
 * player) at the same height as the strip. Here it goes on its own line
 * *below* the hotbar, pushed to the right edge, because this is a touch
 * screen: the hotbar keeps the full screen width it reads best at, and the
 * off-hand lands under the thumb instead of across the device. Right-aligning
 * the right variant puts its cell flush with the screen edge, since the blank
 * columns fall on the inside.
 *
 * Vanilla only draws this box when the off-hand actually holds something. The
 * second screen keeps it on screen empty too, because here it isn't just a
 * readout -- it's the tap target that swaps the held item into the off-hand.
 */
private const val OFFHAND_WIDTH = 29f
private const val OFFHAND_HEIGHT = 24f
private const val OFFHAND_CELL_INSET_X = 8f
private const val OFFHAND_CELL_INSET_Y = 2f

/**
 * Measures and places the off-hand box at full size but reports **zero height**
 * to the parent, so it hangs below the hotbar without pushing anything down.
 *
 * The box is a whole extra line -- 24 of the hotbar's own 22 logical pixels --
 * and it's a small cell hard against the right edge, so charging the entire
 * width of the layout for it wastes a band of screen that is empty either side
 * of it. Whatever sits under the hotbar gets that band back and the box floats
 * over it.
 *
 * Only safe where the panel underneath has nothing in that corner: see
 * [com.exojosh.minecraftsecondscreen.SecondScreenApp], which enables it for the
 * map (square, centred, nowhere near the right edge) and not for the tabs whose
 * content runs the full width. Compose neither clips the overflow nor lets the
 * panel steal the box's taps -- the panel draws nothing there and has no
 * pointer input of its own, so hit testing falls through to the box.
 */
private fun Modifier.hangingBelow(enabled: Boolean): Modifier =
    if (!enabled) this else layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, 0) { placeable.place(0, 0) }
    }

/**
 * The 9 hotbar slots, laid out over vanilla's hotbar sprite and scaled to fill
 * whatever width it's given, with the off-hand box on its own line underneath.
 *
 * The strip always gets the full width -- [offhandSlot] adds a row rather than
 * stealing horizontal space, so the slots stay as large as they've ever been.
 *
 * [offhandOverflowsBelow] makes that extra row cost no layout height; see
 * [hangingBelow] for when that's safe.
 *
 * Enchantment glint is a static translucent overlay rather than vanilla's
 * animated diagonal shimmer -- visually communicates "enchanted" with far
 * less machinery and no animation loop. Worth revisiting with a real shader
 * if it reads as too subtle in practice.
 */
@Composable
fun HotbarRow(
    slots: List<HotbarSlot>,
    selectedIndex: Int,
    backgroundBitmap: Bitmap? = null,
    selectionBitmap: Bitmap? = null,
    offhandSlot: HotbarSlot? = null,
    offhandBitmap: Bitmap? = null,
    offhandOverflowsBelow: Boolean = false,
    fontSheet: MinecraftFontSheet? = null,
    modifier: Modifier = Modifier,
    onSlotClick: (Int) -> Unit = {},
    onOffhandClick: () -> Unit = {},
    itemIcon: (String) -> Bitmap? = { null }
) {
    if (backgroundBitmap == null) {
        // No hotbar texture bundled/resolved yet -- fall back to the
        // original per-slot bordered boxes.
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.forEachIndexed { index, slot ->
                    HotbarSlotView(
                        slot = slot,
                        icon = itemIcon(slot.itemId),
                        isSelected = index == selectedIndex,
                        fontSheet = fontSheet,
                        onClick = { onSlotClick(index + 1) }
                    )
                }
            }
            if (offhandSlot != null) {
                Box(modifier = Modifier.hangingBelow(offhandOverflowsBelow)) {
                    HotbarSlotView(
                        slot = offhandSlot,
                        icon = itemIcon(offhandSlot.itemId),
                        isSelected = false,
                        fontSheet = fontSheet,
                        onClick = onOffhandClick
                    )
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // One logical texture pixel, in dp. Everything below is expressed in
        // those units so the strip and the off-hand box scale together.
        val unit: Dp = maxWidth / TEXTURE_WIDTH

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(unit * TEXTURE_HEIGHT)
            ) {
                Image(
                    bitmap = backgroundBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize()
                )

                if (selectionBitmap != null && selectedIndex in slots.indices) {
                    Image(
                        bitmap = selectionBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .offset(
                                x = unit * (SELECTION_OFFSET + SLOT_PITCH * selectedIndex),
                                y = unit * SELECTION_OFFSET
                            )
                            .size(width = unit * SELECTION_WIDTH, height = unit * SELECTION_HEIGHT)
                    )
                }

                slots.forEachIndexed { index, slot ->
                    // Tap target is the full 20x20 cell, not just the 16x16 icon.
                    Box(
                        modifier = Modifier
                            .offset(
                                x = unit * (SLOT_INSET + SLOT_PITCH * index),
                                y = unit * SLOT_INSET
                            )
                            .size(unit * SLOT_PITCH)
                            .clickable { onSlotClick(index + 1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(unit * ICON_SIZE)) {
                            HotbarSlotContent(
                                slot = slot,
                                icon = itemIcon(slot.itemId),
                                fontSheet = fontSheet,
                                unit = unit
                            )
                        }
                    }
                }
            }

            // Own line, hard right. The off-hand box is drawn whenever the mod
            // reports an off-hand at all, sprite or no sprite -- without one it
            // falls back to a plain bordered cell in the same footprint, so the
            // tap target never silently vanishes.
            if (offhandSlot != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .hangingBelow(offhandOverflowsBelow)
                        .size(width = unit * OFFHAND_WIDTH, height = unit * OFFHAND_HEIGHT)
                ) {
                    OffhandBox(
                        slot = offhandSlot,
                        icon = itemIcon(offhandSlot.itemId),
                        boxBitmap = offhandBitmap,
                        fontSheet = fontSheet,
                        unit = unit,
                        onClick = onOffhandClick
                    )
                }
            }
        }
    }
}

/**
 * The off-hand box, filling the [Box] it's given (29x24 logical pixels).
 *
 * Tapping it sends swap-hands, so the currently held item and whatever is in
 * the off-hand trade places -- which is also the only way to *fill* an empty
 * off-hand from the second screen, hence drawing the box even when it's empty.
 */
@Composable
private fun BoxScope.OffhandBox(
    slot: HotbarSlot,
    icon: Bitmap?,
    boxBitmap: Bitmap?,
    fontSheet: MinecraftFontSheet?,
    unit: Dp,
    onClick: () -> Unit
) {
    if (boxBitmap != null) {
        Image(
            bitmap = boxBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(
        modifier = Modifier
            .offset(x = unit * OFFHAND_CELL_INSET_X, y = unit * OFFHAND_CELL_INSET_Y)
            .size(unit * SLOT_PITCH)
            .then(
                // Only when there's no sprite -- otherwise the texture already
                // draws the cell and a border on top of it would double up.
                if (boxBitmap == null) {
                    Modifier.border(width = 1.dp, color = Color(0xFF555555))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(unit * ICON_SIZE)) {
            HotbarSlotContent(slot = slot, icon = icon, fontSheet = fontSheet, unit = unit)
        }
    }
}

@Composable
private fun HotbarSlotView(
    slot: HotbarSlot,
    icon: Bitmap?,
    isSelected: Boolean,
    fontSheet: MinecraftFontSheet?,
    onClick: () -> Unit
) {
    val size = 56.dp
    Box(
        modifier = Modifier
            .size(size)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color(0xFF555555)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(size * 0.75f)) {
            HotbarSlotContent(
                slot = slot,
                icon = icon,
                fontSheet = fontSheet,
                unit = size * 0.75f / ICON_SIZE
            )
        }
    }
}

/**
 * Contents of one slot, drawn inside a box exactly [ICON_SIZE] * [unit]
 * square -- i.e. the box maps 1:1 onto vanilla's 16x16 item area, so
 * decorations can use vanilla's own pixel offsets.
 */
@Composable
private fun BoxScope.HotbarSlotContent(
    slot: HotbarSlot,
    icon: Bitmap?,
    fontSheet: MinecraftFontSheet?,
    unit: Dp
) {
    if (slot.itemId == "minecraft:air") return

    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (slot.hasGlint) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x00FFFFFF),
                            Color(0x559B6BFF), // translucent purple, matches vanilla's
                            Color(0x00FFFFFF)  // enchant-glint hue without animating it
                        )
                    )
                )
        )
    }

    slot.durabilityFraction?.let { fraction ->
        // Vanilla only shows the bar once an item has taken damage, not on a
        // full-durability item -- matches that here too.
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
    if (slot.count > 1) {
        val countModifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = unit, y = unit)

        if (fontSheet != null) {
            MinecraftText(
                text = slot.count.toString(),
                fontSheet = fontSheet,
                pixelSize = unit,
                modifier = countModifier
            )
        } else {
            // No font sheet bundled -- keep a system-font label rather than
            // dropping the count entirely.
            Text(
                text = slot.count.toString(),
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
        // Vanilla steps through discrete colors (green -> yellow -> red) as
        // durability drops, rather than a smooth gradient -- roughly
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
