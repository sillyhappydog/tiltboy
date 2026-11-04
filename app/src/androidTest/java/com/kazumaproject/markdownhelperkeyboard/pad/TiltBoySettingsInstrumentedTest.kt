package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.pad.ui.PadDiagnosticsFragment
import com.kazumaproject.markdownhelperkeyboard.pad.ui.PadPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.pad.ui.TiltBoyAboutFragment
import com.kazumaproject.markdownhelperkeyboard.pad.ui.TiltBoyHomeFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/**
 * Amendment 19, on the emulator: the launcher opens the TiltBoy settings home and its item texts
 * match the spec; the controller drives it (D-pad focus, A opens, B goes back): 試し打ち opens the
 * diagnostics screen, 細かい調整 and このアプリについて open and B returns; v8 device check: B closes
 * the dialogs opened from the home (変換の学習を消す's confirm, 使うモード's list). The display is set to the
 * RG477V's 1280 × 960 at density 2.0 and the app to Japanese for the report's screenshots
 * (padshot_settings_*.png, decoded from the job log by .github/scripts/run-pad-emulator-test.sh).
 */
@RunWith(AndroidJUnit4::class)
class TiltBoySettingsInstrumentedTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(300)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext
    private val pkg get() = context.packageName

    private companion object {
        const val TAG = "TiltBoySettings"
        val HOME_JA = listOf(
            // v14: 「ボタンの並び」 first under はじめに.
            "# はじめに", "ボタンの並び", "キーボードを使えるようにする", "試し打ち・コントローラーの確認",
            "# 打ち方", "使うモード", "画面に案内を出す",
            "# ことば", "単語の登録", "変換の学習を消す",
            "# そのほか", "細かい調整", "このアプリについて",
        )
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    @Before
    fun setUp() {
        shell("wm size 1280x960")
        shell("wm density 320")
        shell("cmd locale set-app-locales $pkg --locales ja-JP")
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(PadPreferences.MODES, PadPreferences.MODE_VALUES.values.toSet()).commit()
        SystemClock.sleep(1_500)
        Log.i(TAG, "display: ${shell("wm size")} / ${shell("wm density")}")
    }

    @After
    fun tearDown() {
        runCatching { instrumentation.runOnMainSync { resumedMainActivity()?.finish() } }
        shell("cmd locale set-app-locales $pkg")
        shell("wm density reset")
        shell("wm size reset")
    }

    @Test
    fun theLauncherOpensTheTiltBoyHomeAndTheControllerDrivesIt() {
        // The launcher's intent for the package (the LaunchableMainActivity alias).
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: throw AssertionError("no launcher activity")
        Log.i(TAG, "launcher intent: ${launch.component}")
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val home = awaitFragment<TiltBoyHomeFragment>("the TiltBoy home")
        awaitPreferences(home)

        // Exactly the spec's items, in order (what the fragment shows), and the same texts on screen.
        val outline = onMain { outline(home.preferenceScreen) }
        Log.i(TAG, "home outline: $outline")
        assertEquals(HOME_JA, outline)
        val onScreen = visibleTexts(home)
        Log.i(TAG, "home texts on screen: $onScreen")
        for (item in HOME_JA.map { it.removePrefix("# ") }) assertTrue("'$item' is drawn", onScreen.any { it == item })
        val modes = onMain { home.findPreference<Preference>(PadPreferences.MODES)?.summary?.toString() }
        assertEquals("かな・英字・数字", modes)
        onMain { home.listView.scrollToPosition(0) }
        SystemClock.sleep(500)
        screenshot("settings_home_top")
        onMain { home.listView.scrollToPosition(home.listView.adapter!!.itemCount - 1) }
        SystemClock.sleep(500)
        screenshot("settings_home_bottom")
        onMain { home.listView.scrollToPosition(0) }
        SystemClock.sleep(300)

        // 試し打ち・コントローラーの確認: D-pad to it, A opens the diagnostics screen.
        focusItem("試し打ち・コントローラーの確認")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        awaitFragment<PadDiagnosticsFragment>("the diagnostics screen after A on 試し打ち")
        // The diagnostics screen's own controller test keeps B; leave with the device's Back (a second
        // Back if the first one only closed a keyboard).
        var home2: TiltBoyHomeFragment? = null
        for (attempt in 0 until 3) {
            key(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_KEYBOARD)
            home2 = runCatching { awaitFragment<TiltBoyHomeFragment>("the home after Back", 5_000) }.getOrNull()
            if (home2 != null) break
            Log.i(TAG, "Back $attempt did not reach the home yet")
        }
        if (home2 == null) home2 = awaitFragment<TiltBoyHomeFragment>("the home after Back")
        awaitPreferences(home2)

        // 細かい調整: D-pad + A, then B goes back.
        focusItem("細かい調整")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        val fine = awaitFragment<PadPreferenceFragment>("細かい調整 after A")
        awaitPreferences(fine)
        assertEquals("細かい調整", onMain { resumedMainActivity()?.supportActionBar?.title?.toString() })
        SystemClock.sleep(500)
        screenshot("settings_fine_tuning")
        val fineOutline = onMain { outline(fine.preferenceScreen) }
        Log.i(TAG, "fine tuning outline: $fineOutline")
        assertTrue(fineOutline.containsAll(listOf("# 手ざわりの調整", "# ボタンの割り当て", "# ボタンとスティック", "ボタンの並び（案内の表示）", "スティックの扱い")))
        // Amendment 22: the button-role items are indented like the sliders, and L1's role says what it does in 英字.
        onMain {
            val roles = fine.findPreference<androidx.preference.PreferenceCategory>("pad_roles_category")!!
            assertTrue(roles.preferenceCount >= 12)
            for (i in 0 until roles.preferenceCount) assertTrue("${roles.getPreference(i).title} indented", roles.getPreference(i).isIconSpaceReserved)
            val l1 = roles.findPreference<androidx.preference.ListPreference>("pad_role_l1")!!
            Log.i(TAG, "fine tuning L1: ${l1.summary}")
            assertTrue("L1: ${l1.summary}", l1.summary.toString().contains("英字では数字"))
            // Amendment 23: the roles text says what the D-pad does while converting.
            assertTrue("roles: ${roles.summary}", roles.summary.toString().contains("変換中: ←→ 候補、↑ 範囲を長く、↓ 範囲を短く"))
            // Amendment 24: B's role says it deletes while converting too.
            val b = roles.findPreference<androidx.preference.ListPreference>("pad_role_b")!!
            Log.i(TAG, "fine tuning B: ${b.summary}")
            assertTrue("B: ${b.summary}", b.summary.toString().contains("1 文字消す（変換中も変換をやめて 1 文字消す）"))
        }
        onMain { fine.listView.scrollToPosition(fine.listView.adapter!!.itemCount - 1) }
        SystemClock.sleep(500)
        screenshot("settings_fine_tuning_bottom")
        pad(KeyEvent.KEYCODE_BUTTON_B)
        val home3 = awaitFragment<TiltBoyHomeFragment>("the home after B on 細かい調整")
        awaitPreferences(home3)

        // このアプリについて: version, licences, dictionary licences, attribution.
        focusItem("このアプリについて")
        pad(KeyEvent.KEYCODE_BUTTON_A)
        val about = awaitFragment<TiltBoyAboutFragment>("このアプリについて after A")
        awaitPreferences(about)
        SystemClock.sleep(500)
        screenshot("settings_about")
        val version = onMain { about.findPreference<Preference>(TiltBoyAboutFragment.KEY_VERSION)?.summary?.toString() }
        Log.i(TAG, "about: version $version")
        // The suffix moves every round (-tiltboyN); only check that the TiltBoy build is the one shown.
        assertTrue("version shown: $version", version?.contains(Regex("tiltboy\\d+")) == true)
        pad(KeyEvent.KEYCODE_BUTTON_B)
        val home4 = awaitFragment<TiltBoyHomeFragment>("the home after B on このアプリについて")
        awaitPreferences(home4)

        // v8 device check: B closes the dialogs opened from these pages (A opens them; B = Back, so
        // nothing is cleared or changed).
        for (item in listOf("変換の学習を消す", "使うモード")) {
            focusItem(item)
            pad(KeyEvent.KEYCODE_BUTTON_A)
            awaitDialog(item, shown = true)
            pad(KeyEvent.KEYCODE_BUTTON_B)
            awaitDialog(item, shown = false)
            awaitFragment<TiltBoyHomeFragment>("the home after B in the $item dialog")
        }
        val modesAfter = onMain { home4.findPreference<Preference>(PadPreferences.MODES)?.summary?.toString() }
        assertEquals("B in the 使うモード dialog changed nothing", "かな・英字・数字", modesAfter)
        Log.i(TAG, "done")
    }

    /** A dialog over the settings has the window focus ([shown]) or the activity has it back. */
    private fun awaitDialog(what: String, shown: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val activityFocused = onMain { resumedMainActivity()?.hasWindowFocus() == true }
            if (activityFocused != shown) { Log.i(TAG, "dialog of '$what' ${if (shown) "shown" else "closed"}"); SystemClock.sleep(300); return }
            SystemClock.sleep(150)
        }
        throw AssertionError(if (shown) "A on '$what' opened no dialog" else "B did not close the dialog of '$what'")
    }

    // ------------------------------------------------------------------ helpers

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** On the main thread only. */
    private fun resumedMainActivity(): MainActivity? =
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().firstOrNull()

    private fun mainActivity(): MainActivity? = onMain { resumedMainActivity() }

    private inline fun <reified F : androidx.fragment.app.Fragment> awaitFragment(what: String, timeoutMs: Long = 30_000): F {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var last: Any? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val f = onMain {
                val host = resumedMainActivity()?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
                host?.childFragmentManager?.primaryNavigationFragment
            }
            last = f
            if (f is F && f.isResumed) { Log.i(TAG, "showing: $what"); return f }
            SystemClock.sleep(200)
        }
        throw AssertionError("$what did not appear; showing ${last?.javaClass?.simpleName}")
    }

    private fun awaitPreferences(f: PreferenceFragmentCompat) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            val ready = onMain { f.preferenceScreen != null && (f.listView?.adapter?.itemCount ?: 0) > 0 && f.listView.childCount > 0 }
            if (ready) { SystemClock.sleep(300); return }
            SystemClock.sleep(200)
        }
        throw AssertionError("${f.javaClass.simpleName} showed no preferences")
    }

    private fun outline(group: PreferenceGroup): List<String> = buildList {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            if (!p.isVisible) continue
            if (p is PreferenceCategory) { add("# ${p.title}"); addAll(outline(p)) } else if (p.title != null) add(p.title.toString())
        }
    }

    /** Every TextView text the list draws, scrolling item by item. */
    private fun visibleTexts(f: PreferenceFragmentCompat): List<String> {
        val texts = LinkedHashSet<String>()
        val count = onMain { f.listView.adapter!!.itemCount }
        for (i in 0 until count) {
            onMain { f.listView.scrollToPosition(i) }
            SystemClock.sleep(120)
            onMain {
                f.listView.findViewHolderForAdapterPosition(i)?.itemView?.let { collectTexts(it, texts) }
            }
        }
        return texts.toList()
    }

    private fun collectTexts(v: View, into: MutableSet<String>) {
        if (v is TextView && v.isShown && v.text.isNotBlank()) into += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) collectTexts(v.getChildAt(i), into)
    }

    /** The title of the focused list item, if the focus is on one. */
    private fun focusedTitle(): String? = onMain {
        val focused = resumedMainActivity()?.currentFocus ?: return@onMain null
        var item: View? = focused
        while (item != null && item.parent !is RecyclerView) item = item.parent as? View
        (item ?: focused).findViewById<TextView>(android.R.id.title)?.text?.toString()
    }

    /** Move the focus with the D-pad (down) until the item titled [title] has it. */
    private fun focusItem(title: String) {
        var seen = ""
        for (i in 0 until 32) {
            val now = focusedTitle()
            if (now == title) { Log.i(TAG, "focus on '$title' after $i D-pad presses"); return }
            seen += "[$now] "
            // Down through the list first, then back up (the focus may start below the item).
            key(if (i < 16) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP, InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD)
            SystemClock.sleep(250)
        }
        throw AssertionError("the D-pad never focused '$title'; focus went $seen")
    }

    private fun pad(keyCode: Int) {
        key(keyCode, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        SystemClock.sleep(400)
    }

    private fun key(keyCode: Int, source: Int) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val e = KeyEvent(down, SystemClock.uptimeMillis(), action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source)
            assertTrue("inject ${KeyEvent.keyCodeToString(keyCode)}", automation.injectInputEvent(e, true))
            SystemClock.sleep(60)
        }
    }

    /** A PNG of the screen at full size (1280 × 960), written by the shell user to /data/local/tmp/padshot_<name>.png. */
    private fun screenshot(name: String) {
        val shot = automation.takeScreenshot() ?: return
        val bytes = java.io.ByteArrayOutputStream().also { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val pfds = automation.executeShellCommandRw("dd of=/data/local/tmp/padshot_$name.png")
        ParcelFileDescriptor.AutoCloseOutputStream(pfds[1]).use { it.write(bytes) }
        ParcelFileDescriptor.AutoCloseInputStream(pfds[0]).use { it.readBytes() }
        Log.i(TAG, "screenshot $name: ${shot.width}x${shot.height}, ${bytes.size} bytes")
    }
}
