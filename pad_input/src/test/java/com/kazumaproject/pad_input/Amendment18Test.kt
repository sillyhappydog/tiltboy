package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 18 (v7): L3 / R3 step the text mode (with the accidental-click guard), the R2 layer
 * in 英字 / 数字 (記号), the touch digits' engine path, ASCII-95 in stick mode, and no centre
 * character on R3 any more. The test list of `2026-10-31_pad_input_v1_amend18.md` §Tests.
 */
class Amendment18Test {

    private fun driver(modes: List<PadMode> = PadConfig.ALL_TEXT_MODES, mode: InputMode = InputMode.ModeJapanese): PadDriver =
        PadDriver(PadInputEngine(PadConfig(modes = modes), mode)).also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }

    private fun switched(d: PadDriver): List<InputMode> = d.actions.filterIsInstance<PadAction.SwitchMode>().map { it.mode }

    // ------------------------------------------------------------------ mode stepping

    @Test
    fun r3_steps_forward_and_l3_back_through_the_modes_with_wrap() {
        val d = driver()
        d.tap(PadButton.R3); assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.tap(PadButton.R3); assertEquals(PadMode.NUMBER, d.engine.padMode)
        d.tap(PadButton.R3); assertEquals(PadMode.KANA, d.engine.padMode)
        d.tap(PadButton.L3); assertEquals("かな L3 → 数字", PadMode.NUMBER, d.engine.padMode)
        d.tap(PadButton.L3); assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.tap(PadButton.L3); assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals(listOf(InputMode.ModeEnglish, InputMode.ModeNumber, InputMode.ModeJapanese, InputMode.ModeNumber, InputMode.ModeEnglish, InputMode.ModeJapanese), switched(d))
        assertEquals("", d.typed())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 (next mode)") } && d.notes.any { it.contains("L3 (previous mode)") })
    }

    @Test
    fun with_two_modes_both_clicks_toggle() {
        for (pair in listOf(listOf(PadMode.KANA, PadMode.ENGLISH), listOf(PadMode.ENGLISH, PadMode.NUMBER), listOf(PadMode.KANA, PadMode.NUMBER))) {
            val start = if (pair[0] == PadMode.KANA) InputMode.ModeJapanese else InputMode.ModeEnglish
            val d = driver(pair, start)
            assertEquals(pair[0], d.engine.padMode)
            d.tap(PadButton.R3); assertEquals("$pair", pair[1], d.engine.padMode)
            d.tap(PadButton.R3); assertEquals("$pair", pair[0], d.engine.padMode)
            d.tap(PadButton.L3); assertEquals("$pair", pair[1], d.engine.padMode)
            d.tap(PadButton.L3); assertEquals("$pair", pair[0], d.engine.padMode)
            val g = d.engine.guide.labels.buttons
            assertEquals("both pills name the other mode", g[PadButton.L3], g[PadButton.R3])
        }
    }

    @Test
    fun with_one_mode_they_do_nothing_and_their_pills_are_empty() {
        val d = driver(listOf(PadMode.ENGLISH), InputMode.ModeEnglish)
        d.tap(PadButton.R3); d.tap(PadButton.L3)
        assertTrue(d.actions.isEmpty())
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("nothing to switch to") })
        val b = d.engine.guide.labels.buttons
        assertEquals(GuideLabels.NONE, b[PadButton.L3])
        assertEquals(GuideLabels.NONE, b[PadButton.R3])
    }

    @Test
    fun never_into_emoji_start_opens_the_list_and_select_is_the_touch_keyboard() {
        val d = driver()
        repeat(9) { d.tap(PadButton.R3); assertTrue(!d.engine.padMode.isEmoji) }
        repeat(9) { d.tap(PadButton.L3); assertTrue(!d.engine.padMode.isEmoji) }
        assertEquals(PadMode.KANA, d.engine.padMode)
        // Amendment 20: Select no longer cycles (it is the touch keyboard); Start opens the emoji list.
        d.clear()
        d.tap(PadButton.SELECT); assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals(listOf<PadAction>(PadAction.TouchKeyboard), d.actions)
        d.tap(PadButton.START); assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        d.tap(PadButton.START); assertEquals(PadMode.KANA, d.engine.padMode)
        // Select held hides the keyboard, as in v6.
        d.clear()
        d.press(PadButton.SELECT); d.time += d.engine.config.longPressMs + 10; d.poll(); d.release(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.HideKeyboard), d.actions)
    }

    @Test
    fun the_switch_happens_on_release_not_on_press() {
        val d = driver()
        d.press(PadButton.R3)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertTrue(d.actions.isEmpty())
        d.release(PadButton.R3)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.press(PadButton.L3)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.release(PadButton.L3)
        assertEquals(PadMode.KANA, d.engine.padMode)
    }

    // ------------------------------------------------------------------ the guard

    @Test
    fun a_click_with_the_stick_at_0_7_does_nothing_at_0_1_it_switches() {
        val d = driver()
        // R3 with the right stick at 0.7 (a flick is armed): ignored, the flick still types.
        d.left(-0.707f, -0.707f)                    // か
        d.right(0.7f, 0f)
        d.tap(PadButton.R3)
        d.right(0.2f, 0f); d.right(0f, 0f)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals("き", d.typed())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 click ignored") && it.contains("flick") })
        d.leftRelease(); d.clear()
        // R3 at radius 0.4 (out, no flick armed): ignored, nothing typed.
        d.right(0.4f, 0f); d.tap(PadButton.R3); d.right(0f, 0f)
        assertTrue(d.actions.isEmpty())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 click ignored") && it.contains("out") })
        // R3 at radius 0.1: switches.
        d.right(0.1f, 0f); d.tap(PadButton.R3); d.right(0f, 0f)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        // L3 with the left stick at 0.7: ignored; at 0.1: switches.
        d.clear()
        d.left(0f, -0.7f); d.tap(PadButton.L3); d.leftRelease()
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("L3 click ignored") && it.contains("left stick") })
        d.left(0.1f, 0f); d.tap(PadButton.L3); d.leftRelease()
        assertEquals(PadMode.KANA, d.engine.padMode)
    }

    @Test
    fun the_stick_must_stay_home_from_press_to_release() {
        val d = driver()
        d.press(PadButton.R3)
        d.right(0.5f, 0f)                           // pushed out while held (a hard click)
        d.right(0f, 0f)
        d.release(PadButton.R3)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertTrue(d.actions.isEmpty())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 click ignored") })
        d.press(PadButton.L3)
        d.left(0.4f, 0f); d.left(0f, 0f)
        d.release(PadButton.L3)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("L3 click ignored") })
        // Below the threshold the whole time: switches.
        d.press(PadButton.R3); d.right(0.25f, 0f); d.right(0f, 0f); d.release(PadButton.R3)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
    }

    @Test
    fun l3_with_a_layer_flick_armed_on_the_left_stick_is_ignored() {
        val d = driver()
        d.tap(PadButton.R3)                          // 英字
        d.clear()
        d.press(PadButton.R2)
        d.leftOut(Sector9.UP_LEFT)                   // & armed
        d.tap(PadButton.L3)
        d.leftRelease()
        d.release(PadButton.R2)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertEquals("&", d.typed())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("L3 click ignored") })
    }

    // ------------------------------------------------------------------ emoji keeps v6

    @Test
    fun the_emoji_list_ignores_l3_and_r3() {
        // Amendment 20: the wheel (where L3 / R3 kept their v6 jobs) is the D-pad fallback's only.
        val d = driver()
        d.tap(PadButton.START); d.clear()
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        d.tap(PadButton.L3); d.tap(PadButton.R3)
        assertTrue(d.actions.isEmpty())
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        val b = d.engine.guide.labels.buttons
        assertEquals(GuideLabels.NONE, b[PadButton.L3])
        assertEquals(GuideLabels.NONE, b[PadButton.R3])
    }

    // ------------------------------------------------------------------ the 記号 layer

    @Test
    fun english_layer() {
        val d = driver(mode = InputMode.ModeEnglish)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.layer(Sector9.UP_LEFT); d.layer(Sector9.UP_RIGHT); d.layer(Sector9.DOWN)
        assertEquals("&*_", d.typed())
        // ← then rotate to ↙: = (re-aim).
        d.clear()
        d.press(PadButton.R2)
        d.leftOut(Sector9.LEFT)
        assertEquals(Sector9.LEFT, d.engine.guide.pendingLayer)
        val (x, y) = PadDriver.unitAt(202.5 + 10.0)
        d.left(x * 0.95f, y * 0.95f)
        d.leftOut(Sector9.DOWN_LEFT)
        assertEquals(Sector9.DOWN_LEFT, d.engine.guide.pendingLayer)
        d.left(-0.3f, 0.3f); d.leftRelease()
        d.release(PadButton.R2)
        assertEquals("=", d.typed())
        // Centre: nothing (R2 alone, or a flick into the centre slot does not exist).
        d.clear()
        d.press(PadButton.R2); d.release(PadButton.R2)
        assertTrue(d.actions.isEmpty())
        assertNull(FourWayLayouts.layer(PadMode.ENGLISH)[Sector9.CENTER])
        // Tilted before R2: nothing until it returns.
        d.left(-1f, 0f)
        d.press(PadButton.R2)
        d.left(-0.95f, -0.2f); d.left(-0.9f, 0f)
        assertTrue("the tilted stick types nothing", d.actions.isEmpty())
        d.leftRelease()
        assertTrue(d.actions.isEmpty())
        d.flickLeft8(Sector9.RIGHT)
        d.release(PadButton.R2)
        assertEquals("after it returned the next flick counts", ")", d.typed())
        // The right stick is ignored while R2 is held; L2 has no effect on the layer.
        d.clear()
        d.press(PadButton.R2); d.flick(Dir4.UP); d.release(PadButton.R2)
        assertTrue(d.actions.isEmpty())
        d.press(PadButton.L2); d.layer(Sector9.UP_LEFT); d.release(PadButton.L2)
        assertEquals("&", d.typed())
        // The whole table.
        assertEquals(mapOf(Sector9.UP_LEFT to '&', Sector9.UP to ';', Sector9.UP_RIGHT to '*', Sector9.LEFT to '(', Sector9.RIGHT to ')',
            Sector9.DOWN_LEFT to '=', Sector9.DOWN to '_', Sector9.DOWN_RIGHT to '+'), FourWayLayouts.layer(PadMode.ENGLISH))
    }

    @Test
    fun number_layer() {
        val d = driver(mode = InputMode.ModeNumber)
        assertEquals(PadMode.NUMBER, d.engine.padMode)
        d.layer(Sector9.UP); d.layer(Sector9.DOWN_RIGHT)
        assertEquals("^`", d.typed())
        assertEquals(mapOf(Sector9.UP_LEFT to '{', Sector9.UP to '^', Sector9.UP_RIGHT to '}', Sector9.LEFT to '[', Sector9.RIGHT to ']',
            Sector9.DOWN_LEFT to '\\', Sector9.DOWN to '|', Sector9.DOWN_RIGHT to '`'), FourWayLayouts.layer(PadMode.NUMBER))
        val g = d.engine.also { e -> e.onButton(PadButton.R2, true, d.time + 1) }.guide
        assertTrue(g.layer)
        assertEquals(listOf("{", "^", "}", "[", "", "]", "\\", "|", "`"), g.gridLabels)
        assertEquals("記号", g.labels.buttons[PadButton.R2])
    }

    // ------------------------------------------------------------------ ASCII 95

    @Test
    fun every_printable_ascii_character_is_typable_in_stick_mode() {
        val typed = StringBuilder()
        fun collect(d: PadDriver) {
            for (a in d.actions) when (a) {
                is PadAction.InsertText -> typed.append(a.text)
                is PadAction.KanaKey -> a.char?.let { typed.append(it) }
                PadAction.Space -> typed.append(' ')
                else -> {}
            }
            d.clear()
        }
        for ((mode, im) in listOf(PadMode.ENGLISH to InputMode.ModeEnglish, PadMode.NUMBER to InputMode.ModeNumber)) {
            val d = driver(mode = im)
            assertEquals(mode, d.engine.padMode)
            val cells = FourWayLayouts.cells(mode)!!
            for ((i, cell) in cells.withIndex()) for ((j, _) in cell.withIndex()) {
                d.holdSector(Sector9.fromIndex(i)); d.flick(Dir4.entries[j]); d.leftRelease()
                if (mode == PadMode.ENGLISH) { d.press(PadButton.L2); d.holdSector(Sector9.fromIndex(i)); d.flick(Dir4.entries[j]); d.leftRelease(); d.release(PadButton.L2) }
            }
            for (sector in FourWayLayouts.layer(mode).keys) d.layer(sector)
            d.tap(PadButton.X)
            collect(d)
        }
        val missing = (0x20..0x7E).map { it.toChar() }.filter { it !in typed }
        assertTrue("not typable in stick mode: $missing", missing.isEmpty())
        assertFalse("° is gone from stick mode", '°' in typed)
    }

    // ------------------------------------------------------------------ R3 types nothing

    @Test
    fun r3_no_longer_types_a_space_in_english_or_the_a_row_in_kana() {
        val en = driver(listOf(PadMode.ENGLISH), InputMode.ModeEnglish)
        en.holdSector(Sector9.UP); en.tap(PadButton.R3)
        assertTrue(en.actions.none { it == PadAction.Space || it is PadAction.InsertText })
        val kana = driver(listOf(PadMode.KANA))
        kana.holdSector(Sector9.UP_LEFT); kana.tap(PadButton.R3)
        assertTrue(kana.actions.isEmpty())
        assertEquals(PadRole.NONE, PadConfig(roles = PadConfig.DEFAULT_ROLES + (PadButton.R3 to PadRole.TAP_CENTER)).roleOf(PadButton.R3))
    }

    // ------------------------------------------------------------------ touch digits

    @Test
    fun a_touch_digit_types_like_the_number_mode_digit() {
        // The 数字 path for 7: the ↑ cell 5678, flick →.
        val n = driver(mode = InputMode.ModeNumber)
        n.holdSector(Sector9.UP); n.flick(Dir4.RIGHT)
        val numberPath = n.actions.toList()
        assertEquals(listOf<PadAction>(PadAction.InsertText("7")), numberPath)
        // The touch path in かな while composing か: the same action.
        val k = driver()
        k.holdSector(Sector9.UP_LEFT); k.kanaTap()
        assertEquals("か", k.typed())
        k.clear()
        val r = k.engine.onTouchDigit('7')
        assertEquals(numberPath, r.actions)
        assertTrue(r.note, r.note.contains("touch digit 7"))
        assertEquals("the mode stays", PadMode.KANA, k.engine.padMode)
        // Every digit, in every text mode, also with a hold role down (holds do not change digits).
        for (im in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
            val d = driver(mode = im)
            d.press(PadButton.L2)
            for (c in '0'..'9') assertEquals(listOf<PadAction>(PadAction.InsertText(c.toString())), d.engine.onTouchDigit(c).actions)
        }
        // Not a digit, or the emoji modes: nothing.
        assertTrue(k.engine.onTouchDigit('a').actions.isEmpty())
        k.tap(PadButton.START)
        assertTrue(k.engine.onTouchDigit('1').actions.isEmpty())
    }

    @Test
    fun the_guide_draws_the_kana_layout_in_every_text_mode_with_sticks() {
        for (im in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
            val d = driver(mode = im)
            assertTrue("$im", d.engine.guide.kanaLayout)
        }
        val e = driver(); e.tap(PadButton.START)
        assertFalse(e.engine.guide.kanaLayout)
        val f = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        assertFalse(f.engine.guide.kanaLayout)
    }
}
