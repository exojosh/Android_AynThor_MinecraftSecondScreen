package com.exojosh.minecraftsecondscreen.ui

/**
 * A run of chat text sharing one colour, in the form the renderer wants it.
 *
 * Deliberately not [com.exojosh.minecraftsecondscreen.net.ChatSegment]: that
 * one is the wire shape and belongs to the socket layer. This one is a layout
 * input, and keeping it separate is what lets the wrapping below stay free of
 * everything else.
 */
data class ChatRun(val text: String, val color: Int?)

/**
 * Word-wraps chat into lines that fit a given width.
 *
 * **Free of Compose and Android types on purpose**, so it can be unit tested —
 * same line as [HeartLayout] and `InputSlotCodec`. Widths come in as font
 * pixels and character advances come in as a function, so this never has to
 * know what a `Dp` or a bitmap is; the caller supplies
 * [MinecraftFontSheet.advanceOf].
 *
 * Wrapping is by character rather than by whole run because a single run can
 * be longer than a line on its own — a coloured URL, a long translated death
 * message — and colour boundaries have nothing to do with where a line should
 * break. Runs are reassembled per line at the end.
 */
object ChatLineWrapper {

    /**
     * @param runs      the message, in order
     * @param maxWidth  usable width in font pixels; <= 0 disables wrapping
     * @param advanceOf width of one character in font pixels, including its
     *                  trailing 1px gap (i.e. [MinecraftFontSheet.advanceOf])
     * @return one entry per rendered line, never empty — a message with no
     *         text still occupies a line, which is what the game does too.
     *         Every character survives except the whitespace a line broke on.
     */
    fun wrap(runs: List<ChatRun>, maxWidth: Int, advanceOf: (Char) -> Int): List<List<ChatRun>> {
        val chars = ArrayList<Char>()
        val colors = ArrayList<Int?>()
        for (run in runs) {
            for (char in run.text) {
                chars.add(char)
                colors.add(run.color)
            }
        }

        if (chars.isEmpty()) return listOf(emptyList())
        if (maxWidth <= 0) return listOf(toRuns(chars, colors, 0, chars.size))

        val lines = ArrayList<List<ChatRun>>()
        var lineStart = 0
        var width = 0

        // Index just past the last space on the current line, i.e. where the
        // next line would start if we broke on a word boundary. -1 until the
        // line has one, which is what makes an unbroken over-long word fall
        // through to a hard break instead of looping forever.
        var wordBreak = -1

        var i = 0
        while (i < chars.size) {
            val advance = advanceOf(chars[i])

            // `i > lineStart` keeps a single character that is wider than the
            // whole line on its own line rather than on no line at all.
            if (width + advance > maxWidth && i > lineStart) {
                val cut = if (wordBreak > lineStart) wordBreak else i

                // The space a line broke on is consumed, not carried: kept on
                // the line above it widens that line past the limit it was just
                // measured against, and kept on the line below it indents a
                // wrap that the player never typed an indent for.
                var end = cut
                while (end > lineStart && chars[end - 1] == ' ') end--
                lines.add(toRuns(chars, colors, lineStart, end))

                lineStart = cut
                while (lineStart < chars.size && chars[lineStart] == ' ') lineStart++

                i = lineStart
                width = 0
                wordBreak = -1
                continue
            }

            width += advance
            if (chars[i] == ' ') wordBreak = i + 1
            i++
        }

        if (lineStart < chars.size) lines.add(toRuns(chars, colors, lineStart, chars.size))

        return lines.ifEmpty { listOf(emptyList()) }
    }

    /** Rebuilds runs over `[from, to)`, merging neighbours of equal colour so
     *  the renderer doesn't get one run per character. */
    private fun toRuns(
        chars: List<Char>,
        colors: List<Int?>,
        from: Int,
        to: Int
    ): List<ChatRun> {
        val result = ArrayList<ChatRun>()
        var start = from
        while (start < to) {
            val color = colors[start]
            var end = start + 1
            while (end < to && colors[end] == color) end++
            result.add(ChatRun(buildString { for (k in start until end) append(chars[k]) }, color))
            start = end
        }
        return result
    }
}
