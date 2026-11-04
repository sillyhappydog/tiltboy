package com.kazumaproject.markdownhelperkeyboard.pad

import com.kazumaproject.markdownhelperkeyboard.pad.PadConversionRange.Split
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amendment 23: D-pad ↑ / ↓ grow / shrink the converted range by one character while converting
 * (Sumire's head = `inputString`, rest = `stringInTail`).
 */
class PadConversionRangeTest {

    @Test
    fun down_four_times_from_the_whole_reading_leaves_hashi_and_the_rest() {
        var s = Split("はしをわたる", "")
        val seen = mutableListOf<Split>()
        repeat(4) { s = PadConversionRange.shrink(s)!!; seen += s }
        assertEquals(
            listOf(Split("はしをわた", "る"), Split("はしをわ", "たる"), Split("はしを", "わたる"), Split("はし", "をわたる")),
            seen,
        )
        // ↑ once: を joins the converted range.
        assertEquals(Split("はしを", "わたる"), PadConversionRange.grow(s))
    }

    @Test
    fun grow_and_shrink_move_exactly_one_character_and_keep_the_reading() {
        val start = Split("はし", "をわたる")
        val up = PadConversionRange.grow(start)!!
        assertEquals(Split("はしを", "わたる"), up)
        assertEquals(start, PadConversionRange.shrink(up))
        assertEquals("はしをわたる", up.reading)
        assertEquals(up, PadConversionRange.step(start, grow = true))
        assertEquals(Split("は", "しをわたる"), PadConversionRange.step(start, grow = false))
    }

    @Test
    fun the_limits_do_nothing() {
        // ↓ at one character, ↑ with no rest.
        assertNull(PadConversionRange.shrink(Split("は", "しをわたる")))
        assertNull(PadConversionRange.grow(Split("はしをわたる", "")))
        assertNull(PadConversionRange.shrink(Split("", "はし")))
        assertNull(PadConversionRange.grow(Split("", "")))
        // All the way up and down, then one more step each way.
        var s = Split("はしをわたる", "")
        while (true) s = PadConversionRange.shrink(s) ?: break
        assertEquals(Split("は", "しをわたる"), s)
        while (true) s = PadConversionRange.grow(s) ?: break
        assertEquals(Split("はしをわたる", ""), s)
    }

    @Test
    fun a_small_kana_is_one_character() {
        assertEquals(Split("きょ", "う"), PadConversionRange.shrink(Split("きょう", "")))
        assertEquals(Split("き", "ょう"), PadConversionRange.shrink(Split("きょ", "う")))
        assertEquals(Split("きょ", "う"), PadConversionRange.grow(Split("き", "ょう")))
        assertEquals(3, PadConversionRange.length("きょう"))
    }

    @Test
    fun a_surrogate_pair_or_a_combining_mark_is_never_split() {
        val yoshi = "\uD842\uDFB7"                       // 𠮷 (U+20BB7), two UTF-16 units
        assertEquals(Split(yoshi, "野家"), PadConversionRange.shrink(Split(yoshi + "野", "家")))
        assertNull("one character, two units", PadConversionRange.shrink(Split(yoshi, "野家")))
        assertEquals(Split("は$yoshi", "野"), PadConversionRange.grow(Split("は", "${yoshi}野")))
        val ga = "か\u3099"                                 // か + combining voiced sound mark
        assertEquals(Split("は", ga), PadConversionRange.shrink(Split("は$ga", "")))
        assertEquals(Split("は$ga", "る"), PadConversionRange.grow(Split("は", "${ga}る")))
        assertEquals(2, PadConversionRange.length("は$ga"))
    }

    @Test
    fun a_rest_that_is_only_a_partial_candidate_preview_is_no_rest() {
        // Sumire writes the rest of the whole reading into stringInTail when a shorter candidate
        // (types 5 / 7 / 8) is shown; the reading itself is still whole, so the rest is empty.
        assertEquals(Split("はしをわたる", ""), PadConversionRange.current("はしをわたる", "をわたる", restIsReal = false))
        assertEquals(Split("はし", "をわたる"), PadConversionRange.current("はし", "をわたる", restIsReal = true))
        assertEquals(Split("はしをわた", "る"), PadConversionRange.shrink(PadConversionRange.current("はしをわたる", "をわたる", false)))
    }

    // ------------------------------------------------------------------ amendment 24

    @Test
    fun b_deletes_the_last_character_of_the_whole_reading() {
        // がっこお, Y, B → がっこ (no rest).
        assertEquals(Split("がっこ", ""), PadConversionRange.deleteBeforeCursor(Split("がっこお", "")))
        // A preview rest written by a shorter candidate is no rest: the whole reading loses its last character.
        val preview = PadConversionRange.current("がっこお", "お", restIsReal = false)
        assertEquals(Split("がっこ", ""), PadConversionRange.deleteBeforeCursor(preview))
    }

    @Test
    fun b_after_a_range_change_keeps_the_rest_after_the_cursor() {
        // はしをわたる, Y, ↓ ×4 (はし | をわたる), B → は | をわたる.
        var s = Split("はしをわたる", "")
        repeat(4) { s = PadConversionRange.shrink(s)!! }
        assertEquals(Split("は", "をわたる"), PadConversionRange.deleteBeforeCursor(s))
        assertEquals("はをわたる", PadConversionRange.deleteBeforeCursor(s)!!.reading)
        // The real rest counts (isFirstClickHasStringTail set).
        assertEquals(Split("は", "をわたる"), PadConversionRange.deleteBeforeCursor(PadConversionRange.current("はし", "をわたる", restIsReal = true)))
    }

    @Test
    fun b_on_a_one_character_range_leaves_only_the_rest() {
        // Empty head with a rest: the rest stays (composing), nothing else.
        assertEquals(Split("", "しをわたる"), PadConversionRange.deleteBeforeCursor(Split("は", "しをわたる")))
        // One character, no rest: nothing is left.
        assertEquals(Split("", ""), PadConversionRange.deleteBeforeCursor(Split("は", "")))
        // Nothing before the cursor: nothing to do.
        assertNull(PadConversionRange.deleteBeforeCursor(Split("", "はし")))
        assertNull(PadConversionRange.deleteBeforeCursor(Split("", "")))
    }

    @Test
    fun b_deletes_one_grapheme() {
        // A small kana is one character.
        assertEquals(Split("きょ", ""), PadConversionRange.deleteBeforeCursor(Split("きょう", "")))
        assertEquals(Split("き", "う"), PadConversionRange.deleteBeforeCursor(Split("きょ", "う")))
        // A kana written with a combining voiced mark (か + U+3099) goes as one.
        val ga = "か\u3099"
        assertEquals(Split("は", "る"), PadConversionRange.deleteBeforeCursor(Split("は$ga", "る")))
        assertEquals(Split("", ""), PadConversionRange.deleteBeforeCursor(Split(ga, "")))
        // A surrogate pair (𠮷, two UTF-16 units) goes as one.
        val yoshi = "\uD842\uDFB7"
        assertEquals(Split("の", "野家"), PadConversionRange.deleteBeforeCursor(Split("の$yoshi", "野家")))
        assertEquals(Split("", "野家"), PadConversionRange.deleteBeforeCursor(Split(yoshi, "野家")))
    }
}
