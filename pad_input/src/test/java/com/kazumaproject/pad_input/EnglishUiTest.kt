package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amendment 12 §1: the guide in English, and English typed with gestures only. */
class EnglishUiTest {

    private fun english(modes: List<PadMode> = listOf(PadMode.ENGLISH, PadMode.NUMBER)): PadDriver {
        val d = PadDriver(PadInputEngine(PadConfig(modes = modes, words = TestWords.en), InputMode.ModeEnglish))
        d.left(0.1f, 0f)
        d.clear()
        return d
    }

    @Test
    fun english_labels_follow_the_table() {
        val g = english().engine.guide
        val b = g.labels.buttons
        assertEquals("Enter", b[PadButton.A])
        assertEquals("Delete", b[PadButton.B])
        assertEquals("Space", b[PadButton.X])
        assertEquals("Next", b[PadButton.Y])
        assertEquals("amendment 18: R2 is the symbols layer in 英字", "Symbols", b[PadButton.R2])
        assertEquals("L2 = Shift (amendment 16)", "Shift", b[PadButton.L2])
        // Amendment 22: L1 / R1 (半濁点 / 小文字 roles) are 数字's layout while held in 英字.
        assertEquals("123", b[PadButton.L1])
        assertEquals("123", b[PadButton.R1])
        assertEquals("amendment 20: Select is the touch keyboard", "Touch", b[PadButton.SELECT])
        assertEquals("Emoji", b[PadButton.START])
        assertEquals("with two modes L3 and R3 both go to the other one", "To 123", b[PadButton.L3])
        assertEquals("To 123", b[PadButton.R3])
        assertEquals("no bar", "", g.barLabel)
        assertEquals("Cursor", g.labels.dpad)
    }

    @Test
    fun kana_in_english_keeps_kana_and_marks() {
        val d = PadDriver(PadInputEngine(PadConfig(words = TestWords.en)))
        d.left(0.1f, 0f)
        val g = d.engine.guide
        assertEquals("Convert", g.labels.buttons[PadButton.Y])
        assertEquals("゛", g.labels.buttons[PadButton.L2])
        assertEquals("゜", g.labels.buttons[PadButton.L1])
        assertEquals("Small", g.labels.buttons[PadButton.R1])
        assertEquals("amendment 21: R2 is Symbols in kana too", "Symbols", g.labels.buttons[PadButton.R2])
        assertEquals("no bar in kana (amendment 16)", "", g.barLabel)
        assertEquals("To 123", g.labels.buttons[PadButton.L3])
        assertEquals("To ABC", g.labels.buttons[PadButton.R3])
        // Amendment 21: the ↘ key types kana, so its label is kana in every UI language.
        assertEquals(listOf("か", "さ", "た", "な", "あ", "は", "ま", "ら", "や/わ"), g.gridLabels)
        val n = PadDriver(PadInputEngine(PadConfig(words = TestWords.en), InputMode.ModeNumber)).also { it.left(0.1f, 0f) }.engine.guide
        assertEquals("", n.barLabel)
        assertEquals("Symbols", n.labels.buttons[PadButton.R2])
        assertEquals("To ABC", n.labels.buttons[PadButton.L3])
        assertEquals("To kana", n.labels.buttons[PadButton.R3])
    }

    @Test
    fun the_map_in_english_draws_only_names_words_and_characters() {
        val g = english().engine.guide
        val r = GuideLayout.layout(g, GuideLayout.GuideCanvas(1280f, 385f, 2f), map = true)
        val allowed = setOf("A", "B", "X", "Y", "L1", "L2", "R1", "R2", "L3", "R3", "Select", "Start") + ('0'..'9').map { it.toString() } + g.labels.buttons.values + g.labels.dpad + g.barLabel +
            g.gridLabels + (g.flickChars?.let { listOf(it.center, it.left, it.up, it.right, it.down) } ?: emptyList())
        for (t in r.texts) assertTrue("'${t.text}'", t.text in allowed)
        assertTrue("no Japanese word in the English map", r.texts.none { t -> t.text.any { it in '぀'..'ヿ' || it in '一'..'鿿' } })
    }

    @Test
    fun hello_world_with_gestures_only() {
        val d = english()
        fun key(s: Sector9) = d.holdSector(s)
        // Amendment 14 cells: abcd efgh ijkl / mnop qrst uvwx / yz'- ,.?! @/:#  (← ↑ → ↓), X = space.
        d.press(PadButton.L2); key(Sector9.UP); d.flick(Dir4.DOWN); d.release(PadButton.L2)     // H (efgh ↓, Shift = L2 held)
        key(Sector9.UP); d.flick(Dir4.LEFT)                                                     // e (efgh ←)
        key(Sector9.UP_RIGHT); d.flick(Dir4.DOWN); d.flick(Dir4.DOWN)                           // l l (ijkl ↓)
        key(Sector9.LEFT); d.flick(Dir4.RIGHT)                                                  // o (mnop →)
        key(Sector9.DOWN); d.flick(Dir4.LEFT)                                                   // , (,.?! ←)
        d.tap(PadButton.X)                                                                      // space (X; amendment 18: R3 steps the mode)
        key(Sector9.RIGHT); d.flick(Dir4.RIGHT)                                                 // w (uvwx →)
        key(Sector9.LEFT); d.flick(Dir4.RIGHT)                                                  // o
        key(Sector9.CENTER); d.flick(Dir4.UP)                                                   // r (qrst ↑)
        key(Sector9.UP_RIGHT); d.flick(Dir4.DOWN)                                               // l
        key(Sector9.UP_LEFT); d.flick(Dir4.DOWN)                                                // d (abcd ↓)
        key(Sector9.DOWN); d.flick(Dir4.UP)                                                     // . (,.?! ↑)
        val text = d.actions.joinToString("") {
            when (it) {
                is PadAction.KanaKey -> it.char?.toString().orEmpty()
                is PadAction.InsertText -> it.text
                PadAction.Space -> " "
                else -> ""
            }
        }
        assertEquals("Hello, world.", text)
    }
}
