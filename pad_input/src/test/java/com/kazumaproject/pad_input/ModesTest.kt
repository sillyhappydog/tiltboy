package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amendment 12 §2: the 「モード」 / "Modes" setting. R3 / L3 (amendment 18; Select until v7) cycle only the checked text modes. */
class ModesTest {

    private fun driver(vararg modes: PadMode, initial: InputMode = InputMode.ModeJapanese): PadDriver {
        val d = PadDriver(PadInputEngine(PadConfig(modes = modes.toList(), words = TestWords.ja), initial))
        d.left(0.1f, 0f)
        d.clear()
        return d
    }

    private fun PadDriver.switches() = actions.filterIsInstance<PadAction.SwitchMode>().map { it.mode }

    // Amendment 20: Select no longer cycles the modes; R3 (MODE_NEXT) and L3 (MODE_PREVIOUS) do (amendment 18).

    @Test
    fun all_three_modes() {
        val d = driver(PadMode.KANA, PadMode.ENGLISH, PadMode.NUMBER)
        repeat(4) { d.nextMode() }
        assertEquals(listOf(InputMode.ModeEnglish, InputMode.ModeNumber, InputMode.ModeJapanese, InputMode.ModeEnglish), d.switches())
        d.clear()
        repeat(4) { d.tap(PadButton.SELECT) }
        assertTrue("Select switches no mode", d.switches().isEmpty())
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
    }

    @Test
    fun english_and_numbers_only() {
        val d = driver(PadMode.ENGLISH, PadMode.NUMBER, initial = InputMode.ModeEnglish)
        repeat(3) { d.nextMode() }
        assertEquals(listOf(InputMode.ModeNumber, InputMode.ModeEnglish, InputMode.ModeNumber), d.switches())
        assertTrue(d.notes.none { it.contains("kana") })
    }

    @Test
    fun a_single_mode_makes_r3_do_nothing_and_its_pill_empty() {
        val d = driver(PadMode.ENGLISH, initial = InputMode.ModeEnglish)
        d.nextMode(); d.nextMode()
        assertTrue(d.actions.isEmpty())
        assertTrue(d.notes.last().contains("nothing to switch"))
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertEquals("no label: the map draws only the dimmed name", GuideLabels.NONE, d.engine.guide.labels.buttons[PadButton.R3])
        assertEquals("Select is the touch keyboard", "タッチ", d.engine.guide.labels.buttons[PadButton.SELECT])
        // Amendment 20: Start opens the emoji list; B goes back to the one text mode.
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        assertEquals("戻る", d.engine.guide.labels.buttons[PadButton.B])
        d.tap(PadButton.B)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        // Select held long still hides the keyboard.
        d.clear()
        d.press(PadButton.SELECT); d.time += 600; d.poll(); d.release(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.HideKeyboard), d.actions)
    }

    @Test
    fun a_disabled_mode_from_the_touch_keyboard_moves_to_an_enabled_one_on_the_next_event() {
        val d = driver(PadMode.ENGLISH, PadMode.NUMBER, initial = InputMode.ModeJapanese)
        // driver() already sent one stick event: we are in English now.
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.engine.mode = InputMode.ModeJapanese      // the user tapped かな on the touch keyboard
        d.clear()
        d.left(0f, -1f); d.flick(Dir4.LEFT)         // efgh cell, ← (amendment 14)
        assertEquals(InputMode.ModeEnglish, (d.actions.first() as PadAction.SwitchMode).mode)
        assertEquals("e", d.typed())
    }

    @Test
    fun opening_in_the_guide_in_a_disabled_mode_moves_to_an_enabled_one_before_any_event() {
        // v17: Sumire is still in かな, only 英字 and 数字 are checked, no controller event yet.
        val engine = PadInputEngine(PadConfig(modes = listOf(PadMode.ENGLISH, PadMode.NUMBER), words = TestWords.ja), InputMode.ModeJapanese)
        assertEquals(PadMode.KANA, engine.padMode)
        val d = engine.enterEnabledMode()
        assertEquals(PadMode.ENGLISH, engine.padMode)
        assertEquals(listOf<PadAction>(PadAction.SwitchMode(InputMode.ModeEnglish)), d.actions)
        // Already in an enabled mode: nothing happens.
        assertTrue(engine.enterEnabledMode().actions.isEmpty())
        val all = PadInputEngine(PadConfig(words = TestWords.ja), InputMode.ModeJapanese)
        assertTrue(all.enterEnabledMode().actions.isEmpty())
        assertEquals(PadMode.KANA, all.padMode)
    }

    @Test
    fun first_run_defaults_follow_the_system_language() {
        assertEquals(PadConfig.ALL_TEXT_MODES, PadConfig.defaultModes("ja", existingInstall = false))
        assertEquals(listOf(PadMode.ENGLISH, PadMode.NUMBER), PadConfig.defaultModes("en", existingInstall = false))
        assertEquals(listOf(PadMode.ENGLISH, PadMode.NUMBER), PadConfig.defaultModes("de", existingInstall = false))
        assertEquals("existing installs keep all three", PadConfig.ALL_TEXT_MODES, PadConfig.defaultModes("en", existingInstall = true))
        assertEquals("empty means all", PadConfig.ALL_TEXT_MODES, PadConfig(modes = emptyList()).textModes())
        assertEquals("fixed order", listOf(PadMode.KANA, PadMode.NUMBER), PadConfig(modes = listOf(PadMode.NUMBER, PadMode.KANA)).textModes())
    }
}
