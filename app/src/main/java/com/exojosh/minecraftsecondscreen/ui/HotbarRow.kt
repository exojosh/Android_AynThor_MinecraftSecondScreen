package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.net.HotbarSlot

/**
 * First-pass hotbar rendering. Two things deliberately kept simple here,
 * both worth revisiting later:
 *
 * 1. Item icons: no per-item texture lookup yet (there are hundreds of
 *    possible item IDs vs. the fixed dozen-ish HUD sprites we already
 *    handle) -- empty items render nothing, non-empty items render as a
 *    plain tinted square with the stack count. Swapping in real item
 *    textures later means adding an item-id -> texture-path resolver
 *    (mirroring assets/minecraft/textures/item/<name>.png in a resource
 *    pack) and is a bigger chunk of work than durability/glint.
 *
 * 2. Enchantment glint: vanilla's real glint is an animated diagonal
 *    shimmer using a special texture with additive blending. This draws a
 *    static purple gradient overlay instead -- visually communicates
 *    "enchanted," much less code, no animation loop to manage. Worth
 *    revisiting with a real animated shader if the static version reads as
 *    too subtle in practice.
 */
@Composable
fun HotbarRow(
    slots: List<HotbarSlot>,
    selectedIndex: Int,
    onSlotClick: (Int) -> Unit = {},
    itemIcon: (String) -> Bitmap? = { null }
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEachIndexed { index, slot ->
            HotbarSlotView(
                slot = slot,
                icon = itemIcon(slot.itemId),
                isSelected = index == selectedIndex,
                onClick = { onSlotClick(index + 1) }
            )
        }
    }
}

@Composable
private fun HotbarSlotView(slot: HotbarSlot, icon: Bitmap?, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) Color.White else Color(0xFF555555))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (slot.itemId != "minecraft:air") {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(modifier = Modifier.size(88.dp).border(1.dp, Color(0xFF888888)))
            }

            if (slot.hasGlint) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
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

            if (slot.count > 1) {
                Text(
                    text = slot.count.toString(),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            slot.durabilityFraction?.let { fraction ->
                // Vanilla only shows the bar once an item has taken damage,
                // not on a full-durability item -- matches that here too.
                if (fraction < 1f) {
                    DurabilityBar(fraction, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
private fun DurabilityBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 88.dp, height = 3.dp)) {
        // Vanilla steps through discrete colors (green -> yellow -> red) as
        // durability drops, rather than a smooth gradient -- roughly
        // reproduced here with two thresholds.
        val color = when {
            fraction > 0.5f -> Color(0xFF4CD137)
            fraction > 0.2f -> Color(0xFFE1B12C)
            else -> Color(0xFFE84118)
        }
        drawRoundRect(color = Color(0xFF2A2A2A), cornerRadius = CornerRadius(1f, 1f))
        drawRoundRect(
            color = color,
            size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)),
            cornerRadius = CornerRadius(1f, 1f)
        )
    }
}
