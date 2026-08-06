package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.exojosh.minecraftsecondscreen.net.ContainerState
import com.exojosh.minecraftsecondscreen.net.HotbarSlot

/** Gap between the groups of rows (container / equipment / main / hotbar),
 *  which is what makes them read as separate blocks rather than one wall. */
private val GROUP_GAP = 6.dp
private val CELL_GAP = 2.dp
private val SCREEN_PADDING = 6.dp
private val CONTROL_BAR_HEIGHT = 44.dp

private val SLOT_BACKGROUND = Color(0x66000000)
private val SLOT_BORDER = Color(0xFF5A5A5A)
private val SLOT_BLOCKED = Color(0x55FF4444)
private val HOTBAR_BORDER = Color(0xFF9A9A9A)
private val DRAG_SOURCE_TINT = Color(0x33FFFFFF)
private val MODE_ACTIVE = Color(0xFF6750A4)
private val MODE_IDLE = Color(0xFF3A3A44)

/** Test hooks. Drag-to-move can only be checked by actually dragging, and the
 *  gesture handler is on the grid rather than on any cell. */
const val GRID_TEST_TAG = "inventory-grid"

fun slotTestTag(slotIndex: Int) = "inventory-slot-$slotIndex"

/**
 * How a tap is sent to the server. Drag is always a plain two-step `PICKUP`
 * regardless — dragging a stack somewhere means "move this there", and nothing
 * else.
 *
 * These are modes rather than gestures on purpose. Long-press and two-finger
 * taps are the obvious mappings for half-stack and shift-click, but they have
 * to be raced against the drag detector, and losing that race silently does the
 * *wrong move* to a real inventory. A visible mode you can see the state of
 * beats a hidden gesture you have to trust.
 */
private enum class TapMode(val label: String) {
    /** Vanilla's plain left click: take all, place all, or swap. */
    TAKE_ALL("Whole"),

    /** Vanilla's right click: take half, or place one. `PICKUP` with button 1. */
    TAKE_HALF("Half"),

    /** Vanilla's shift-click: send the stack to the other half of the screen. */
    QUICK_MOVE("Move")
}

/**
 * The Items tab: the open screen handler, with tap and drag to move items.
 *
 * With no container open this is the player's own inventory, because the mod
 * streams `currentScreenHandler` either way. A move is not a write to the
 * inventory — it's a click on a slot, which is the only thing the server will
 * honour, so every interaction here goes back as a `SLOT:` click and the
 * resulting state comes back from the game rather than being predicted locally.
 * Nothing is optimistically applied: what's drawn is always what the game says.
 *
 * The status stack is hidden on this tab (see `SecondScreenApp`) — six rows of
 * slots don't fit under it, and the inventory is a thing you look at
 * deliberately rather than glance at mid-fight.
 */
