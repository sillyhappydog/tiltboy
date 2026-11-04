package com.kazumaproject.markdownhelperkeyboard.pad

import android.os.Looper
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Amendment 15 §3: the tofu filter ([PadGlyphFilter]) must sit on the path that actually feeds the
 * candidate strip. v3 only applied it in SuggestionAdapter's old `suggestions` setter, while
 * IMEService fills the strip through `submitContent`, so rare kanji the RG477V font lacks still
 * showed as □. The adapter now filters every strip update; [SuggestionAdapter.suggestions] (what
 * IMEService reads for Y / A and clicks), the drawn items and the test hook agree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PadGlyphFilterStripTest {

    private fun candidate(s: String) = Candidate(string = s, type = 1.toByte(), length = s.length.toUByte(), score = 0)

    /** A font that lacks 𠮷 (U+20BB7, CJK Extension B) and 﨑 (U+FA11, compatibility ideograph). */
    private val missing = setOf(0x20BB7, 0xFA11)
    private val fakeFilter: (List<Candidate>) -> List<Candidate> = { list ->
        PadGlyphFilter.filterWith(list) { text -> PadGlyphFilter.canDrawWith(text) { cp -> cp !in missing } }
    }

    private fun adapter() = SuggestionAdapter(Executor { it.run() })

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun the_strip_path_drops_candidates_the_font_cannot_draw() {
        val adapter = adapter()
        adapter.candidateFilter = fakeFilter
        val list = listOf(candidate("吉田"), candidate("𠮷田"), candidate("よしだ"), candidate("ヨシダ"))
        adapter.submitContent(CandidateStripContent.Candidates(candidates = list))
        idle()
        assertEquals(listOf("吉田", "よしだ", "ヨシダ"), adapter.suggestions.map { it.string })
        assertEquals("the drawn items follow the filtered list", 3, adapter.itemCount)
        assertEquals("the emulator test hook sees what the strip shows", listOf("吉田", "よしだ", "ヨシダ"), PadTestHooks.candidates)
        adapter.release()
    }

    @Test
    fun zero_query_suggestions_are_filtered_too_and_selection_actions_are_not() {
        val zeroQuery = CandidateStripContent.ZeroQuerySuggestions(candidates = listOf(candidate("宮﨑"), candidate("宮崎"), candidate("みやざき")))
        val plain = adapter()
        plain.submitContent(zeroQuery)
        idle()
        val adapter = adapter()
        adapter.candidateFilter = fakeFilter
        adapter.submitContent(zeroQuery)
        idle()
        assertEquals("one zero-query item fewer (宮﨑 dropped)", plain.itemCount - 1, adapter.itemCount)
        plain.release()
        val actions = listOf(candidate("𠮷"), candidate("copy"))
        adapter.submitContent(CandidateStripContent.SelectionActions(actions = actions, showShortcutEntry = false))
        idle()
        assertEquals("selection actions are never filtered", actions, adapter.suggestions)
        adapter.release()
    }

    @Test
    fun without_the_filter_nothing_changes() {
        val adapter = adapter()
        val list = listOf(candidate("吉田"), candidate("𠮷田"))
        adapter.submitContent(CandidateStripContent.Candidates(candidates = list))
        idle()
        assertEquals(list, adapter.suggestions)
        adapter.release()
    }

    @Test
    fun the_old_setter_still_filters() {
        val adapter = adapter()
        adapter.candidateFilter = fakeFilter
        adapter.suggestions = listOf(candidate("𠮷"), candidate("吉"), candidate("きち"))
        idle()
        assertEquals(listOf("吉", "きち"), adapter.suggestions.map { it.string })
        adapter.release()
    }

    @Test
    fun the_filter_rules() {
        // Only rare ideographs are checked: kana, Latin, emoji and the basic CJK block are never dropped.
        assertTrue(PadGlyphFilter.canDrawWith("今日はいい天気 ok 😀") { false })
        assertFalse(PadGlyphFilter.canDrawWith("𠮷") { false })
        assertTrue(PadGlyphFilter.isRareIdeograph(0x3400) && PadGlyphFilter.isRareIdeograph(0xFA11) && PadGlyphFilter.isRareIdeograph(0x20BB7))
        assertFalse(PadGlyphFilter.isRareIdeograph(0x4E00) || PadGlyphFilter.isRareIdeograph('あ'.code))
        // A check that would throw away most of the list is not trusted.
        val mostlyRare = listOf(candidate("𠮷"), candidate("﨑"), candidate("吉"))
        assertEquals(mostlyRare, fakeFilter(mostlyRare))
    }
}
