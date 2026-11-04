package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.app.Activity
import android.app.Dialog
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.WeakHashMap

/**
 * Amendment 19: the settings pages work with the controller. The D-pad moves the focus (Android
 * does that), A opens the focused item and B goes back. Android's generic key map already turns
 * an unhandled A into D-pad centre and B into Back; this listener does the same for the settings
 * activity whatever the device's key map says (and for injected test events). Only keys no view
 * handled reach it, so a text field or the diagnostics screen's own controller keeps its keys.
 *
 * v8 device check: a dialog has its own window, so the activity's listener never sees its keys,
 * and on the RG477V B did nothing in them (only the device's Back closed them). Every dialog
 * opened from these pages gets B = Back too ([installOn]): the preference dialogs (使うモード, the
 * role lists) through a fragment-lifecycle hook on the activity, other dialogs (変換の学習を消す's
 * confirm) where they are shown. A stays as it was there: it presses the focused button.
 */
object TiltBoyControllerKeys {
    private val installed = WeakHashMap<Window, Boolean>()
    private val dialogs = WeakHashMap<Dialog, Boolean>()

    fun install(activity: Activity) {
        val window = activity.window ?: return
        if (installed[window] == true) return
        installed[window] = true
        ViewCompat.addOnUnhandledKeyEventListener(window.decorView) { _, event -> handle(activity, event) }
        // Dialog fragments anywhere under this activity (PreferenceFragmentCompat's list dialogs).
        (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(dialogFragmentHook, true)
    }

    /** B in [dialog] acts as Back (cancels it if it is cancelable), whatever the device's key map says. */
    fun installOn(dialog: Dialog) {
        if (dialogs[dialog] == true) return
        val window = dialog.window ?: return
        dialogs[dialog] = true
        ViewCompat.addOnUnhandledKeyEventListener(window.decorView) { _, event -> handleDialogKey(dialog, event) }
    }

    /** For tests: [dialog] has the B = Back listener. */
    fun isInstalledOn(dialog: Dialog?): Boolean = dialog != null && dialogs[dialog] == true

    /** True when [event] was used: A → D-pad centre (opens the focused item), B → back. */
    fun handle(activity: Activity, event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> {
            val centre = KeyEvent(
                event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount,
                event.metaState, event.deviceId, event.scanCode, event.flags, InputDevice.SOURCE_DPAD,
            )
            activity.dispatchKeyEvent(centre)
            true
        }
        KeyEvent.KEYCODE_BUTTON_B -> {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() ?: activity.finish()
            }
            true
        }
        else -> false
    }

    /** In a dialog: B (on release) is Back; every other key is left alone (A presses the focused button). */
    fun handleDialogKey(dialog: Dialog, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return false
        @Suppress("DEPRECATION")
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dialog.onBackPressed()
        return true
    }

    private val dialogFragmentHook = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            (f as? DialogFragment)?.dialog?.let(::installOn)
        }
    }
}
