package com.exojosh.minecraftsecondscreen.ui

import android.graphics.Bitmap
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.exojosh.minecraftsecondscreen.net.ContainerState
import com.exojosh.minecraftsecondscreen.net.HotbarSlot
import kotlin.math.floor

/** Gap between the groups of rows (container / equipment / main / hotbar),
 *  which is what makes them read as separate blocks rather than one wall. */
private val GROUP_GAP = 6.dp
private val CELL_GAP = 2.dp
private val SCREEN_PADDING = 4.dp

/** Clearance between the panel's bevel and the controls inside it. */
private val PANEL_INSET = 6.dp
private val CONTROL_BAR_HEIGHT = 40.dp

private val SLOT_BLOCKED = Color(0x55FF4444)
private val DRAG_SOURCE_TINT = Color(0x40FFFFFF)

/** Vanilla's own slot highlight (white at half alpha), reused to show which
 *  slots a distribute drag has painted so far. */
private val DISTRIBUTE_TINT = Color(0x80FFFFFF)

/** Vanilla draws its GUI labels in this grey on the panel; white would glare. */
private val PANEL_LABEL = Color(0xFF404040)

/**
 * How long a finger has to sit still before a drag becomes a *distribute* drag
 * rather than a move.
 *
 * Longer than Compose's own long-press timeout (500ms) would make the gesture
 * feel broken on a handheld; much shorter and an ordinary slow drag turns into
 * a spread. This is the one hidden gesture on this screen, and it can afford to
 * be: unlike a mis-fired half-click it doesn't do the *wrong* move, it does a
 * move the player can see building up slot by slot before they lift.
 */
private const val DISTRIBUTE_HOLD_MS = 300L

/** Test hooks. Drag-to-move can only be checked by actually dragging, and the
 *  gesture handler is on the grid rather than on any cell. */
const val GRID_TEST_TAG = "inventory-grid"

