package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 22 (v9), the test list of the amendment: in 英字, while a button whose role is
 * HOLD_HANDAKUTEN, HOLD_SMALL or HOLD_SMALL_OR_HANDAKUTEN is held (L1 and R1 by default), the grid,
 * the ring and the R2 layer are 数字's; latched when the flick arms; L2 does nothing then.
 */
class Amendment22Test {

    /** 英字 with the sticks used, the left stick at rest (the qrst / ()=% centre cell). */
    private fun english(config: PadConfig = PadConfig()): PadDriver =
        PadDriver(PadInputEngine(config, InputMode.ModeEnglish)).also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }

    private fun PadDriver.rightOut(dir: Dir4) {
        val (x, y) = PadDriver.unit(dir)
        right(x * 0.5f, y * 0.5f); right(x * 0.95f, y * 0.95f)
    }

    private fun PadDriver.rightHome(dir: Dir4) {
        val (x, y) = PadDriver.unit(dir)
        right(x * 0.4f, y * 0.4f); right(0f, 0f)
    }

    @Test
    fun l1_held_up_left_flick_left_types_1() {
        val d = english()
        d.press(PadButton.L1); d.holdSector(Sector9.UP_LEFT); d.flick(Dir4.LEFT); d.release(PadButton.L1)
        assertEquals("1", d.typed())
        assertEquals("a digit goes out as text, the way 数字 types it", listOf<PadAction>(PadAction.InsertText("1")), d.actions)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        // The same with R1.
        val r = english()
        r.press(PadButton.R1); r.holdSector(Sector9.UP_LEFT); r.flick(Dir4.LEFT); r.release(PadButton.R1)
        assertEquals("1", r.typed())
    }

    @Test
    fun the_ring_is_numbers_left_up_right_down() {
        val d = english()
        d.press(PadButton.L1); d.holdSector(Sector9.UP_LEFT)
        for (dir in listOf(Dir4.LEFT, Dir4.UP, Dir4.RIGHT, Dir4.DOWN)) d.flick(dir)
        d.holdSector(Sector9.UP); d.flick(Dir4.LEFT)                 // 5678 ← = 5
        d.holdSector(Sector9.UP_RIGHT); d.flick(Dir4.UP)             // 90., ↑ = 0
        d.leftRelease(); d.flick(Dir4.RIGHT)                         // (=)% → = )
        d.release(PadButton.L1)
        assertEquals("123450)", d.typed())
    }

    @Test
    fun l1_let_go_before_the_stick_returns_still_types_the_digit() {
        val d = english()
        d.holdSector(Sector9.UP_LEFT)
        d.press(PadButton.L1); d.rightOut(Dir4.UP); d.release(PadButton.L1)
        assertTrue("the guide still shows the latched digits", d.engine.guide.numberLayout)
        d.rightHome(Dir4.UP)
        assertEquals("2", d.typed())
        // L1 pressed after the flick armed changes nothing: a letter.
        d.clear()
        d.rightOut(Dir4.UP); d.press(PadButton.L1); d.rightHome(Dir4.UP); d.release(PadButton.L1)
        assertEquals("b", d.typed())
    }

    @Test
    fun l2_with_l1_types_the_digit() {
        val d = english()
        d.holdSector(Sector9.UP_LEFT)
        d.press(PadButton.L2); d.press(PadButton.L1); d.flick(Dir4.LEFT); d.release(PadButton.L1); d.release(PadButton.L2)
        d.press(PadButton.L1); d.press(PadButton.L2); d.flick(Dir4.UP); d.release(PadButton.L2); d.release(PadButton.L1)
        assertEquals("12", d.typed())
        // L2 alone is still upper case.
        d.clear()
        d.press(PadButton.L2); d.flick(Dir4.LEFT); d.release(PadButton.L2)
        assertEquals("A", d.typed())
    }

    @Test
    fun l1_with_r2_types_the_number_symbols() {
        val d = english()
        d.press(PadButton.L1); d.press(PadButton.R2); d.flickLeft8(Sector9.UP_LEFT); d.release(PadButton.R2); d.release(PadButton.L1)
        assertEquals("{", d.typed())
        // Without L1 the 英字 layer: &.
        d.clear()
        d.layer(Sector9.UP_LEFT)
        assertEquals("&", d.typed())
        // L1 let go before the left stick is home: still 数字's layer (latched at arming).
        d.clear()
        d.press(PadButton.L1); d.press(PadButton.R2); d.leftOut(Sector9.DOWN); d.release(PadButton.L1); d.left(0f, 0f); d.release(PadButton.R2)
        assertEquals("|", d.typed())
    }

    @Test
    fun after_l1_is_released_the_next_flick_types_a_letter() {
        val d = english()
        d.holdSector(Sector9.UP_LEFT)
        d.press(PadButton.L1); d.flick(Dir4.LEFT); d.release(PadButton.L1)
        d.flick(Dir4.LEFT)
        assertEquals("1a", d.typed())
    }

    @Test
    fun abc123_without_leaving_english() {
        val d = english()
        d.holdSector(Sector9.UP_LEFT)
        d.flick(Dir4.LEFT); d.flick(Dir4.UP); d.flick(Dir4.RIGHT)
        d.press(PadButton.L1); d.flick(Dir4.LEFT); d.flick(Dir4.UP); d.flick(Dir4.RIGHT); d.release(PadButton.L1)
        assertEquals("abc123", d.typed())
        assertTrue("no mode switch", d.actions.none { it is PadAction.SwitchMode })
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
    }

    @Test
    fun works_without_number_in_the_text_modes() {
        val d = english(PadConfig(modes = listOf(PadMode.KANA, PadMode.ENGLISH)))
        d.press(PadButton.R1); d.holdSector(Sector9.UP); d.flick(Dir4.DOWN); d.release(PadButton.R1)
        assertEquals("8", d.typed())
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        val only = english(PadConfig(modes = listOf(PadMode.ENGLISH)))
        only.press(PadButton.L1); only.holdSector(Sector9.UP_RIGHT); only.flick(Dir4.RIGHT); only.release(PadButton.L1)
        assertEquals(".", only.typed())
    }

    @Test
    fun number_mode_with_l1_types_the_plain_number_character() {
        val d = PadDriver(PadInputEngine(PadConfig(), InputMode.ModeNumber)).also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }
        d.press(PadButton.L1); d.holdSector(Sector9.UP_LEFT); d.flick(Dir4.LEFT); d.release(PadButton.L1)
        assertEquals("1", d.typed())
        assertEquals("L1 stays empty in 数字", GuideLabels.NONE, d.engine.guide.labels.buttons[PadButton.L1])
        assertFalse(d.engine.guide.numberLayout)
    }

    @Test
    fun kana_l1_is_still_handakuten() {
        val d = PadDriver().also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }
        d.press(PadButton.L1); d.left(1f, 0f); d.kanaTap(); d.release(PadButton.L1)
        assertEquals("ぱ", d.typed())
        assertEquals("半濁点", d.engine.guide.labels.buttons[PadButton.L1])
    }

    @Test
    fun the_grid_index_is_kept_on_press_and_release() {
        val d = english()
        d.holdSector(Sector9.UP_LEFT)
        val before = d.engine.guide
        d.press(PadButton.L1)
        val held = d.engine.guide
        d.release(PadButton.L1)
        val after = d.engine.guide
        assertEquals(Sector9.UP_LEFT, held.selectedSector)
        assertEquals(before.selectedKey, held.selectedKey)
        assertEquals(before.selectedKey, after.selectedKey)
        assertEquals("abcd", before.gridLabels[0]); assertEquals("1234", held.gridLabels[0]); assertEquals("abcd", after.gridLabels[0])
    }

    @Test
    fun the_guide_while_l1_is_held() {
        val d = english()
        var g = d.engine.guide
        assertEquals("123", g.labels.buttons[PadButton.L1])
        assertEquals("123", g.labels.buttons[PadButton.R1])
        assertEquals("大文字", g.labels.buttons[PadButton.L2])
        assertFalse(g.numberLayout)
        d.press(PadButton.L1); d.holdSector(Sector9.UP_LEFT)
        g = d.engine.guide
        assertTrue(g.numberLayout)
        assertEquals(FourWayLayouts.NUMBER, g.gridLabels)
        assertTrue("no cell dimmed", g.gridDim.none { it })
        assertEquals(KanaGrid.FlickChars("", "1", "2", "3", "4"), g.flickChars)
        assertEquals(GuideLabels.NONE, g.labels.buttons[PadButton.L2])
        assertEquals("記号", g.labels.buttons[PadButton.R2])
        assertEquals("候補", g.labels.buttons[PadButton.Y])
        assertEquals("かなへ", g.labels.buttons[PadButton.L3])
        assertEquals("数字へ", g.labels.buttons[PadButton.R3])
        assertTrue(PadButton.L1 in g.heldButtons)
        // With R2 too: 数字's 記号 layer.
        d.leftRelease()
        d.press(PadButton.R2)
        g = d.engine.guide
        assertEquals(KanaGrid.layerLabels(PadMode.NUMBER), g.gridLabels)
        d.release(PadButton.R2); d.release(PadButton.L1)
        g = d.engine.guide
        assertFalse(g.numberLayout)
        assertEquals(FourWayLayouts.ENGLISH, g.gridLabels)
        assertEquals("大文字", g.labels.buttons[PadButton.L2])
        // In English the pills read "123" too.
        val en = PadDriver(PadInputEngine(PadConfig(words = TestWords.en), InputMode.ModeEnglish)).also { it.left(0.05f, 0f) }
        assertEquals("123", en.engine.guide.labels.buttons[PadButton.L1])
    }

    @Test
    fun the_held_pill_and_the_dimmed_l2_on_the_map() {
        val d = english()
        d.press(PadButton.R1); d.holdSector(Sector9.UP_LEFT)
        val r = GuideLayout.layout(d.engine.guide, GuideLayout.GuideCanvas(1280f, 385f, 2f), map = true)
        assertTrue("R1 lit", r.texts.any { it.text == "R1" && it.style == GuideLayout.TextStyle.SELECTED })
        assertTrue("L2 dimmed", r.texts.any { it.text == "L2" && it.style == GuideLayout.TextStyle.DIM })
        assertTrue("L1 reads 123", r.texts.any { it.text == "123" })
        assertTrue(r.texts.any { it.text == "1234" && it.style == GuideLayout.TextStyle.SELECTED })
    }

    @Test
    fun any_number_hold_role_works_and_a_remapped_button_carries_it() {
        // HOLD_SMALL_OR_HANDAKUTEN on L1, and the number layout on X when X has HOLD_HANDAKUTEN.
        val cfg = PadConfig(roles = PadConfig.DEFAULT_ROLES + mapOf(PadButton.L1 to PadRole.HOLD_SMALL_OR_HANDAKUTEN, PadButton.X to PadRole.HOLD_HANDAKUTEN))
        val d = english(cfg)
        d.holdSector(Sector9.UP_LEFT)
        d.press(PadButton.L1); d.flick(Dir4.DOWN); d.release(PadButton.L1)
        d.press(PadButton.X); d.flick(Dir4.UP); d.release(PadButton.X)
        assertEquals("42", d.typed())
        // L2 (大文字) is not a number hold.
        assertFalse(PadRole.HOLD_DAKUTEN.isNumberHold)
        assertTrue(PadRole.HOLD_HANDAKUTEN.isNumberHold && PadRole.HOLD_SMALL.isNumberHold && PadRole.HOLD_SMALL_OR_HANDAKUTEN.isNumberHold)
    }

    @Test
    fun the_d_pad_fallback_keeps_its_l1_bar() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK), InputMode.ModeEnglish))
        d.press(PadButton.L1); d.tap(PadButton.X); d.release(PadButton.L1)   // the 英字 bar's ← = (
        assertEquals("(", d.typed())
        assertFalse(d.engine.guide.numberLayout)
    }

    @Test
    fun the_hold_note_says_what_it_does() {
        val d = english()
        d.press(PadButton.L1)
        assertTrue(d.notes.last(), d.notes.last().contains("number layout"))
    }
}
