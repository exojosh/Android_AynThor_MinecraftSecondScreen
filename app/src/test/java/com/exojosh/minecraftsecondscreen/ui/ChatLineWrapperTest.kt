package com.exojosh.minecraftsecondscreen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wrapping is where a chat log silently goes wrong: a bad break doesn't crash,
 * it just loses characters or spins. Every case here is one of those.
 *
 * Advances are a fixed 1 per character so line widths read as character counts.
 */
class ChatLineWrapperTest {

    private val oneWide: (Char) -> Int = { 1 }

    private fun textOf(line: List<ChatRun>) = line.joinToString("") { it.text }

    private fun wrap(text: String, maxWidth: Int, color: Int? = null) =
        ChatLineWrapper.wrap(listOf(ChatRun(text, color)), maxWidth, oneWide)

    @Test
    fun `text that fits stays on one line`() {
        val lines = wrap("hello", 10)
        assertEquals(1, lines.size)
        assertEquals("hello", textOf(lines[0]))
    }

    @Test
    fun `breaks on a word boundary and drops the space`() {
        val lines = wrap("hello there", 6)
        assertEquals(listOf("hello", "there"), lines.map(::textOf))
    }

    @Test
    fun `a word longer than the line is hard-broken rather than dropped`() {
        val lines = wrap("abcdefghij", 4)
        assertEquals(listOf("abcd", "efgh", "ij"), lines.map(::textOf))
    }

    /** The case that would hang: nothing to break on and no room for the
     *  character either. It has to claim a line of its own and move on. */
    @Test
    fun `a single character wider than the whole line still terminates`() {
        val lines = ChatLineWrapper.wrap(listOf(ChatRun("ab", null)), 1) { 5 }
        assertEquals(listOf("a", "b"), lines.map(::textOf))
    }

    /** Only the space a line broke on may disappear; everything else has to
     *  come out the other side, in order. */
    @Test
    fun `nothing but the break space is lost across a wrap`() {
        val message = "the quick brown fox jumps over the lazy dog"
        val joined = wrap(message, 11).joinToString(" ", transform = ::textOf)
        assertEquals(message, joined)
    }

    @Test
    fun `runs keep their colours across a break`() {
        val lines = ChatLineWrapper.wrap(
            listOf(ChatRun("<Steve> ", 0xFF5555), ChatRun("hello world", null)),
            10,
            oneWide
        )
        assertEquals(listOf("<Steve>", "hello", "world"), lines.map(::textOf))
        assertEquals(0xFF5555, lines[0][0].color)
        assertEquals(null, lines[1][0].color)
    }

    /** A colour change mid-word must not become a line break, and must not
     *  merge the two runs either. */
    @Test
    fun `a colour change inside a line splits runs but not lines`() {
        val lines = ChatLineWrapper.wrap(
            listOf(ChatRun("ab", 1), ChatRun("cd", 2)),
            10,
            oneWide
        )
        assertEquals(1, lines.size)
        assertEquals(listOf("ab" to 1, "cd" to 2), lines[0].map { it.text to it.color })
    }

    @Test
    fun `adjacent runs of the same colour are merged`() {
        val lines = ChatLineWrapper.wrap(
            listOf(ChatRun("ab", 7), ChatRun("cd", 7)),
            10,
            oneWide
        )
        assertEquals(listOf("abcd"), lines[0].map { it.text })
    }

    @Test
    fun `an empty message still occupies a line`() {
        val lines = ChatLineWrapper.wrap(emptyList(), 10, oneWide)
        assertEquals(1, lines.size)
        assertTrue(lines[0].isEmpty())
    }

    /** Guards against a zero-width panel (a first frame before layout) turning
     *  into one line per character, or an infinite loop. */
    @Test
    fun `a non-positive width disables wrapping instead of looping`() {
        val lines = wrap("hello there", 0)
        assertEquals(listOf("hello there"), lines.map(::textOf))
    }

    @Test
    fun `runs of spaces at a break do not indent the next line`() {
        val lines = wrap("aa    bb", 3)
        assertEquals(listOf("aa", "bb"), lines.map(::textOf).map { it.trim() })
        assertTrue(lines[1].joinToString("") { it.text }.first() != ' ')
    }
}