fun slotTestTag(slotIndex: Int) = "inventory-slot-$slotIndex"

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

    var moveMode by remember { mutableStateOf(MoveMode.STACK) }
    val grid = remember(state.syncId, state.slots.size, state.playerStart, state.armorStart) {
        InventoryLayout.compute(state)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING)
            // The whole tab sits on the game's own container panel, so the grid
            // reads as an inventory screen rather than as cells floating on the
            // dirt backdrop the rest of the app tiles.
            .vanillaPanel()
            .padding(PANEL_INSET)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ControlBar(
                state = state,
                moveMode = moveMode,
                fontSheet = fontSheet,
                glintTexture = glintTexture,
                itemIcon = itemIcon,
                onModeChange = { moveMode = it },
                onDropCursor = {
                    val drop = InventoryMoves.dropCursor()
                    onSlotClick(state.syncId, drop.slotId, drop.button, drop.action)
                }
            )

            SlotGrid(
                state = state,
                grid = grid,
                moveMode = moveMode,
                fontSheet = fontSheet,
                glintTexture = glintTexture,
                itemIcon = itemIcon,
                onSlotClick = onSlotClick,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
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
    moveMode: MoveMode,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    onModeChange: (MoveMode) -> Unit,
    onDropCursor: () -> Unit
) {
    val gui = LocalMinecraftGui.current

    Row(
        modifier = Modifier.fillMaxWidth().height(CONTROL_BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MoveMode.entries.forEach { mode ->
            MinecraftButton(
                text = mode.label,
                onClick = { onModeChange(mode) },
                selected = mode == moveMode,
                modifier = Modifier.height(CONTROL_BAR_HEIGHT - 4.dp)
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // Two things share this space because they're never both wanted:
            // the distribute drag is the one hidden gesture on this screen and
            // a line of grey text is what stops it being a secret, but once
            // something is on the cursor, saying so matters more.
            val label = if (state.hasCursorStack) "Holding" else "hold, then drag to spread"
            if (fontSheet != null) {
                MinecraftText(
                    text = label,
                    fontSheet = fontSheet,
                    pixelSize = 1.5.dp,
                    color = PANEL_LABEL,
                    style = MinecraftTextStyle.PLAIN
                )
            } else {
                Text(text = label, color = PANEL_LABEL, fontSize = 10.sp)
            }
        }

        if (state.hasCursorStack) {
            Box(
                modifier = Modifier
                    .size(CONTROL_BAR_HEIGHT - 4.dp)
                    .vanillaSlot(gui.slot)
            ) {
                SlotContents(
                    stack = state.cursor,
                    fontSheet = fontSheet,
                    glintTexture = glintTexture,
                    itemIcon = itemIcon,
                    cellSize = CONTROL_BAR_HEIGHT - 4.dp
                )
            }
            MinecraftButton(
                text = "Drop",
                onClick = onDropCursor,
                modifier = Modifier.height(CONTROL_BAR_HEIGHT - 4.dp)
            )
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
    moveMode: MoveMode,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    onSlotClick: (Int, Int, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val gui = LocalMinecraftGui.current

        // Cells are square and sized to whichever axis runs out first, so the
        // whole grid is always on screen without scrolling -- an inventory you
        // have to scroll to drag across is worse than smaller slots.
        val groupGaps = countGroupGaps(grid)
        val availableHeight = maxHeight - GROUP_GAP * groupGaps - CELL_GAP * (grid.rowCount - 1)
        val cellFromHeight = availableHeight / grid.rowCount
        val cellFromWidth =
            (maxWidth - CELL_GAP * (InventoryLayout.COLUMNS - 1)) / InventoryLayout.COLUMNS
        val cellFit = minOf(cellFromHeight, cellFromWidth).coerceAtLeast(20.dp)

        // Rounded down to a whole number of texture pixels: the slot sprite is
        // 18x18 with a 1px bevel, and at a fractional scale that bevel comes out
        // one pixel wide on some cells and two on others.
        val cell = with(density) {
            val pixels = floor(cellFit.toPx() / SLOT_PIXELS).coerceAtLeast(1f)
            (pixels * SLOT_PIXELS).toDp()
        }

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
        var painted by remember { mutableStateOf(emptySet<Int>()) }

        // The gesture handler outlives the composition that started it, so
        // reading `state` or `moveMode` straight out of the closure would use
        // whatever they were when the pointer input last restarted. The keys
        // below deliberately don't include either -- restarting the handler
        // mid-drag would cancel the drag -- so they're read through these.
        val currentState by rememberUpdatedState(state)
        val currentMode by rememberUpdatedState(moveMode)
        val send by rememberUpdatedState(onSlotClick)

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

                        // Three outcomes race here, and only two of them are
                        // ambiguous: the finger moves (a drag), lifts (a tap),
                        // or does neither for long enough to mean "spread this
                        // stack". withTimeoutOrNull can't distinguish its own
                        // timeout from the lambda returning null, hence the
                        // flag -- a lift and a hold both come back null.
                        //
                        // This is AwaitPointerEventScope's *own* withTimeoutOrNull,
                        // not kotlinx.coroutines': it's driven by the pointer
                        // input frame clock, which is what makes the hold
                        // measurable from a test's virtual clock. Importing the
                        // coroutines one would compile and quietly break the
                        // instrumented test.
                        var lifted = false
                        val slopChange = withTimeoutOrNull(DISTRIBUTE_HOLD_MS) {
                            val change = awaitTouchSlopOrCancellation(down.id) { c, _ ->
                                c.consume()
                            }
                            if (change == null) lifted = true
                            change
                        }

                        val syncId = currentState.syncId
                        fun sendAll(clicks: List<SlotClick>) = clicks.forEach {
                            send(syncId, it.slotId, it.button, it.action)
                        }

                        if (slopChange == null && lifted) {
                            sendAll(
                                InventoryMoves.tap(
                                    mode = currentMode,
                                    holdingCursor = currentState.hasCursorStack,
                                    slot = start
                                )
                            )
                            return@awaitEachGesture
                        }

                        if (slopChange == null) {
                            // Held still: a distribute drag. The whole stack
                            // comes up first, because that's what gets divided,
                            // and the slots the finger paints are collected and
                            // sent as one quick-craft on release -- the same
                            // order vanilla's own HandledScreen uses.
                            if (!currentState.hasCursorStack) {
                                sendAll(listOf(InventoryMoves.pickUp(MoveMode.STACK, start)))
                            }
                            dragFrom = start
                            painted = setOf(start)

                            drag(down.id) { change ->
                                dragPosition = change.position
                                slotAt(change.position)?.let { painted = painted + it }
                                change.consume()
                            }

                            sendAll(InventoryMoves.distribute(currentMode, painted))

                            dragFrom = null
                            painted = emptySet()
                            return@awaitEachGesture
                        }

                        // An ordinary drag. The source is picked up immediately
                        // so the item visibly leaves its slot and follows the
                        // finger -- the game's own state does the moving, we
                        // just asked for the first half of it early. How *much*
                        // comes up is the mode's business.
                        val lifting = !currentState.hasCursorStack
                        if (lifting) {
                            sendAll(listOf(InventoryMoves.pickUp(currentMode, start)))
                        }
                        dragFrom = start
                        dragPosition = slopChange.position

                        drag(down.id) { change ->
                            dragPosition = change.position
                            change.consume()
                        }

                        val target = slotAt(dragPosition)
                        when {
                            target != null && target != start -> sendAll(
                                InventoryMoves.drop(
                                    mode = currentMode,
                                    source = start,
                                    target = target,
                                    targetIsEmpty = currentState.isEmptyAt(target),
                                    targetHasSameItem = currentState.itemsMatch(start, target)
                                )
                            )

                            // Released on the source, or off the grid entirely:
                            // put it back rather than dropping it on the floor,
                            // which is not what a slipped finger meant.
                            lifting -> sendAll(InventoryMoves.returnToSource(start))
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
                            slotTexture = gui.slot,
                            highlight = when {
                                slotIndex in painted -> DISTRIBUTE_TINT
                                dragFrom == slotIndex -> DRAG_SOURCE_TINT
                                else -> null
                            },
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

/** Whether the slot at [index] is empty — an out-of-range index reads as empty
 *  rather than throwing, since it can only come from a stale grid. */
private fun ContainerState.isEmptyAt(index: Int): Boolean {
    val stack = slots.getOrNull(index)?.stack ?: return true
    return stack.itemId == InventoryMoves.AIR_ITEM_ID || stack.count <= 0
}

/** Whether two slots hold the same item — the question [InventoryMoves.drop]
 *  asks to decide whether a single item can be placed or the drag has to fall
 *  back to a swap. */
private fun ContainerState.itemsMatch(a: Int, b: Int): Boolean {
    val first = slots.getOrNull(a)?.stack ?: return false
    val second = slots.getOrNull(b)?.stack ?: return false
    return first.itemId == second.itemId
}

/** How many group boundaries the grid has, so the height budget accounts for
 *  the same gaps the layout will actually insert. */
private fun countGroupGaps(grid: InventoryGrid): Int =
    grid.rows.zipWithNext().count { (a, b) -> a.kind != b.kind }

@Composable
private fun SlotCell(
    stack: HotbarSlot,
    mayPlace: Boolean,
    slotTexture: ImageBitmap?,
    highlight: Color?,
    fontSheet: MinecraftFontSheet?,
    glintTexture: Bitmap?,
    itemIcon: (String) -> Bitmap?,
    cellSize: Dp
) {
    Box(modifier = Modifier.fillMaxSize().vanillaSlot(slotTexture)) {
        SlotContents(stack, fontSheet, glintTexture, itemIcon, cellSize)

        // Drawn over the item rather than under it, the way vanilla's own hover
        // highlight is: a tint behind a full slot would be invisible.
        if (!mayPlace) SlotOverlay(SLOT_BLOCKED)
        if (highlight != null) SlotOverlay(highlight)
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
    if (stack.itemId == InventoryMoves.AIR_ITEM_ID || stack.count <= 0) return

    // Inset by the slot's own 1px bevel, so the 16x16 item sits in the 16x16
    // hole vanilla leaves for it inside an 18x18 cell.
    val inset = cellSize / SLOT_PIXELS
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
