package com.exojosh.minecraftsecondscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exojosh.minecraftsecondscreen.net.ChatMessage
import kotlinx.coroutines.delay

/**
 * Size of one font pixel in the chat log, so a glyph is 8x this tall.
 *
 * The log is the one place on this screen showing a lot of text at once, and
 * it's read at arm's length on a handheld — 2dp puts a line at 16dp, which is
 * roughly the game's own chat on a desktop monitor and fits ~55 characters
 * across the Thor's bottom panel.
 */
private val CHAT_PIXEL_SIZE = 2.dp

private val LOG_PADDING = 8.dp
private val DRAFT_BAR_HEIGHT = 40.dp

/** Space the keyboard takes when open, as a fraction of the panel, bounded so
 *  it neither squeezes the log to nothing on a short panel nor floats with
 *  huge keys on a tall one. */
private const val KEYBOARD_HEIGHT_FRACTION = 0.55f
private val KEYBOARD_MIN_HEIGHT = 130.dp
private val KEYBOARD_MAX_HEIGHT = 210.dp

private val DRAFT_HINT_COLOR = Color(0xFF8A8A8A)
private val PANEL_COLOR = Color.Black.copy(alpha = 0.35f)

/**
 * The chat draft, held above [ChatScreen] so it survives a tab switch.
 *
 * Tabs swap the panel's content outright, which disposes whatever was in it —
 * so a draft owned by `ChatScreen` would be lost by glancing at the map
 * mid-sentence. Hoisting it to [com.exojosh.minecraftsecondscreen.SecondScreenApp],
 * which stays composed, is the whole reason this class exists.
 */
class ChatDraftState {
    var text by mutableStateOf("")
    var keyboardOpen by mutableStateOf(false)
}

@Composable
fun rememberChatDraftState(): ChatDraftState = remember { ChatDraftState() }

/**
 * The Chat tab: the game's chat log, and a keyboard to answer it with.
 *
 * The log renders through [MinecraftTextLines] rather than Compose `Text`, for
 * the same reason everything else on this screen does — it's Minecraft's own
 * bitmap font, so a resource pack restyling it applies here too. That does mean
 * the log is limited to the codepoints the `ascii.png` sheet carries; anything
 * outside 0x00–0xFF draws as `?`, which is the same limit every other piece of
 * text on this screen already has.
 *
 * [fontSheet] being null means the mod hasn't delivered the font yet (or ever,
 * against a build predating asset delivery); the log falls back to plain
 * uncoloured Compose text rather than showing nothing.
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    fontSheet: MinecraftFontSheet?,
    isConnected: Boolean,
    draft: ChatDraftState,
    onSend: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val keyboardHeight = (maxHeight * KEYBOARD_HEIGHT_FRACTION)
            .coerceIn(KEYBOARD_MIN_HEIGHT, KEYBOARD_MAX_HEIGHT)
            // On a very short panel the keyboard would leave the log and draft
            // bar with negative space; give it what's left rather than pushing
            // them off the bottom.
            .coerceAtMost(maxHeight - DRAFT_BAR_HEIGHT)
            .coerceAtLeast(0.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            ChatLog(
                messages = messages,
                fontSheet = fontSheet,
                isConnected = isConnected,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            DraftBar(draft = draft, fontSheet = fontSheet)

            if (draft.keyboardOpen) {
                ChatKeyboard(
                    onType = { draft.text += it },
                    onBackspace = { draft.text = draft.text.dropLast(1) },
                    // Held rather than sent-and-cleared while disconnected:
                    // sendChat drops silently in that state, so clearing the
                    // draft anyway would delete the message and show nothing
                    // for it.
                    onSend = {
                        if (isConnected && draft.text.isNotBlank()) {
                            onSend(draft.text)
                            draft.text = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(keyboardHeight)
                )
            }
        }
    }
}

/**
 * The scrolling log.
 *
 * Wrapping is done once per (message list, width, font) rather than per frame:
 * a full 100-message backlog is several hundred lines of character-by-character
 * measurement, and it changes only when one of those three does.
 */
