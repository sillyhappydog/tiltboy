package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendments 7 and 10: shoulder hold roles, taken from the touch ゛゜小 key's table, applied the
 * moment the character is typed. Amendment 16 defaults: L2 = 濁点 (英字: 大文字), L1 = 半濁点,
 * R1 = 小文字, R2 = や・わ. Grid: ↖ か ↑ さ ↗ た / ← な ・あ → は / ↙ ま ↓ ら ↘ 記号. Amendment 18:
 * kana is typed on the pentagon ([PadDriver.kana]); L3 / R3 step the mode.
 */
class HoldRolesTest {

    private fun kana(): PadDriver = PadDriver().also { it.left(0.1f, 0f) }

    @Test
    fun defaults_are_the_amendment_16_table() {
        val c = PadConfig()
        assertEquals(PadRole.HOLD_DAKUTEN, c.roleOf(PadButton.L2))
        assertEquals(PadRole.HOLD_HANDAKUTEN, c.roleOf(PadButton.L1))
        assertEquals(PadRole.HOLD_SMALL, c.roleOf(PadButton.R1))
        assertEquals(PadRole.YA_WA_LAYER, c.roleOf(PadButton.R2))
        assertEquals(PadRole.MODE_PREVIOUS, c.roleOf(PadButton.L3))
        assertEquals(PadRole.MODE_NEXT, c.roleOf(PadButton.R3))
        assertEquals("amendment 20", PadRole.TOUCH_KEYBOARD, c.roleOf(PadButton.SELECT))
        assertEquals(PadRole.EMOJI_JUMP, c.roleOf(PadButton.START))
        assertEquals(PadRole.CONFIRM, c.roleOf(PadButton.A))
        assertEquals(PadRole.CONVERT_NEXT, c.roleOf(PadButton.Y))
        assertEquals(PadRole.BACKSPACE, c.roleOf(PadButton.B))
        assertEquals(PadRole.SPACE, c.roleOf(PadButton.X))
    }

    @Test
    fun ga_with_L2_ka_and_up() {
        val d = kana()
        d.press(PadButton.L2); d.left(-0.707f, -0.707f); d.kanaTap(); d.release(PadButton.L2)
        assertEquals("が", d.typed())
        assertEquals("transformed characters go out as InsertText", listOf<PadAction>(PadAction.InsertText("が")), d.actions)
    }

    @Test
    fun small_tsu_with_R1_ta_and_flick_to_tsu() {
        val d = kana()
        d.press(PadButton.R1); d.left(0.707f, -0.707f); d.kana(GestureType.FlickTop); d.release(PadButton.R1)
        assertEquals("っ", d.typed())
    }

    @Test
    fun pa_with_L1_ha_and_up() {
        val d = kana()
        d.press(PadButton.L1); d.left(1f, 0f); d.kanaTap(); d.release(PadButton.L1)
        assertEquals("ぱ", d.typed())
    }

    @Test
    fun small_ya_with_R1_and_the_ya_wa_key() {
        // Amendment 21: や/わ is the ↘ grid key; R1 + ↑ = ゃ (v5–v8: R1 + R2 + the left stick).
        val d = kana()
        d.press(PadButton.R1); d.yaWa(0); d.release(PadButton.R1)
        assertEquals("ゃ", d.typed())
        assertEquals(listOf<PadAction>(PadAction.InsertText("ゃ")), d.actions)
    }

    @Test
    fun gakkou() {
        val d = kana()
        d.press(PadButton.L2); d.left(-0.707f, -0.707f); d.kanaTap(); d.release(PadButton.L2)   // が
        d.press(PadButton.R1); d.left(0.707f, -0.707f); d.kana(GestureType.FlickTop); d.release(PadButton.R1)        // っ
        d.left(-0.707f, -0.707f); d.kana(GestureType.FlickBottom)                                                     // こ
        d.leftRelease(); d.kana(GestureType.FlickTop)                                                                // う
        assertEquals("がっこう", d.typed())
    }

    @Test
    fun a_hold_that_does_not_apply_types_the_plain_character() {
        val d = kana()
        d.press(PadButton.L2); d.leftRelease(); d.kanaTap(); d.release(PadButton.L2)              // L2 + あ
        assertEquals("あ", d.typed())
        assertTrue("plain characters stay KanaKey", d.actions.single() is PadAction.KanaKey)
        d.clear()
        d.press(PadButton.L1); d.left(-0.707f, -0.707f); d.kanaTap(); d.release(PadButton.L1)      // L1 + か: no 半濁点
        assertEquals("か", d.typed())
    }

    @Test
    fun first_hold_pressed_wins() {
        val d = kana()
        d.left(1f, 0f) // は
        d.press(PadButton.L2); d.press(PadButton.L1); d.kanaTap(); d.release(PadButton.L1); d.release(PadButton.L2)
        assertEquals("ば", d.typed())
        d.clear()
        d.press(PadButton.L1); d.press(PadButton.L2); d.kanaTap(); d.release(PadButton.L2); d.release(PadButton.L1)
        assertEquals("ぱ", d.typed())
    }

