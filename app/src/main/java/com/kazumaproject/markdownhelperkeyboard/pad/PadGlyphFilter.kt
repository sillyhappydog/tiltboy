package com.kazumaproject.markdownhelperkeyboard.pad

import android.graphics.Paint
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * Drops conversion candidates the system font cannot draw. On the RG477V the candidate bar
 * showed tofu boxes (□) for rare kanji after typing 「の」 (amendment 3): the device's font
 * lacks those glyphs, so offering them only wastes a slot. Only rare ideographs (CJK
 * extensions, compatibility ideographs) are checked, so kana, Latin, symbols, emoji and the
 * basic kanji block are never dropped.
 *
 * Installed on the candidate adapters while controller input is enabled (see IMEService); the
 * adapter applies it to every candidate-strip update (`SuggestionAdapter.submitContent`).
 * Results are cached per code point; the check is skipped entirely when [Paint.hasGlyph]
 * looks unreliable (it cannot even confirm the letter A, e.g. under Robolectric).
 */
object PadGlyphFilter {
    private val paint = Paint()
    private val cache = HashMap<Int, Boolean>()
    private val reliable: Boolean by lazy { runCatching { paint.hasGlyph("A") }.getOrDefault(false) }

    /**
     * Only the ideograph ranges that device fonts actually lack: CJK Extension A–H and the
     * compatibility ideographs. The basic block (U+4E00–9FFF) is left alone: every CJK font has
     * it, and an emulator image whose `hasGlyph` is pessimistic there must not lose 今日 / 天気
     * (GitHub Actions run 12 reached 供はいいてんき instead of 今日はいい天気 with the broad filter).
     */
    fun isRareIdeograph(cp: Int): Boolean =
        cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3134F

    fun canDraw(text: String): Boolean {
        if (!reliable) return true
        return canDrawWith(text) { cp ->
            cache.getOrPut(cp) { runCatching { paint.hasGlyph(String(Character.toChars(cp))) }.getOrDefault(true) }
        }
    }

    /** [canDraw] with the font check passed in (tests): only rare ideographs are asked about. */
    internal fun canDrawWith(text: String, hasGlyph: (Int) -> Boolean): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!isRareIdeograph(cp)) continue
            if (!hasGlyph(cp)) return false
        }
        return true
    }

    fun filter(candidates: List<Candidate>): List<Candidate> {
        if (!reliable) return candidates
        return filterWith(candidates, ::canDraw)
    }

    /** [filter] with the drawability check passed in (tests). */
    internal fun filterWith(candidates: List<Candidate>, canDraw: (String) -> Boolean): List<Candidate> {
        if (candidates.isEmpty()) return candidates
        val kept = candidates.filter { canDraw(it.string) }
        // A check that would throw away most of the list is not trustworthy; keep everything.
        return if (kept.size * 2 < candidates.size) candidates else kept
    }
}
