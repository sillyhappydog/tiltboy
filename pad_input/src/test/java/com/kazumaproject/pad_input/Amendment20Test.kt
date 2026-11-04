package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 20 (v8): Select = the touch keyboard (tap) / hide (held); Start = Sumire's emoji list,
 * driven by the D-pad and the left stick; no emoji wheel with sticks. (The touch side, a touch on the
 * guide doing nothing, is the app's: PadGuideFitTest and the emulator test.)
 */
class Amendment20Test {
    private fun driver(initial: InputMode = InputMode.ModeJapanese): PadDriver =
        PadDriver(PadInputEngine(PadConfig(words = TestWords.ja), initial)).also { it.left(0.1f, 0f); it.leftRelease(); it.clear() }

    private fun PadDriver.commands() = actions.filterIsInstance<PadAction.EmojiKeyboard>().map { it.command }

    // ------------------------------------------------------------------ 2. Select

    @Test
    fun a_select_tap_asks_for_the_touch_keyboard_each_time_and_switches_no_mode() {
        val d = driver()
        d.tap(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.TouchKeyboard), d.actions)
        d.tap(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.TouchKeyboard, PadAction.TouchKeyboard), d.actions)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals("タッチ", d.engine.guide.labels.buttons[PadButton.SELECT])
        assertEquals("Touch", PadDriver(PadInputEngine(PadConfig(words = TestWords.en))).engine.guide.labels.buttons[PadButton.SELECT])
        // Nothing happens on the press: the tap is decided on release (a hold hides).
        d.clear(); d.press(PadButton.SELECT)
        assertTrue(d.actions.isEmpty())
        d.release(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.TouchKeyboard), d.actions)
    }

    @Test
    fun select_held_600_ms_hides_the_keyboard_and_no_touch_keyboard_follows() {
        val d = driver()
        d.press(PadButton.SELECT)
        d.time += 300; d.poll()
        assertTrue("not yet at 300 ms", d.actions.isEmpty())
        d.time += 300; d.poll()
        assertEquals(listOf<PadAction>(PadAction.HideKeyboard), d.actions)
        d.release(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.HideKeyboard), d.actions)
    }

    @Test
    fun typing_buttons_and_sticks_still_type_while_the_touch_keys_are_up() {
        // The engine has no notion of the touch keys: after Select the next stick / button types as usual
        // (the host brings the guide back on it).
        val d = driver()
        d.tap(PadButton.SELECT); d.clear()
        d.kanaTap()
        assertEquals("あ", d.typed())
        d.clear(); d.tap(PadButton.B)
        assertEquals(listOf<PadAction>(PadAction.Backspace), d.actions)
    }

    // ------------------------------------------------------------------ 3. Start, the emoji list

    @Test
    fun start_opens_the_list_at_once_and_b_or_start_return_to_the_mode_the_user_came_from() {
        for ((initial, steps) in listOf(PadMode.KANA to 0, PadMode.ENGLISH to 1, PadMode.NUMBER to 2)) {
            for (back in listOf(PadButton.B, PadButton.START)) {
                val d = driver()
                repeat(steps) { d.nextMode() }
                assertEquals(initial, d.engine.padMode)
                d.clear()
                d.tap(PadButton.START)
                assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
                assertEquals(listOf(EmojiKeyboardCommand.OPEN), d.commands())
                d.clear()
                d.tap(back)
                assertEquals("$back from the list opened in $initial", initial, d.engine.padMode)
                assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(initial.inputMode!!)), d.actions)
            }
        }
    }

    @Test
    fun the_left_stick_steps_the_selection_like_the_dpad() {
        val d = driver()
        d.tap(PadButton.START); d.clear()
        val c = d.engine.config
        // One step per tilt past the select threshold, in the stick's four-way direction.
        d.left(1f, 0f)
        assertEquals(listOf(EmojiKeyboardCommand.MOVE_RIGHT), d.commands())
        d.left(0.95f, 0.1f)
        assertEquals("still the same tilt: no second step", 1, d.commands().size)
        d.left(0f, 0f)
        d.left(0f, 1f); d.left(0f, 0f)
        d.left(-1f, 0f); d.left(0f, 0f)
        d.left(0f, -1f); d.left(0f, 0f)
        assertEquals(listOf(EmojiKeyboardCommand.MOVE_RIGHT, EmojiKeyboardCommand.MOVE_DOWN, EmojiKeyboardCommand.MOVE_LEFT, EmojiKeyboardCommand.MOVE_UP), d.commands())
        // Between the release and the select threshold nothing new happens (hysteresis).
        d.clear()
        d.left(c.leftSelectThreshold - 0.05f, 0f)
        assertTrue("under the select threshold: no step", d.commands().isEmpty())
        d.left(c.leftSelectThreshold + 0.05f, 0f)
        d.left((c.leftSelectThreshold + c.leftReleaseThreshold) / 2f, 0f)
        d.left(c.leftSelectThreshold + 0.05f, 0f)
        assertEquals("one step: it never came home", listOf(EmojiKeyboardCommand.MOVE_RIGHT), d.commands())
        // Turning to another direction past the threshold is a new step.
        d.left(0f, 1f)
        assertEquals(listOf(EmojiKeyboardCommand.MOVE_RIGHT, EmojiKeyboardCommand.MOVE_DOWN), d.commands())
        d.left(0f, 0f)
    }

    @Test
    fun the_left_stick_repeats_while_held_with_the_backspace_timings() {
        val d = driver()
        d.tap(PadButton.START); d.clear()
        val c = d.engine.config
        d.left(0f, 1f)
        val t0 = d.time
        assertTrue(d.engine.isRepeating)
        d.time = t0 + c.repeatDelayMs - 1; d.poll()
        assertEquals("no repeat before the delay", 1, d.commands().size)
        d.time = t0 + c.repeatDelayMs; d.poll()
        assertEquals(2, d.commands().size)
        d.time += c.repeatIntervalMs - 1; d.poll()
        assertEquals(2, d.commands().size)
        d.time += 1; d.poll()
        d.time += c.repeatIntervalMs; d.poll()
        assertEquals(List(4) { EmojiKeyboardCommand.MOVE_DOWN }, d.commands())
        // Home: the repeat stops.
        d.left(0f, 0f)
        assertTrue(!d.engine.isRepeating)
        d.time += 1000; d.poll()
        assertEquals(4, d.commands().size)
    }

    @Test
    fun a_stick_already_out_when_the_list_opens_waits_for_home() {
        val d = driver()
        d.left(0f, -1f)                 // selecting さ on the grid
        d.tap(PadButton.START); d.clear()
        d.left(0f, -0.95f); d.left(0.1f, -1f)
        assertTrue("no step from the tilt that was there before", d.commands().isEmpty())
        d.left(0f, 0f)
        d.left(0f, -1f)
        assertEquals(listOf(EmojiKeyboardCommand.MOVE_UP), d.commands())
    }

    @Test
    fun a_inserts_l2_r2_change_the_category_and_the_rest_does_nothing() {
        val d = driver()
        d.tap(PadButton.START); d.clear()
        d.tap(PadButton.R2); d.tap(PadButton.L2); d.tap(PadButton.A)
        assertEquals(listOf(EmojiKeyboardCommand.NEXT_TAB, EmojiKeyboardCommand.PREVIOUS_TAB, EmojiKeyboardCommand.SELECT), d.commands())
        d.clear()
        d.right(1f, 0f); d.right(0f, 0f); d.right(0f, -1f); d.right(0f, 0f)
        d.tap(PadButton.L3); d.tap(PadButton.R3); d.tap(PadButton.X); d.tap(PadButton.Y)
        assertTrue(d.actions.toString(), d.actions.isEmpty())
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        val b = d.engine.guide.labels.buttons
        assertEquals("入れる", b[PadButton.A]); assertEquals("戻る", b[PadButton.B]); assertEquals("戻る", b[PadButton.START])
        assertEquals("前のカテゴリー", b[PadButton.L2]); assertEquals("次のカテゴリー", b[PadButton.R2])
        for (x in listOf(PadButton.X, PadButton.Y, PadButton.L3, PadButton.R3)) assertEquals("$x", GuideLabels.NONE, b[x])
    }

    @Test
    fun the_kana_to_emoji_scenario_of_the_emulator_test() {
        // Start → left stick → A inserts an emoji → B back in かな.
        val d = driver()
        d.tap(PadButton.START)
        d.left(1f, 0f); d.left(0f, 0f)
        d.tap(PadButton.A)
        d.tap(PadButton.B)
        assertEquals(
            listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.OPEN), PadAction.EmojiKeyboard(EmojiKeyboardCommand.MOVE_RIGHT),
                PadAction.EmojiKeyboard(EmojiKeyboardCommand.SELECT), PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(InputMode.ModeJapanese)),
            d.actions,
        )
        assertEquals(PadMode.KANA, d.engine.padMode)
        d.clear(); d.kanaTap()
        assertEquals("かな types again", "あ", d.typed())
    }

    @Test
    fun the_wheel_is_never_entered_with_sticks() {
        val d = driver()
        val seen = mutableSetOf<PadMode>()
        for (b in listOf(PadButton.START, PadButton.START, PadButton.R3, PadButton.START, PadButton.B, PadButton.L3, PadButton.SELECT, PadButton.START, PadButton.SELECT)) {
            d.tap(b); seen += d.engine.padMode
        }
        assertTrue(seen.toString(), PadMode.EMOJI !in seen)
    }
}
