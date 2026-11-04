package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiModeTest {

    @Test
    fun history_fills_the_wheel_first_then_defaults_without_duplicates() {
        val history = listOf("🐱", "🍣", "🐱", " ", "🎮")
        val slots = EmojiWheel.slots(history)
        assertEquals(64, slots.size)
        assertEquals(listOf("🐱", "🍣", "🎮"), slots.take(3))
        assertEquals(64, slots.toSet().size)
        assertEquals("😀", slots[3]) // first default
        assertTrue(slots.indexOf("🐱") == 0 && slots.count { it == "🐱" } == 1)
        // More history than slots: cut to 64, oldest dropped.
        val many = (0 until 100).map { "e$it" }
        assertEquals(64, EmojiWheel.slots(many).size)
        assertEquals("e63", EmojiWheel.slots(many).last())
    }

    @Test
    fun page_layout_is_8_petals_of_4_in_order() {
        val slots = EmojiWheel.slots((0 until 64).map { "e$it" })
        val page0 = EmojiWheel.page(slots, 0)
        assertEquals(8, page0.size)
        assertEquals(listOf("e0", "e1", "e2", "e3"), page0[0])
        assertEquals(listOf("e28", "e29", "e30", "e31"), page0[7])
        assertEquals(listOf("e32", "e33", "e34", "e35"), EmojiWheel.page(slots, 1)[0])
        // Short list → empty strings, never a crash.
        assertEquals(listOf("", "", "", ""), EmojiWheel.page(listOf("a"), 1)[7])
    }

    @Test
    fun select_is_the_touch_keyboard_and_never_switches_modes() {
        // Amendment 20: a Select tap switches between the guide and the touch keyboard (the host's job).
        val d = PadDriver()
        d.left(0.1f, 0f)
        d.tap(PadButton.SELECT); d.tap(PadButton.SELECT); d.tap(PadButton.SELECT)
        assertEquals(listOf<PadAction>(PadAction.TouchKeyboard, PadAction.TouchKeyboard, PadAction.TouchKeyboard), d.actions)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals("タッチ", d.engine.guide.labels.buttons[PadButton.SELECT])
        d.clear()
        repeat(6) { d.tap(PadButton.SELECT) }
        assertTrue("never a mode switch", d.actions.none { it is PadAction.SwitchMode })
    }

    @Test
    fun start_opens_the_list_from_any_mode_and_b_or_start_go_back_to_that_mode() {
        val d = PadDriver()
        d.left(0.1f, 0f)
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        assertEquals(listOf<PadAction>(PadAction.EmojiKeyboard(EmojiKeyboardCommand.OPEN)), d.actions)
        d.clear()
        d.tap(PadButton.B)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(InputMode.ModeJapanese)), d.actions)
        d.clear()
        // From English, Start → the list; Start → back to English (the mode we came from).
        d.nextMode(); d.clear()
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        assertEquals("the Sumire mode underneath stays English until we leave", InputMode.ModeEnglish, d.engine.mode)
        assertEquals("戻る", d.engine.guide.labels.buttons[PadButton.START])
        assertEquals("戻る", d.engine.guide.labels.buttons[PadButton.B])
        assertEquals("タッチ", d.engine.guide.labels.buttons[PadButton.SELECT])
        d.tap(PadButton.START)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertEquals(InputMode.ModeEnglish, d.engine.mode)
        assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.OPEN), PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(InputMode.ModeEnglish)), d.actions)
        assertTrue("never the wheel", d.notes.none { it.contains("mode → emoji") && !it.contains("list") })
    }

    @Test
    fun r3_is_no_longer_the_emoji_shortcut() {
        val d = PadDriver()
        d.tap(PadButton.R3)
        // Amendment 18: R3 steps to the next text mode, never into 絵文字 (that is Start).
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.tap(PadButton.R3); d.tap(PadButton.R3)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals("", d.typed())
    }

    @Test
    fun with_sticks_nothing_reaches_the_emoji_wheel() {
        // Amendment 20: the wheel (PadMode.EMOJI) is gone from the stick input.
        val d = PadDriver()
        d.left(0.1f, 0f)
        for (b in listOf(PadButton.START, PadButton.START, PadButton.L3, PadButton.R3, PadButton.SELECT, PadButton.START, PadButton.B)) {
            d.tap(b)
            assertTrue("$b: ${d.engine.padMode}", d.engine.padMode != PadMode.EMOJI)
        }
        // A wheel left over from the D-pad layout ends at the first stick motion.
        val f = PadDriver(PadInputEngine(PadConfig(words = TestWords.ja)))
        f.engine.setDeviceHasSticks(false)
        f.tap(PadButton.R3)
        assertEquals(PadMode.EMOJI, f.engine.padMode)
        f.clear()
        f.left(0.5f, 0f)
        assertEquals(PadMode.KANA, f.engine.padMode)
        assertEquals(PadAction.SwitchMode(InputMode.ModeJapanese), f.actions.first())
    }

    @Test
    fun guide_on_the_fallback_wheel_shows_emoji_petals() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK, words = TestWords.ja)))
        d.engine.setEmojiHistory(listOf("🐱"))
        d.tap(PadButton.R3)
        d.press(PadButton.DPAD_RIGHT)
        val g = d.engine.guide
        assertEquals(PadMode.EMOJI, g.padMode)
        assertTrue(g.isEmoji)
        assertEquals(8, g.petals!!.size)
        assertEquals("🐱", g.petals!![0][0])
        assertEquals(2, g.selectedPetal)
        assertNull(g.gridKeys)
        assertTrue(g.legend.toString(), g.legend.contains("Start 一覧"))
        assertEquals("次の 32 個", g.labels.buttons[PadButton.L1])
        assertEquals("次の 32 個", g.barLabel)
    }

    @Test
    fun the_list_dpad_l2_r2_a_and_the_buttons_that_do_nothing() {
        val d = PadDriver()
        d.left(0.1f, 0f); d.leftRelease()
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        assertEquals(listOf<PadAction>(PadAction.EmojiKeyboard(EmojiKeyboardCommand.OPEN)), d.actions)
        d.clear()
        d.tap(PadButton.DPAD_RIGHT); d.tap(PadButton.DPAD_DOWN); d.tap(PadButton.R2); d.tap(PadButton.L2); d.tap(PadButton.A)
        assertEquals(
            listOf(EmojiKeyboardCommand.MOVE_RIGHT, EmojiKeyboardCommand.MOVE_DOWN, EmojiKeyboardCommand.NEXT_TAB, EmojiKeyboardCommand.PREVIOUS_TAB, EmojiKeyboardCommand.SELECT),
            d.actions.map { (it as PadAction.EmojiKeyboard).command },
        )
        d.clear()
        // The right stick, X, Y, L3, R3 and L1 / R1 do nothing in the list.
        d.right(-1f, 0f); d.right(0f, 0f); d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.L3); d.tap(PadButton.R3); d.tap(PadButton.L1); d.tap(PadButton.R1)
        assertTrue(d.actions.toString(), d.actions.isEmpty())
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        // Select tap: close the list, back to かな, then the touch keyboard.
        d.tap(PadButton.SELECT)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(InputMode.ModeJapanese), PadAction.TouchKeyboard), d.actions)
        d.clear()
        // Select held long in the list: close it and hide the keyboard.
        d.tap(PadButton.START); d.clear(); d.press(PadButton.SELECT)
        d.time += 600; d.poll()
        assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.SwitchMode(InputMode.ModeJapanese), PadAction.HideKeyboard), d.actions)
        d.release(PadButton.SELECT)
        assertEquals(3, d.actions.size)
        assertEquals(PadMode.KANA, d.engine.padMode)
    }

    @Test
    fun host_mode_sync_does_not_kick_us_out_of_the_emoji_list() {
        val d = PadDriver()
        d.tap(PadButton.START)
        d.engine.mode = InputMode.ModeJapanese // host re-syncs the same Sumire mode
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        d.engine.mode = InputMode.ModeEnglish // the user tapped 文字 on the touch keyboard
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
    }

    @Test
    fun fallback_emoji_wheel_and_list_keep_the_v1_table() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.engine.setEmojiHistory(listOf("🐱", "🍣"))
        d.tap(PadButton.R3) // fallback: R3 = emoji wheel / back (no stick to flick with)
        assertEquals(PadMode.EMOJI, d.engine.padMode)
        d.press(PadButton.DPAD_UP); d.tap(PadButton.Y); d.release(PadButton.DPAD_UP)
        assertEquals(listOf<PadAction>(PadAction.InsertEmoji("🍣")), d.actions)
        d.clear()
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI_LIST, d.engine.padMode)
        d.tap(PadButton.DPAD_LEFT)
        assertEquals(PadAction.EmojiKeyboard(EmojiKeyboardCommand.MOVE_LEFT), d.actions.last())
        d.clear()
        d.tap(PadButton.START)
        assertEquals(PadMode.EMOJI, d.engine.padMode)
        d.tap(PadButton.R3)
        assertEquals(PadMode.KANA, d.engine.padMode)
    }
}
