package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3, the right stick's click. Amendment 4 §2: the tilt that comes with the click must never turn
 * into a flick. Amendment 18 (v7): R3 no longer types (no あ段, no space); it steps to the next text
 * mode on release, and only when the right stick stayed home with no flick armed. A flick already
 * armed when the click comes is not cancelled any more: it types, and the click is ignored.
 */
class R3CenterTest {

    /** かな only: R3 has nothing to switch to, so the tests can look at the flicks alone. */
    private fun kanaOnly() = PadDriver(PadInputEngine(PadConfig(modes = listOf(PadMode.KANA))))

    @Test
    fun r3_types_nothing_and_steps_to_the_next_mode() {
        val d = PadDriver()
        d.left(-0.707f, -0.707f)        // か
        d.tap(PadButton.R3)
        d.left(0f, 0f)
        assertEquals("", d.typed())
        assertEquals(listOf<PadAction>(PadAction.SwitchMode(com.kazumaproject.core.domain.state.InputMode.ModeEnglish)), d.actions)
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        assertEquals(PadRole.MODE_NEXT, d.engine.config.roleOf(PadButton.R3))
        assertEquals(PadRole.CONFIRM, d.engine.config.roleOf(PadButton.A))
        d.clear()
        d.tap(PadButton.R3)
        assertEquals("R3 in 英字 types no space any more", PadMode.NUMBER, d.engine.padMode)
        assertTrue(d.actions.none { it == PadAction.Space })
    }

    @Test
    fun tilt_noise_while_r3_is_pressed_never_types_a_flick() {
        val d = kanaOnly()
        d.left(-0.707f, -0.707f)        // か
        d.press(PadButton.R3)
        // The stick wobbles up to 0.8 while it is pressed down.
        d.right(0.3f, 0.1f); d.right(0.8f, 0.0f); d.right(0.6f, -0.5f); d.right(0.2f, 0f)
        d.release(PadButton.R3)
        d.right(0f, 0f)
        assertEquals("", d.typed())
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 click ignored") })
        // Noise that is still out when R3 comes up, then returns: still nothing.
        d.clear()
        d.press(PadButton.R3)
        d.right(0.7f, 0f)
        d.release(PadButton.R3)
        d.right(0.9f, 0f)               // still out after the release
        d.right(0f, 0f)
        assertEquals("", d.typed())
    }

    @Test
    fun a_real_flick_right_after_r3_still_works() {
        val d = kanaOnly()
        d.left(-0.707f, -0.707f)
        d.tap(PadButton.R3)             // nothing (one mode)
        d.kana(com.kazumaproject.core.domain.state.GestureType.FlickLeft)   // き (→)
        assertEquals("き", d.typed())
        d.clear()
        // R3 released while the stick is already home: the very next flick counts.
        d.press(PadButton.R3); d.right(0.1f, 0f); d.release(PadButton.R3)
        d.kana(com.kazumaproject.core.domain.state.GestureType.FlickTop)    // く (↘)
        assertEquals("く", d.typed())
    }

    @Test
    fun r3_no_longer_cancels_a_flick_in_progress() {
        val d = PadDriver()
        d.left(-0.707f, -0.707f)
        d.right(-1f, 0f)                // flick ← begins (こ on the pentagon)
        d.tap(PadButton.R3)             // a hard flick pushed the stick in: the click is ignored
        d.right(-0.4f, 0f); d.right(0f, 0f)
        assertEquals("こ", d.typed())
        assertEquals("no mode change", PadMode.KANA, d.engine.padMode)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("R3 click ignored") && it.contains("flick") })
    }

    @Test
    fun r3_presses_in_quick_succession() {
        val d = kanaOnly()
        d.left(1f, 0f)                  // は
        d.press(PadButton.R3); d.press(PadButton.A); d.release(PadButton.A); d.release(PadButton.R3)
        d.tap(PadButton.R3); d.tap(PadButton.R3)
        assertEquals("", d.typed())
        assertEquals(1, d.actions.count { it == PadAction.Confirm })
        assertEquals(1, d.actions.size)
    }

    @Test
    fun the_full_stop_is_r2_up() {
        // Amendment 21: the 記号 key is gone; 。 is R2 + the left stick ↑, still the 、。？！ key's flick ←.
        val d = PadDriver()
        d.left(0.1f, 0f); d.leftRelease()
        d.layer(Sector9.UP)
        assertEquals("。", d.typed())
        assertEquals("。 goes out as the 、。？！ key's flick ←", listOf<PadAction>(PadAction.KanaKey(com.kazumaproject.core.domain.key.Key.KeyKutouten, com.kazumaproject.core.domain.state.GestureType.FlickLeft, '。')), d.actions)
        // The ↘ key's slot 0 (↑) is や now.
        d.clear()
        d.left(0.707f, 0.707f); d.kanaTap()
        assertEquals("や", d.typed())
    }

    @Test
    fun guide_legend_names_the_roles() {
        val d = PadDriver()
        val legend = d.engine.guide.legend
        assertEquals("A 決定", legend.first())
        assertTrue(legend.toString(), legend.contains("Y 変換") && legend.contains("B 消す") && legend.contains("X 空白") && legend.contains("R3 英字へ") && legend.contains("L3 数字へ"))
        assertTrue(legend.toString(), legend.contains("L2 濁点") && legend.contains("L1 半濁点") && legend.contains("R1 小文字") && legend.contains("R2 記号") && legend.contains("Start 絵文字"))
        assertTrue("no あ段 any more", legend.none { it.contains("あ段") })
    }
}
