package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stick and button edge cases. Amendment 16 grid: the stick at rest is the あ key, ↑ is さ, ↖ is か. Amendment 18: kana on the pentagon (↑ あ段 → い段 ↘ う段 ↙ え段 ← お段). */
class EdgeCaseTest {

    /** R2 back on 変換 (as before amendment 16), for the trigger tests. */
    private val r2Convert = PadConfig(roles = PadConfig.DEFAULT_ROLES + (PadButton.R2 to PadRole.CONVERT_NEXT))

    @Test
    fun noise_inside_dead_zone_does_nothing() {
        val d = PadDriver()
        repeat(50) { i ->
            val a = (i % 7 - 3) * 0.05f
            val b = (i % 5 - 2) * 0.05f
            d.sticks(lx = a, ly = b, rx = -a, ry = b)
        }
        assertTrue(d.actions.isEmpty())
        assertEquals(Key.KeyA, d.engine.guide.selectedKey)
    }

    @Test
    fun flick_that_barely_passes_threshold_counts_and_one_that_does_not_is_ignored() {
        val cfg = PadConfig()
        val d = PadDriver(PadInputEngine(cfg))
        d.right(0f, -(cfg.flickThreshold - 0.01f)); d.right(0f, 0f)
        assertTrue("below threshold: nothing", d.actions.isEmpty())
        d.right(0f, -(cfg.flickThreshold + 0.01f)); d.right(0f, 0f)
        assertEquals("あ", d.typed())
    }

    @Test
    fun flick_is_entered_only_when_stick_returns() {
        val d = PadDriver()
        d.right(-1f, 0f)
        assertTrue(d.actions.isEmpty())
        d.right(-0.5f, 0f)
        assertTrue("still above return threshold", d.actions.isEmpty())
        d.right(-0.2f, 0f)
        assertEquals("お", d.typed())
    }

    @Test
    fun diagonal_flick_horizontal_wins_on_exact_diagonal_otherwise_larger_axis() {
        assertEquals(Dir4.LEFT, StickGeometry.sector4(-0.7f, -0.7f))
        assertEquals(Dir4.RIGHT, StickGeometry.sector4(0.7f, 0.7f))
        assertEquals(Dir4.UP, StickGeometry.sector4(0.5f, -0.9f))
        assertEquals(Dir4.DOWN, StickGeometry.sector4(-0.5f, 0.9f))
        val d = PadDriver()
        d.right(0.6f, -0.9f); d.right(0f, 0f)
        assertEquals("56° is in the あ sector", "あ", d.typed())
    }

    @Test
    fun flick_direction_is_where_the_stick_points_last_at_the_rim() {
        // Amendment 17 (re-aim) replaces "where the stick went farthest".
        val d = PadDriver()
        d.right(-0.65f, 0f)      // crosses threshold going left (お)
        d.right(-0.3f, -0.9f)    // then swings up (108°: あ)
        d.right(0f, 0f)
        assertEquals("あ", d.typed())
        d.clear()
        d.right(0f, -1f)         // full ↑ (あ)
        d.right(-0.62f, 0f)      // swings to ←, less far out but ≥ 0.6: re-aimed to お (v5 typed あ)
        d.right(0f, 0f)
        assertEquals("お", d.typed())
    }

    @Test
    fun changing_left_stick_while_right_stick_is_out_keeps_the_latched_key() {
        val d = PadDriver()
        d.left(-0.707f, -0.707f) // か
        d.right(-1f, 0f)         // flick left begins: か latched (← = こ)
        d.left(1f, 0f)           // user moves to は while the right stick is still out
        d.right(0f, 0f)
        assertEquals("こ", d.typed())
    }

    @Test
    fun both_sticks_released_at_once() {
        val d = PadDriver()
        d.left(-0.707f, -0.707f)
        d.right(0f, -1f)
        d.sticks(lx = 0f, ly = 0f, rx = 0f, ry = 0f)
        assertEquals("か", d.typed())
    }

    @Test
    fun very_fast_sequence_each_flick_counts_once() {
        val d = PadDriver()
        d.left(-0.707f, -0.707f)
        repeat(20) {
            d.right(-1f, 0f)
            d.right(0f, 0f)
        }
        assertEquals("こ".repeat(20), d.typed())
    }

    @Test
    fun left_stick_hysteresis_keeps_selection_between_thresholds() {
        val cfg = PadConfig()
        val d = PadDriver(PadInputEngine(cfg))
        d.left(0f, -(cfg.leftSelectThreshold + 0.02f))
        assertEquals(Key.KeySA, d.engine.guide.selectedKey)
        d.left(0f, -(cfg.leftReleaseThreshold + 0.05f))
        assertEquals("still さ between release and select thresholds", Key.KeySA, d.engine.guide.selectedKey)
        d.left(0f, -(cfg.leftReleaseThreshold - 0.05f))
        assertEquals(Key.KeyA, d.engine.guide.selectedKey)
        d.left(0f, -(cfg.leftSelectThreshold - 0.02f))
        assertEquals("below select threshold stays neutral (あ)", Key.KeyA, d.engine.guide.selectedKey)
    }

