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
 * The 9 hotbar slots, laid out over vanilla's hotbar sprite and scaled to
 * fill whatever width it's given.
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
    fontSheet: MinecraftFontSheet? = null,
    modifier: Modifier = Modifier,
    onSlotClick: (Int) -> Unit = {},
    itemIcon: (String) -> Bitmap? = { null }
) {
    if (backgroundBitmap == null) {
        // No hotbar texture bundled/resolved yet -- fall back to the
        // original per-slot bordered boxes.
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // One logical texture pixel, in dp. Everything below is expressed in
        // those units so the whole strip scales as one piece.
        val unit: Dp = maxWidth / TEXTURE_WIDTH

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
