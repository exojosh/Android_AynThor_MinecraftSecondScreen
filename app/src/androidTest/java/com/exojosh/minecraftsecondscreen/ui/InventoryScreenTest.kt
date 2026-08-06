package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exojosh.minecraftsecondscreen.net.ContainerSlot
import com.exojosh.minecraftsecondscreen.net.ContainerState
import com.exojosh.minecraftsecondscreen.net.HotbarSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drag-to-move, exercised by actually dragging.
 *
 * This is an *instrumented* test rather than a JVM one because the thing worth
 * checking is the gesture: `InventoryLayout` already has the slot maths pinned
 * in `InventoryLayoutTest`, but the wiring from "a finger moved from here to
 * there" to "these two clicks went to the server" only exists inside a real
 * Compose pointer pipeline. A wrong answer here isn't a crash — it's an item
 * moving somewhere the player didn't ask for.
 *
 * It also happens to be the only way this UI can be checked on the Thor at all
 * when the AYN dual-screen assistant panel is sitting over the `Presentation`;
 * this renders in the activity's own window on the primary display.
 */
@RunWith(AndroidJUnit4::class)
class InventoryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Click(val syncId: Int, val slotId: Int, val button: Int, val action: String)

    private val stone = HotbarSlot("minecraft:stone", 42, 0, 0, false)
    private val empty = HotbarSlot("minecraft:air", 0, 0, 0, false)

    /** The player's own inventory, with something in the first hotbar slot. */
    private fun playerInventory(cursor: HotbarSlot = empty) = ContainerState(
        syncId = 7,
        handlerType = null,
        cursor = cursor,
        slots = List(46) { index ->
            ContainerSlot(if (index == 36) stone else empty, mayPlace = true)
        },
        playerStart = 9,
        hotbarStart = 36,
        armorStart = 5,
        offhandIndex = 45
    )

    private fun setContent(
        state: ContainerState,
        clicks: MutableList<Click>
    ) {
        rule.setContent {
            InventoryScreen(
                state = state,
                fontSheet = null,
                glintTexture = null,
                isConnected = true,
                itemIcon = { null },
                onSlotClick = { syncId, slotId, button, action ->
                    clicks += Click(syncId, slotId, button, action)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    /** Centre of a slot, in coordinates relative to the grid node. */
    private fun slotCentreInGrid(slotIndex: Int): Offset {
        val grid = rule.onNodeWithTag(GRID_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val slot = rule.onNodeWithTag(slotTestTag(slotIndex)).fetchSemanticsNode().boundsInRoot
        return Offset(slot.center.x - grid.left, slot.center.y - grid.top)
    }

    @Test
    fun tappingASlotSendsOnePickup() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(), clicks)

        val target = slotCentreInGrid(36)
        rule.onNodeWithTag(GRID_TEST_TAG).performTouchInput {
            down(target)
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(Click(7, 36, 0, "PICKUP")), clicks)
    }

    /**
     * The headline behaviour: a drag has to come out as *two* clicks — pick the
     * source up, put it down on the target — because that's the only sequence
     * the server understands as a move.
     */
    @Test
    fun draggingBetweenSlotsSendsPickupFromSourceThenTarget() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(), clicks)

        val from = slotCentreInGrid(36)
        val to = slotCentreInGrid(38)

        rule.onNodeWithTag(GRID_TEST_TAG).performTouchInput {
            down(from)
            // Several moves so the gesture passes touch slop and is unambiguous.
            moveTo(from + Offset((to.x - from.x) / 3f, 0f))
            moveTo(from + Offset((to.x - from.x) * 2f / 3f, 0f))
            moveTo(to)
            up()
        }
        rule.waitForIdle()

        assertEquals(2, clicks.size)
        assertEquals(Click(7, 36, 0, "PICKUP"), clicks[0])
        assertEquals(Click(7, 38, 0, "PICKUP"), clicks[1])
    }

    /** A slipped finger must put the stack back, not drop it on the floor. */
    @Test
    fun draggingOffTheGridReturnsTheStackToItsSlot() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(), clicks)

        val from = slotCentreInGrid(36)

        rule.onNodeWithTag(GRID_TEST_TAG).performTouchInput {
            down(from)
            moveTo(from + Offset(0f, -40f))
            // Well above the grid, into the gap the control bar occupies.
            moveTo(Offset(from.x, -400f))
            up()
        }
        rule.waitForIdle()

        assertEquals(2, clicks.size)
        assertEquals(Click(7, 36, 0, "PICKUP"), clicks[0])
        assertEquals("must go back where it came from", Click(7, 36, 0, "PICKUP"), clicks[1])
    }

    @Test
    fun halfModeSendsTheRightMouseButton() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(), clicks)

        rule.onNodeWithText("Half").performClick()
        val target = slotCentreInGrid(36)
        rule.onNodeWithTag(GRID_TEST_TAG).performTouchInput {
            down(target)
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(Click(7, 36, 1, "PICKUP")), clicks)
    }

    @Test
    fun moveModeSendsQuickMove() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(), clicks)

        rule.onNodeWithText("Move").performClick()
        val target = slotCentreInGrid(36)
        rule.onNodeWithTag(GRID_TEST_TAG).performTouchInput {
            down(target)
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(Click(7, 36, 0, "QUICK_MOVE")), clicks)
    }

    /** The held stack has to be visible and disposable, or a half-finished move
     *  leaves the player with no idea where their item went. */
    @Test
    fun theHeldStackIsShownAndCanBeDropped() {
        val clicks = mutableListOf<Click>()
        setContent(playerInventory(cursor = stone), clicks)

        rule.onNodeWithText("Holding").assertIsDisplayed()
        rule.onNodeWithText("Drop").performClick()
        rule.waitForIdle()

        assertEquals(listOf(Click(7, -999, 0, "PICKUP")), clicks)
    }

    @Test
    fun everySlotOfThePlayerInventoryIsOnScreen() {
        setContent(playerInventory(), mutableListOf())
        for (index in 0..45) {
            rule.onNodeWithTag(slotTestTag(index)).assertIsDisplayed()
        }
        // ...and none of them overlap, which the layout test can't see.
        val bounds = (0..45).map { rule.onNodeWithTag(slotTestTag(it)).fetchSemanticsNode().boundsInRoot }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val a = bounds[i]
                val b = bounds[j]
                val overlaps = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                assertTrue("slots $i and $j overlap", !overlaps)
            }
        }
    }
}
