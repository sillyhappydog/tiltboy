package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.pad_input.PadAction
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadDecision
import com.kazumaproject.pad_input.PadGuideState
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.PadRole

/**
 * Bridges Android input events to the pure [PadInputEngine] and the engine's actions back
 * to the host (the IME service, or the diagnostics screen which only watches).
 *
 * Pass-through rule: when [Host.isPadActive] is false nothing is consumed and nothing is
 * fed to the engine, so games and emulators see every controller event untouched. The only
 * exception is the key-up of a key whose key-down we consumed, so apps never see an
 * unmatched key-up.
 */
class PadController(private val host: Host, private val logSource: String = "keyboard") {

    interface Host {
        /** Keyboard shown and controller mode on. */
        fun isPadActive(): Boolean
        fun currentMode(): InputMode
        fun candidatesShown(): Boolean
        fun perform(action: PadAction)
        fun onGuideChanged(state: PadGuideState)
        /**
         * A controller event was consumed (stick past the dead zone, or a button): the pad is "in use".
         * Amendment 20: not for the touch-keyboard button (Select), which switches between the guide
         * and the touch keys itself ([PadAction.TouchKeyboard]).
         */
        fun onControllerUsed() {}
    }

    var engine = PadInputEngine()
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val consumedDownKeys = HashSet<Int>()
    private var lastMotionLogAt = 0L
    private var lastLoggedAxes = ""
    private var deviceChecked = Int.MIN_VALUE // -1 is KeyCharacterMap.VIRTUAL_KEYBOARD, a real id
    private var hatX = 0
    private var hatY = 0

    private var faceLayoutAuto = true
    private var detectedFaceLayout: com.kazumaproject.pad_input.FaceLayout? = null

    /** v13: controllers (InputDevice.descriptor) whose sticks have reached the keyboard before. */
    private var prefs: android.content.SharedPreferences? = null
    private var sticksSeenDevices: MutableSet<String>? = null

    private fun sticksSeen(): MutableSet<String> = sticksSeenDevices
        ?: HashSet(prefs?.getStringSet(PREF_STICKS_SEEN_DEVICES, emptySet()) ?: emptySet<String>()).also { sticksSeenDevices = it }

    /** A remembered controller never falls back to the D-pad layout by the press count. */
    private fun applyRememberedSticks(device: InputDevice) {
        if (device.descriptor in sticksSeen()) engine.markSticksKnownToWork()
    }

    private fun rememberSticks(device: InputDevice?) {
        val id = device?.descriptor ?: return
        val seen = sticksSeen()
        if (!seen.add(id)) return
        prefs?.edit()?.putStringSet(PREF_STICKS_SEEN_DEVICES, HashSet(seen))?.apply()
        log("remembered '${device.name}': its sticks work (no D-pad fallback guess from now on)")
    }

    /** Load thresholds and button roles from settings. */
    fun reloadConfig(context: Context) {
        val prefs = PadPreferences.prefs(context)
        this.prefs = prefs
        faceLayoutAuto = PadPreferences.faceLayoutChoice(prefs) == null
        engine.config = PadPreferences.config(prefs, PadText.words(context))
        applyDetectedFaceLayout()
        log("config: ${engine.config}")
    }

    /** 「ボタンの並び：自動」: the arrangement comes from the controller (vendor 0x484b = Anbernic → Nintendo). */
    private fun noteDevice(device: InputDevice) {
        detectedFaceLayout = com.kazumaproject.pad_input.DeviceProfile.defaultFaceLayout(
            device.name, android.os.Build.MANUFACTURER, device.vendorId, android.os.Build.MODEL,
        )
        applyDetectedFaceLayout()
    }

    private fun applyDetectedFaceLayout() {
        val detected = detectedFaceLayout ?: return
        if (faceLayoutAuto && engine.config.faceLayout != detected) {
            engine.config = engine.config.copy(faceLayout = detected)
            log("face buttons drawn in the ${detected.name} arrangement (auto)")
        }
    }