@Composable
fun InventoryScreen(
    state: ContainerState?,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    isConnected: Boolean,
    itemIcon: (String) -> Bitmap?,
    onSlotClick: (syncId: Int, slotId: Int, button: Int, action: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isConnected) "Waiting for inventory…" else "Not connected.",
                color = Color(0xFF8A8A8A)
            )
        }
        return
    }

    var tapMode by remember { mutableStateOf(TapMode.TAKE_ALL) }
    val grid = remember(state.syncId, state.slots.size, state.playerStart, state.armorStart) {
        InventoryLayout.compute(state)
    }

    Column(modifier = modifier.fillMaxSize().padding(SCREEN_PADDING)) {
        ControlBar(
            state = state,
            tapMode = tapMode,
            fontSheet = fontSheet,
            glintTexture = glintTexture,
            itemIcon = itemIcon,
            onModeChange = { tapMode = it },
            onDropCursor = {
                // -999 is vanilla's "clicked outside the window", which throws
                // the held stack on the floor. The only way to put down
                // something that has nowhere to go.
                onSlotClick(state.syncId, -999, 0, "PICKUP")
            }
        )

        SlotGrid(
            state = state,
            grid = grid,
            tapMode = tapMode,
            fontSheet = fontSheet,
            glintTexture = glintTexture,
            itemIcon = itemIcon,
            onSlotClick = onSlotClick,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

/**
 * Mode buttons, plus whatever is currently on the cursor.
 *
 * The cursor readout is not decoration. A half-finished move — an item picked
 * up and not yet placed — is invisible otherwise, and the player is left
 * wondering where their diamonds went.
 */
@Composable
private fun ControlBar(
    state: ContainerState,
    tapMode: TapMode,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    onModeChange: (TapMode) -> Unit,
    onDropCursor: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(CONTROL_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TapMode.entries.forEach { mode ->
            Button(
                onClick = { onModeChange(mode) },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == tapMode) MODE_ACTIVE else MODE_IDLE,
                    contentColor = Color.White
                )
            ) {
                Text(text = mode.label, fontSize = 12.sp, maxLines = 1)
            }
        }

        Box(modifier = Modifier.weight(1f))

        if (state.hasCursorStack) {
            Text(text = "Holding", color = Color(0xFFBBBBBB), fontSize = 11.sp)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(SLOT_BACKGROUND, RoundedCornerShape(3.dp))
                    .border(1.dp, HOTBAR_BORDER, RoundedCornerShape(3.dp))
            ) {
                SlotContents(
                    stack = state.cursor,
                    fontSheet = fontSheet,
                    glintTexture = glintTexture,
                    itemIcon = itemIcon,
                    cellSize = 36.dp
                )
            }
            Button(
                onClick = onDropCursor,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MODE_IDLE,
                    contentColor = Color.White
                )
            ) {
                Text(text = "Drop", fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/**
 * The grid, and the one gesture handler that drives it.
 *
 * **Hit testing is arithmetic, not per-slot pointer input.** Every cell is at a
 * known offset, so a drag can be tracked across the whole grid by one handler
 * that converts a position into a slot index. Giving each slot its own
 * `pointerInput` would make a drag stop existing the moment the finger left the
 * cell it started in, which is exactly the gesture we need.
 */
@Composable
private fun SlotGrid(
    state: ContainerState,
    grid: InventoryGrid,
    tapMode: TapMode,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    onSlotClick: (Int, Int, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current

        // Cells are square and sized to whichever axis runs out first, so the
        // whole grid is always on screen without scrolling -- an inventory you
        // have to scroll to drag across is worse than smaller slots.
        val groupGaps = countGroupGaps(grid)
        val availableHeight = maxHeight - GROUP_GAP * groupGaps - CELL_GAP * (grid.rowCount - 1)
        val cellFromHeight = availableHeight / grid.rowCount
        val cellFromWidth =
            (maxWidth - CELL_GAP * (InventoryLayout.COLUMNS - 1)) / InventoryLayout.COLUMNS
        val cell = minOf(cellFromHeight, cellFromWidth).coerceAtLeast(20.dp)

        val cellPx = with(density) { cell.toPx() }
        val cellGapPx = with(density) { CELL_GAP.toPx() }
        val groupGapPx = with(density) { GROUP_GAP.toPx() }

        // Row tops in pixels, so hit testing doesn't have to re-derive the
        // group gaps it laid out with.
        val rowTops = remember(grid, cellPx, cellGapPx, groupGapPx) {
            var y = 0f
            grid.rows.mapIndexed { index, row ->
                if (index > 0) {
                    y += cellPx + cellGapPx
                    if (row.kind != grid.rows[index - 1].kind) y += groupGapPx
                }
                y
            }
        }

        fun slotAt(position: Offset): Int? {
            val rowIndex = rowTops.indexOfLast { position.y >= it && position.y < it + cellPx }
            if (rowIndex < 0) return null
            val column = ((position.x + cellGapPx / 2) / (cellPx + cellGapPx)).toInt()
            if (column !in 0 until InventoryLayout.COLUMNS) return null
            return grid.slotAt(rowIndex, column)
        }

        var dragFrom by remember { mutableStateOf<Int?>(null) }
        var dragPosition by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Tagged so the instrumented drag test can aim at the grid as a
                // whole -- the gesture handler lives here, not on the cells.
                .testTag(GRID_TEST_TAG)
                .pointerInput(state.syncId, grid, cellPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = slotAt(down.position) ?: return@awaitEachGesture

                        dragPosition = down.position

                        // Slop first: below it this is a tap, above it a drag.
                        // Racing a long-press timer in here as well is what
                        // makes touch inventories send the wrong move, so the
                        // other actions are modes instead (see TapMode).
                        val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }

                        if (slopChange == null) {
                            // Pointer went up (or was cancelled) without moving.
                            onSlotClick(
                                state.syncId,
                                start,
                                if (tapMode == TapMode.TAKE_HALF) 1 else 0,
                                if (tapMode == TapMode.QUICK_MOVE) "QUICK_MOVE" else "PICKUP"
                            )
                            return@awaitEachGesture
                        }

                        // A drag picks the source up immediately, so the item
                        // visibly leaves its slot and follows the finger -- the
                        // game's own state does the moving, we just asked for
                        // the first half of it early.
                        if (!state.hasCursorStack) {
                            onSlotClick(state.syncId, start, 0, "PICKUP")
                        }
                        dragFrom = start
                        dragPosition = slopChange.position

                        drag(down.id) { change ->
                            dragPosition = change.position
                            change.consume()
                        }

                        val target = slotAt(dragPosition)
                        if (target != null && target != start) {
                            onSlotClick(state.syncId, target, 0, "PICKUP")
                        } else if (target == null) {
                            // Released off the grid: put it back where it came
                            // from rather than dropping it on the floor, which
                            // is not what a slipped finger meant.
                            onSlotClick(state.syncId, start, 0, "PICKUP")
                        }
                        dragFrom = null
                    }
                }
        ) {
            grid.rows.forEachIndexed { rowIndex, row ->
                row.cells.forEachIndexed { column, slotIndex ->
                    if (slotIndex == null) return@forEachIndexed
                    val slot = state.slots.getOrNull(slotIndex) ?: return@forEachIndexed

                    Box(
                        modifier = Modifier
                            .offset(
                                x = (cell + CELL_GAP) * column,
                                y = with(density) { rowTops[rowIndex].toDp() }
                            )
                            .size(cell)
                            // Cells carry no pointer input of their own; the tag
                            // is only so a test can read back where a slot ended
                            // up rather than recomputing the layout.
                            .testTag(slotTestTag(slotIndex))
                    ) {
                        SlotCell(
                            stack = slot.stack,
                            mayPlace = slot.mayPlace,
                            isHotbar = row.kind == InventoryRowKind.HOTBAR,
                            isDragSource = dragFrom == slotIndex,
                            fontSheet = fontSheet,
                            glintTexture = glintTexture,
                            itemIcon = itemIcon,
                            cellSize = cell
                        )
                    }
                }
            }

            // The dragged stack, under the finger. Drawn last and raised so it
            // passes over the slots rather than disappearing behind them.
            if (dragFrom != null && state.hasCursorStack) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .offset(
                            x = with(density) { (dragPosition.x - cellPx / 2).toDp() },
                            y = with(density) { (dragPosition.y - cellPx / 2).toDp() }
                        )
                        .size(cell)
                ) {
                    SlotContents(
                        stack = state.cursor,
                        fontSheet = fontSheet,
                        glintTexture = glintTexture,
                        itemIcon = itemIcon,
                        cellSize = cell
                    )
                }
            }
        }
    }
}