    @Test
    fun L2_is_upper_case_in_english_and_nothing_in_numbers() {
        val d = kana()
        d.nextMode() // 英字
        d.clear()
        // Amendment 14: the ↑ cell is efgh (← ↑ → ↓).
        d.press(PadButton.L2); d.left(0f, -1f); d.flick(Dir4.LEFT); d.flick(Dir4.UP); d.release(PadButton.L2)
        d.flick(Dir4.LEFT)
        assertEquals("EFe", d.typed())
        // Amendment 22: L1 (半濁点) held in 英字 is 数字's layout: the ↑ cell is 5678, ← = 5.
        d.clear()
        d.press(PadButton.L1); d.flick(Dir4.LEFT); d.release(PadButton.L1)
        assertEquals("5", d.typed())
        d.nextMode() // 数字
        d.clear()
        d.press(PadButton.L2); d.left(0f, -1f); d.flick(Dir4.LEFT); d.release(PadButton.L2)
        assertEquals("5", d.typed())
        assertTrue(d.notes.any { it.contains("nothing in number") })
    }

    @Test
    fun small_wa_with_R1_and_the_ya_wa_key() {
        val d = kana()
        d.press(PadButton.R1); d.yaWa(3); d.release(PadButton.R1) // ↓ わ → ゎ
        assertEquals("ゎ", d.typed())
        assertEquals(listOf<PadAction>(PadAction.InsertText("ゎ")), d.actions)
    }

    @Test
    fun guide_shows_transformed_characters_while_held_and_dims_the_rest() {
        val d = kana()
        d.left(-0.707f, -0.707f)
        d.press(PadButton.L2)
        val g = d.engine.guide
        assertEquals(PadRole.HOLD_DAKUTEN, g.holdRole)
        assertEquals(listOf("が", "ざ", "だ", "な", "あ", "ば", "ま", "ら", "や/わ"), g.gridLabels)
        assertEquals(listOf(false, false, false, true, true, false, true, true, true), g.gridDim)
        assertEquals(KanaGrid.FlickChars("が", "ぎ", "ぐ", "げ", "ご"), g.flickChars)
        assertEquals("が", g.preview)
        assertTrue(PadButton.L2 in g.heldButtons)
        d.release(PadButton.L2)
        d.press(PadButton.R1)
        val r = d.engine.guide
        assertEquals(listOf("か", "さ", "た", "な", "ぁ", "は", "ま", "ら", "ゃ/ゎ"), r.gridLabels)
        assertEquals(KanaGrid.FlickChars("か", "き", "く", "け", "こ"), r.flickChars)
        d.release(PadButton.R1)
        d.press(PadButton.L1)
        val h = d.engine.guide
        assertEquals(PadRole.HOLD_HANDAKUTEN, h.holdRole)
        assertEquals(listOf("か", "さ", "た", "な", "あ", "ぱ", "ま", "ら", "や/わ"), h.gridLabels)
        assertEquals(listOf(true, true, true, true, true, false, true, true, true), h.gridDim)
        d.release(PadButton.L1)
        assertEquals(null, d.engine.guide.holdRole)
        assertEquals(listOf("か", "さ", "た", "な", "あ", "は", "ま", "ら", "や/わ"), d.engine.guide.gridLabels)
    }

    @Test
    fun guide_labels_for_the_new_defaults() {
        val g = kana().engine.guide
        assertEquals("濁点", g.labels.buttons[PadButton.L2])
        assertEquals("半濁点", g.labels.buttons[PadButton.L1])
        assertEquals("小文字", g.labels.buttons[PadButton.R1])
        assertEquals("amendment 21: R2 is 記号 in かな too", "記号", g.labels.buttons[PadButton.R2])
        assertEquals("タッチ", g.labels.buttons[PadButton.SELECT])
        assertEquals("絵文字", g.labels.buttons[PadButton.START])
        assertEquals("決定", g.labels.buttons[PadButton.A])
        assertEquals("変換", g.labels.buttons[PadButton.Y])
        assertEquals("消す", g.labels.buttons[PadButton.B])
        assertEquals("空白", g.labels.buttons[PadButton.X])
        assertEquals("R3 goes to the next mode", "英字へ", g.labels.buttons[PadButton.R3])
        assertEquals("L3 goes to the previous mode", "数字へ", g.labels.buttons[PadButton.L3])
        assertEquals("", g.barLabel)
        assertEquals("", g.barButtons)
        assertEquals("A 決定", g.legend.first())
        assertTrue(g.legend.toString(), g.legend.contains("Y 変換") && g.legend.contains("L2 濁点") && g.legend.contains("L1 半濁点") &&
            g.legend.contains("R1 小文字") && g.legend.contains("R2 記号") && g.legend.contains("R3 英字へ") && g.legend.contains("L3 数字へ"))
    }

    @Test
    fun dpad_moves_candidates_both_ways_while_they_are_shown() {
        val d = kana()
        d.engine.candidatesShown = true
        d.tap(PadButton.DPAD_LEFT); d.tap(PadButton.DPAD_RIGHT)
        assertEquals(listOf(PadAction.Dpad(Dir4.LEFT), PadAction.Dpad(Dir4.RIGHT)), d.actions)
        assertTrue(d.notes.all { it.contains("candidate") })
    }
}
