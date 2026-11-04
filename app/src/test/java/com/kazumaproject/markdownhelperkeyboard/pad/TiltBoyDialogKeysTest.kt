package com.kazumaproject.markdownhelperkeyboard.pad

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Looper
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.kazumaproject.markdownhelperkeyboard.pad.ui.TiltBoyControllerKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A list-style dialog fragment like PreferenceFragmentCompat's (使うモード, the role lists). */
class TiltBoyTestListDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setMultiChoiceItems(arrayOf("かな", "英字", "数字"), booleanArrayOf(true, true, true), null)
            .setNegativeButton("やめる", null)
            .create()
}

/**
 * v8 device check: in the dialogs opened from the TiltBoy settings pages the controller's B did
 * nothing (only the device's Back closed them). B now acts as Back in every such dialog; A is
 * left alone (it presses the focused button, as before).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TiltBoyDialogKeysTest {
    private fun key(action: Int, code: Int) =
        KeyEvent(0L, 0L, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_GAMEPAD)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun b_cancels_a_dialog_on_release_and_a_is_left_alone() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        var cancelled = false
        val dialog = AlertDialog.Builder(activity)
            .setMessage("変換の学習を消しますか？")
            .setPositiveButton("消す", null)
            .setNegativeButton("やめる", null)
            .setOnCancelListener { cancelled = true }
            .show()
        TiltBoyControllerKeys.installOn(dialog)
        assertTrue(TiltBoyControllerKeys.isInstalledOn(dialog))

        assertFalse("A is not taken (it presses the focused button)", TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertFalse(TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
        assertFalse("other keys are not taken", TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        idle()
        assertTrue(dialog.isShowing)

        assertTrue(TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
        idle()
        assertTrue("B down alone keeps the dialog", dialog.isShowing)
        assertTrue(TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)))
        idle()
        assertFalse("B closed the dialog", dialog.isShowing)
        assertTrue("B cancels, like Back (nothing is cleared)", cancelled)
    }

    @Test
    fun b_keeps_a_dialog_that_back_would_keep() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val dialog = AlertDialog.Builder(activity).setMessage("x").setCancelable(false).show()
        TiltBoyControllerKeys.installOn(dialog)
        TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))
        TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B))
        idle()
        assertTrue(dialog.isShowing)
    }

    @Test
    fun dialog_fragments_under_the_settings_activity_get_b_as_back() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        TiltBoyControllerKeys.install(activity)
        val f = TiltBoyTestListDialog()
        f.show(activity.supportFragmentManager, "list")
        activity.supportFragmentManager.executePendingTransactions()
        idle()
        val dialog = f.dialog ?: throw AssertionError("no dialog")
        assertTrue(dialog.isShowing)
        assertTrue("the hook installed B on the dialog fragment's dialog", TiltBoyControllerKeys.isInstalledOn(dialog))
        TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B))
        TiltBoyControllerKeys.handleDialogKey(dialog, key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B))
        idle()
        activity.supportFragmentManager.executePendingTransactions()
        assertFalse(dialog.isShowing)
        assertNull("the dialog fragment is gone", activity.supportFragmentManager.findFragmentByTag("list"))
    }
}