    /**
     * Before any event arrives: look at the connected input devices and tell the engine whether a
     * game controller with joystick axes exists, so AUTO starts in stick mode on the RG477V
     * instead of showing the D-pad layout until the first stick motion (device report, 2026-11-01).
     */
    fun probeDevices() {
        if (deviceChecked != Int.MIN_VALUE) return
        val ids: IntArray = InputDevice.getDeviceIds()
        var found: InputDevice? = null
        for (id in ids) {
            val d = InputDevice.getDevice(id) ?: continue
            val isPad = (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isPad || d.isVirtual) continue
            if (found == null || hasStickAxes(d) && !hasStickAxes(found)) found = d
        }
        val device = found ?: return
        val hasSticks = hasStickAxes(device)
        engine.setDeviceHasSticks(hasSticks)
        if (hasSticks) applyRememberedSticks(device)
        noteDevice(device)
        log("probe: '${device.name}' id=${device.id} axes=${device.motionRanges.map { MotionEvent.axisToString(it.axis) }} sticks=$hasSticks fallback=${engine.fallback}")
    }

    private fun hasStickAxes(d: InputDevice): Boolean = d.motionRanges.any {
        it.axis == MotionEvent.AXIS_X && (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
    }

    /**
     * Amendment 25: take the host's current mode before the guide is drawn without a controller event.
     * v17: if that mode is switched off in 「使うモード」, move to an enabled one now (the host switches
     * with it), so the first frame shows the mode the user will type in.
     */
    fun syncWithHost() {
        syncEngine()
        val d = engine.enterEnabledMode()
        if (d.actions.isNotEmpty()) apply(d)
    }

    /**
     * v12 device check: the keyboard opened in the guide before any controller event. A config
     * reload (every keyboard open) puts AUTO back into the D-pad fallback until stick motion is
     * seen, so declare the connected controller's sticks again here.
     */
    fun declareConnectedControllerSticks() {
        engine.setDeviceHasSticks(gameControllerHasSticks())
    }

    /** Forget held buttons and timers (keyboard hidden, input finished). */
    fun reset() {
        engine.reset()
        handler.removeCallbacks(repeatRunnable)
        repeatPosted = false
        consumedDownKeys.clear()
        hatX = 0; hatY = 0
    }

    // ------------------------------------------------------------------ touch digits (amendment 18)

    /**
     * A tap on one of the controller map's touch digits (PadGuideView). Typed through the engine
     * ([PadInputEngine.onTouchDigit]) and [Host.perform], the path 数字 mode's digits take.
     */
    fun onTouchDigit(digit: Char) {
        syncEngine()
        log("touch digit $digit")
        apply(engine.onTouchDigit(digit))
    }

    // ------------------------------------------------------------------ key events

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || !isControllerKeyEvent(keyCode, event)) return false
        val active = host.isPadActive()
        if (!active) {
            if (consumedDownKeys.isNotEmpty()) reset()
            return false
        }
        val button = buttonFor(keyCode) ?: run {
            log("key down $keyCode (${KeyEvent.keyCodeToString(keyCode)}) from ${describeDevice(event)}: no pad button, passed through")
            return false
        }
        checkDevice(event)
        syncEngine()
        if (event.repeatCount > 0) {
            consumedDownKeys.add(keyCode)
            return true // system auto-repeat; the engine does its own repeat
        }
        log("key down ${KeyEvent.keyCodeToString(keyCode)} → $button  src=0x${Integer.toHexString(event.source)} dev=${describeDevice(event)}")
        consumedDownKeys.add(keyCode)
        if (!isTouchKeyboardButton(button)) host.onControllerUsed()
        apply(engine.onButton(button, true, SystemClock.uptimeMillis()))
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || !isControllerKeyEvent(keyCode, event)) return false
        val consumed = consumedDownKeys.remove(keyCode)
        if (!consumed) return false
        val button = buttonFor(keyCode) ?: return true
        log("key up ${KeyEvent.keyCodeToString(keyCode)} → $button")
        apply(engine.onButton(button, false, SystemClock.uptimeMillis()))
        return true
    }

    // ------------------------------------------------------------------ motion events

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false
        if (!host.isPadActive()) return false
        checkDevice(event)
        syncEngine()
        val now = SystemClock.uptimeMillis()
        val device = event.device
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        // Right stick: AXIS_Z / AXIS_RZ on most gamepads; some report AXIS_RX / AXIS_RY instead.
        // Use RX/RY only when the device declares RX but not Z. A device without declared
        // ranges (virtual / injected events) keeps the standard Z/RZ.
        val useRx = device != null &&
            device.getMotionRange(MotionEvent.AXIS_Z, event.source) == null &&
            device.getMotionRange(MotionEvent.AXIS_RX, event.source) != null
        val rx = if (useRx) event.getAxisValue(MotionEvent.AXIS_RX) else event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = if (useRx) event.getAxisValue(MotionEvent.AXIS_RY) else event.getAxisValue(MotionEvent.AXIS_RZ)
        val l2 = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val r2 = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        if (Math.hypot(lx.toDouble(), ly.toDouble()) >= USE_DEAD_ZONE || Math.hypot(rx.toDouble(), ry.toDouble()) >= USE_DEAD_ZONE ||
            hx != 0f || hy != 0f || l2 >= 0.5f || r2 >= 0.5f) host.onControllerUsed()
        val axes = "L(%.2f,%.2f) R(%.2f,%.2f) L2=%.2f R2=%.2f hat(%.0f,%.0f)".format(lx, ly, rx, ry, l2, r2, hx, hy)
        if (axes != lastLoggedAxes && now - lastMotionLogAt >= 100) {
            lastLoggedAxes = axes
            lastMotionLogAt = now
            log("motion $axes src=0x${Integer.toHexString(event.source)}")
        }

        // D-pad reported as hat axes → synthesized D-pad buttons.
        val nhx = if (hx > 0.5f) 1 else if (hx < -0.5f) -1 else 0
        val nhy = if (hy > 0.5f) 1 else if (hy < -0.5f) -1 else 0
        if (nhx != hatX) { hatButton(hatX, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, false, now); hatButton(nhx, PadButton.DPAD_LEFT, PadButton.DPAD_RIGHT, true, now); hatX = nhx }
        if (nhy != hatY) { hatButton(hatY, PadButton.DPAD_UP, PadButton.DPAD_DOWN, false, now); hatButton(nhy, PadButton.DPAD_UP, PadButton.DPAD_DOWN, true, now); hatY = nhy }

        apply(engine.onSticks(lx, ly, rx, ry, now))
        apply(engine.onTriggers(l2, r2, now))
        if (Math.hypot(lx.toDouble(), ly.toDouble()) >= USE_DEAD_ZONE || Math.hypot(rx.toDouble(), ry.toDouble()) >= USE_DEAD_ZONE) {
            rememberSticks(device)
        }
        // Consumed: while the keyboard is up, stick motion must not reach the app
        // (ViewRootImpl would otherwise synthesize D-pad keys from it).
        return true
    }

    private fun hatButton(value: Int, negative: PadButton, positive: PadButton, pressed: Boolean, now: Long) {
        val b = when {
            value < 0 -> negative
            value > 0 -> positive
            else -> return
        }
        log("hat ${if (pressed) "press" else "release"} $b")
        apply(engine.onButton(b, pressed, now))
    }

    // ------------------------------------------------------------------ plumbing

    /** Amendment 20: Select (TOUCH_KEYBOARD) with sticks; the D-pad fallback's fixed table has no such button. */
    private fun isTouchKeyboardButton(button: PadButton): Boolean =
        !engine.fallback && engine.config.roleOf(button) == PadRole.TOUCH_KEYBOARD

    private fun syncEngine() {
        engine.mode = host.currentMode()
        engine.candidatesShown = host.candidatesShown()
    }

    private fun apply(decision: PadDecision) {
        if (decision.note.isNotEmpty()) log("→ ${decision.note}")
        for (action in decision.actions) {
            host.perform(action)
        }
        host.onGuideChanged(engine.guide)
        // Amendment 20: keep a running poll (a held stick in the emoji list sends motion events all the
        // time; re-posting on each would keep pushing the repeat back).
        if (engine.isRepeating) {
            if (!repeatPosted) {
                repeatPosted = true
                handler.postDelayed(repeatRunnable, engine.config.repeatIntervalMs)
            }
        } else {
            handler.removeCallbacks(repeatRunnable)
            repeatPosted = false
        }
    }

    private var repeatPosted = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatPosted = false
            if (!host.isPadActive()) { reset(); return }
            val d = engine.pollRepeat(SystemClock.uptimeMillis())
            if (d.note.isNotEmpty() && d.actions.none { it is PadAction.EmojiKeyboard || it is PadAction.Backspace }) log("→ ${d.note}")
            d.actions.forEach { host.perform(it) }
            if (d.actions.isNotEmpty() && PadAction.HideKeyboard !in d.actions) host.onGuideChanged(engine.guide)
            if (engine.isRepeating) {
                repeatPosted = true
                handler.postDelayed(this, engine.config.repeatIntervalMs)
            }
        }
    }

    private fun checkDevice(event: android.view.InputEvent) {
        if (event.deviceId == deviceChecked) return
        deviceChecked = event.deviceId
        val device = event.device
        if (device == null) {
            // Unknown device (e.g. injected events): assume sticks exist. The engine only
            // falls back to the D-pad layout when a device is known to have no joystick axes.
            engine.setDeviceHasSticks(true)
            log("device id=${event.deviceId} unknown to InputDevice; assuming sticks, fallback=${engine.fallback}")
            return
        }
        val hasSticks = device.motionRanges.any {
            it.axis == MotionEvent.AXIS_X && (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        }
        engine.setDeviceHasSticks(hasSticks)
        if (hasSticks) applyRememberedSticks(device)
        noteDevice(device)
        log("device '${device.name}' id=${device.id} sources=0x${Integer.toHexString(device.sources)} axes=${device.motionRanges.map { MotionEvent.axisToString(it.axis) }} sticks=$hasSticks fallback=${engine.fallback}")
    }

    private fun describeDevice(event: android.view.InputEvent): String {
        val d = event.device ?: return "id=${event.deviceId}"
        return "'${d.name}'"
    }

    private var logcatBudget = 40

    private fun log(text: String) {
        PadEventLog.add(logSource, text)
        // The first few lines also go to logcat, so a CI run shows whether events reached us.
        if (logcatBudget > 0) {
            logcatBudget--
            android.util.Log.d("PadController", "[$logSource] $text")
        }
    }

    companion object {
        /** Stick radius from which the controller counts as "in use" (full-size guide takes over). */
        const val USE_DEAD_ZONE = 0.3
        /** v13: string set of InputDevice descriptors whose sticks have reached the keyboard. */
        const val PREF_STICKS_SEEN_DEVICES = "tiltboy_sticks_seen_devices"

        /**
         * Amendment 25: is a real game controller connected right now ([DeviceProfile.isGameController])?
         * Read fresh each time the keyboard opens, so plugging a controller in or out takes effect.
         */
        fun gameControllerConnected(): Boolean = com.kazumaproject.pad_input.DeviceProfile.anyGameController(
            InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id)?.let(::describe) },
        )

        /**
         * v12 device check: a connected game controller declares a left stick. The guide that opens
         * before any controller event must be the stick guide, not the D-pad fallback.
         */
        fun gameControllerHasSticks(): Boolean =
            InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id)?.let(::describe) }.any {
                com.kazumaproject.pad_input.DeviceProfile.isGameController(it) &&
                    com.kazumaproject.pad_input.DeviceProfile.hasSticks(it.joystickAxes)
            }

        private fun describe(d: InputDevice): com.kazumaproject.pad_input.DeviceProfile.InputDeviceInfo =
            com.kazumaproject.pad_input.DeviceProfile.InputDeviceInfo(
                name = d.name ?: "",
                isVirtual = d.isVirtual,
                sources = d.sources,
                joystickAxes = d.motionRanges.filter { (it.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 }.map { it.axis }.toSet(),
                hasDpadKeys = d.hasKeys(
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                ).any { it },
            )

        /** Keys we treat as controller input (only these are ever consumed). */
        fun buttonFor(keyCode: Int): PadButton? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> PadButton.A
            KeyEvent.KEYCODE_BUTTON_B -> PadButton.B
            KeyEvent.KEYCODE_BUTTON_X -> PadButton.X
            KeyEvent.KEYCODE_BUTTON_Y -> PadButton.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> PadButton.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> PadButton.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> PadButton.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> PadButton.R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> PadButton.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> PadButton.R3
            KeyEvent.KEYCODE_BUTTON_START -> PadButton.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> PadButton.SELECT
            KeyEvent.KEYCODE_DPAD_UP -> PadButton.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> PadButton.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> PadButton.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> PadButton.DPAD_RIGHT
            else -> null
        }

        private fun isDpadKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

        /**
         * A key event is "controller" when it is a gamepad button, or comes from a gamepad /
         * joystick source. D-pad key codes also come from keyboards' arrow keys, so for them
         * the device must have a gamepad or joystick source. A keyboard is never a controller.
         */
        fun isControllerKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
            if (KeyEvent.isGamepadButton(keyCode)) return true
            val src = event.source
            if ((src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return true
            if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return true
            if (isDpadKey(keyCode)) {
                val dev = event.device ?: return false
                val s = dev.sources
                val isPad = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                val isKeyboard = dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                return isPad && !isKeyboard
            }
            return false
        }
    }
}
