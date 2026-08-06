package com.exojosh.minecraftsecondscreen.ui

import com.exojosh.minecraftsecondscreen.net.ContainerSlot
import com.exojosh.minecraftsecondscreen.net.ContainerState
import com.exojosh.minecraftsecondscreen.net.HotbarSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong slot index here doesn't crash — it sends a click at the wrong slot,
 * which on a live world means an item moving somewhere the player never asked
 * for. That's why this maths is kept away from Compose and pinned here.
 *
 * The vanilla index roles being asserted (crafting 0-4, armor 5-8, main 9-35,
 * hotbar 36-44, off-hand 45) are `PlayerScreenHandler`'s own constants, which
 * the mod sends across rather than either side restating.
 */
class InventoryLayoutTest {

    private fun slots(n: Int) = List(n) {
        ContainerSlot(HotbarSlot("minecraft:air", 0, 0, 0, false), mayPlace = true)
    }

    /** The player's own inventory handler: 46 slots with vanilla's roles. */
    private fun playerInventory() = ContainerState(
        syncId = 0,
        handlerType = null,
        cursor = HotbarSlot("minecraft:air", 0, 0, 0, false),
        slots = slots(46),
        playerStart = 9,
        hotbarStart = 36,
        armorStart = 5,
        offhandIndex = 45
    )

    /** A double chest: 54 container slots then the player's 36. */
    private fun doubleChest() = ContainerState(
        syncId = 3,
        handlerType = "minecraft:generic_9x6",
        cursor = HotbarSlot("minecraft:air", 0, 0, 0, false),
        slots = slots(90),
        playerStart = 54,
        hotbarStart = 81,
        armorStart = -1,
        offhandIndex = -1
    )

    private fun InventoryGrid.allSlots(): List<Int> = rows.flatMap { it.cells.filterNotNull() }

    @Test
    fun `player inventory lays out every slot exactly once`() {
        val grid = InventoryLayout.compute(playerInventory())
        val seen = grid.allSlots()
        assertEquals("no slot may appear twice", seen.size, seen.toSet().size)
        assertEquals((0..45).toSet(), seen.toSet())
    }

    @Test
    fun `player inventory rows are armor, main and hotbar in that order`() {
        val grid = InventoryLayout.compute(playerInventory())
        assertEquals(
            listOf(
                InventoryRowKind.EQUIPMENT, InventoryRowKind.EQUIPMENT,
                InventoryRowKind.MAIN, InventoryRowKind.MAIN, InventoryRowKind.MAIN,
                InventoryRowKind.HOTBAR
            ),
            grid.rows.map { it.kind }
        )
    }

    @Test
    fun `the hotbar row is slots 36 to 44 left to right`() {
        val grid = InventoryLayout.compute(playerInventory())
        val hotbar = grid.rows.single { it.kind == InventoryRowKind.HOTBAR }
        assertEquals((36..44).toList(), hotbar.cells)
    }

    @Test
    fun `main inventory reads 9 to 35 in rows of nine`() {
        val grid = InventoryLayout.compute(playerInventory())
        val main = grid.rows.filter { it.kind == InventoryRowKind.MAIN }
        assertEquals(3, main.size)
        assertEquals((9..35).toList(), main.flatMap { it.cells.filterNotNull() })
    }

    @Test
    fun `armor sits at the start of the equipment row and off-hand below it`() {
        val grid = InventoryLayout.compute(playerInventory())
        val equipment = grid.rows.filter { it.kind == InventoryRowKind.EQUIPMENT }
        assertEquals(listOf(5, 6, 7, 8), equipment[0].cells.take(4))
        assertEquals(45, equipment[1].cells[0])
    }

    /** The result slot must not land where a fifth armor slot would read as
     *  belonging — it's the one slot that never accepts a placement. */
    @Test
    fun `the crafting result is separated from the crafting inputs`() {
        val grid = InventoryLayout.compute(playerInventory())
        val equipment = grid.rows.filter { it.kind == InventoryRowKind.EQUIPMENT }
        val inputs = listOf(1, 2, 3, 4)
        val resultColumn = equipment[0].cells.indexOf(0)
        val inputColumns = equipment.flatMap { row ->
            row.cells.mapIndexedNotNull { col, slot -> col.takeIf { slot in inputs } }
        }
        assertTrue("result must not be adjacent to an input",
            inputColumns.none { it == resultColumn - 1 || it == resultColumn + 1 })
    }

    @Test
    fun `every row is exactly nine cells wide`() {
        for (state in listOf(playerInventory(), doubleChest())) {
            InventoryLayout.compute(state).rows.forEach {
                assertEquals(InventoryLayout.COLUMNS, it.cells.size)
            }
        }
    }

    @Test
    fun `a chest puts its own slots above the player's`() {
        val grid = InventoryLayout.compute(doubleChest())
        val kinds = grid.rows.map { it.kind }
        assertEquals(6, kinds.count { it == InventoryRowKind.CONTAINER })
        assertEquals(3, kinds.count { it == InventoryRowKind.MAIN })
        assertEquals(1, kinds.count { it == InventoryRowKind.HOTBAR })
        // Container slots come first, and nothing is lost.
        assertEquals((0..89).toSet(), grid.allSlots().toSet())
        assertEquals((0..53).toList(),
            grid.rows.filter { it.kind == InventoryRowKind.CONTAINER }.flatMap { it.cells.filterNotNull() })
    }

    /** A furnace has 3 slots, which is not a multiple of nine. The short row
     *  has to pad rather than pull the player's inventory up into it. */
    @Test
    fun `a container whose size is not a multiple of nine pads its last row`() {
        val furnace = ContainerState(
            syncId = 2,
            handlerType = "minecraft:furnace",
            cursor = HotbarSlot("minecraft:air", 0, 0, 0, false),
            slots = slots(39),
            playerStart = 3,
            hotbarStart = 30,
            armorStart = -1,
            offhandIndex = -1
        )
        val grid = InventoryLayout.compute(furnace)
        val container = grid.rows.single { it.kind == InventoryRowKind.CONTAINER }
        assertEquals(listOf(0, 1, 2), container.cells.take(3))
        assertEquals(InventoryLayout.COLUMNS, container.cells.size)
        assertTrue(container.cells.drop(3).all { it == null })
    }

    @Test
    fun `slotAt returns null for a gap and for out of bounds`() {
        val grid = InventoryLayout.compute(playerInventory())
        assertNull("column 4 of the equipment row is a deliberate gap", grid.slotAt(0, 4))
        assertNull(grid.slotAt(-1, 0))
        assertNull(grid.slotAt(999, 0))
        assertNull(grid.slotAt(0, InventoryLayout.COLUMNS))
    }
}
