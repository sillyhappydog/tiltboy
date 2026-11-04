package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.pad.PadController
import com.kazumaproject.markdownhelperkeyboard.pad.PadEventLog
import com.kazumaproject.markdownhelperkeyboard.pad.PadGuideView
import com.kazumaproject.markdownhelperkeyboard.pad.PadPreferences
import com.kazumaproject.pad_input.PadAction
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadGuideState
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.KanaGrid
import com.kazumaproject.pad_input.KanaPentagon
import com.kazumaproject.pad_input.Sector9
import kotlin.math.hypot

/**
 * Live controller diagnostics (Japanese UI). Shows every controller event this screen
 * receives, what the engine would do with it, and whatever the keyboard service itself logged
 * (same process). Saves the whole session to a file the user can share, and offers a guided
 * 4-step check whose result answers: do stick events reach the keyboard, and which key code
 * does each face button send.
 *
 * Events reach this screen through a focused view. While the test field has focus and the
 * keyboard is up, the keyboard service receives them instead and logs them as `[keyboard]`.
 */
class PadDiagnosticsFragment : Fragment() {

    private companion object {
        const val RECENT_LINES = 4
    }

    private lateinit var logView: TextView
    private lateinit var statusView: TextView
    private lateinit var recentView: TextView
    private lateinit var sessionView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var guide: PadGuideView
    private lateinit var imeBanner: LinearLayout
    private lateinit var imeBannerText: TextView
    private lateinit var imeEnableButton: Button
    private lateinit var imeSwitchButton: Button
    private lateinit var checkText: TextView
    private lateinit var checkButton: Button
    private lateinit var checkSkipButton: Button
    private lateinit var introView: TextView
    private lateinit var buttonsRow: LinearLayout
    private var keyboardShown = false
    private lateinit var checkRow: LinearLayout

    /**
     * The character step 6 asks for: the ↑ key flicked ← in the first enabled text mode, from the
     * tables rather than a literal. Amendment 16: the ↑ key is さ, so with かな on it is そ on the
     * pentagon (← = お段; amendment 18: the only kana right stick); without かな, e on the 英字 table.
     */
    private val checkTarget: String by lazy {
        val prefs = PadPreferences.prefs(requireContext())
        val modes = PadPreferences.modes(prefs)
        val mode = if (PadMode.KANA in modes) PadMode.KANA else PadMode.ENGLISH
        val gesture = if (mode == PadMode.KANA) KanaPentagon.gesture(KanaPentagon.slotOf(-1f, 0f)) else GestureType.FlickLeft
        KanaGrid.charFor(KanaGrid.keyFor(Sector9.UP, mode), gesture, mode)?.toString().orEmpty()
    }

    private var mode: InputMode = InputMode.ModeJapanese
    private var sessionMark = 0L

    private val controller = PadController(object : PadController.Host {
        override fun isPadActive(): Boolean = true
        override fun currentMode(): InputMode = mode
        override fun candidatesShown(): Boolean = false
        override fun perform(action: PadAction) {
            if (action is PadAction.SwitchMode) mode = action.mode
            PadEventLog.add("screen", "would do: ${PadInputEngine.describe(action)}")
        }
        override fun onGuideChanged(state: PadGuideState) { guide.render(state) }
    }, logSource = "screen")

    private val listener = PadEventLog.Listener { line -> onLogLine(line) }
    private val bannerTick = Runnable { refreshImeBanner() }

    // ------------------------------------------------------------------ guided check state

    private var check: GuidedCheck? = null

