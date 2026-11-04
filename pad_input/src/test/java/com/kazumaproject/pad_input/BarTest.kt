package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 10's bar, after amendment 18 (v7). With sticks the L3 bar is gone from the text modes:
 * L3 steps the text mode, and the 英字 / 数字 bar symbols are on the R2 layer (Amendment18Test).
 * The bar lives on in two places: the D-pad fallback (L1 held; kana わ を ん ー 〜 ！ ？ 、 。,
 * 英字 / 数字 their symbols with the centre) and the emoji wheel's page 2 (L3 = MODE_PREVIOUS:
 * a tap for one emoji, held for as long as it is held).
 */
class BarTest {

    @Test
    fun no_bar_in_the_text_modes_with_sticks() {
        for (mode in listOf(PadMode.KANA, PadMode.ENGLISH, PadMode.NUMBER)) {
            val d = PadDriver(PadInputEngine(PadConfig(modes = listOf(mode))))
            d.left(0.1f, 0f)
            d.clear()
            assertEquals(mode, d.engine.padMode)
            d.press(PadButton.L3)
            assertFalse("$mode: held L3 is no bar", d.engine.barActive)
            d.release(PadButton.L3)
            assertFalse("$mode: a tap arms nothing", d.engine.barActive)
            val g = d.engine.guide
            assertEquals("$mode: no bar on the guide", "", g.barButtons)
            assertEquals("", g.barLabel)
            assertTrue(d.actions.isEmpty())
        }
    }

    @Test
    fun the_fallback_kana_bar_is_the_users_table_and_every_character_is_a_touch_keys_own() {
        val bar = KanaGrid.bar(PadMode.KANA)
        assertEquals("わ", bar.charAt(Sector9.CENTER))
        assertEquals("を", bar.charAt(Sector9.LEFT))
        assertEquals("ん", bar.charAt(Sector9.UP))
        assertEquals("ー", bar.charAt(Sector9.RIGHT))
        assertEquals("〜", bar.charAt(Sector9.DOWN))
        assertEquals("！", bar.charAt(Sector9.UP_LEFT))
        assertEquals("？", bar.charAt(Sector9.UP_RIGHT))
        assertEquals("、", bar.charAt(Sector9.DOWN_LEFT))
        assertEquals("。", bar.charAt(Sector9.DOWN_RIGHT))
        for ((sector, slot) in bar.slots) {
            assertTrue("$sector comes from the わ or 、。 key", slot.key == Key.KeyWA || slot.key == Key.KeyKutouten)
            assertEquals("$sector", slot.char, KanaGrid.charFor(slot.key!!, slot.gesture!!, PadMode.KANA))
        }
        // The D-pad fallback still types it (L1 held, A = centre, X = ←).
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.press(PadButton.L1); d.tap(PadButton.A); d.tap(PadButton.X); d.release(PadButton.L1)
        assertEquals("わを", d.typed())
        assertEquals(listOf<PadAction>(PadAction.KanaKey(Key.KeyWA, GestureType.Tap, 'わ'), PadAction.KanaKey(Key.KeyWA, GestureType.FlickLeft, 'を')), d.actions)
    }

    @Test
    fun the_fallback_english_and_number_bars_keep_their_centre() {
        // Amendment 14 tables; amendment 18 keeps them whole for the D-pad fallback (° and " in the centre).
        val en = KanaGrid.bar(PadMode.ENGLISH)
        assertEquals(listOf("\"", "(", ";", ")", "_", "&", "*", "=", "+"), KanaGrid.BAR_ORDER.map { en.charAt(it) })
        val num = KanaGrid.bar(PadMode.NUMBER)
        assertEquals(listOf("°", "[", "^", "]", "|", "{", "}", "\\", "`"), KanaGrid.BAR_ORDER.map { num.charAt(it) })
        assertTrue((en.slots.values + num.slots.values).all { it.key == null })
        // Typing in the fallback: L1 held, A = centre, X = ←, Y = ↑.
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.tap(PadButton.R1)                       // the fallback's mode button: 英字
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.press(PadButton.L1); d.tap(PadButton.A); d.tap(PadButton.X); d.tap(PadButton.Y); d.release(PadButton.L1)
        assertEquals("\"(;", d.typed())
        d.clear()
        d.tap(PadButton.R1)                       // 数字
        d.press(PadButton.L1); d.tap(PadButton.A); d.release(PadButton.L1)
        assertEquals("°", d.typed())
    }

    @Test
    fun fallback_emoji_wheel_page_2_with_L1() {
        // Amendment 20: the wheel is the D-pad fallback's only; its page 2 is the fallback's L1 bar.
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK, words = TestWords.ja)))
        d.engine.setEmojiHistory(listOf("🐱"))
        d.tap(PadButton.R3)
        assertEquals(PadMode.EMOJI, d.engine.padMode)
        d.press(PadButton.L1); d.press(PadButton.DPAD_UP); d.tap(PadButton.X); d.release(PadButton.L1)
        assertEquals(listOf<PadAction>(PadAction.InsertEmoji(d.engine.emojiSlots[32])), d.actions)
        d.clear()
        d.tap(PadButton.X)
        assertEquals("L1 let go: page 1", listOf<PadAction>(PadAction.InsertEmoji("🐱")), d.actions)
        d.press(PadButton.L1)
        val g = d.engine.guide
        assertEquals("次の 32 個", g.barLabel)
        assertEquals("L1", g.barButtons)
        assertEquals("次の 32 個", g.labels.buttons[PadButton.L1])
        assertEquals(PadMode.EMOJI, d.engine.padMode)
    }

    @Test
    fun the_log_names_the_angle() {
        assertEquals(0f, StickGeometry.angleDeg(1f, 0f), 0.01f)
        assertEquals(90f, StickGeometry.angleDeg(0f, -1f), 0.01f)
        assertEquals(180f, StickGeometry.angleDeg(-1f, 0f), 0.01f)
        assertEquals(270f, StickGeometry.angleDeg(0f, 1f), 0.01f)
    }
}
