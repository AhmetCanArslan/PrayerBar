package com.arslan.prayerbar.statusbar

/**
 * Turns a rendered label into the drawables that will stand in the bar.
 *
 * A slot is expensive: measured on a bar carrying a clock, two notification icons, VPN, Wi-Fi,
 * signal and battery, each one costs about 40px of a 1080px bar, of which roughly 15px is the
 * spacing every icon view gets whether it needs it or not. Spelling "45dk" out of four characters
 * therefore pays that spacing four times and puts visible gaps between the letters.
 *
 * So whole readings are drawn in one piece where they are known ahead of time — the countdown has
 * only a few dozen — and single characters are the fallback for everything else.
 */
object StatusBarText {

    /**
     * How many icons the label may claim. SystemUI hides everything that does not fit behind a
     * single overflow dot rather than clipping the tail, so overshooting costs the whole label.
     */
    const val MAX_SLOTS = 8

    fun sanitize(raw: String): String = raw.filter { Glyphs.has(it) }.trim()

    /** Characters the template asked for that no glyph can draw. */
    fun unsupported(raw: String): Set<Char> = raw.trim().filterNot { Glyphs.has(it) }.toSet()

    /** One drawable per slot: the longest chunk that matches here, otherwise one character. */
    fun segments(raw: String): List<Int> {
        val text = sanitize(raw)
        val slots = ArrayList<Int>(MAX_SLOTS)
        var index = 0
        while (index < text.length && slots.size < MAX_SLOTS) {
            val chunk = chunkAt(text, index)
            if (chunk == null) {
                Glyphs.byChar[text[index]]?.let(slots::add)
                index++
            } else {
                slots.add(chunk.second)
                index += chunk.first
            }
        }
        return slots
    }

    private fun chunkAt(text: String, index: Int): Pair<Int, Int>? {
        val longest = minOf(Glyphs.longestChunk, text.length - index)
        for (length in longest downTo 2) {
            val resource = Glyphs.byChunk[text.substring(index, index + length)] ?: continue
            return length to resource
        }
        return null
    }
}
