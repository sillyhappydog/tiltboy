package com.kazumaproject.pad_input

/**
 * Decisions that depend on what Android reports about the controller, kept free of Android
 * classes so they can be unit-tested with recorded device descriptors.
 * Axis ids are `MotionEvent.AXIS_*` values.
 */
object DeviceProfile {
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_Z = 11
    const val AXIS_RX = 12
    const val AXIS_RY = 13
    const val AXIS_RZ = 14
    const val AXIS_HAT_X = 15
    const val AXIS_HAT_Y = 16
    const val AXIS_LTRIGGER = 17
    const val AXIS_RTRIGGER = 18
    const val AXIS_GAS = 22
    const val AXIS_BRAKE = 23

    /** What the Anbernic RG477V reported on 2026-11-01 (name `retrogame_joypad`, sources 0x1000511). */
    val RG477V_DEVICE_NAME = "retrogame_joypad"
    val RG477V_AXES: Set<Int> = setOf(
        AXIS_HAT_Y, AXIS_HAT_X, AXIS_RTRIGGER, AXIS_GAS, AXIS_RZ, AXIS_Z, AXIS_LTRIGGER, AXIS_BRAKE, AXIS_Y, AXIS_X,
    )

    /** The device has a left stick: it declares a joystick X axis. */
    fun hasSticks(joystickAxes: Set<Int>): Boolean = AXIS_X in joystickAxes

    /** Right stick on RX/RY only when the device declares RX but no Z (see PadController). */
    fun rightStickUsesRx(joystickAxes: Set<Int>): Boolean = AXIS_Z !in joystickAxes && AXIS_RX in joystickAxes

    /** L2/R2 arrive as analog axes (LTRIGGER/BRAKE, RTRIGGER/GAS) on this device. */
    fun hasAnalogTriggers(joystickAxes: Set<Int>): Boolean =
        (AXIS_LTRIGGER in joystickAxes || AXIS_BRAKE in joystickAxes) &&
            (AXIS_RTRIGGER in joystickAxes || AXIS_GAS in joystickAxes)

    /** `InputDevice.SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` (each includes its source-class bit). */
    const val SOURCE_GAMEPAD = 0x00000401
    const val SOURCE_JOYSTICK = 0x01000010

    /**
     * Amendment 25: what the keyboard needs to know about one connected input device.
     * [joystickAxes] are the axes declared for a joystick-class source; [hasDpadKeys] is true when
     * the device reports any of the D-pad key codes.
     */
    data class InputDeviceInfo(
        val name: String,
        val isVirtual: Boolean,
        val sources: Int,
        val joystickAxes: Set<Int>,
        val hasDpadKeys: Boolean,
    )

    /**
     * Amendment 25: a real game controller is a non-virtual device with a gamepad or joystick
     * source and at least a stick (X/Y) or a D-pad (hat axes or D-pad keys). The RG477V's
     * `retrogame_joypad` counts; its touch overlay `virtual_gamepad` and keyboards do not.
     */
    fun isGameController(d: InputDeviceInfo): Boolean {
        if (d.isVirtual) return false
        val padSource = (d.sources and SOURCE_GAMEPAD) == SOURCE_GAMEPAD || (d.sources and SOURCE_JOYSTICK) == SOURCE_JOYSTICK
        if (!padSource) return false
        val stick = AXIS_X in d.joystickAxes || AXIS_Y in d.joystickAxes
        val dpad = AXIS_HAT_X in d.joystickAxes || AXIS_HAT_Y in d.joystickAxes || d.hasDpadKeys
        return stick || dpad
    }

    /** Amendment 25: the keyboard opens straight into the guide when this is true. */
    fun anyGameController(devices: List<InputDeviceInfo>): Boolean = devices.any(::isGameController)

    /** USB vendor id of the RG477V's joypad (`Vendor_484b_Product_1101.kl`). */
    const val ANBERNIC_VENDOR_ID = 0x484b

    /** AYN's built-in `Odin Controller` (Odin 2 family, Thor). */
    const val AYN_ODIN_VENDOR_ID = 0x2020

    /**
     * Face-button arrangement for the setting 「ボタンの並び：自動」. Anbernic handhelds (vendor
     * 0x484b, controller named `retrogame_joypad`) use the Nintendo arrangement (X top). v14: so
     * does the AYN Thor's built-in controller (measured 2026-11-04: X top, Y left, A right, B
     * bottom, labels = keycodes); other AYN models are unverified and stay Xbox. Everything else is
     * assumed Xbox (Y top). Only decides where the guide draws each letter; since v14 the settings
     * home asks the user, and this is only the guess before they answer.
     */
    fun defaultFaceLayout(deviceName: String?, manufacturer: String? = null, vendorId: Int = 0, model: String? = null): FaceLayout {
        val n = deviceName?.lowercase() ?: ""
        val m = manufacturer?.lowercase() ?: ""
        val anbernic = vendorId == ANBERNIC_VENDOR_ID || n.contains("retrogame") || n.contains("anbernic") || m.contains("anbernic")
        val aynThor = vendorId == AYN_ODIN_VENDOR_ID && m == "ayn" && model?.lowercase()?.contains("thor") == true
        return if (anbernic || aynThor) FaceLayout.NINTENDO else FaceLayout.XBOX
    }
}
