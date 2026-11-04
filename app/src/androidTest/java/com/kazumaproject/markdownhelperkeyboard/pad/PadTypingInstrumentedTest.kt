package com.kazumaproject.markdownhelperkeyboard.pad

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.kazumaproject.markdownhelperkeyboard.pad.PadEventLog
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Emulator test: the real Sumire Pad IME receives injected game-controller events
 * (gamepad key events + joystick motion) and, with the amendment 16 (v5) defaults, types
 * 「きょうはいいてんき」 (left stick 3×3 with あ at rest, the kana pentagon on the right stick;
 * amendment 21: the ↘ key is や/わ with six slots on the right stick: R1 + ↘ (−30°) for ょ, ↖ (150°)
 * for ん), converts with Y by looking the candidate up in the IME's own list, confirms with A; types
 * がっ with the hold roles (L2 濁点, R1 小文字), gestures only; 「きかき」 → 気化器; 「ねこ」 → an emoji
 * candidate; 「わをん」 on the や/わ key (を by re-aiming: ↑ out rotated to ↙, amendment 17) and 。
 * through R2 (記号 in かな since amendment 21), and む by re-aiming; 「げーむ」 → ゲーム with ー through
 * R2 (←); then, on the amendment 14 four-way layouts, "hello world" (X = space)
 * and "Hi!" (L2 for H) in 英字 and "2026" in 数字 (the mode is probed, not assumed); amendment 22:
 * "abc123" in 英字 with L1 held for the digits, L1 + R2 ↖ = {, and L1 let go before the right stick is
 * home still typing the digit; A with
 * nothing composing; amendment 18: the modes stepped with R3 / L3 (not Select), & through R2 in 英字,
 * and a tap on the guide's touch digit 7, which types 7 and keeps the guide up; amendment 20: touches
 * on the guide's grid, ring, ABXY and background do nothing, Select shows the touch keys (英字:
 * QWERTY) and brings the guide back, as does a stick tilt; Start opens Sumire's emoji list, the left
 * stick moves the selection, A inserts an emoji and B goes back to かな. Amendment 23: はしをわたる, Y, then
 * the D-pad ↑ / ↓ change the conversion range (はし | をわたる, はしを | わたる, the limits do nothing),
 * ← / → still step through the candidates, A commits the converted part and the rest stays composing.
 * Amendment 24: B while converting leaves the conversion and deletes one character: がっこお, Y, B →
 * がっこ (then う, Y → 学校); はしをわたる, Y, ↓ ×4, B → は | をわたる (Y converts は); がっこう, Y, D-pad ←
 * still the previous candidate, B held → several characters gone, nothing converted.
 *
 * Readiness (amendment 14): nothing waits a fixed time for the IME. The test waits until the
 * kana-kanji engine has loaded ([PadTestHooks.engineReady]) before typing, and until the IME has
 * shown the candidate list computed for the typed reading ([PadTestHooks.candidatesInput]) before
 * converting. The timeouts are ceilings for a hung IME.
 *
 * Needs an emulator/device with the liteFdroid debug build installed (the debug source set
 * provides FastInputHostActivity). Run with
 *   ./gradlew :app:connectedLiteFdroidDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.kazumaproject.markdownhelperkeyboard.pad.PadTypingInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class PadTypingInstrumentedTest {

    /**
     * A hung IME would otherwise make every synchronous injection wait ~30 s; fail fast instead.
     * v5 (run 22) took 217 s; amendment 17 adds the ゲーム conversion, so the ceiling is 420 s.
     * v9 (run 29) took about 300 s; amendment 23 adds the D-pad conversion range, so 600 s.
     * v10 (run 30) took 460 s; amendment 24 adds three B-while-converting sequences, and on a busy
     * emulator (run 31) the scenario took 603 s, so 900 s.
     */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(900)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val target get() = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"

    private var previousIme: String = ""
    private var wasEnabled = false
    private var downTime = 0L

    private companion object {
        const val TAG = "PadTyping"
        /** Ceilings for a hung IME, not expected delays. */
        const val ENGINE_CEILING_MS = 90_000L
        const val CANDIDATE_CEILING_MS = 30_000L
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    @Before
    fun selectSumirePad() {
        // Amendment 12: a fresh install on an English emulator starts with 英字 + 数字 only; this
        // scenario types kana too, so turn all three modes on before the keyboard reads its settings.
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(PadPreferences.MODES, PadPreferences.MODE_VALUES.values.toSet()).commit()
        // The previous IME instance's probe (a new instance publishes its own one: awaitOurIme).
        val previousProbe = PadTestHooks.touchKeyboard?.get()
        previousIme = shell("settings get secure default_input_method")
        wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        shell("ime enable $target")
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            shell("ime set $target")
            if (shell("settings get secure default_input_method") == target) break
            SystemClock.sleep(300)
        }
        assertEquals("Sumire Pad must be the current IME", target, shell("settings get secure default_input_method"))
        Log.i(TAG, "IME selected: $target")
        // Same way upstream's device tests open the editor (no ActivityScenario: its onActivity()
        // calls waitForIdleSync(), which never returned in CI run 7 while the IME was up).
        shell("am start -W -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
        awaitHost()
        Log.i(TAG, "host resumed")
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        awaitOurIme(previousProbe)
        Log.i(TAG, "IME shown")
        awaitEngine()
    }

    /**
     * The keyboard shown is TiltBoy's: a TiltBoy instance other than [previous] (the one the previous
     * test disabled) has built its views and published its probe.
     * Run 31 (first attempt): after the previous test had disabled TiltBoy, the system showed a
     * keyboard but never started TiltBoy again, and nothing reached it. Then select it once more.
     * Never fails by itself (the scenario does if the keyboard is not there).
     */
    private fun awaitOurIme(previous: PadTestHooks.TouchKeyboardProbe?) {
        for (attempt in 0 until 3) {
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (SystemClock.uptimeMillis() < deadline) {
                val probe = PadTestHooks.touchKeyboard?.get()
                if (probe != null && probe !== previous) { Log.i(TAG, "TiltBoy's keyboard is up (attempt ${attempt + 1})"); return }
                SystemClock.sleep(200)
            }
            Log.w(TAG, "TiltBoy has not built its keyboard (attempt ${attempt + 1}): selecting it again")
            shell("ime enable $target")
            shell("ime set $target")
            onActivity { it.restartEditorInput(clearText = true) }
            awaitIme()
        }
        Log.w(TAG, "no new TiltBoy keyboard seen; going on")
    }

    /** Readiness: the IME's kana-kanji engine (core dictionaries) has loaded. */
    private fun awaitEngine() {
        val start = SystemClock.uptimeMillis()
        while (!PadTestHooks.engineReady && SystemClock.uptimeMillis() - start < ENGINE_CEILING_MS) SystemClock.sleep(100)
        assertTrue("the kana-kanji engine did not finish loading within ${ENGINE_CEILING_MS / 1000} s", PadTestHooks.engineReady)
        Log.i(TAG, "engine ready after ${SystemClock.uptimeMillis() - start} ms")
    }

    @After
    fun restore() {
        runCatching {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().forEach { it.finish() }
            }
        }
        if (previousIme.isNotBlank() && previousIme != "null" && previousIme != target) shell("ime set $previousIme")
        if (!wasEnabled) shell("ime disable $target")
    }

    private fun host(): FastInputHostActivity? {
        var found: FastInputHostActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FastInputHostActivity>().firstOrNull()
        }
        return found
    }

    private fun awaitHost() {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (host() != null) return
            SystemClock.sleep(100)
        }
        throw AssertionError("FastInputHostActivity did not resume")
    }

    // ------------------------------------------------------------------ the test

    @Test
    fun typesConvertsConfirmsAndInsertsEmojiWithTheController() {
        try {
            runScenario()
        } catch (t: Throwable) {
            // The IME runs in this process, so its own event log is readable here.
            Log.e(TAG, "FAILED: ${t.message}; text now='${runCatching { text() }.getOrNull()}'")
            PadEventLog.snapshot().takeLast(60).forEach { Log.e(TAG, "padlog: $it") }
            throw t
        }
    }

    private fun step(name: String) { Log.i(TAG, "step: $name (text='${runCatching { text() }.getOrNull()}')") }

    // ------------------------------------------------------------------ amendment 19: the touch keyboard

    /** What the touch keyboard shows ("tenkey/kana", "qwerty/english", "tenkey/number", …), from the IME. */
    private fun touchState(): String {
        var s = "none"
        instrumentation.runOnMainSync { s = PadTestHooks.touchKeyboard?.get()?.state() ?: "none" }
        return s
    }

    /** The visible view whose resource entry name is [name] under [root]. */
    private fun findByName(root: View, name: String): View? {
        if (root.isShown && root.id != View.NO_ID && runCatching { root.resources.getResourceEntryName(root.id) }.getOrNull() == name) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findByName(root.getChildAt(i), name)?.let { return it }
        return null
    }

    /**
     * A finger tap on the touch keyboard's own switch key: the 12-key's あA1 key, QWERTY's key back
     * to the 12-key (its number key, else its keyboard key).
     */
    private fun tapSwitchKey() {
        var pos: IntArray? = null
        var which = ""
        instrumentation.runOnMainSync {
            val root = PadTestHooks.touchKeyboard?.get()?.keyboardRoot() ?: return@runOnMainSync
            val key = listOf("key_switch_key_mode", "switch_number_layout", "key_switch_default")
                .firstNotNullOfOrNull { n -> findByName(root, n)?.also { which = n } } ?: return@runOnMainSync
            val xy = IntArray(2).also { key.getLocationOnScreen(it) }
            pos = intArrayOf(xy[0] + key.width / 2, xy[1] + key.height / 2)
        }
        val (x, y) = pos ?: throw AssertionError("no switch key on the touch keyboard (${touchState()})")
        Log.i(TAG, "tap on the switch key '$which' at ($x, $y), keyboard ${touchState()}")
        val down = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 70)) {
            val e = MotionEvent.obtain(down, time, action, x.toFloat(), y.toFloat(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue("inject touch", automation.injectInputEvent(e, true))
            e.recycle()
            SystemClock.sleep(70)
        }
    }

    /**
     * Amendment 19: the touch keyboard's own switch key cycles only the 12-key (かな), QWERTY (英字)
     * and the 12-key's numbers (数字); no 五十音, モールス, スミレ入力 or custom keyboard appears.
     * Then the controller, used while QWERTY is up, types on the 12-key again (英字 cell ↑ + ← = e).
     */
    @Test
    fun theTouchSwitchKeyCyclesOnlyTenkeyQwertyAndNumbers() {
        try {
            val allowed = setOf("tenkey/kana", "qwerty/english", "tenkey/number")
            val seen = mutableListOf(touchState())
            for (i in 0 until 6) {
                tapSwitchKey()
                val deadline = SystemClock.uptimeMillis() + 3_000
                while (SystemClock.uptimeMillis() < deadline && touchState() == seen.last()) SystemClock.sleep(100)
                SystemClock.sleep(300)
                seen += touchState()
            }
            Log.i(TAG, "SWITCH-KEY states: $seen")
            if (seen.first() != "tenkey/kana") Log.w(TAG, "SWITCH-KEY: the session started on ${seen.first()}")
            assertTrue("only the 12-key, QWERTY and the numbers appear: $seen", seen.all { it in allowed })
            assertEquals("the switch key reaches all three (かな, 英字 on QWERTY, 数字): $seen", allowed, seen.toSet())
            // To QWERTY again, then the controller takes over: the 12-key comes back and types e.
            for (i in 0 until 3) {
                if (touchState() == "qwerty/english") break
                val before = touchState()
                tapSwitchKey()
                val deadline = SystemClock.uptimeMillis() + 3_000
                while (SystemClock.uptimeMillis() < deadline && touchState() == before) SystemClock.sleep(100)
                SystemClock.sleep(300)
            }
            assertEquals("qwerty/english", touchState())
            stick(0f, -1f); flick(Dir.LEFT); stick(0f, 0f)
            awaitText("e", 5_000)
            assertEquals("the controller types on the 12-key", "tenkey/english", touchState())
            step("controller typed e after the touch QWERTY")
            tap(KeyEvent.KEYCODE_BUTTON_A)
        } catch (t: Throwable) {
            Log.e(TAG, "FAILED: ${t.message}; touch keyboard ${runCatching { touchState() }.getOrNull()}; text='${runCatching { text() }.getOrNull()}'")
            PadEventLog.snapshot().takeLast(30).forEach { Log.e(TAG, "padlog: $it") }
            throw t
        }
    }

    private fun runScenario() {
        step("start")
        // --- きょうはいいてんき (kana, amendment 16): the left stick picks the key (↖ か ↑ さ ↗ た /
        //     ← な ・あ → は / ↙ ま ↓ ら ↘ や/わ), the right stick types on the kana pentagon
        //     (↑ あ段, → い段, ↘ う段, ↙ え段, ← お段); amendment 21: on the や/わ key the right stick
        //     has six slots ↑ や ↗ ゆ ↘ よ ↓ わ ↙ を ↖ ん (60° each); R1 held = 小文字.
        stick(-0.707f, -0.707f); flick(Dir.RIGHT)               // き (か key ↖, → い段)
        stick(0.707f, 0.707f)
        key(KeyEvent.KEYCODE_BUTTON_R1, down = true)
        flickDeg(-30.0)                                         // ょ (R1 + the や/わ key ↘, right stick ↘ −30°)
        key(KeyEvent.KEYCODE_BUTTON_R1, down = false)
        stick(0f, 0f)
        flick(Dir.DOWN_RIGHT)                                   // う (あ key at rest, ↘ う段)
        stick(1f, 0f); flick(Dir.UP)                            // は (は key →, ↑ あ段)
        stick(0f, 0f); flick(Dir.RIGHT); flick(Dir.RIGHT)       // いい (あ key, → い段)
        stick(0.707f, -0.707f); flick(Dir.DOWN_LEFT)            // て (た key ↗, ↙ え段)
        stick(0.707f, 0.707f); flickDeg(150.0)                  // ん (the や/わ key ↘, right stick ↖ 150°)
        stick(-0.707f, -0.707f); flick(Dir.RIGHT)               // き
        stick(0f, 0f)
        step("kana typed")
        awaitText("きょうはいいてんき", 8_000)
        convertTo("きょうはいいてんき", "今日はいい天気")
        tap(KeyEvent.KEYCODE_BUTTON_A)                          // 決定
        awaitText("今日はいい天気", 5_000)
        step("confirmed")
        onActivity { assertTrue("composing span must be gone after 決定", android.view.inputmethod.BaseInputConnection.getComposingSpanStart(it.editText.text) < 0) }

        // --- hold roles, gestures only: L2 + か (↖) + ↑ (あ段) → が, R1 + た (↗) + ↘ (う段) → っ ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        key(KeyEvent.KEYCODE_BUTTON_L2, down = true); stick(-0.707f, -0.707f); flick(Dir.UP); key(KeyEvent.KEYCODE_BUTTON_L2, down = false)
        key(KeyEvent.KEYCODE_BUTTON_R1, down = true); stick(0.707f, -0.707f); flick(Dir.DOWN_RIGHT); key(KeyEvent.KEYCODE_BUTTON_R1, down = false)
        stick(0f, 0f)
        awaitText("がっ", 5_000)
        step("hold roles typed がっ")

        // --- きかき → 気化器 with Y, A confirms (amendment 11) ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(-0.707f, -0.707f); flick(Dir.RIGHT); flick(Dir.UP); flick(Dir.RIGHT)   // き か き (か key ↖: → い段, ↑ あ段)
        stick(0f, 0f)
        awaitText("きかき", 5_000)
        convertTo("きかき", "気化器")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("気化器", 5_000)
        step("気化器 confirmed")

        // --- ねこ → emoji candidate (Y walks the list, A confirms) ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(-1f, 0f); flick(Dir.DOWN_LEFT)                    // ね (な key ←, ↙ え段)
        stick(-0.707f, -0.707f); flick(Dir.LEFT)                // こ (か key ↖, ← お段)
        stick(0f, 0f)
        awaitText("ねこ", 5_000)
        val isEmoji = { c: String -> c.isNotEmpty() && c.codePoints().allMatch { cp -> cp >= 0x1F000 || cp == 0xFE0F || cp == 0x200D } }
        if (awaitCandidatesFor("ねこ").none(isEmoji)) tap(KeyEvent.KEYCODE_BUTTON_Y)   // not predicted: start the conversion
        val emojiIndex = awaitCandidate(CANDIDATE_CEILING_MS, isEmoji)
        var emojiFound = false
        for (i in 0 until emojiIndex + 6) {
            if (isEmoji(text())) { emojiFound = true; break }
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            SystemClock.sleep(250)
        }
        if (!emojiFound && isEmoji(text())) emojiFound = true
        assertTrue("no emoji candidate reached for ねこ; text=${text()} candidates=${PadTestHooks.candidates.take(20)}", emojiFound)
        val emoji = text()
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText(emoji, 5_000)
        step("emoji candidate confirmed: $emoji")

        // --- Amendment 21: わをん on the や/わ key (↘): right stick ↓ わ; を by re-aiming: ↑ out (や)
        //     and rotated along the rim past ↖ (ん) to ↙; ↖ ん; then 。 through R2 (記号 in かな, ↑),
        //     and ？ through R2 by re-aiming (← ー out, rotated to ↙). ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(0.707f, 0.707f)
        flick(Dir.DOWN)                                         // わ
        rimFlick(90.0, 210.0)                                   // を (↑ や out, rotated to ↙)
        flickDeg(150.0)                                         // ん
        stick(0f, 0f)
        key(KeyEvent.KEYCODE_BUTTON_R2, down = true)
        layerFlick(0f, -1f)                                     // 。 (R2 記号, left stick ↑)
        layerRimFlick(180.0, 225.0)                             // ？ (← ー out, rotated to ↙)
        key(KeyEvent.KEYCODE_BUTTON_R2, down = false)
        awaitText("わをん。？", 5_000)
        step("わをん。？ typed (や/わ key with a re-aim, R2 記号 with a re-aim)")

        // --- ゲーム (amendment 17, the user's decision): L2 + か (↖) + ↙ = げ; ー through R2 (記号, ←;
        //     amendment 21); む from the ま key (↙) by re-aiming: ↑ out (ま),
        //     rotated along the rim past → to ↘ (む). Converted with Y, confirmed with A. ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        key(KeyEvent.KEYCODE_BUTTON_L2, down = true); stick(-0.707f, -0.707f); flick(Dir.DOWN_LEFT); key(KeyEvent.KEYCODE_BUTTON_L2, down = false) // げ
        stick(0f, 0f)
        key(KeyEvent.KEYCODE_BUTTON_R2, down = true); layerFlick(-1f, 0f); key(KeyEvent.KEYCODE_BUTTON_R2, down = false) // ー
        stick(-0.707f, 0.707f); rimFlick(90.0, -54.0)           // む (ま key, ↑ out rotated to ↘)
        stick(0f, 0f)
        awaitText("げーむ", 5_000)
        step("げーむ typed")
        convertTo("げーむ", "ゲーム")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("ゲーム", 5_000)
        step("ゲーム confirmed")

        // --- Amendment 23: the D-pad changes the conversion range while converting (see conversionRange()).
        conversionRange()

        // --- Amendment 24: B while converting leaves the conversion and deletes one character
        //     (see backspaceWhileConverting()).
        backspaceWhileConverting()

        // --- 英字, amendment 14: the left stick picks the cell, a right-stick tilt types one of its four
        //     characters (← ↑ → ↓); amendment 18: X = space (R3 steps the mode). The mode is probed, not
        //     assumed (a new editor session starts in kana). Cells: abcd efgh ijkl / mnop qrst uvwx / yz'- ,.?! @/:#
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        switchToMode("e")                                        // 英字: ↑ cell + ← types e
        stick(0f, -1f); flick(Dir.DOWN)                          // h  (↑ cell efgh, ↓)
        flick(Dir.LEFT)                                          // e  (↑ cell, ←)
        stick(0.707f, -0.707f); flick(Dir.DOWN); flick(Dir.DOWN) // l l (↗ cell ijkl, ↓)
        stick(-1f, 0f); flick(Dir.RIGHT)                         // o  (← cell mnop, →)
        tap(KeyEvent.KEYCODE_BUTTON_X)                           // space (X)
        stick(1f, 0f); flick(Dir.RIGHT)                          // w  (→ cell uvwx, →)
        stick(-1f, 0f); flick(Dir.RIGHT)                         // o
        stick(0f, 0f); flick(Dir.UP)                             // r  (centre cell qrst, ↑)
        stick(0.707f, -0.707f); flick(Dir.DOWN)                  // l
        stick(-0.707f, -0.707f); flick(Dir.DOWN)                 // d  (↖ cell abcd, ↓)
        stick(0f, 0f)
        awaitText("hello world", 8_000)
        step("hello world typed")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("hello world", 5_000)
        onActivity { assertTrue("composing span must be gone after 決定", android.view.inputmethod.BaseInputConnection.getComposingSpanStart(it.editText.text) < 0) }

        // --- Hi! (L2 held = upper case, amendment 16) ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        switchToMode("e")
        key(KeyEvent.KEYCODE_BUTTON_L2, down = true); stick(0f, -1f); flick(Dir.DOWN); key(KeyEvent.KEYCODE_BUTTON_L2, down = false) // H
        stick(0.707f, -0.707f); flick(Dir.LEFT)                  // i  (↗ cell ijkl, ←)
        stick(0f, 1f); flick(Dir.DOWN)                           // !  (↓ cell ,.?!, ↓)
        stick(0f, 0f)
        awaitText("Hi!", 8_000)
        step("Hi! typed")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("Hi!", 5_000)

        // --- 数字 in the same session: 2026. Cells: 1234 5678 90., / +-*/ (=)% ¥$€& / !?'" ~@_# <:>; ---
        // Amendment 18: one R3 click from 英字 is 数字 (no probing needed any more).
        stepMode(KeyEvent.KEYCODE_BUTTON_THUMBR, "5", prefix = "Hi!")
        stick(-0.707f, -0.707f); flick(Dir.UP)                   // 2  (↖ cell 1234, ↑)
        stick(0.707f, -0.707f); flick(Dir.UP)                    // 0  (↗ cell 90.,, ↑)
        stick(-0.707f, -0.707f); flick(Dir.UP)                   // 2
        stick(0f, -1f); flick(Dir.UP)                            // 6  (↑ cell 5678, ↑)
        stick(0f, 0f)
        awaitText("Hi!2026", 8_000)
        step("2026 typed")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("Hi!2026", 5_000)
        // A with nothing composing: Sumire's Enter (a newline in this multi-line field).
        tap(KeyEvent.KEYCODE_BUTTON_A)
        val afterEnter = awaitTextOrNull("Hi!2026\n", 3_000) ?: text()
        Log.i(TAG, "A with nothing composing → text='${afterEnter.replace("\n", "\\n")}'")
        assertTrue("A with nothing composing must not eat text: '$afterEnter'", afterEnter.startsWith("Hi!2026"))
        step("enter sent")

        // --- Amendment 22: abc123 in 英字 with L1 held for the digits (数字's ↖ 1234 under L1), L1 + R2
        //     ↖ = { (数字's 記号 layer), and L1 let go while the right stick is still out: still 2. ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        switchToMode("e")
        stick(-0.707f, -0.707f); flick(Dir.LEFT); flick(Dir.UP); flick(Dir.RIGHT)   // a b c (↖ cell abcd)
        key(KeyEvent.KEYCODE_BUTTON_L1, down = true)
        flick(Dir.LEFT); flick(Dir.UP); flick(Dir.RIGHT)                            // 1 2 3 (L1: ↖ cell 1234)
        key(KeyEvent.KEYCODE_BUTTON_L1, down = false)
        stick(0f, 0f)
        awaitText("abc123", 8_000)
        step("abc123 typed in 英字 with L1")
        key(KeyEvent.KEYCODE_BUTTON_L1, down = true); key(KeyEvent.KEYCODE_BUTTON_R2, down = true)
        layerFlick(-0.707f, -0.707f)                                                 // { (L1 + R2, left stick ↖)
        key(KeyEvent.KEYCODE_BUTTON_R2, down = false); key(KeyEvent.KEYCODE_BUTTON_L1, down = false)
        awaitText("abc123{", 5_000)
        stick(-0.707f, -0.707f)
        key(KeyEvent.KEYCODE_BUTTON_L1, down = true)
        motion(lx, ly, 0f, -0.5f); SystemClock.sleep(20)
        motion(lx, ly, 0f, -1f); SystemClock.sleep(40)                              // ↑ out: 2 armed under L1
        key(KeyEvent.KEYCODE_BUTTON_L1, down = false)                                // L1 let go first
        motion(lx, ly, 0f, -0.4f); SystemClock.sleep(20)
        motion(lx, ly, 0f, 0f); SystemClock.sleep(150)                              // home: still 2
        stick(0f, 0f)
        awaitText("abc123{2", 5_000)
        step("L1 + R2 typed {, L1 let go early still typed 2")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("abc123{2", 5_000)

        // --- Amendment 18: L3 / R3 step the modes (かな → 英字 → 数字, wrapping), & through R2 in 英字,
        //     and the guide's touch digit 7 types 7 without bringing the touch keys back. ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        switchToMode("e")                                        // 英字 (R3 steps, the mode is probed)
        stepMode(KeyEvent.KEYCODE_BUTTON_THUMBL, "そ")            // L3: 英字 → かな
        stepMode(KeyEvent.KEYCODE_BUTTON_THUMBL, "5")            // L3: かな → 数字 (wraps)
        stepMode(KeyEvent.KEYCODE_BUTTON_THUMBR, "そ")            // R3: 数字 → かな (wraps)
        stepMode(KeyEvent.KEYCODE_BUTTON_THUMBR, "e")            // R3: かな → 英字
        step("R3 / L3 stepped the modes")
        key(KeyEvent.KEYCODE_BUTTON_R2, down = true)
        layerFlick(-0.707f, -0.707f)                             // & (R2 + left stick ↖)
        key(KeyEvent.KEYCODE_BUTTON_R2, down = false)
        awaitText("&", 5_000)
        step("& typed through R2 in 英字")
        tapGuideDigit('7')
        awaitText("&7", 5_000)
        assertTrue("the touch keys came back after a tap on the digit 7", fullGuideShown())
        step("7 typed from the guide's touch digit, guide still up")

        // --- Amendment 20: a touch on the guide outside the digits does nothing; Select switches to the
        //     touch keys of the mode (英字: QWERTY) and back; a stick tilt also brings the guide back. ---
        for ((what, xy) in listOf("grid" to (500f to 176f), "ring" to (819f to 176f), "ABXY" to (1113f to 234f), "background" to (660f to 372f))) {
            tapGuideAt(xy.first, xy.second)
            SystemClock.sleep(400)
            assertEquals("a touch on the guide's $what types nothing", "&7", text())
            assertTrue("the guide stays after a touch on its $what", fullGuideShown())
        }
        step("touches on the grid, ring, ABXY and background did nothing")
        tap(KeyEvent.KEYCODE_BUTTON_SELECT)
        awaitTouch("qwerty/english", guide = false)
        step("Select: the touch keys (QWERTY in 英字) are up")
        tap(KeyEvent.KEYCODE_BUTTON_SELECT)
        awaitTouch("tenkey/english", guide = true)
        step("Select again: the guide is back")
        tap(KeyEvent.KEYCODE_BUTTON_SELECT)
        awaitTouch("qwerty/english", guide = false)
        stick(0f, -1f); stick(0f, 0f)
        awaitTouch("tenkey/english", guide = true)
        assertEquals("the tilt typed nothing", "&7", text())
        step("a stick tilt brought the guide back")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("&7", 5_000)

        // --- English suggestions (amendment 12 §3): reported, not asserted. "hel" on the English
        //     tenkey, then a screenshot of the candidate strip, then Y (next suggestion) and A.
        englishSuggestionProbe()

        // --- Amendment 20: Start opens Sumire's emoji list at once; the left stick moves the selection,
        //     A inserts, B goes back to かな (the mode we came from). ---
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(0f, 0f)
        tap(KeyEvent.KEYCODE_BUTTON_START)
        awaitTouch("symbols/kana", guide = false)
        awaitEmojiListLoaded()                                   // the list loads its emoji (6 s on a busy emulator, run 31)
        step("Start: the emoji list is up")
        assertTrue("no hint strip over the emoji list in stick mode (v8 device check)", !emojiHintShown())
        // Run 31: on a busy emulator the moves landed before the list had settled and A inserted
        // nothing; then move and press A again.
        var inserted = ""
        for (attempt in 0 until 3) {
            stick(1f, 0f); stick(0f, 0f)                         // one step right (left stick)
            stick(0f, 1f); stick(0f, 0f)                         // one step down
            tap(KeyEvent.KEYCODE_BUTTON_A)
            val deadline = SystemClock.uptimeMillis() + 8_000
            while (SystemClock.uptimeMillis() < deadline && text().isEmpty()) SystemClock.sleep(100)
            inserted = text()
            if (inserted.isNotEmpty()) break
            Log.w(TAG, "emoji list: nothing inserted (attempt ${attempt + 1}); moving and pressing A again")
        }
        assertTrue("the emoji list inserted nothing", inserted.isNotEmpty())
        assertTrue("the emoji list inserted non-emoji text: $inserted",
            inserted.codePoints().allMatch { cp -> cp >= 0x1F000 || cp == 0xFE0F || cp == 0x200D || cp in 0x2600..0x27BF || cp in 0x1F3FB..0x1F3FF || cp == 0x20E3 || cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A })
        step("emoji list inserted $inserted (left stick → ↓, A)")
        tap(KeyEvent.KEYCODE_BUTTON_B)
        awaitTouch("tenkey/kana", guide = true)
        stick(0f, -1f); flick(Dir.LEFT); stick(0f, 0f)          // そ: かな again
        awaitText(inserted + "そ", 5_000)
        step("B: back in かな")
        tap(KeyEvent.KEYCODE_BUTTON_B)
        step("done")
    }

    // ------------------------------------------------------------------ amendment 23

    /** What the IME composes ([PadTestHooks.CompositionProbe]), read on the main thread. */
    private data class Composition(val reading: String, val rest: String, val converting: Boolean, val shown: String?)

    private fun composition(): Composition {
        var c = Composition("?", "?", false, null)
        instrumentation.runOnMainSync {
            PadTestHooks.composition?.get()?.let { c = Composition(it.reading(), it.rest(), it.converting(), it.shownCandidate()) }
        }
        return c
    }

    /** The field's composing span and its background (highlight) spans, as "start-end" ranges. */
    private data class FieldSpans(val composing: IntRange?, val highlights: List<IntRange>)

    private fun fieldSpans(): FieldSpans {
        var r = FieldSpans(null, emptyList())
        onActivity { a ->
            val t = a.editText.text
            val cs = android.view.inputmethod.BaseInputConnection.getComposingSpanStart(t)
            val ce = android.view.inputmethod.BaseInputConnection.getComposingSpanEnd(t)
            val hl = t.getSpans(0, t.length, android.text.style.BackgroundColorSpan::class.java)
                .map { t.getSpanStart(it) until t.getSpanEnd(it) }.filter { !it.isEmpty() }
            r = FieldSpans(if (cs >= 0 && ce > cs) cs until ce else null, hl)
        }
        return r
    }

    /**
     * Wait until the conversion covers [converted] with [rest] after it, the field shows the shown
     * candidate followed by the plain rest ([prefix] = text committed before it), and only the
     * candidate is highlighted. Returns the shown candidate.
     */
    private fun awaitRange(converted: String, rest: String, prefix: String = "", timeoutMs: Long = CANDIDATE_CEILING_MS): String {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var c = composition()
        while (SystemClock.uptimeMillis() < deadline) {
            c = composition()
            val shown = c.shown
            if (c.converting && c.reading == converted && c.rest == rest && shown != null && text() == prefix + shown + rest) {
                val spans = fieldSpans()
                Log.i(TAG, "RANGE '$converted' | '$rest': field '${text()}', candidate '$shown', composing ${spans.composing}, highlights ${spans.highlights}")
                assertEquals("composing covers the candidate and the rest", prefix.length until prefix.length + shown.length + rest.length, spans.composing)
                assertTrue("the candidate '$shown' is highlighted: ${spans.highlights}", spans.highlights.any { it == prefix.length until prefix.length + shown.length })
                assertTrue("the rest '$rest' is not highlighted: ${spans.highlights}", spans.highlights.none { it.last >= prefix.length + shown.length })
                return shown
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("conversion range not reached: wanted '$converted' | '$rest'; IME $c; field '${text()}' ${fieldSpans()}; " +
            "candidates for '${PadTestHooks.candidatesInput}': ${PadTestHooks.candidates.take(10)}")
    }

    /** A D-pad press that must change nothing (a limit): the field and the composition stay as they are. */
    private fun assertDpadDoesNothing(keyCode: Int, what: String) {
        val before = composition(); val textBefore = text()
        tap(keyCode)
        SystemClock.sleep(800)
        assertEquals("$what: the composition", before, composition())
        assertEquals("$what: the field", textBefore, text())
        step("$what did nothing")
    }

    /**
     * Amendment 23: はしをわたる, Y; D-pad ↑ at the full length does nothing; ↓ ×4 leaves はし converted
     * and をわたる as plain kana; ↑ makes it はしを | わたる; ↓ at one character does nothing; ← / →
     * still step through the candidates; A commits the converted part and the rest stays composing
     * (it becomes the reading); Y converts it; A.
     */
    private fun conversionRange() {
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(1f, 0f); flick(Dir.UP)                            // は (は key →, ↑ あ段)
        stick(0f, -1f); flick(Dir.RIGHT)                        // し (さ key ↑, → い段)
        stick(0.707f, 0.707f); flickDeg(210.0)                  // を (や/わ key ↘, right stick ↙)
        flick(Dir.DOWN)                                         // わ (や/わ key, ↓)
        stick(0.707f, -0.707f); flick(Dir.UP)                   // た (た key ↗, ↑ あ段)
        stick(0f, 1f); flick(Dir.DOWN_RIGHT)                    // る (ら key ↓, ↘ う段)
        stick(0f, 0f)
        awaitText("はしをわたる", 8_000)
        step("はしをわたる typed")
        awaitCandidatesFor("はしをわたる")
        tap(KeyEvent.KEYCODE_BUTTON_Y)                          // 変換
        val deadline = SystemClock.uptimeMillis() + CANDIDATE_CEILING_MS
        while (SystemClock.uptimeMillis() < deadline && !(composition().converting && composition().shown != null)) SystemClock.sleep(100)
        val whole = composition()
        assertTrue("Y did not start the conversion: $whole, field '${text()}'", whole.converting && whole.shown != null)
        Log.i(TAG, "RANGE after Y: $whole, field '${text()}' ${fieldSpans()}")
        assertEquals("はしをわたる", whole.reading)
        screenshot("range_after_y")

        assertDpadDoesNothing(KeyEvent.KEYCODE_DPAD_UP, "D-pad ↑ at the full length")

        val steps = listOf("はしをわた" to "る", "はしをわ" to "たる", "はしを" to "わたる", "はし" to "をわたる")
        var shown = ""
        for ((converted, rest) in steps) {
            tap(KeyEvent.KEYCODE_DPAD_DOWN)
            shown = awaitRange(converted, rest)
        }
        assertTrue("the field ends with をわたる: '${text()}'", text().endsWith("をわたる"))
        step("D-pad ↓ ×4: '$shown' | をわたる")
        screenshot("range_hashi")

        tap(KeyEvent.KEYCODE_DPAD_UP)
        shown = awaitRange("はしを", "わたる")
        step("D-pad ↑: '$shown' | わたる")

        tap(KeyEvent.KEYCODE_DPAD_DOWN); awaitRange("はし", "をわたる")
        tap(KeyEvent.KEYCODE_DPAD_DOWN); awaitRange("は", "しをわたる")
        assertDpadDoesNothing(KeyEvent.KEYCODE_DPAD_DOWN, "D-pad ↓ at one character")
        tap(KeyEvent.KEYCODE_DPAD_UP)
        val first = awaitRange("はし", "をわたる")

        // ← / → still go through the candidates of the converted part; the rest stays.
        // (Up to three → in case two candidates read the same, then as many ←.)
        var rights = 0
        var second: String? = first
        while (rights < 3 && second == first) {
            tap(KeyEvent.KEYCODE_DPAD_RIGHT); rights++
            val d = SystemClock.uptimeMillis() + 2_000
            while (SystemClock.uptimeMillis() < d && composition().shown == first) SystemClock.sleep(100)
            second = composition().shown
        }
        assertTrue("D-pad → did not change the candidate (still '$first')", second != null && second != first)
        assertEquals("→ keeps the range", Composition("はし", "をわたる", true, second), composition())
        assertEquals(second + "をわたる", text())
        repeat(rights) { tap(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(300) }
        var deadline2 = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline2 && composition().shown != first) SystemClock.sleep(100)
        assertEquals("D-pad ← went back to the first candidate", first, composition().shown)
        assertEquals(first + "をわたる", text())
        step("D-pad → ×$rights '$second', ← ×$rights '$first'")

        // A commits the converted part; をわたる stays composing and becomes the reading.
        tap(KeyEvent.KEYCODE_BUTTON_A)
        deadline2 = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline2 && !(composition().reading == "をわたる" && composition().rest.isEmpty() && text() == first + "をわたる")) SystemClock.sleep(100)
        val afterA = composition()
        Log.i(TAG, "RANGE after A: $afterA, field '${text()}' ${fieldSpans()}")
        assertEquals("after A the field is the committed part + the composing rest", first + "をわたる", text())
        assertEquals("after A the rest is the reading: $afterA", "をわたる", afterA.reading)
        assertEquals("after A: $afterA", "", afterA.rest)
        assertEquals("the rest is still composing", first.length until first.length + 4, fieldSpans().composing)
        step("A committed '$first', をわたる still composing")
        screenshot("range_after_a")

        // Y converts the rest, A commits it (a shorter candidate leaves a rest: convert that too).
        var committed = first
        for (round in 0 until 4) {
            val reading = composition().reading
            if (reading.isEmpty() && fieldSpans().composing == null) break
            awaitCandidatesFor(reading)
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            val d = SystemClock.uptimeMillis() + CANDIDATE_CEILING_MS
            while (SystemClock.uptimeMillis() < d && !(composition().converting && composition().shown != null)) SystemClock.sleep(100)
            val conv = composition()
            Log.i(TAG, "RANGE rest converted (round $round): $conv, field '${text()}'")
            assertTrue("Y did not convert '$reading': $conv", conv.converting && conv.shown != null)
            assertEquals(committed + conv.shown + conv.rest, text())
            tap(KeyEvent.KEYCODE_BUTTON_A)
            committed += conv.shown
            val d2 = SystemClock.uptimeMillis() + 5_000
            while (SystemClock.uptimeMillis() < d2 && !text().startsWith(committed)) SystemClock.sleep(100)
            SystemClock.sleep(600)
        }
        val done = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < done && fieldSpans().composing != null) SystemClock.sleep(100)
        assertEquals("nothing composing at the end: field '${text()}'", null, fieldSpans().composing)
        assertTrue("the field starts with the committed part: '${text()}'", text().startsWith(first) && text().length > first.length)
        step("conversion range done: '${text()}'")
    }

    // ------------------------------------------------------------------ amendment 24

    /** がっこ + [last] on the あ key at rest (↘ う, ← お): L2 + か (↖) ↑ = が, R1 + た (↗) ↘ = っ, か (↖) ← = こ. */
    private fun typeGakko(last: Dir) {
        key(KeyEvent.KEYCODE_BUTTON_L2, down = true); stick(-0.707f, -0.707f); flick(Dir.UP); key(KeyEvent.KEYCODE_BUTTON_L2, down = false)
        key(KeyEvent.KEYCODE_BUTTON_R1, down = true); stick(0.707f, -0.707f); flick(Dir.DOWN_RIGHT); key(KeyEvent.KEYCODE_BUTTON_R1, down = false)
        stick(-0.707f, -0.707f); flick(Dir.LEFT)
        stick(0f, 0f); flick(last)
    }

    /** Y has started a conversion: the IME converts and the field shows a candidate. */
    private fun awaitConverting(what: String): Composition {
        val deadline = SystemClock.uptimeMillis() + CANDIDATE_CEILING_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val c = composition()
            if (c.converting && c.shown != null) return c
            SystemClock.sleep(100)
        }
        throw AssertionError("$what: Y did not start the conversion: ${composition()}, field '${text()}'")
    }

    /**
     * After B: the IME composes [reading] (before the cursor) | [rest] (after it) and converts
     * nothing; the field reads [field], all of it composing after [prefix], and only the reading is
     * coloured (the rest is plain underlined kana).
     */
    private fun awaitComposingAfterB(reading: String, rest: String, field: String, prefix: String = "") {
        val want = Composition(reading, rest, false, null)
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline && !(composition() == want && text() == field)) SystemClock.sleep(100)
        val spans = fieldSpans()
        Log.i(TAG, "B-CONVERSION after B: ${composition()}, field '${text()}', composing ${spans.composing}, highlights ${spans.highlights}")
        assertEquals("the IME after B (field '${text()}')", want, composition())
        assertEquals("the field after B", field, text())
        assertEquals("all of it is composing", prefix.length until field.length, spans.composing)
        assertTrue("only '$reading' (before the cursor) is coloured: ${spans.highlights}",
            spans.highlights.none { it.first < prefix.length || it.last >= prefix.length + reading.length })
    }

    /** Amendment 24: while converting, D-pad ← still goes to the previous candidate (→ first, then ←). */
    private fun assertDpadLeftIsPreviousCandidate(prefix: String) {
        val start = composition()
        val first = start.shown!!
        var rights = 0
        var second: String? = first
        while (rights < 3 && second == first) {
            tap(KeyEvent.KEYCODE_DPAD_RIGHT); rights++
            val d = SystemClock.uptimeMillis() + 2_000
            while (SystemClock.uptimeMillis() < d && composition().shown == first) SystemClock.sleep(100)
            second = composition().shown
        }
        assertTrue("D-pad → did not change the candidate (still '$first')", second != null && second != first)
        repeat(rights) { tap(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(300) }
        val d = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < d && composition().shown != first) SystemClock.sleep(100)
        assertEquals("D-pad ← went back to the previous candidate", start, composition())
        assertEquals(prefix + first + start.rest, text())
        step("D-pad → ×$rights '$second', ← ×$rights '$first' (still the previous candidate while converting)")
    }

    /**
     * Amendment 24: B while converting leaves the conversion, gives the reading back as composing
     * kana and deletes the character before the cursor (where the converted range ends).
     * がっこお, Y, B → がっこ (not converted); う, Y → 学校, A. はしをわたる, Y, ↓ ×4 (the last ↓
     * followed at once by B, before its candidates are shown) → は | をわたる, both composing; Y
     * converts は with をわたる after it; A, A. がっこう after that, Y, D-pad ← still the previous
     * candidate, then B held about 1 s: the press leaves the conversion and deletes, the repeats
     * delete on (composing first, then committed text); nothing is left converted.
     */
    private fun backspaceWhileConverting() {
        // (1) がっこお, Y, B → がっこ.
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        typeGakko(last = Dir.LEFT)                              // がっこお (お = あ key, ← お段)
        stick(0f, 0f)
        awaitText("がっこお", 8_000)
        awaitCandidatesFor("がっこお")
        tap(KeyEvent.KEYCODE_BUTTON_Y)
        val gakkoo = awaitConverting("がっこお")
        Log.i(TAG, "B-CONVERSION がっこお after Y: $gakkoo, field '${text()}'")
        assertEquals("がっこお", gakkoo.reading)
        tap(KeyEvent.KEYCODE_BUTTON_B)
        awaitComposingAfterB("がっこ", "", "がっこ")
        step("がっこお, Y, B: がっこ composing, not converted")
        screenshot("b_gakko")
        flick(Dir.DOWN_RIGHT)                                   // う (あ key at rest, ↘ う段)
        awaitText("がっこう", 5_000)
        convertTo("がっこう", "学校")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        awaitText("学校", 5_000)
        step("う, Y → 学校, A")

        // (2) はしをわたる, Y, ↓ ×4, B → は | をわたる.
        onActivity { it.restartEditorInput(clearText = true) }
        awaitIme()
        stick(1f, 0f); flick(Dir.UP)                            // は
        stick(0f, -1f); flick(Dir.RIGHT)                        // し
        stick(0.707f, 0.707f); flickDeg(210.0)                  // を
        flick(Dir.DOWN)                                         // わ
        stick(0.707f, -0.707f); flick(Dir.UP)                   // た
        stick(0f, 1f); flick(Dir.DOWN_RIGHT)                    // る
        stick(0f, 0f)
        awaitText("はしをわたる", 8_000)
        awaitCandidatesFor("はしをわたる")
        tap(KeyEvent.KEYCODE_BUTTON_Y)
        awaitConverting("はしをわたる")
        for ((converted, rest) in listOf("はしをわた" to "る", "はしをわ" to "たる", "はしを" to "わたる")) {
            tap(KeyEvent.KEYCODE_DPAD_DOWN); awaitRange(converted, rest)
        }
        // The 4th ↓ and B at once: B also works while the new range's list is not shown yet.
        key(KeyEvent.KEYCODE_DPAD_DOWN, true); key(KeyEvent.KEYCODE_DPAD_DOWN, false)
        val between = composition()
        tap(KeyEvent.KEYCODE_BUTTON_B)
        Log.i(TAG, "B-CONVERSION between the 4th ↓ and B: $between (candidates for '${PadTestHooks.candidatesInput}')")
        awaitComposingAfterB("は", "をわたる", "はをわたる")
        step("はしをわたる, Y, ↓ ×4, B: は | をわたる, both composing")
        screenshot("b_ha_wowataru")
        awaitCandidatesFor("は")
        tap(KeyEvent.KEYCODE_BUTTON_Y)
        val ha = awaitRange("は", "をわたる")
        step("Y: '$ha' + をわたる")
        tap(KeyEvent.KEYCODE_BUTTON_A)
        var deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline && !(composition().reading == "をわたる" && text() == ha + "をわたる")) SystemClock.sleep(100)
        assertEquals("after A: '$ha' committed, をわたる composing", ha + "をわたる", text())
        assertEquals("をわたる is the reading now: ${composition()}", "をわたる", composition().reading)
        tap(KeyEvent.KEYCODE_BUTTON_A)                          // commit をわたる as kana
        deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline && fieldSpans().composing != null) SystemClock.sleep(100)
        assertEquals("nothing composing after A, A", null, fieldSpans().composing)
        assertEquals(ha + "をわたる", text())
        step("A, A: '${text()}'")

        // (3) がっこう after it, Y, D-pad ← (previous candidate), B held about 1 s.
        val prefix = text()
        typeGakko(last = Dir.DOWN_RIGHT)                        // がっこう (う = あ key, ↘ う段)
        stick(0f, 0f)
        awaitText(prefix + "がっこう", 8_000)
        awaitCandidatesFor("がっこう")
        tap(KeyEvent.KEYCODE_BUTTON_Y)
        val gakkou = awaitConverting("がっこう")
        assertEquals("がっこう", gakkou.reading)
        assertDpadLeftIsPreviousCandidate(prefix)
        key(KeyEvent.KEYCODE_BUTTON_B, down = true)
        SystemClock.sleep(1_000)
        key(KeyEvent.KEYCODE_BUTTON_B, down = false)
        SystemClock.sleep(800)
        val after = text()
        val c = composition()
        val deleted = prefix.length + 4 - after.length
        Log.i(TAG, "B-CONVERSION held B on '${gakkou.shown}' after '$prefix': field '$after', IME $c, $deleted characters deleted, spans ${fieldSpans()}")
        assertTrue("nothing converted after the held B: $c", !c.converting)
        assertTrue("the held B deleted from the end of the kana reading (field '$after')", (prefix + "がっこう").startsWith(after))
        // The press and at least one repeat (a busy emulator managed two repeats in 1 s in run 31).
        assertTrue("the held B deleted several characters (the press and its repeats): '$after'", deleted >= 2)
        step("がっこう, Y, B held 1 s: $deleted characters deleted, field '$after'")
    }

    /**
     * Amendment 12 §3: does Sumire's English tenkey give word suggestions? Types "hel", logs the
     * candidate strip and a screenshot (PadShot lines, decoded by the CI script), then presses Y
     * (the controller's next suggestion in 英字) and A, logging the field each time. Never fails
     * the run: this is a report.
     */
    private fun englishSuggestionProbe() {
        runCatching {
            onActivity { it.restartEditorInput(clearText = true) }
            awaitIme()
            switchToMode("e")
            stick(0f, -1f); flick(Dir.DOWN)                     // h
            flick(Dir.LEFT)                                     // e
            stick(0.707f, -0.707f); flick(Dir.DOWN)             // l
            awaitTextOrNull("hel", 5_000)
            SystemClock.sleep(2_000)
            val candidates = PadTestHooks.candidates
            Log.i(TAG, "ENGLISH-SUGGESTIONS for 'hel': count=${candidates.size} first=${candidates.take(15)}")
            screenshot("english_suggestions_hel")
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            SystemClock.sleep(800)
            Log.i(TAG, "ENGLISH-SUGGESTIONS after Y: text='${text()}' candidates=${PadTestHooks.candidates.take(5)}")
            screenshot("english_suggestions_after_y")
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            SystemClock.sleep(800)
            Log.i(TAG, "ENGLISH-SUGGESTIONS after Y Y: text='${text()}'")
            screenshot("english_suggestions_after_yy")
            tap(KeyEvent.KEYCODE_BUTTON_A)
            SystemClock.sleep(800)
            Log.i(TAG, "ENGLISH-SUGGESTIONS after A: text='${text()}'")
        }.onFailure { Log.w(TAG, "ENGLISH-SUGGESTIONS probe failed: $it") }
    }

    /**
     * A downscaled JPEG of the screen, written by the shell user to /data/local/tmp/padshot_<name>.jpg
     * (the CI script pulls it into the job log). Run 17 logged it as base64 lines instead, and
     * logd dropped them.
     */
    private fun screenshot(name: String) {
        val full = automation.takeScreenshot() ?: return
        val w = 960
        val scaled = android.graphics.Bitmap.createScaledBitmap(full, w, full.height * w / full.width, true)
        val bytes = java.io.ByteArrayOutputStream().also { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        // executeShellCommandRw splits on spaces without a shell (run 18: "sh -c '…'" wrote nothing), so no redirection.
        val pfds = automation.executeShellCommandRw("dd of=/data/local/tmp/padshot_$name.jpg")
        ParcelFileDescriptor.AutoCloseOutputStream(pfds[1]).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(pfds[0]).use { it.readBytes() }   // wait for cat to finish
        Log.i(TAG, "screenshot $name: ${bytes.size} bytes, size on device: ${shell("stat -c %s /data/local/tmp/padshot_$name.jpg")}")
    }

    /**
     * Wait until the IME has shown the candidate list for [reading] (the readiness signal), look
     * [wanted] up in it, then walk to it with Y (変換 / 次の候補), checking the field after every
     * press. Runs 12 and 14 failed by assuming the candidate's position, run 16 by polling a hook
     * the strip no longer fed for a fixed 8 s.
     */
    private fun convertTo(reading: String, wanted: String) {
        var index = awaitCandidatesFor(reading).indexOf(wanted)
        if (index < 0) {
            // Not among the predictions: start the conversion with Y and look again.
            Log.i(TAG, "'$wanted' not predicted for '$reading'; pressing Y to start the conversion")
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            if (awaitTextOrNull(wanted, 1_500) != null) { step("candidate reached: $wanted (first)"); return }
            index = awaitCandidate(CANDIDATE_CEILING_MS) { it == wanted }
        }
        Log.i(TAG, "candidate '$wanted' is #$index of ${PadTestHooks.candidates.size}: ${PadTestHooks.candidates.take(12)}")
        var found = text() == wanted
        for (i in 0 until index + 4) {
            if (found) break
            tap(KeyEvent.KEYCODE_BUTTON_Y)
            if (awaitTextOrNull(wanted, 1_500) != null) found = true
        }
        assertTrue("candidate $wanted (#$index) not reached; text=${text()} candidates=${PadTestHooks.candidates.take(20)}", found)
        step("candidate reached: $wanted")
    }

    /**
     * Readiness: the IME published the candidate list computed for exactly [reading]
     * ([PadTestHooks.publish], called right after the candidate strip shows it). Returns that list.
     */
    private fun awaitCandidatesFor(reading: String): List<String> {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < CANDIDATE_CEILING_MS) {
            val list = PadTestHooks.candidates
            if (PadTestHooks.candidatesInput == reading && list.isNotEmpty()) {
                Log.i(TAG, "candidates for '$reading' shown after ${SystemClock.uptimeMillis() - start} ms (revision ${PadTestHooks.candidatesRevision}): ${list.take(12)}")
                return list
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("the IME showed no candidates for '$reading' within ${CANDIDATE_CEILING_MS} ms; " +
            "last reading='${PadTestHooks.candidatesInput}' revision=${PadTestHooks.candidatesRevision} " +
            "candidates=${PadTestHooks.candidates.take(20)} engineReady=${PadTestHooks.engineReady} text=${text()}")
    }

    /** Index of the first candidate matching [pred], once the list has it (fails with the list after [timeoutMs]). */
    private fun awaitCandidate(timeoutMs: Long, pred: (String) -> Boolean): Int {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var list: List<String> = emptyList()
        while (SystemClock.uptimeMillis() < deadline) {
            list = PadTestHooks.candidates
            val i = list.indexOfFirst(pred)
            if (i >= 0) { SystemClock.sleep(400); return PadTestHooks.candidates.indexOfFirst(pred).takeIf { it >= 0 } ?: i }
            SystemClock.sleep(200)
        }
        throw AssertionError("no matching candidate within ${timeoutMs}ms; text=${text()} candidates=$list")
    }

    /**
     * R3 clicks (amendment 18: the next text mode, as Select taps before) step かな → 英字 → 数字・記号;
     * a new editor session may start in kana, so instead of counting clicks the mode is probed: the ↑
     * cell + a right-stick ← types そ / e / 5 (kana on the pentagon, さ key since amendment 16 / 英字 /
     * 数字, amendments 14 and 15). The probe is deleted with B. [prefix] is the text already in the field.
     */
    private fun switchToMode(probeChar: String, prefix: String = "") {
        for (attempt in 0 until 5) {
            stick(0f, 0f)                                        // the click guard: both sticks at rest
            tap(KeyEvent.KEYCODE_BUTTON_THUMBR)
            SystemClock.sleep(400)
            stick(0f, -1f); flick(Dir.LEFT); stick(0f, 0f)
            val got = awaitTextOrNull(prefix + probeChar, 1_500)
            val typed = text()
            tap(KeyEvent.KEYCODE_BUTTON_B)                      // delete the probe (composing or committed)
            awaitTextOrNull(prefix, 1_500)
            if (got != null) { step("mode probe '$probeChar' ok after ${attempt + 1} R3 click(s)"); return }
            Log.i(TAG, "mode probe wanted '$probeChar', field showed '$typed'")
            if (text() != prefix) { tap(KeyEvent.KEYCODE_BUTTON_B); awaitTextOrNull(prefix, 1_500) }
        }
        throw AssertionError("could not reach the mode that types '$probeChar'; text=${text()}")
    }

    /**
     * Amendment 18: one L3 / R3 click ([keyCode]) with both sticks at rest must land in the mode that
     * types [probeChar] (the same probe as [switchToMode], deleted again with B).
     */
    private fun stepMode(keyCode: Int, probeChar: String, prefix: String = "") {
        stick(0f, 0f)
        tap(keyCode)
        SystemClock.sleep(400)
        stick(0f, -1f); flick(Dir.LEFT); stick(0f, 0f)
        val got = awaitTextOrNull(prefix + probeChar, 3_000)
        val typed = text()
        tap(KeyEvent.KEYCODE_BUTTON_B)
        awaitTextOrNull(prefix, 1_500)
        assertTrue("${KeyEvent.keyCodeToString(keyCode)} should have reached the mode that types '$probeChar'; the probe typed '$typed'", got != null)
        step("${KeyEvent.keyCodeToString(keyCode)} → mode of '$probeChar'")
    }

    /** The emoji list shows its emoji: a shown list in the keyboard with many items (a ceiling, not a delay). */
    private fun awaitEmojiListLoaded() {
        val start = SystemClock.uptimeMillis()
        var count = 0
        while (SystemClock.uptimeMillis() - start < 30_000) {
            instrumentation.runOnMainSync { count = PadTestHooks.touchKeyboard?.get()?.keyboardRoot()?.let { largestShownList(it) } ?: 0 }
            if (count >= 100) break
            SystemClock.sleep(200)
        }
        Log.i(TAG, "emoji list: $count items after ${SystemClock.uptimeMillis() - start} ms")
        SystemClock.sleep(800)
    }

    private fun largestShownList(v: View): Int {
        var best = if (v is androidx.recyclerview.widget.RecyclerView && v.isShown) v.adapter?.itemCount ?: 0 else 0
        if (v is ViewGroup) for (i in 0 until v.childCount) best = maxOf(best, largestShownList(v.getChildAt(i)))
        return best
    }

    /** Amendment 18: the full guide (controller map) is up, i.e. the touch keys did not come back. */
    private fun fullGuideShown(): Boolean {
        var shown = false
        instrumentation.runOnMainSync { shown = PadTestHooks.fullGuide?.get()?.let { it.isShown && it.visibility == android.view.View.VISIBLE } == true }
        return shown
    }

    /** Amendment 18: a finger tap (down, up) on the guide's touch digit [digit], at its on-screen position. */
    private fun tapGuideDigit(digit: Char) {
        var pos: IntArray? = null
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (pos == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { pos = PadTestHooks.fullGuide?.get()?.digitCentreOnScreen(digit) }
            if (pos == null) SystemClock.sleep(100)
        }
        val (x, y) = pos ?: throw AssertionError("the guide shows no touch digit $digit (full guide shown: ${fullGuideShown()})")
        Log.i(TAG, "tap on the touch digit $digit at screen ($x, $y)")
        val down = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 80)) {
            val e = MotionEvent.obtain(down, time, action, x.toFloat(), y.toFloat(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue("inject touch", automation.injectInputEvent(e, true))
            e.recycle()
            SystemClock.sleep(80)
        }
    }

    /**
     * Amendment 20: a finger tap on the full guide at ([bx], [by]) in the guide's 1280 × 385 design
     * coordinates (GuideMap), scaled to the guide's on-screen size.
     */
    private fun tapGuideAt(bx: Float, by: Float) {
        var pos: IntArray? = null
        instrumentation.runOnMainSync {
            val g = PadTestHooks.fullGuide?.get() ?: return@runOnMainSync
            val xy = IntArray(2).also { g.getLocationOnScreen(it) }
            val sc = g.width / com.kazumaproject.pad_input.GuideMap.BASE_W
            pos = intArrayOf(xy[0] + (bx * sc).toInt(), xy[1] + (by * sc).toInt())
        }
        val (x, y) = pos ?: throw AssertionError("no full guide to touch")
        Log.i(TAG, "tap on the guide at design ($bx, $by) = screen ($x, $y)")
        val down = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 80)) {
            val e = MotionEvent.obtain(down, time, action, x.toFloat(), y.toFloat(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue("inject touch", automation.injectInputEvent(e, true))
            e.recycle()
            SystemClock.sleep(80)
        }
    }

    /**
     * v8 device check: the one-line hint over Sumire's emoji list (「… カテゴリー」 / "… Tabs", the
     * D-pad fallback's only) is not drawn in stick mode.
     */
    private fun emojiHintShown(): Boolean {
        var shown = false
        instrumentation.runOnMainSync {
            val root = PadTestHooks.touchKeyboard?.get()?.keyboardRoot() ?: return@runOnMainSync
            shown = shownText(root) { it.endsWith("Tabs") || it.endsWith("カテゴリー") }
        }
        return shown
    }

    private fun shownText(v: View, pred: (String) -> Boolean): Boolean {
        if (v is android.widget.TextView && v.isShown && pred(v.text.toString())) return true
        if (v is ViewGroup) for (i in 0 until v.childCount) if (shownText(v.getChildAt(i), pred)) return true
        return false
    }

    /** Amendment 20: wait until the touch keyboard shows [state] and the full guide is up ([guide]) or not. */
    private fun awaitTouch(state: String, guide: Boolean, timeoutMs: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline && (touchState() != state || fullGuideShown() != guide)) SystemClock.sleep(100)
        assertEquals("touch keyboard (guide shown: ${fullGuideShown()})", state, touchState())
        assertEquals("full guide shown with $state", guide, fullGuideShown())
    }

    // ------------------------------------------------------------------ helpers

    /** Right-stick pushes: the four tilts, plus ↘ / ↙ for the kana pentagon's う段 / え段 (amendment 15). */
    private enum class Dir { LEFT, UP, RIGHT, DOWN, DOWN_RIGHT, DOWN_LEFT }

    private var lx = 0f
    private var ly = 0f

    private fun stick(x: Float, y: Float) {
        lx = x; ly = y
        motion(lx, ly, 0f, 0f)
        SystemClock.sleep(60)
    }

    /** Amendment 16: the left stick out toward (x, y) and back, the right stick at rest (the R2 記号 layer while R2 is held). */
    private fun layerFlick(x: Float, y: Float) {
        motion(x * 0.5f, y * 0.5f, 0f, 0f); SystemClock.sleep(20)
        motion(x, y, 0f, 0f); SystemClock.sleep(40)
        motion(x * 0.2f, y * 0.2f, 0f, 0f); SystemClock.sleep(20)
        motion(0f, 0f, 0f, 0f); SystemClock.sleep(150)
        lx = 0f; ly = 0f
    }

    /** Point of a unit push toward [deg] (0° = right, 90° = up, counter-clockwise) in stick axes (y down). */
    private fun rim(deg: Double, r: Float = 1f): Pair<Float, Float> {
        val rad = Math.toRadians(deg)
        return (Math.cos(rad) * r).toFloat() to (-Math.sin(rad) * r).toFloat()
    }

    /**
     * Amendment 17, re-aim on the right stick: out toward [from], rotated along the rim to [to] in
     * steps of about 15°, back home. Types the character at [to].
     */
    private fun rimFlick(from: Double, to: Double) {
        rim(from, 0.5f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(20)
        val steps = maxOf(2, (Math.abs(to - from) / 15.0).toInt())
        for (i in 0..steps) { rim(from + (to - from) * i / steps, 0.95f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(20) }
        rim(to, 0.4f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(20)
        motion(lx, ly, 0f, 0f); SystemClock.sleep(150)
    }

    /** Amendment 17, re-aim on the R2 layer (記号 since amendment 21): the left stick out toward [from], rotated to [to], home. */
    private fun layerRimFlick(from: Double, to: Double) {
        rim(from, 0.5f).let { (x, y) -> motion(x, y, 0f, 0f) }; SystemClock.sleep(20)
        val steps = maxOf(2, (Math.abs(to - from) / 15.0).toInt())
        for (i in 0..steps) { rim(from + (to - from) * i / steps, 0.95f).let { (x, y) -> motion(x, y, 0f, 0f) }; SystemClock.sleep(20) }
        rim(to, 0.2f).let { (x, y) -> motion(x, y, 0f, 0f) }; SystemClock.sleep(20)
        motion(0f, 0f, 0f, 0f); SystemClock.sleep(150)
        lx = 0f; ly = 0f
    }

    /** Amendment 21: a right-stick flick toward [deg] (0° = right, 90° = up), e.g. the や/わ key's six slots. */
    private fun flickDeg(deg: Double) {
        rim(deg, 0.5f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(20)
        rim(deg, 1f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(40)
        rim(deg, 0.4f).let { (x, y) -> motion(lx, ly, x, y) }; SystemClock.sleep(20)
        motion(lx, ly, 0f, 0f); SystemClock.sleep(150)
    }

    private fun flick(dir: Dir) {
        val (x, y) = when (dir) {
            Dir.LEFT -> -1f to 0f; Dir.RIGHT -> 1f to 0f; Dir.UP -> 0f to -1f; Dir.DOWN -> 0f to 1f
            Dir.DOWN_RIGHT -> 0.707f to 0.707f; Dir.DOWN_LEFT -> -0.707f to 0.707f
        }
        motion(lx, ly, x * 0.5f, y * 0.5f); SystemClock.sleep(20)
        motion(lx, ly, x, y); SystemClock.sleep(40)
        motion(lx, ly, x * 0.4f, y * 0.4f); SystemClock.sleep(20)
        motion(lx, ly, 0f, 0f); SystemClock.sleep(150)
    }

    private var injected = 0

    private fun motion(x: Float, y: Float, z: Float, rz: Float) {
        val now = SystemClock.uptimeMillis()
        if (downTime == 0L) downTime = now
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_X, x)
            setAxisValue(MotionEvent.AXIS_Y, y)
            setAxisValue(MotionEvent.AXIS_Z, z)
            setAxisValue(MotionEvent.AXIS_RZ, rz)
        })
        val event = MotionEvent.obtain(downTime, now, MotionEvent.ACTION_MOVE, 1, props, coords, 0, 0, 1f, 1f,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD, 0)
        val ok = automation.injectInputEvent(event, true)
        if (injected++ < 6 || !ok) Log.i(TAG, "inject motion L($x,$y) R($z,$rz) -> $ok")
        assertTrue("inject motion", ok)
        event.recycle()
    }

    private fun key(keyCode: Int, down: Boolean) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode, 0, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        val ok = automation.injectInputEvent(event, true)
        if (!ok || keyCode == KeyEvent.KEYCODE_BUTTON_A) Log.i(TAG, "inject key ${KeyEvent.keyCodeToString(keyCode)} down=$down -> $ok")
        assertTrue("inject key", ok)
        SystemClock.sleep(40)
    }

    private fun tap(keyCode: Int) { key(keyCode, true); key(keyCode, false); SystemClock.sleep(120) }

    /** Runs on the main thread without ActivityScenario's waitForIdleSync(). */
    private fun onActivity(block: (FastInputHostActivity) -> Unit) {
        val activity = host() ?: throw AssertionError("host activity gone")
        instrumentation.runOnMainSync { block(activity) }
    }

    private fun text(): String {
        var t = ""
        onActivity { t = it.editText.text.toString() }
        return t
    }

    private fun awaitTextOrNull(expected: String, timeoutMs: Long): String? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val t = text()
            if (t == expected) return t
            SystemClock.sleep(100)
        }
        return null
    }

    private fun awaitText(expected: String, timeoutMs: Long) {
        val got = awaitTextOrNull(expected, timeoutMs)
        assertEquals(expected, got ?: text())
    }

    /**
     * The IME window is up. A ceiling, not an expected delay: the first IME of a fresh emulator
     * took about 17 s to draw its input view in run 26 (the 15 s ceiling of v7 failed the first test).
     */
    private fun awaitIme() {
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < deadline) {
            var shown = false
            onActivity { a ->
                a.requestImeForEditor()
                shown = a.editText.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
            }
            if (shown) { SystemClock.sleep(800); return }
            SystemClock.sleep(300)
        }
        throw AssertionError("IME window did not appear")
    }
}
