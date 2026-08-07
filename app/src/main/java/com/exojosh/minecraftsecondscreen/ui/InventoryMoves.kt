package com.exojosh.minecraftsecondscreen.ui

/**
 * How much of a stack a tap or a drag moves.
 *
 * These are modes rather than gestures on purpose. Long-press and two-finger
 * taps are the obvious mappings for half-stack and shift-click, but they have
 * to be raced against the drag detector, and losing that race silently does the
 * *wrong move* to a real inventory. A visible mode you can see the state of
 * beats a hidden gesture you have to trust.
 */
enum class MoveMode(val label: String) {
    /** Vanilla's plain left click: take all, place all, or swap. */
    STACK("Stack"),

    /** Half the stack ends up at the destination. */
    HALF("Half"),

    /** One item ends up at the destination. */
    SINGLE("Single"),

    /** Vanilla's shift-click: send the stack to the other half of the screen.
     *  An amount doesn't apply, so a drag in this mode moves like [STACK]. */
    QUICK("Move")
}

/** One `SLOT:<syncId>,<slotId>,<button>,<ACTION>` click, minus the syncId the
 *  caller holds. */
data class SlotClick(val slotId: Int, val button: Int, val action: String)

/**
 * The click sequences behind every gesture on the Items tab.
 *
 * **Compose- and Android-free so it can be unit tested** — same line as
 * [InventoryLayout], [HeartLayout], `InputSlotCodec` and [ChatLineWrapper], and
 * for the same reason, sharpened: getting a sequence wrong here doesn't crash,
 * it moves a different number of items than the player asked for, on a live
 * world, and looks like the mode buttons simply don't work. That was the actual
 * bug — every drag was a plain two-step `PICKUP` regardless of mode, so "Half"
 * moved whole stacks.
 *
 * Everything here is expressed as vanilla clicks, because that is the only
 * vocabulary the server accepts. There is deliberately no local prediction: the
 * app sends clicks and redraws whatever the game sends back.
 */
object InventoryMoves {

    /** `SlotActionType` names, as the mod parses them. */
    const val PICKUP = "PICKUP"
    const val QUICK_MOVE = "QUICK_MOVE"
    const val QUICK_CRAFT = "QUICK_CRAFT"

    /** Vanilla's "clicked outside the window": drops the cursor stack, and also
     *  the slot a quick-craft's start and end clicks are addressed to. */
    const val OUTSIDE_SLOT = -999

    const val AIR_ITEM_ID = "minecraft:air"

    /**
     * Vanilla's quick-craft click encoding: the stage in the low two bits, the
     * mouse button in the next two (`ScreenHandler.packQuickCraftData`).
     */
    fun packQuickCraft(stage: Int, button: Int): Int = (stage and 3) or ((button and 3) shl 2)

    /**
     * What a tap does.
     *
     * Each mode is a pair: how much comes up off an empty cursor, and how much
     * goes down off a loaded one. [MoveMode.HALF] takes half (a right click)
     * and puts all of it down (a left one). [MoveMode.SINGLE] is the mirror —
     * it takes everything and places one item per tap, so repeated taps deal
     * singles out of the held stack. That asymmetry isn't a shortcut: **no
     * vanilla click picks up exactly one item**, so "one item at the
     * destination" can only be built out of a place-one.
     */
    fun tap(mode: MoveMode, holdingCursor: Boolean, slot: Int): List<SlotClick> = when (mode) {
        MoveMode.QUICK -> listOf(SlotClick(slot, 0, QUICK_MOVE))
        MoveMode.STACK -> listOf(SlotClick(slot, 0, PICKUP))
        MoveMode.HALF -> listOf(SlotClick(slot, if (holdingCursor) 0 else 1, PICKUP))
        MoveMode.SINGLE -> listOf(SlotClick(slot, if (holdingCursor) 1 else 0, PICKUP))
    }

    /**
     * Picking the source up at the start of a drag, so the stack visibly leaves
     * its slot and follows the finger.
     *
     * Only [MoveMode.HALF] takes a partial stack here; [MoveMode.SINGLE] lifts
     * the whole thing and trims it at the far end (see [drop]).
     */
    fun pickUp(mode: MoveMode, slot: Int): SlotClick =
        SlotClick(slot, if (mode == MoveMode.HALF) 1 else 0, PICKUP)

    /**
     * Finishing a drag on [target], with [source] already on the cursor.
     *
     * [MoveMode.HALF] is the easy one: half is on the cursor already, so a
     * plain left click puts all of it down. [MoveMode.SINGLE] right-clicks a
     * single item in and then left-clicks the *source* to return the remainder.
     *
     * That two-step is skipped when the target holds a **different** item: a
     * right click there swaps the two stacks rather than placing one, and
     * "return the rest to the source" after a swap would scatter both. A swap
     * is what a whole-stack drag onto an occupied slot does anyway, so it falls
     * back to that.
     */
    fun drop(
        mode: MoveMode,
        source: Int,
        target: Int,
        targetIsEmpty: Boolean,
        targetHasSameItem: Boolean
    ): List<SlotClick> {
        if (mode == MoveMode.SINGLE && (targetIsEmpty || targetHasSameItem)) {
            return listOf(
                SlotClick(target, 1, PICKUP),
                SlotClick(source, 0, PICKUP)
            )
        }
        return listOf(SlotClick(target, 0, PICKUP))
    }

    /** Putting a dragged stack back where it came from — a release on the
     *  source slot, or off the grid entirely, which a slipped finger did not
     *  mean as "drop this on the floor". */
    fun returnToSource(source: Int): List<SlotClick> = listOf(SlotClick(source, 0, PICKUP))

    /**
     * Spreading the held stack across every slot the finger painted, as
     * vanilla's own drag does.
     *
     * The shape is `HandledScreen.mouseReleased`'s: one stage-0 click outside
     * the window to open the drag, one stage-1 click per slot, one stage-2
     * click to commit. Nothing is sent while the finger moves — the whole
     * sequence goes at once on release, which is also why the painted slots
     * only need collecting rather than diffing.
     *
     * [MoveMode.SINGLE] maps to vanilla's right-drag (one item per slot);
     * everything else to the left-drag even split. Button 2, the creative
     * full-stack drag, is never sent: the server only honours it in creative,
     * and there's no mode that means it.
     *
     * The cursor **must** already hold the stack — an empty cursor makes the
     * server abandon the drag at its first click.
     */
    fun distribute(mode: MoveMode, slots: Collection<Int>): List<SlotClick> {
        val button = if (mode == MoveMode.SINGLE) 1 else 0
        val clicks = mutableListOf(SlotClick(OUTSIDE_SLOT, packQuickCraft(0, button), QUICK_CRAFT))
        slots.mapTo(clicks) { SlotClick(it, packQuickCraft(1, button), QUICK_CRAFT) }
        clicks += SlotClick(OUTSIDE_SLOT, packQuickCraft(2, button), QUICK_CRAFT)
        return clicks
    }

    /** Vanilla's "clicked outside the window", which throws the cursor stack on
     *  the floor — the only way to put down something with nowhere to go. */
    fun dropCursor(): SlotClick = SlotClick(OUTSIDE_SLOT, 0, PICKUP)
}