    private class GuidedCheck {
        var step = 1
        val leftSectors = HashSet<Int>()
        val rightSectors = HashSet<Int>()
        val buttons = LinkedHashMap<String, String>() // printed order A,B,X,Y -> key code name
        var l3: String? = null          // key code the left stick click sent, or null
        var r3: String? = null
        var l3Skipped = false
        var r3Skipped = false
        val otherKeys = LinkedHashSet<String>() // keys seen during the stick-click steps that were not L3/R3
        var screenMotion = 0
        var keyboardMotion = 0
        var typedKi = false
        val startMark: Long = PadEventLog.totalAdded
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.pad_diag_title)
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(8), px(12), px(8))
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }

        // Banner: Sumire Pad not enabled / not the current keyboard.
        imeBanner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFF3CD.toInt())
            setPadding(px(10), px(8), px(10), px(8))
            isVisible = false
        }
        imeBannerText = TextView(ctx).apply { setTextColor(0xFF5C4400.toInt()) }
        imeBanner.addView(imeBannerText)
        val bannerButtons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        imeEnableButton = Button(ctx).apply {
            text = getString(R.string.pad_ime_enable_button)
            isFocusable = false
            setOnClickListener { runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        imeSwitchButton = Button(ctx).apply {
            text = getString(R.string.pad_ime_switch_button)
            isFocusable = false
            setOnClickListener { ctx.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
        }
        bannerButtons.addView(imeEnableButton)
        bannerButtons.addView(imeSwitchButton)
        imeBanner.addView(bannerButtons)
        root.addView(imeBanner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(6) })

        introView = TextView(ctx).apply { text = getString(R.string.pad_diag_intro); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        root.addView(introView)
        root.addView(EditText(ctx).apply {
            hint = getString(R.string.pad_diag_test_field_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
        })
        // The newest events, right under the text field, so they stay visible while the keyboard
        // covers the lower half of the screen (amendment 3).
        recentView = TextView(ctx).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = RECENT_LINES
            setBackgroundColor(0x14000000)
            setPadding(px(6), px(2), px(6), px(2))
        }
        root.addView(recentView)
        statusView = TextView(ctx).apply {
            text = getString(R.string.pad_diag_status_waiting)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        root.addView(statusView)

        // Guided check row.
        checkRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        checkText = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); text = getString(R.string.pad_check_title) }
        checkButton = Button(ctx).apply {
            text = getString(R.string.pad_check_start)
            isFocusable = false
            setOnClickListener { if (check == null) startCheck() else cancelCheck() }
        }
        checkSkipButton = Button(ctx).apply {
            text = getString(R.string.pad_check_skip)
            isFocusable = false
            isVisible = false
            setOnClickListener { skipStep() }
        }
        checkRow.addView(checkText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        checkRow.addView(checkSkipButton)
        checkRow.addView(checkButton)
        root.addView(checkRow)

        guide = PadGuideView(ctx)
        root.addView(guide, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(140)).apply { topMargin = px(4) })
        guide.render(controller.engine.guide)

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        buttonsRow = buttons
        sessionView = TextView(ctx).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) }
        buttons.addView(sessionView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(ctx).apply {
            text = getString(R.string.pad_diag_clear)
            isFocusable = false
            setOnClickListener { logView.text = ""; sessionMark = PadEventLog.totalAdded; updateSessionCount(); updateRecent() }
        })
        buttons.addView(Button(ctx).apply {
            text = getString(R.string.pad_diag_save)
            isFocusable = false
            setOnClickListener { saveAndShare(share = true) }
        })
        root.addView(buttons)

        logView = TextView(ctx).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            movementMethod = ScrollingMovementMethod()
            setTextIsSelectable(true)
            isFocusable = false
        }
        scroll = ScrollView(ctx).apply { addView(logView); isFocusable = false }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.setOnKeyListener { _, keyCode, event ->
            observeKey(keyCode, event)
            when (event.action) {
                KeyEvent.ACTION_DOWN -> controller.onKeyDown(keyCode, event)
                KeyEvent.ACTION_UP -> controller.onKeyUp(keyCode, event)
                else -> false
            }
        }
        root.setOnGenericMotionListener { _, event ->
            observeMotion(event)
            controller.onGenericMotionEvent(event)
        }
        // While the keyboard is up only ~230 px remain between the title bar and the candidate bar
        // on the RG477V. Padding the root for the IME inset left that area blank (device check,
        // amendment 8), so instead: hide what the keyboard would cover anyway and keep the text
        // field, the newest log lines and the guided-check text at the top.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = ViewCompat.getRootWindowInsets(v)?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            setKeyboardShown(ime - bars > px(40))
            insets
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        controller.reloadConfig(requireContext())
        controller.probeDevices()
        sessionMark = PadEventLog.totalAdded
        val dm = resources.displayMetrics
        PadEventLog.add("screen", "diagnostics opened: ${deviceSummary()}")
        PadEventLog.add("screen", "display: ${dm.widthPixels}x${dm.heightPixels} px, density=${dm.density} (${dm.densityDpi} dpi), ime=${currentIme()}")
        logView.text = PadEventLog.since(sessionMark).joinToString("\n")
        statusView.text = deviceSummary()
        updateSessionCount()
        updateRecent()
        view.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        PadEventLog.addListener(listener)
    }

    override fun onStop() {
        PadEventLog.removeListener(listener)
        controller.reset()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshImeBanner()
        view?.requestFocus()
    }

    override fun onPause() {
        view?.removeCallbacks(bannerTick)
        super.onPause()
    }

    /** Keyboard up: drop everything below the check row so the field and the newest lines stay in view. */
    /** The check row while the keyboard is up: step text only, and only during a check (amendment 13). */
    private fun updateCheckRow() {
        checkRow.isVisible = !keyboardShown || check != null
        checkButton.isVisible = !keyboardShown
        if (keyboardShown) checkSkipButton.isVisible = false
        else checkSkipButton.isVisible = check?.step in 4..5
    }

    private fun setKeyboardShown(shown: Boolean) {
        if (shown == keyboardShown) return
        keyboardShown = shown
        introView.isVisible = !shown
        statusView.isVisible = !shown
        guide.isVisible = !shown
        buttonsRow.isVisible = !shown
        scroll.isVisible = !shown
        // Amendment 13: with the keyboard up there is no room for the check's buttons (they were
        // squashed to dots). Keep only the step text, and only while a check runs.
        updateCheckRow()
        recentView.maxLines = if (shown) 3 else RECENT_LINES
        updateRecent()
    }

    // ------------------------------------------------------------------ IME banner

    private fun imeComponent(): String = ComponentName(requireContext(), IMEService::class.java).flattenToShortString()

    private fun currentIme(): String =
        Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()

    private fun isSumirePadCurrent(): Boolean {
        val cur = ComponentName.unflattenFromString(currentIme()) ?: return false
        return cur == ComponentName(requireContext(), IMEService::class.java)
    }

    private fun isSumirePadEnabled(): Boolean {
        val imm = requireContext().getSystemService(InputMethodManager::class.java) ?: return false
        val me = ComponentName(requireContext(), IMEService::class.java)
        return imm.enabledInputMethodList.any { it.component == me }
    }

    /** Shows the banner while Sumire Pad is not the current keyboard; re-checked every 2 s. */
    private fun refreshImeBanner() {
        val v = view ?: return
        if (!isAdded) return
        when {
            isSumirePadCurrent() -> imeBanner.isVisible = false
            !isSumirePadEnabled() -> {
                imeBanner.isVisible = true
                imeBannerText.text = getString(R.string.pad_ime_banner_not_enabled)
                imeEnableButton.isVisible = true
                imeSwitchButton.isVisible = true
            }
            else -> {
                imeBanner.isVisible = true
                val label = currentImeLabel()
                imeBannerText.text = getString(R.string.pad_ime_banner_not_selected, label)
                imeEnableButton.isVisible = false
                imeSwitchButton.isVisible = true
            }
        }
        v.removeCallbacks(bannerTick)
        v.postDelayed(bannerTick, 2000)
    }

    private fun currentImeLabel(): String {
        val cur = currentIme()
        val imm = requireContext().getSystemService(InputMethodManager::class.java) ?: return cur
        val info = imm.inputMethodList.firstOrNull { it.id == cur } ?: return cur
        return info.loadLabel(requireContext().packageManager).toString()
    }

    // ------------------------------------------------------------------ log

    private fun onLogLine(line: String) {
        if (!isAdded) return
        logView.append((if (logView.text.isNotEmpty()) "\n" else "") + line)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        updateSessionCount()
        updateRecent()
        check?.let { c ->
            if (line.contains("[keyboard]") && line.contains("motion L(")) c.keyboardMotion++
            if (line.contains("[keyboard]") && checkTarget.isNotEmpty() && line.contains("typed $checkTarget")) c.typedKi = true
            advanceCheck()
        }
    }

    private fun updateRecent() {
        val n = if (keyboardShown) 3 else RECENT_LINES
        recentView.text = PadEventLog.since(sessionMark).takeLast(n).joinToString("\n") { it.substringAfter(' ') }
    }

    private fun updateSessionCount() {
        val lines = PadEventLog.since(sessionMark)
        sessionView.text = getString(R.string.pad_diag_session_lines, lines.size, lines.count { it.contains("[keyboard]") })
    }

    private fun deviceSummary(): String {
        val ids: IntArray = InputDevice.getDeviceIds()
        val pads = ids.toList().mapNotNull { id -> InputDevice.getDevice(id) }.filter {
            (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (it.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
        if (pads.isEmpty()) return getString(R.string.pad_diag_no_devices)
        return getString(R.string.pad_diag_devices, pads.joinToString("; ") { d ->
            "${d.name} (id=${d.id}, sources=0x${Integer.toHexString(d.sources)}, axes=${d.motionRanges.map { MotionEvent.axisToString(it.axis) }})"
        })
    }

    private fun header(): String {
        val dm = resources.displayMetrics
        return buildString {
            append("TiltBoy controller log\n")
            append("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("display: ${dm.widthPixels}x${dm.heightPixels} px, density=${dm.density} (${dm.densityDpi} dpi)\n")
            append("app: ${requireContext().packageName} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), kana right stick: pentagon\n")
            append("current ime: ${currentIme()} (TiltBoy = ${imeComponent()})\n")
            append(deviceSummary())
            check?.let { append("\n").append(checkSummary(it)) }
        }
    }

    private fun saveAndShare(share: Boolean): Int {
        val ctx = requireContext()
        val body = PadEventLog.since(sessionMark)
        val file = PadEventLog.save(ctx, header(), body)
        if (body.size < 20) Toast.makeText(ctx, getString(R.string.pad_diag_save_warning_few, body.size), Toast.LENGTH_LONG).show()
        Toast.makeText(ctx, getString(R.string.pad_diag_saved, body.size, file.absolutePath), Toast.LENGTH_LONG).show()
        if (!share) return body.size
        val uri = runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file) }.getOrNull()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, file.readText())
            }
        }
        runCatching { startActivity(Intent.createChooser(intent, file.name)) }
        return body.size
    }

    // ------------------------------------------------------------------ guided check

    private fun startCheck() {
        check = GuidedCheck()
        checkButton.text = getString(R.string.pad_check_cancel)
        PadEventLog.add("screen", "guided check started")
        showStep()
    }

    private fun cancelCheck() {
        check = null
        checkButton.text = getString(R.string.pad_check_start)
        updateCheckRow()
        checkSkipButton.isVisible = false
        checkText.text = getString(R.string.pad_check_title)
        PadEventLog.add("screen", "guided check cancelled")
    }

    /** Steps 4 and 5 (stick clicks) can be skipped when the device sends nothing for them. */
    private fun skipStep() {
        val c = check ?: return
        when (c.step) {
            4 -> { c.l3Skipped = true; c.step = 5; PadEventLog.add("screen", "guided check: L3 skipped (nothing arrived)") }
            5 -> { c.r3Skipped = true; c.step = 6; PadEventLog.add("screen", "guided check: R3 skipped (nothing arrived)") }
        }
        advanceCheck() // a click already seen for the next step moves on at once; also redraws the step
    }

    private fun showStep() {
        val c = check ?: return
        checkSkipButton.isVisible = !keyboardShown && (c.step == 4 || c.step == 5)
        checkRow.isVisible = true
        checkText.text = when (c.step) {
            1 -> getString(R.string.pad_check_step1) + "\n" + getString(R.string.pad_check_progress, "${c.leftSectors.size}/8")
            2 -> getString(R.string.pad_check_step2) + "\n" + getString(R.string.pad_check_progress, "${c.rightSectors.size}/8")
            3 -> getString(R.string.pad_check_step3) + "\n" + getString(R.string.pad_check_progress, c.buttons.keys.joinToString(" "))
            4 -> getString(R.string.pad_check_step4) + (if (c.otherKeys.isEmpty()) "" else "\n" + getString(R.string.pad_check_progress, c.otherKeys.joinToString(" ")))
            5 -> getString(R.string.pad_check_step5) + (if (c.otherKeys.isEmpty()) "" else "\n" + getString(R.string.pad_check_progress, c.otherKeys.joinToString(" ")))
            else -> getString(R.string.pad_check_step6, checkTarget)
        }
    }

    private fun sectorOf(x: Float, y: Float): Int? {
        if (hypot(x, y) < 0.6f) return null
        var deg = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble()))
        if (deg < 0) deg += 360.0
        return ((deg + 22.5) / 45.0).toInt() % 8
    }

    private fun observeMotion(event: MotionEvent) {
        val c = check ?: return
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        c.screenMotion++
        sectorOf(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))?.let { c.leftSectors += it }
        sectorOf(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))?.let { c.rightSectors += it }
        advanceCheck()
    }

    private fun observeKey(keyCode: Int, event: KeyEvent) {
        val c = check ?: return
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return
        if (!KeyEvent.isGamepadButton(keyCode) && !event.isFromSource(InputDevice.SOURCE_GAMEPAD)) return
        val name = KeyEvent.keyCodeToString(keyCode)
        if (c.step == 4 || c.step == 5) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_THUMBL -> { c.l3 = name; PadEventLog.add("screen", "guided check: L3 arrives as $name") }
                KeyEvent.KEYCODE_BUTTON_THUMBR -> { c.r3 = name; PadEventLog.add("screen", "guided check: R3 arrives as $name") }
                else -> { c.otherKeys += name; PadEventLog.add("screen", "guided check: stick click step saw $name") }
            }
            advanceCheck()
            return
        }
        if (c.step != 3) return
        val button = PadController.buttonFor(keyCode)
        val expected = listOf(PadButton.A, PadButton.B, PadButton.X, PadButton.Y)
        val next = expected.getOrNull(c.buttons.size) ?: return
        // Record what the device sent for the printed button the user was asked to press.
        c.buttons[next.name] = "${KeyEvent.keyCodeToString(keyCode)}${if (button != null && button != next) " (= ${button.name} by label)" else ""}"
        PadEventLog.add("screen", "guided check: printed ${next.name} sends ${KeyEvent.keyCodeToString(keyCode)}")
        advanceCheck()
    }

    private fun advanceCheck() {
        val c = check ?: return
        when (c.step) {
            1 -> if (c.leftSectors.size >= 8) { c.step = 2; PadEventLog.add("screen", "guided check: left stick ok") }
            2 -> if (c.rightSectors.size >= 8) { c.step = 3; PadEventLog.add("screen", "guided check: right stick ok") }
            3 -> if (c.buttons.size >= 4) { c.step = 4; PadEventLog.add("screen", "guided check: buttons ${c.buttons}") }
            4 -> if (c.l3 != null) c.step = 5
            5 -> if (c.r3 != null) c.step = 6
            6 -> if (c.typedKi) finishCheck()
        }
        if (check != null) showStep()
    }

    private fun checkSummary(c: GuidedCheck): String = buildString {
        append("guided check: step=${c.step}\n")
        append("  screen motion events=${c.screenMotion}, keyboard motion lines=${c.keyboardMotion}\n")
        append("  left sectors=${c.leftSectors.sorted()}, right sectors=${c.rightSectors.sorted()}\n")
        append("  buttons=${c.buttons}\n")
        append("  L3=${c.l3 ?: if (c.l3Skipped) "not received (skipped)" else "-"}, R3=${c.r3 ?: if (c.r3Skipped) "not received (skipped)" else "-"}, other keys during stick-click steps=${c.otherKeys}\n")
        append("  typed $checkTarget via keyboard=${c.typedKi}")
    }

    private fun finishCheck() {
        val c = check ?: return
        PadEventLog.add("screen", checkSummary(c).replace("\n", " | "))
        val reached = getString(R.string.pad_check_reached)
        val notReached = getString(R.string.pad_check_not_reached)
        val lines = saveAndShare(share = false)
        checkText.text = listOf(
            getString(R.string.pad_check_done, lines),
            getString(R.string.pad_check_result_sticks, if (c.screenMotion > 0) reached else notReached, if (c.keyboardMotion > 0) reached else notReached),
            getString(R.string.pad_check_result_buttons, c.buttons.entries.joinToString(", ") { "${it.key}→${it.value}" }),
            getString(
                R.string.pad_check_result_clicks,
                c.l3?.let { getString(R.string.pad_check_reached_detail, reached, it) } ?: notReached,
                c.r3?.let { getString(R.string.pad_check_reached_detail, reached, it) } ?: notReached,
            ),
            getString(when {
                c.r3 != null -> R.string.pad_check_r3_ok
                else -> R.string.pad_check_r3_missing
            }),
            getString(R.string.pad_check_result_typed, checkTarget, if (c.typedKi) reached else notReached),
        ).joinToString("\n")
        check = null
        checkSkipButton.isVisible = false
        checkButton.text = getString(R.string.pad_check_start)
        updateCheckRow()
    }
}
