package com.kazumaproject.markdownhelperkeyboard.pad

import java.text.BreakIterator

/**
 * Amendment 23 (v10): D-pad ↑ / ↓ change how much of the reading a kana conversion covers.
 *
 * Sumire already has the state: while converting, `inputString` is the reading being converted
 * and `stringInTail` the unconverted rest after it (what a cursor moved left before 変換 leaves).
 * ↑ moves the first character of the rest into the converted range, ↓ moves the last character of
 * the range back to the front of the rest. A character is a grapheme cluster: a small kana (ょ) is
 * one, a surrogate pair or a kana with a combining mark is never split.
 *
 * Pure (no Android): the IME applies the result to its own fields (IMEService `padResizeConversion`).
 * Amendment 24 (v11) adds [deleteBeforeCursor] for controller B while converting
 * (IMEService `padBackspaceInConversion`).
 */
object PadConversionRange {

    /** [converted] is the reading being converted, [rest] the kana after it (shown unconverted). */
    data class Split(val converted: String, val rest: String) {
        val reading: String get() = converted + rest
    }

    /**
     * The split the user sees. [rest] is Sumire's `stringInTail`, which only holds the real rest
     * when [restIsReal] (Sumire's `isFirstClickHasStringTail`: the conversion had a rest when it
     * started, or a range change gave it one). Otherwise it is either empty or a preview a
     * partial-length candidate wrote there (candidate types 5 / 7 / 8: the rest of the whole
     * reading, while [reading] itself stays whole), and the rest is empty.
     */
    fun current(reading: String, rest: String, restIsReal: Boolean): Split =
        Split(reading, if (restIsReal) rest else "")

    /** ↑: the first character of the rest joins the converted range; null when there is no rest. */
    fun grow(split: Split): Split? {
        if (split.rest.isEmpty()) return null
        val end = firstGraphemeEnd(split.rest)
        return Split(split.converted + split.rest.substring(0, end), split.rest.substring(end))
    }

    /** ↓: the last character of the range goes back to the rest; null when the range is one character (or empty). */
    fun shrink(split: Split): Split? {
        if (split.converted.isEmpty()) return null
        val start = lastGraphemeStart(split.converted)
        if (start <= 0) return null
        return Split(split.converted.substring(0, start), split.converted.substring(start) + split.rest)
    }

    /** One D-pad step: [grow] for ↑, [shrink] for ↓. */
    fun step(split: Split, grow: Boolean): Split? = if (grow) grow(split) else shrink(split)

    /**
     * Amendment 24 (v11): controller B while converting leaves the conversion and deletes the
     * character before the cursor, which sits where the converted range ends: the last character
     * of [Split.converted] goes, the [Split.rest] stays after the cursor, always. The result may
     * have an empty converted part (the rest stays composing) or be empty altogether. Null when
     * there is nothing before the cursor.
     */
    fun deleteBeforeCursor(split: Split): Split? {
        if (split.converted.isEmpty()) return null
        return Split(split.converted.substring(0, lastGraphemeStart(split.converted)), split.rest)
    }

    /** Number of characters (grapheme clusters) in [text]. */
    fun length(text: String): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    private fun firstGraphemeEnd(text: String): Int {
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        val end = it.following(0)
        return if (end == BreakIterator.DONE || end <= 0) text.length else end
    }

    private fun lastGraphemeStart(text: String): Int {
        val it = BreakIterator.getCharacterInstance().apply { setText(text) }
        val start = it.preceding(text.length)
        return if (start == BreakIterator.DONE) 0 else start
    }
}
