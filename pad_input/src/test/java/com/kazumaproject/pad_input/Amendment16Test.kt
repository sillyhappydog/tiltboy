package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 16 (v5), the test list of the amendment, with the default pentagon right stick:
 * the あ key in the left stick's centre, the 記号 key, the R2 や・わ layer on the left stick, the
 * new shoulder defaults, no stick clicks for kana.
 */
class Amendment16Test {

    /** The default setup: pentagon right stick, the stick at rest. */
    private fun kana(): PadDriver = PadDriver().also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }

    // Pentagon slots: ↑ (あ段) → (い段) ↘ (う段) ↙ (え段) ← (お段).
    private val UP = 90.0
    private val RIGHT = 18.0
    private val DOWN_RIGHT = -54.0
    private val DOWN_LEFT = -126.0
    private val LEFT = 162.0

    @Test
    fun centre_up_is_a_and_centre_down_right_is_u() {
        val d = kana()
        d.flickAt(UP)
        d.flickAt(DOWN_RIGHT)
        assertEquals("あう", d.typed())
        assertEquals(listOf(Key.KeyA, Key.KeyA), d.kanaKeys().map { it.key })
    }

    @Test
    fun up_left_up_is_ka() {
        val d = kana()
        d.holdSector(Sector9.UP_LEFT); d.flickAt(UP)
        assertEquals("か", d.typed())
    }

    @Test
    fun the_down_right_key_is_ya_wa() {
        // Amendment 21 replaced the 記号 key (↘) with the や/わ key: six slots, ↑ や ↗ ゆ ↘ よ ↓ わ ↙ を ↖ ん.
        val d = kana()
        for (slot in 0 until KanaHexagon.SLOTS) d.yaWa(slot)
        assertEquals("やゆよわをん", d.typed())
        // Every one is the touch や or わ key's own character.
        assertEquals(
            listOf(Key.KeyYA to GestureType.Tap, Key.KeyYA to GestureType.FlickTop, Key.KeyYA to GestureType.FlickBottom,
                Key.KeyWA to GestureType.Tap, Key.KeyWA to GestureType.FlickLeft, Key.KeyWA to GestureType.FlickTop),
            d.kanaKeys().map { it.key to it.gesture },
        )
    }

    @Test
    fun ha_is_right_and_ra_is_down() {
        val d = kana()
        d.holdSector(Sector9.RIGHT); d.flickAt(UP)
        d.holdSector(Sector9.DOWN); d.flickAt(UP)
        assertEquals("はら", d.typed())
        assertEquals(Key.KeyHA, KanaGrid.keyFor(Sector9.RIGHT))
        assertEquals(Key.KeyRA, KanaGrid.keyFor(Sector9.DOWN))
    }

    @Test
    fun gakkou_with_gestures_only() {
        val d = kana()
        d.press(PadButton.L2); d.holdSector(Sector9.UP_LEFT); d.flickAt(UP); d.release(PadButton.L2)          // が
        d.press(PadButton.R1); d.holdSector(Sector9.UP_RIGHT); d.flickAt(DOWN_RIGHT); d.release(PadButton.R1)  // っ
        d.holdSector(Sector9.UP_LEFT); d.flickAt(LEFT)                                                       // こ
        d.leftRelease(); d.flickAt(DOWN_RIGHT)                                                               // う
        assertEquals("がっこう", d.typed())
        assertFalse("no stick click", d.notes.any { it.startsWith("R3") || it.startsWith("L3") })
    }

    @Test
    fun the_layer_types_all_eight_directions() {
        // Amendment 21: R2 in かな is 記号: ↖ 〜 ↑ 。 ↗ … / ← ー → 、 / ↙ ？ ↓ ・ ↘ ！.
        val d = kana()
        d.press(PadButton.R2)
        for (s in listOf(Sector9.UP_LEFT, Sector9.UP, Sector9.UP_RIGHT, Sector9.LEFT, Sector9.RIGHT, Sector9.DOWN_LEFT, Sector9.DOWN, Sector9.DOWN_RIGHT)) d.flickLeft8(s)
        d.release(PadButton.R2)
        assertEquals("〜。…ー、？・！", d.typed())
        assertTrue("touch-key symbols go out as touch keys, ・ as text",
            d.actions.filter { it !is PadAction.KanaKey } == listOf<PadAction>(PadAction.InsertText("・")))
    }

    @Test
    fun r1_l2_and_l1_change_nothing_on_the_layer() {
        // Amendment 21: R1 has no effect while R2 is held in かな (the small kana are on the や/わ key).
        val d = kana()
        d.press(PadButton.R1); d.press(PadButton.R2)
        d.flickLeft8(Sector9.UP_LEFT); d.flickLeft8(Sector9.DOWN_LEFT)
        d.release(PadButton.R2); d.release(PadButton.R1)
        assertEquals("〜？", d.typed())
        d.clear()
        d.press(PadButton.L2); d.press(PadButton.R2); d.flickLeft8(Sector9.UP); d.release(PadButton.R2); d.release(PadButton.L2)
        d.press(PadButton.L1); d.press(PadButton.R2); d.flickLeft8(Sector9.RIGHT); d.release(PadButton.R2); d.release(PadButton.L1)
        assertEquals("。、", d.typed())
    }

    @Test
    fun r2_pressed_while_the_left_stick_is_out_arms_nothing_until_it_returns() {
        val d = kana()
        d.leftOut(Sector9.UP_LEFT)                 // the か key selected
        d.press(PadButton.R2)
        d.left(-0.5f, -0.9f); d.left(-0.2f, -0.95f) // still out, moving
        d.left(-0.25f, -0.25f)                     // 0.35: not yet under 0.3
        assertTrue("nothing armed or typed", d.actions.isEmpty())
        assertNull(d.engine.guide.pendingLayer)
        d.left(0f, 0f)                             // home: ready
        assertTrue(d.actions.isEmpty())
        d.flickLeft8(Sector9.DOWN)                 // now a real layer flick
        d.release(PadButton.R2)
        assertEquals("・", d.typed())
    }

    @Test
    fun r2_released_before_the_stick_returns_still_types() {
        val d = kana()
        d.press(PadButton.R2)
        d.leftOut(Sector9.DOWN_RIGHT)
        d.release(PadButton.R2)                    // a few ms early
        assertTrue(d.engine.guide.layer)
        assertEquals(Sector9.DOWN_RIGHT, d.engine.guide.pendingLayer)
        d.left(0.2f, 0.2f); d.left(0f, 0f)
        assertEquals("！", d.typed())
        assertFalse(d.engine.guide.layer)
        // R1 on the や/わ key is latched with the flick too: let go before the stick is home, still ゅ.
        d.clear()
        d.press(PadButton.R1); d.holdSector(Sector9.DOWN_RIGHT); d.outAt(30.0); d.release(PadButton.R1); d.home(30.0)
        assertEquals("ゅ", d.typed())
        // R1 pressed after the flick armed does not change it.
        d.clear()
        d.outAt(-30.0); d.press(PadButton.R1); d.home(-30.0); d.release(PadButton.R1)
        assertEquals("よ", d.typed())
    }

    @Test
    fun right_flicks_are_ignored_while_r2_is_held() {
        val d = kana()
        d.press(PadButton.R2)
        d.flickAt(UP); d.flickAt(LEFT)
        assertTrue("nothing typed", d.actions.isEmpty())
        assertTrue(d.notes.any { it.contains("ignored while the symbols layer is held") })
        d.release(PadButton.R2)
        d.flickAt(UP)
        assertEquals("back to normal after R2", "あ", d.typed())
        // A right flick armed before R2 went down completes normally.
        d.clear()
        d.holdSector(Sector9.UP_LEFT)
        d.outAt(LEFT)
        d.press(PadButton.R2)
        d.home(LEFT)
        d.release(PadButton.R2)
        assertEquals("こ", d.typed())
        // A right stick that is pushed while R2 is held and still out after R2 is let go types nothing.
        d.clear()
        d.press(PadButton.R2); d.outAt(UP); d.release(PadButton.R2); d.home(UP)
        assertTrue(d.actions.isEmpty())
    }

    @Test
    fun r2_is_the_symbols_layer_outside_kana() {
        // Amendment 18: R2 is the layer in 英字 / 数字 too (記号), with the same mechanics.
        val d = kana()
        d.nextMode()                    // 英字
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.clear()
        d.press(PadButton.R2)
        assertTrue(d.engine.layerHeld)
        d.flickLeft8(Sector9.UP_LEFT)              // &
        d.flick(Dir4.UP)                           // the right stick is ignored while the layer is held
        d.release(PadButton.R2)
        assertEquals("&", d.typed())
        assertFalse(d.engine.layerHeld)
        assertEquals("記号", d.engine.guide.labels.buttons[PadButton.R2])
        assertTrue(d.notes.any { it.contains("ignored while the symbols layer is held") })
        // The emoji list keeps R2 for the next category.
        val e = kana()
        e.tap(PadButton.START)   // amendment 20: Start opens the list at once
        assertEquals(PadMode.EMOJI_LIST, e.engine.padMode)
        e.clear()
        e.tap(PadButton.R2); e.tap(PadButton.L2)
        assertEquals(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.NEXT_TAB), PadAction.EmojiKeyboard(EmojiKeyboardCommand.PREVIOUS_TAB)), e.actions)
    }

    @Test
    fun english_l2_h_and_y_next_suggestion() {
        val d = kana()
        d.nextMode()                    // 英字
        d.clear()
        d.press(PadButton.L2); d.holdSector(Sector9.UP); d.flick(Dir4.DOWN); d.release(PadButton.L2)   // efgh ↓ = h → H
        assertEquals("H", d.typed())
        d.clear()
        d.tap(PadButton.Y)
        assertEquals(listOf<PadAction>(PadAction.ConvertOrNextCandidate), d.actions)
        assertEquals("候補", d.engine.guide.labels.buttons[PadButton.Y])
        d.clear()
        d.tap(PadButton.R2)
        assertTrue("R2 alone types nothing in 英字", d.actions.isEmpty())
    }

    @Test
    fun kana_l3_and_r3_type_nothing_they_step_the_mode() {
        // Amendment 18: no bar on L3, no あ段 on R3; with the stick at rest they step the mode.
        val d = kana()
        d.tap(PadButton.L3)
        assertEquals(PadMode.NUMBER, d.engine.padMode)
        assertFalse(d.engine.barActive)
        d.tap(PadButton.R3)
        assertEquals(PadMode.KANA, d.engine.padMode)
        d.holdSector(Sector9.LEFT); d.tap(PadButton.L3)
        assertEquals("", d.typed())
        assertEquals("the left stick was out: the L3 click is ignored", PadMode.KANA, d.engine.padMode)
        d.leftRelease()
        d.right(0.5f, 0f); d.tap(PadButton.R3); d.right(0f, 0f)
        assertEquals("", d.typed())
        assertEquals("the right stick was out: the R3 click is ignored", PadMode.KANA, d.engine.padMode)
    }

    @Test
    fun the_guide_while_r2_is_held() {
        val d = kana()
        d.holdSector(Sector9.UP_LEFT)
        d.leftRelease()
        d.press(PadButton.R2)
        var g = d.engine.guide
        assertTrue(g.layer)
        assertEquals(listOf("〜", "。", "…", "ー", "", "、", "？", "・", "！"), g.gridLabels)
        assertNull(g.selectedKey)
        assertNull(g.pendingLayer)
        assertTrue(g.pentagon)
        assertTrue(PadButton.R2 in g.heldButtons)
        d.leftOut(Sector9.DOWN_LEFT)
        g = d.engine.guide
        assertEquals(Sector9.DOWN_LEFT, g.pendingLayer)
        assertEquals("？", g.preview)
        d.left(0f, 0f)
        assertNull(d.engine.guide.pendingLayer)
        // R1 + R2 (amendment 21): nothing changes, nothing dimmed.
        d.press(PadButton.R1)
        g = d.engine.guide
        assertEquals(listOf("〜", "。", "…", "ー", "", "、", "？", "・", "！"), g.gridLabels)
        assertTrue(g.gridDim.none { it })
        d.release(PadButton.R1); d.release(PadButton.R2)
        g = d.engine.guide
        assertFalse(g.layer)
        assertEquals(listOf("か", "さ", "た", "な", "あ", "は", "ま", "ら", "や/わ"), g.gridLabels)
        assertEquals(Key.KeyA, g.selectedKey)
    }

    @Test
    fun the_d_pad_fallback_keeps_the_v4_grid_and_bar() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.tap(PadButton.A)                         // centre: な (v4 grid)
        d.press(PadButton.DPAD_DOWN); d.tap(PadButton.A); d.release(PadButton.DPAD_DOWN)   // ↓ や
        d.press(PadButton.L1); d.tap(PadButton.A); d.release(PadButton.L1)                  // the bar's centre わ
        assertEquals("なやわ", d.typed())
        assertEquals(InputMode.ModeJapanese, d.engine.mode)
    }

    // ---- helpers: the right stick out / home toward an angle

    private fun PadDriver.outAt(deg: Double) {
        val r = Math.toRadians(deg)
        val x = Math.cos(r).toFloat(); val y = -Math.sin(r).toFloat()
        right(x * 0.5f, y * 0.5f); right(x * 0.95f, y * 0.95f)
    }

    private fun PadDriver.home(deg: Double) {
        val r = Math.toRadians(deg)
        val x = Math.cos(r).toFloat(); val y = -Math.sin(r).toFloat()
        right(x * 0.4f, y * 0.4f); right(0f, 0f)
    }
}
