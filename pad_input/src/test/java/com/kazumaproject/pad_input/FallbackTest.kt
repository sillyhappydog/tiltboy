package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-pad + buttons layout used when no stick motion reaches the keyboard. */
class FallbackTest {

    private fun fallbackDriver(): PadDriver {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        assertTrue(d.engine.fallback)
        return d
    }

    @Test
    fun auto_mode_starts_in_fallback_until_a_stick_moves() {
        val e = PadInputEngine()
        assertTrue("AUTO: no stick seen yet", e.fallback)
        e.setDeviceHasSticks(true)
        assertFalse(e.fallback)
        e.setDeviceHasSticks(false)
        assertTrue(e.fallback)
        e.onSticks(0.1f, 0f, 0f, 0f, 1)
        assertFalse("first stick sample leaves fallback", e.fallback)
        e.setDeviceHasSticks(false)
        assertFalse("device info no longer overrides observed motion", e.fallback)
    }

    @Test
    fun declared_sticks_start_in_stick_mode_and_fall_back_only_after_presses_without_motion() {
        val e = PadInputEngine()
        e.setDeviceHasSticks(true)                       // RG477V: AXIS_X/Y + Z/RZ declared
        assertFalse("stick layout from the start, no 十字キー label", e.fallback)
        assertFalse(e.guide.fallback)
        var t = 0L
        repeat(PadInputEngine.FALLBACK_AFTER_PRESSES - 1) { e.onButton(PadButton.A, true, ++t); e.onButton(PadButton.A, false, ++t) }
        assertFalse("still stick mode", e.fallback)
        val d = e.onButton(PadButton.A, true, ++t)
        assertTrue("the ${PadInputEngine.FALLBACK_AFTER_PRESSES}th press without motion switches", e.fallback)
        assertTrue(d.note.contains("D-pad layout"))
        assertEquals("the switching press is handled by the fallback table (A = あ段 of な)", listOf("な"), d.actions.map { (it as PadAction.KanaKey).char.toString() })
        e.onButton(PadButton.A, false, ++t)
        // Motion arriving later brings the sticks back for good.
        e.onSticks(0.5f, 0f, 0f, 0f, ++t)
        assertFalse(e.fallback)
        // A device that moved its stick first never counts presses.
        val e2 = PadInputEngine()
        e2.setDeviceHasSticks(true)
        e2.onSticks(0.2f, 0f, 0f, 0f, 1)
        repeat(20) { e2.onButton(PadButton.B, true, 2L + it); e2.onButton(PadButton.B, false, 3L + it) }
        assertFalse(e2.fallback)
    }

    @Test
    fun forced_sticks_mode_never_falls_back() {
        val e = PadInputEngine(PadConfig(stickSource = StickSource.STICKS))
        assertFalse(e.fallback)
        e.setDeviceHasSticks(false)
        assertFalse(e.fallback)
    }

    /** v13 device check: B tapped to delete right after the keyboard opened turned into a letter. */
    @Test
    fun a_controller_whose_sticks_worked_before_never_counts_presses() {
        val e = PadInputEngine()
        e.setDeviceHasSticks(true)
        e.markSticksKnownToWork()                         // the host remembered this controller
        var t = 0L
        repeat(PadInputEngine.FALLBACK_AFTER_PRESSES * 3) {
            val d = e.onButton(PadButton.B, true, ++t)
            assertEquals("B stays delete", listOf(PadAction.Backspace), d.actions)
            e.onButton(PadButton.B, false, ++t)
        }
        assertFalse(e.fallback)
        e.config = e.config.copy()                        // every keyboard open reloads the config
        assertFalse("still stick mode after a reload", e.fallback)
    }

