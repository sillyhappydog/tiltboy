package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The hold role is latched when a right-stick flick goes out, together with the key (JoyFlick:
 * users bring the sticks, and so the shoulder, back to neutral almost at the same time). Letting
 * go of L2 (濁点 since amendment 16) a few ms before the right stick is home must still give ぎ, not
 * き. Amendment 18: kana is typed on the pentagon (→ い段 at 18°, ↘ う段 at −54°).
 */
class LatchedHoldRoleTest {

    private val I = 18.0
    private val U = -54.0

    private fun kana(): PadDriver = PadDriver().also { it.left(0.1f, 0f); it.clear() }

    /** Push the right stick out toward [deg] (past the flick threshold) without bringing it back. */
    private fun PadDriver.out(deg: Double) {
        val (x, y) = PadDriver.unitAt(deg)
        right(x * 0.5f, y * 0.5f)
        right(x * 0.95f, y * 0.95f)
    }

    /** Bring the right stick back home (under the return threshold): the flick is typed. */
    private fun PadDriver.home(deg: Double) {
        val (x, y) = PadDriver.unitAt(deg)
        right(x * 0.4f, y * 0.4f)
        right(0f, 0f)
    }

    @Test
    fun l2_let_go_before_the_stick_is_home_still_gives_gi() {
        val d = kana()
        d.press(PadButton.L2)
        d.left(-0.707f, -0.707f)        // か key
        d.out(I)
        d.release(PadButton.L2)         // a few ms early
        d.home(I)
        assertEquals("ぎ", d.typed())
    }

    @Test
    fun r1_let_go_early_still_gives_the_small_kana() {
        val d = kana()
        d.press(PadButton.R1); d.left(0.707f, -0.707f); d.out(U); d.release(PadButton.R1); d.home(U)     // つ → っ (た key)
        d.leftRelease()
        d.press(PadButton.R1); d.out(U); d.release(PadButton.R1); d.home(U)                               // う → ぅ (あ key)
        assertEquals("っぅ", d.typed())
        // The あ key's あ段 (↑) with R1 held: ぁ.
        d.clear()
        d.press(PadButton.R1); d.kanaTap(); d.release(PadButton.R1)
        assertEquals("ぁ", d.typed())
    }

    @Test
    fun a_hold_pressed_after_the_flick_went_out_does_not_change_it() {
        val d = kana()
        d.left(-0.707f, -0.707f)
        d.out(I)
        d.press(PadButton.L2)
        d.home(I)
        d.release(PadButton.L2)
        assertEquals("き", d.typed())
        d.clear()
        d.press(PadButton.L2); d.flickAt(I); d.release(PadButton.L2)
        assertEquals("the next flick reads the role again", "ぎ", d.typed())
    }

    @Test
    fun l2_let_go_early_in_english_still_gives_the_capital() {
        val d = kana()
        d.nextMode(); d.clear()   // 英字
        d.press(PadButton.L2); d.left(0f, -1f); d.out(270.0); d.release(PadButton.L2); d.home(270.0)
        assertEquals("H", d.typed())
    }

    @Test
    fun the_guide_shows_the_latched_role_while_the_flick_is_out() {
        val d = kana()
        d.press(PadButton.L2); d.left(-0.707f, -0.707f); d.out(I); d.release(PadButton.L2)
        val g = d.engine.guide
        assertEquals(1, g.pendingSlot)
        assertEquals("ぎ", g.flickChars!!.left)
        d.home(I)
        assertEquals("back to the plain table once the flick is typed", "き", d.engine.guide.flickChars!!.left)
    }
}