@Composable
private fun ChatLog(
    messages: List<ChatMessage>,
    fontSheet: MinecraftFontSheet?,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.background(PANEL_COLOR)) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(LOG_PADDING),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isConnected) "No messages yet." else "Not connected.",
                    color = DRAFT_HINT_COLOR
                )
            }
            return@BoxWithConstraints
        }

        if (fontSheet == null) {
            // No font sheet delivered: plain text beats a blank panel.
            PlainTextLog(messages)
            return@BoxWithConstraints
        }

        val widthFontPixels = ((maxWidth - LOG_PADDING * 2) / CHAT_PIXEL_SIZE).toInt()
        val wrapped = remember(messages, widthFontPixels, fontSheet) {
            messages.map { message ->
                message.id to ChatLineWrapper.wrap(
                    runs = message.segments.map { ChatRun(it.text, it.color) },
                    maxWidth = widthFontPixels,
                    advanceOf = fontSheet::advanceOf
                )
            }
        }

        LazyColumn(
            state = rememberTailFollowingListState(wrapped.size),
            modifier = Modifier.fillMaxSize().padding(LOG_PADDING),
            reverseLayout = true
        ) {
            items(wrapped.asReversed(), key = { it.first }) { (_, lines) ->
                MinecraftTextLines(
                    lines = lines,
                    fontSheet = fontSheet,
                    pixelSize = CHAT_PIXEL_SIZE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * A list state for a `reverseLayout` log that follows the newest message,
 * unless the player has scrolled back to read.
 *
 * **Both halves of this are load-bearing and neither is the obvious version.**
 *
 * *Reversed layout* rather than scrolling to the last item: the natural
 * "scroll to the bottom if we're already at the bottom" broke on device the
 * moment the keyboard opened. That shrinks the viewport, which leaves the list
 * showing its top with items below it, so "at the bottom" reads false forever
 * and messages stop appearing. Reversed, the newest message is index 0.
 *
 * *[following] recomputed only when a scroll gesture ends*, never when a
 * message arrives: a `LazyColumn` re-anchors onto whichever item was first
 * visible when the list changes, so inserting at index 0 moves the anchor to
 * index 1 and any check made afterwards reports "scrolled away" even though the
 * player never touched it. That was the bug that made the tail-anchored version
 * look like it was working right up until a message actually arrived. Inserts
 * don't start a scroll, so gating on [LazyListState.isScrollInProgress] reads
 * only real gestures.
 */
@Composable
private fun rememberTailFollowingListState(messageCount: Int): LazyListState {
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = listState.firstVisibleItemIndex == 0
        }
    }

    LaunchedEffect(messageCount) {
        if (following) listState.scrollToItem(0)
    }

    return listState
}

@Composable
private fun PlainTextLog(messages: List<ChatMessage>) {
    LazyColumn(
        state = rememberTailFollowingListState(messages.size),
        modifier = Modifier.fillMaxSize().padding(LOG_PADDING),
        reverseLayout = true
    ) {
        items(messages.asReversed(), key = { it.id }) { message ->
            Text(text = message.plainText, color = Color.White, fontSize = 13.sp)
        }
    }
}

/**
 * What's being typed.
 *
 * Tapping it is what opens and closes the keyboard, so the log gets the whole
 * panel whenever nobody is mid-message — which is most of the time, and matters
 * here because the panel below the hotbar isn't tall enough to carry both
 * comfortably.
 *
 * **No Send button of its own**, deliberately: the keyboard already has one,
 * and a draft can only be typed with the keyboard open, so a second one is only
 * ever reachable in the state where the first is on screen too. It was there
 * and it was worse than nothing — disabled-grey over a dirt background, it read
 * as a control that had failed to load.
 */
@Composable
private fun DraftBar(
    draft: ChatDraftState,
    fontSheet: MinecraftFontSheet?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DRAFT_BAR_HEIGHT)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .clickable { draft.keyboardOpen = !draft.keyboardOpen }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Vanilla's chat box blinks its cursor; the same tell is what makes
            // an empty draft read as "ready for input" rather than as a dead
            // panel, since there's no system caret here to borrow.
            val cursorVisible by produceState(true, draft.keyboardOpen) {
                if (!draft.keyboardOpen) {
                    value = false
                    return@produceState
                }
                while (true) {
                    value = true
                    delay(500)
                    value = false
                    delay(500)
                }
            }

            if (draft.text.isEmpty() && !draft.keyboardOpen) {
                Text(text = "Tap to type…", color = DRAFT_HINT_COLOR, fontSize = 13.sp)
            } else if (fontSheet != null) {
                val shown = draft.text + if (cursorVisible) "_" else ""
                val widthFontPixels = ((maxWidth - 12.dp) / CHAT_PIXEL_SIZE).toInt()
                // The draft doesn't wrap -- it's one line in a fixed-height bar
                // -- so an over-long message scrolls by showing its tail, which
                // is the end you're still typing.
                MinecraftTextLines(
                    lines = listOf(listOf(ChatRun(fontSheet.tailFitting(shown, widthFontPixels), null))),
                    fontSheet = fontSheet,
                    pixelSize = CHAT_PIXEL_SIZE,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = draft.text + if (cursorVisible) "_" else "",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The longest suffix of [text] that fits [maxWidth] font pixels.
 *
 * Trailing rather than leading, because a draft is being typed at its end:
 * clipping the front keeps the cursor and the last word in view.
 */
private fun MinecraftFontSheet.tailFitting(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    var width = 0
    var start = text.length
    while (start > 0) {
        val advance = advanceOf(text[start - 1])
        if (width + advance > maxWidth) break
        width += advance
        start--
    }
    return text.substring(start)
}