    @Test
    fun dpad_selects_key_and_face_buttons_pick_the_vowel() {
        val d = fallbackDriver()
        d.press(PadButton.DPAD_UP)              // か row
        d.tap(PadButton.A); d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.L2)
        d.release(PadButton.DPAD_UP)
        assertEquals("かきくけこ", d.typed())
        d.clear()
        d.tap(PadButton.A)                      // neutral = な
        assertEquals("な", d.typed())
        d.clear()
        d.press(PadButton.DPAD_UP); d.press(PadButton.DPAD_LEFT)   // diagonal = あ
        d.tap(PadButton.L2)
        assertEquals("お", d.typed())
        d.release(PadButton.DPAD_UP); d.release(PadButton.DPAD_LEFT)
    }

    @Test
    fun fallback_covers_all_12_keys() {
        val d = fallbackDriver()
        val combos = mapOf(
            Sector9.UP_LEFT to listOf(PadButton.DPAD_UP, PadButton.DPAD_LEFT),
            Sector9.UP to listOf(PadButton.DPAD_UP),
            Sector9.UP_RIGHT to listOf(PadButton.DPAD_UP, PadButton.DPAD_RIGHT),
            Sector9.LEFT to listOf(PadButton.DPAD_LEFT),
            Sector9.CENTER to emptyList(),
            Sector9.RIGHT to listOf(PadButton.DPAD_RIGHT),
            Sector9.DOWN_LEFT to listOf(PadButton.DPAD_DOWN, PadButton.DPAD_LEFT),
            Sector9.DOWN to listOf(PadButton.DPAD_DOWN),
            Sector9.DOWN_RIGHT to listOf(PadButton.DPAD_DOWN, PadButton.DPAD_RIGHT),
        )
        for ((sector, buttons) in combos) {
            buttons.forEach { d.press(it) }
            d.tap(PadButton.A)
            buttons.forEach { d.release(it) }
        }
        assertEquals("あかさたなはまやら", d.typed())
        d.clear()
        // L1 held = the bar (amendment 10): A / X / Y / B / L2 = わ を ん ー 〜, the D-pad does not matter.
        d.press(PadButton.L1)
        d.press(PadButton.DPAD_LEFT); d.tap(PadButton.X); d.release(PadButton.DPAD_LEFT)
        d.tap(PadButton.A); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.L2)
        d.release(PadButton.L1)
        assertTrue(d.kanaKeys().all { it.key == Key.KeyWA })
        assertEquals("をわんー〜", d.typed())
    }

    @Test
    fun fallback_editing_buttons() {
        val d = fallbackDriver()
        d.tap(PadButton.START); d.tap(PadButton.SELECT); d.tap(PadButton.R2)
        d.press(PadButton.L1); d.tap(PadButton.R2); d.tap(PadButton.SELECT); d.release(PadButton.L1)
        assertEquals(
            listOf(PadAction.Confirm, PadAction.Backspace, PadAction.ConvertOrNextCandidate, PadAction.Space, PadAction.HideKeyboard),
            d.actions,
        )
    }

    @Test
    fun fallback_dpad_moves_candidates_when_they_are_shown() {
        val d = fallbackDriver()
        d.engine.candidatesShown = true
        d.tap(PadButton.DPAD_RIGHT)
        assertEquals(listOf<PadAction>(PadAction.Dpad(Dir4.RIGHT)), d.actions)
        d.engine.candidatesShown = false
        d.clear()
        d.tap(PadButton.DPAD_RIGHT)
        assertTrue(d.actions.isEmpty())
    }

    @Test
    fun fallback_english_is_the_tenkey_like_kana() {
        val d = fallbackDriver()
        d.tap(PadButton.R1)
        assertEquals(InputMode.ModeEnglish, d.engine.mode)
        d.clear()
        // D-pad ↑ = the efgh cell (amendment 14): X = ←, Y = ↑, L2 = ↓; A (the centre) = space.
        d.press(PadButton.DPAD_UP); d.tap(PadButton.A); d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.L2); d.release(PadButton.DPAD_UP)
        val cell = KanaGrid.flickChars(KanaGrid.keyFor(Sector9.UP, PadMode.ENGLISH, fallback = true), PadMode.ENGLISH)
        assertEquals(PadAction.Space, d.actions.first())
        assertEquals(cell.left + cell.up + cell.down, d.typed())
        d.clear()
        d.tap(PadButton.START); d.tap(PadButton.SELECT)
        assertEquals(listOf(PadAction.Confirm, PadAction.Backspace), d.actions)
        d.clear()
        // L1 = the bar in the fixed fallback table: its centre is '"' in English.
        d.press(PadButton.L1); d.tap(PadButton.A); d.release(PadButton.L1)
        assertEquals("\"", d.typed())
    }

    @Test
    fun stick_samples_are_ignored_while_forced_to_fallback() {
        val d = fallbackDriver()
        d.left(0f, -1f); d.flick(Dir4.LEFT)
        assertTrue(d.actions.isEmpty())
        assertTrue(d.engine.fallback)
    }

    @Test
    fun guide_in_fallback_follows_the_dpad() {
        val d = fallbackDriver()
        d.press(PadButton.DPAD_RIGHT)
        assertEquals(Key.KeyHA, d.engine.guide.selectedKey)
        assertTrue(d.engine.guide.fallback)
        d.release(PadButton.DPAD_RIGHT)
        assertEquals(Key.KeyNA, d.engine.guide.selectedKey)
    }
}
