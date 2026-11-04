package com.kazumaproject.markdownhelperkeyboard.pad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.pad_input.PadAction
import com.kazumaproject.pad_input.PadGuideState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The most important rule of Sumire Pad: controller events are consumed only while the
 * keyboard is shown and controller mode is on. Otherwise they pass through untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PadControllerPassThroughTest {

    private class FakeHost : PadController.Host {
        var active = false
        var mode: InputMode = InputMode.ModeJapanese
        val performed = mutableListOf<PadAction>()
        override fun isPadActive() = active
        override fun currentMode() = mode
        override fun candidatesShown() = false
        override fun perform(action: PadAction) { performed += action }
        override fun onGuideChanged(state: PadGuideState) {}
    }

    private fun gamepadKey(action: Int, keyCode: Int, source: Int = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD): KeyEvent =
        KeyEvent(0L, 0L, action, keyCode, 0, 0, 7, 0, 0, source)

    private fun keyboardKey(action: Int, keyCode: Int): KeyEvent =
        KeyEvent(0L, 0L, action, keyCode, 0, 0, 8, 0, 0, InputDevice.SOURCE_KEYBOARD)

    private fun joystickMotion(lx: Float, ly: Float, rx: Float = 0f, ry: Float = 0f): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_X, lx)
            setAxisValue(MotionEvent.AXIS_Y, ly)
            setAxisValue(MotionEvent.AXIS_Z, rx)
            setAxisValue(MotionEvent.AXIS_RZ, ry)
        })
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, props, coords, 0, 0, 1f, 1f, 7, 0, InputDevice.SOURCE_JOYSTICK, 0)
    }

    @Test
    fun keyboard_hidden_or_mode_off_consumes_nothing() {
        val host = FakeHost().apply { active = false }
        val c = PadController(host, "test")
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_DPAD_LEFT)) {
            assertFalse("down $code", c.onKeyDown(code, gamepadKey(KeyEvent.ACTION_DOWN, code)))
            assertFalse("up $code", c.onKeyUp(code, gamepadKey(KeyEvent.ACTION_UP, code)))
        }
        assertFalse(c.onGenericMotionEvent(joystickMotion(0f, -1f, -1f, 0f)))
        assertFalse(c.onGenericMotionEvent(joystickMotion(0f, 0f, 0f, 0f)))
        assertTrue(host.performed.isEmpty())
    }

    @Test
    fun keyboard_shown_and_mode_on_consumes_controller_events() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        assertTrue(c.onKeyDown(KeyEvent.KEYCODE_BUTTON_A, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue(c.onKeyUp(KeyEvent.KEYCODE_BUTTON_A, gamepadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
        assertEquals("A = 決定 (amendment 11)", listOf<PadAction>(PadAction.Confirm), host.performed)
        host.performed.clear()
        // Stick: left ↖ (か, amendment 16) then right flick → and back → き (the kana pentagon, amendment 15: → = い段)
        assertTrue(c.onGenericMotionEvent(joystickMotion(-0.707f, -0.707f)))
        assertTrue(c.onGenericMotionEvent(joystickMotion(-0.707f, -0.707f, 1f, 0f)))
        assertTrue(c.onGenericMotionEvent(joystickMotion(-0.707f, -0.707f, 0f, 0f)))
        val typed = host.performed.filterIsInstance<PadAction.KanaKey>().single()
        assertEquals('き', typed.char)
    }

    @Test
    fun physical_keyboard_keys_are_never_consumed() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_SPACE)) {
            assertFalse("down $code", c.onKeyDown(code, keyboardKey(KeyEvent.ACTION_DOWN, code)))
            assertFalse("up $code", c.onKeyUp(code, keyboardKey(KeyEvent.ACTION_UP, code)))
        }
        assertTrue(host.performed.isEmpty())
    }

    @Test
    fun non_controller_keys_from_a_gamepad_pass_through() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        // e.g. a controller whose B is mapped to BACK, or volume keys: not ours.
        assertFalse(c.onKeyDown(KeyEvent.KEYCODE_BACK, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertFalse(c.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(host.performed.isEmpty())
    }

    @Test
    fun key_up_is_consumed_when_its_key_down_was_even_if_keyboard_hid_in_between() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        assertTrue(c.onKeyDown(KeyEvent.KEYCODE_BUTTON_B, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
        host.active = false
        assertTrue("matching up is swallowed so the app never sees a lone key-up",
            c.onKeyUp(KeyEvent.KEYCODE_BUTTON_B, gamepadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)))
        assertFalse("a fresh press now passes through", c.onKeyDown(KeyEvent.KEYCODE_BUTTON_B, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
        assertFalse(c.onKeyUp(KeyEvent.KEYCODE_BUTTON_B, gamepadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)))
    }

    @Test
    fun system_auto_repeat_key_downs_are_consumed_but_not_retyped() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        c.onKeyDown(KeyEvent.KEYCODE_BUTTON_X, gamepadKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X))
        val repeat = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X, 3, 0, 7, 0, 0, InputDevice.SOURCE_GAMEPAD)
        assertTrue(c.onKeyDown(KeyEvent.KEYCODE_BUTTON_X, repeat))
        assertTrue(c.onKeyDown(KeyEvent.KEYCODE_BUTTON_X, repeat))
        c.onKeyUp(KeyEvent.KEYCODE_BUTTON_X, gamepadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(listOf<PadAction>(PadAction.Space), host.performed)
    }

    @Test
    fun mouse_and_touch_motion_is_not_ours() {
        val host = FakeHost().apply { active = true }
        val c = PadController(host, "test")
        val touch = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 10f, 10f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        assertFalse(c.onGenericMotionEvent(touch))
        val mouse = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_HOVER_MOVE, 10f, 10f, 0).apply { source = InputDevice.SOURCE_MOUSE }
        assertFalse(c.onGenericMotionEvent(mouse))
    }
}