/** How many group boundaries the grid has, so the height budget accounts for
 *  the same gaps the layout will actually insert. */
private fun countGroupGaps(grid: InventoryGrid): Int =
    grid.rows.zipWithNext().count { (a, b) -> a.kind != b.kind }

@Composable
private fun SlotCell(
    stack: HotbarSlot,
    mayPlace: Boolean,
    isHotbar: Boolean,
    isDragSource: Boolean,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    cellSize: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDragSource) DRAG_SOURCE_TINT else SLOT_BACKGROUND, RoundedCornerShape(3.dp))
            .border(
                width = 1.dp,
                // The hotbar is outlined brighter so the row you're actually
                // holding is findable at a glance -- it's the one group whose
                // contents you're about to use rather than store.
                color = when {
                    !mayPlace -> SLOT_BLOCKED
                    isHotbar -> HOTBAR_BORDER
                    else -> SLOT_BORDER
                },
                shape = RoundedCornerShape(3.dp)
            )
    ) {
        SlotContents(stack, fontSheet, glintTexture, itemIcon, cellSize)
    }
}

/**
 * One stack drawn inside a cell: icon, glint, durability, count.
 *
 * Deliberately the same decorations, in the same order, as a hotbar slot —
 * they're the same thing on screen and a player shouldn't have to re-learn
 * them one tab over.
 */
@Composable
private fun SlotContents(
    stack: HotbarSlot,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    cellSize: Dp
) {
    if (stack.itemId == "minecraft:air" || stack.count <= 0) return

    // Inset so the icon sits inside the cell's border the way vanilla's 16x16
    // item sits inside its 18x18 slot, rather than touching the edges.
    val inset = cellSize * 0.1f
    val itemBox = cellSize - inset * 2

    Box(modifier = Modifier.fillMaxSize().padding(inset)) {
        ItemStackGraphic(
            stack = stack,
            icon = itemIcon(stack.itemId),
            fontSheet = fontSheet,
            glintTexture = glintTexture,
            unit = itemBox / ITEM_AREA_PIXELS
        )
    }
}
