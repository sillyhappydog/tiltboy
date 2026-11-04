package com.kazumaproject.pad_input

import com.kazumaproject.pad_input.DeviceProfile.InputDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amendment 25 (v12): which connected devices make the keyboard open straight into the guide. */
class ControllerDetectionTest {
    private val keyboardSource = 0x00000101
    private val touchscreenSource = 0x00001002
    private val dpadSource = 0x00000201

    private val rg477vJoypad = InputDeviceInfo(
        name = DeviceProfile.RG477V_DEVICE_NAME, isVirtual = false, sources = 0x1000511,
        joystickAxes = DeviceProfile.RG477V_AXES, hasDpadKeys = true,
    )

    /** The RG477V's on-screen mapping overlay: a touch device, not a controller. */
    private val virtualGamepadOverlay = InputDeviceInfo(
        name = "virtual_gamepad", isVirtual = false, sources = touchscreenSource or keyboardSource,
        joystickAxes = emptySet(), hasDpadKeys = false,
    )

    @Test
    fun the_rg477v_built_in_joypad_counts() {
        assertTrue(DeviceProfile.isGameController(rg477vJoypad))
        assertTrue(DeviceProfile.anyGameController(listOf(virtualGamepadOverlay, rg477vJoypad)))
    }

    @Test
    fun the_virtual_overlay_does_not_count() {
        assertFalse(DeviceProfile.isGameController(virtualGamepadOverlay))
        assertFalse(DeviceProfile.anyGameController(listOf(virtualGamepadOverlay)))
    }

    @Test
    fun a_device_android_marks_virtual_does_not_count() {
        assertFalse(DeviceProfile.isGameController(rg477vJoypad.copy(name = "Virtual", isVirtual = true)))
    }

    @Test
    fun a_keyboard_only_device_does_not_count() {
        // Arrow keys are D-pad key codes, but a keyboard has no gamepad / joystick source.
        val keyboard = InputDeviceInfo("qwerty2", isVirtual = false, sources = keyboardSource or dpadSource, joystickAxes = emptySet(), hasDpadKeys = true)
        assertFalse(DeviceProfile.isGameController(keyboard))
        assertFalse(DeviceProfile.anyGameController(emptyList()))
    }

    @Test
    fun a_gamepad_without_sticks_but_with_a_dpad_counts() {
        val hatPad = InputDeviceInfo("hat pad", isVirtual = false, sources = DeviceProfile.SOURCE_GAMEPAD or DeviceProfile.SOURCE_JOYSTICK,
            joystickAxes = setOf(DeviceProfile.AXIS_HAT_X, DeviceProfile.AXIS_HAT_Y), hasDpadKeys = false)
        assertTrue(DeviceProfile.isGameController(hatPad))
        val keyPad = InputDeviceInfo("dpad keys pad", isVirtual = false, sources = DeviceProfile.SOURCE_GAMEPAD or keyboardSource,
            joystickAxes = emptySet(), hasDpadKeys = true)
        assertTrue(DeviceProfile.isGameController(keyPad))
    }

    @Test
    fun a_gamepad_source_with_neither_sticks_nor_dpad_does_not_count() {
        val buttonsOnly = InputDeviceInfo("buttons", isVirtual = false, sources = DeviceProfile.SOURCE_GAMEPAD,
            joystickAxes = setOf(DeviceProfile.AXIS_LTRIGGER), hasDpadKeys = false)
        assertFalse(DeviceProfile.isGameController(buttonsOnly))
    }
}
