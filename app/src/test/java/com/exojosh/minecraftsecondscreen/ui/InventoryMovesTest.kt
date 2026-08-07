package com.exojosh.minecraftsecondscreen.ui

import com.exojosh.minecraftsecondscreen.ui.InventoryMoves.OUTSIDE_SLOT
import com.exojosh.minecraftsecondscreen.ui.InventoryMoves.PICKUP
import com.exojosh.minecraftsecondscreen.ui.InventoryMoves.QUICK_CRAFT
import com.exojosh.minecraftsecondscreen.ui.InventoryMoves.QUICK_MOVE
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The amount that ends up at the destination is the whole promise of the mode
 * buttons, and it is invisible until it's wrong on a live world. Every mode's
 * click sequence is pinned here.
 *
 * The vanilla semantics being relied on (right-click takes half or places one;
 * a quick-craft's stage/button packing; the even-split vs. one-each drag
 * buttons) were read out of the 1.21.11 `ScreenHandler`/`HandledScreen`
 * sources, not recalled.
 */
class InventoryMovesTest {

    @Test
    fun `stack mode taps are plain left clicks either way`() {
        assertEquals(
            listOf(SlotClick(9, 0, PICKUP)),
            InventoryMoves.tap(MoveMode.STACK, holdingCursor = false, slot = 9)
        )
        assertEquals(
            listOf(SlotClick(9, 0, PICKUP)),
            InventoryMoves.tap(MoveMode.STACK, holdingCursor = true, slot = 9)
        )
    }

    @Test
    fun `half mode takes half then places all of it`() {
        assertEquals(
            listOf(SlotClick(9, 1, PICKUP)),
            InventoryMoves.tap(MoveMode.HALF, holdingCursor = false, slot = 9)
        )
        assertEquals(
            listOf(SlotClick(12, 0, PICKUP)),
            InventoryMoves.tap(MoveMode.HALF, holdingCursor = true, slot = 12)
        )
    }

    @Test
    fun `single mode takes the stack then deals one item per tap`() {
        // No vanilla click picks up exactly one item, so "single" has to be the
        // mirror of "half": lift everything, place one at a time.
        assertEquals(
            listOf(SlotClick(9, 0, PICKUP)),
            InventoryMoves.tap(MoveMode.SINGLE, holdingCursor = false, slot = 9)
        )
        assertEquals(
            listOf(SlotClick(12, 1, PICKUP)),
            InventoryMoves.tap(MoveMode.SINGLE, holdingCursor = true, slot = 12)
        )
    }

    @Test
    fun `move mode taps shift-click`() {
        assertEquals(
            listOf(SlotClick(9, 0, QUICK_MOVE)),
            InventoryMoves.tap(MoveMode.QUICK, holdingCursor = false, slot = 9)
        )
    }

    @Test
    fun `only half mode picks up a partial stack for a drag`() {
        assertEquals(SlotClick(9, 1, PICKUP), InventoryMoves.pickUp(MoveMode.HALF, 9))
        assertEquals(SlotClick(9, 0, PICKUP), InventoryMoves.pickUp(MoveMode.STACK, 9))
        assertEquals(SlotClick(9, 0, PICKUP), InventoryMoves.pickUp(MoveMode.SINGLE, 9))
        assertEquals(SlotClick(9, 0, PICKUP), InventoryMoves.pickUp(MoveMode.QUICK, 9))
    }

    @Test
    fun `dragging in half mode leaves the held half at the target`() {
        // Half is already on the cursor from pickUp, so one left click puts all
        // of it down -- and the other half never left the source.
        assertEquals(
            listOf(SlotClick(20, 0, PICKUP)),
            InventoryMoves.drop(
                mode = MoveMode.HALF,
                source = 9,
                target = 20,
                targetIsEmpty = true,
                targetHasSameItem = false
            )
        )
    }

    @Test
    fun `dragging in single mode places one and returns the rest`() {
        assertEquals(
            listOf(SlotClick(20, 1, PICKUP), SlotClick(9, 0, PICKUP)),
            InventoryMoves.drop(
                mode = MoveMode.SINGLE,
                source = 9,
                target = 20,
                targetIsEmpty = true,
                targetHasSameItem = false
            )
        )
    }

    @Test
    fun `single onto a stack of the same item still places one`() {
        assertEquals(
            listOf(SlotClick(20, 1, PICKUP), SlotClick(9, 0, PICKUP)),
            InventoryMoves.drop(
                mode = MoveMode.SINGLE,
                source = 9,
                target = 20,
                targetIsEmpty = false,
                targetHasSameItem = true
            )
        )
    }

    @Test
    fun `single onto a different item swaps instead of placing one`() {
        // A right click on an occupied slot swaps rather than placing, and
        // "give the rest back" after a swap would scatter both stacks.
        assertEquals(
            listOf(SlotClick(20, 0, PICKUP)),
            InventoryMoves.drop(
                mode = MoveMode.SINGLE,
                source = 9,
                target = 20,
                targetIsEmpty = false,
                targetHasSameItem = false
            )
        )
    }

    @Test
    fun `quick craft data packs the stage low and the button high`() {
        // ScreenHandler.packQuickCraftData: stage & 3 | (button & 3) << 2.
        assertEquals(0, InventoryMoves.packQuickCraft(0, 0))
        assertEquals(1, InventoryMoves.packQuickCraft(1, 0))
        assertEquals(2, InventoryMoves.packQuickCraft(2, 0))
        assertEquals(4, InventoryMoves.packQuickCraft(0, 1))
        assertEquals(5, InventoryMoves.packQuickCraft(1, 1))
        assertEquals(6, InventoryMoves.packQuickCraft(2, 1))
    }

    @Test
    fun `distribute brackets the painted slots with an open and a commit`() {
        assertEquals(
            listOf(
                SlotClick(OUTSIDE_SLOT, 0, QUICK_CRAFT),
                SlotClick(11, 1, QUICK_CRAFT),
                SlotClick(12, 1, QUICK_CRAFT),
                SlotClick(13, 1, QUICK_CRAFT),
                SlotClick(OUTSIDE_SLOT, 2, QUICK_CRAFT)
            ),
            InventoryMoves.distribute(MoveMode.STACK, listOf(11, 12, 13))
        )
    }

    @Test
    fun `distribute in single mode uses vanilla's right-drag button`() {
        // Button 1 is one item per slot; button 0 is the even split. Both are
        // shifted two bits up, so stage 1 with button 1 is 5, not 1.
        assertEquals(
            listOf(
                SlotClick(OUTSIDE_SLOT, 4, QUICK_CRAFT),
                SlotClick(11, 5, QUICK_CRAFT),
                SlotClick(OUTSIDE_SLOT, 6, QUICK_CRAFT)
            ),
            InventoryMoves.distribute(MoveMode.SINGLE, listOf(11))
        )
    }

    @Test
    fun `half distributes evenly, since half is an amount and not a split`() {
        // Only SINGLE maps to the one-each drag; HALF and MOVE spread evenly,
        // because "spread half of it" isn't a drag vanilla has.
        assertEquals(
            InventoryMoves.distribute(MoveMode.STACK, listOf(1, 2)),
            InventoryMoves.distribute(MoveMode.HALF, listOf(1, 2))
        )
        assertEquals(
            InventoryMoves.distribute(MoveMode.STACK, listOf(1, 2)),
            InventoryMoves.distribute(MoveMode.QUICK, listOf(1, 2))
        )
    }

    @Test
    fun `dropping the cursor clicks outside the window`() {
        assertEquals(SlotClick(OUTSIDE_SLOT, 0, PICKUP), InventoryMoves.dropCursor())
    }
}