    @Test
    fun sector8_boundaries() {
        assertEquals(Sector9.RIGHT, StickGeometry.sector8(1f, 0f))
        assertEquals(Sector9.UP, StickGeometry.sector8(0f, -1f))
        assertEquals(Sector9.LEFT, StickGeometry.sector8(-1f, 0f))
        assertEquals(Sector9.DOWN, StickGeometry.sector8(0f, 1f))
        assertEquals(Sector9.UP_LEFT, StickGeometry.sector8(-0.7f, -0.7f))
        assertEquals(Sector9.UP_RIGHT, StickGeometry.sector8(0.7f, -0.7f))
        assertEquals(Sector9.DOWN_LEFT, StickGeometry.sector8(-0.7f, 0.7f))
        assertEquals(Sector9.DOWN_RIGHT, StickGeometry.sector8(0.7f, 0.7f))
        // 20° above the right axis is still RIGHT; 25° is UP_RIGHT.
        assertEquals(Sector9.RIGHT, StickGeometry.sector8(0.94f, -0.342f))
        assertEquals(Sector9.UP_RIGHT, StickGeometry.sector8(0.906f, -0.423f))
    }

    @Test
    fun backspace_button_repeats_while_held_and_stops_on_release() {
        val d = PadDriver()
        d.press(PadButton.B)
        assertEquals(1, d.actions.size)
        d.time += 399; d.poll()
        assertEquals(1, d.actions.size)
        d.time += 1; d.poll()
        assertEquals(2, d.actions.size)
        d.time += 59; d.poll()
        assertEquals(2, d.actions.size)
        d.time += 1; d.poll()
        assertEquals(3, d.actions.size)
        d.release(PadButton.B)
        d.time += 500; d.poll()
        assertEquals(3, d.actions.size)
        assertTrue(d.actions.all { it == PadAction.Backspace })
    }

    @Test
    fun system_key_auto_repeat_is_ignored() {
        val d = PadDriver()
        d.press(PadButton.Y); d.press(PadButton.Y); d.press(PadButton.Y)
        assertEquals(1, d.actions.size)
        d.release(PadButton.Y)
        d.release(PadButton.Y)
        assertEquals(1, d.actions.size)
    }

    @Test
    fun analog_triggers_act_as_buttons_with_hysteresis() {
        // Amendment 16 default: the R2 axis holds the や・わ layer like the R2 key.
        val layer = PadDriver()
        layer.engine.onTriggers(0f, 0.6f, 1).let { assertTrue(it.actions.isEmpty()) }
        assertTrue("R2 axis pressed: the layer is held", layer.engine.layerHeld)
        layer.engine.onTriggers(0f, 0.2f, 2)
        assertTrue("released", !layer.engine.layerHeld)
        val d = PadDriver(PadInputEngine(r2Convert))
        d.engine.onTriggers(0f, 0.3f, 1).let { assertTrue(it.actions.isEmpty()) }
        d.engine.onTriggers(0f, 0.6f, 2).let { assertEquals(listOf<PadAction>(PadAction.ConvertOrNextCandidate), it.actions) }
        d.engine.onTriggers(0f, 0.45f, 3).let { assertTrue("still pressed above release level", it.actions.isEmpty()) }
        d.engine.onTriggers(0f, 0.2f, 4).let { assertTrue(it.actions.isEmpty()) }
        d.engine.onTriggers(0f, 0.9f, 5).let { assertEquals(1, it.actions.size) }
        d.engine.onTriggers(0.9f, 0.9f, 6).let { assertTrue("L2 is a hold role now: pressing it types nothing", it.actions.isEmpty() && it.note.contains("hold")) }
    }

    @Test
    fun trigger_axis_is_ignored_once_the_same_trigger_sends_key_events() {
        val d = PadDriver(PadInputEngine(r2Convert))
        d.tap(PadButton.R2)
        assertEquals(1, d.actions.size)
        d.engine.onTriggers(0f, 1f, 100).let { assertTrue(it.actions.isEmpty()) }
    }

    @Test
    fun switching_mode_drops_a_pending_flick() {
        val d = PadDriver()
        d.right(-1f, 0f)
        // Amendment 20: Select no longer switches modes; L3 (the previous mode) does, and it guards the left stick only.
        d.tap(PadButton.L3)
        d.right(0f, 0f)
        assertEquals(listOf<PadAction>(PadAction.SwitchMode(com.kazumaproject.core.domain.state.InputMode.ModeNumber)), d.actions)
    }
}
