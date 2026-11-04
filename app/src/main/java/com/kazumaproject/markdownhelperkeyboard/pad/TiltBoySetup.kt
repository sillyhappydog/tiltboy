package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService

/**
 * Amendment 19: 「キーボードを使えるようにする」 / "Set up the keyboard" on the TiltBoy home. The
 * same enable-then-switch flow as the diagnostics screen's banner: the system's keyboard list to
 * turn TiltBoy on, then the keyboard picker to make it the one in use.
 */
object TiltBoySetup {
    enum class State { NOT_ENABLED, NOT_CURRENT, DONE }

    /** 「済み」 only when TiltBoy is both enabled and the current keyboard. */
    fun state(enabled: Boolean, current: Boolean): State = when {
        !enabled -> State.NOT_ENABLED
        !current -> State.NOT_CURRENT
        else -> State.DONE
    }

    @StringRes
    fun summary(state: State): Int = when (state) {
        State.DONE -> R.string.tiltboy_setup_done
        State.NOT_ENABLED -> R.string.tiltboy_setup_not_enabled
        State.NOT_CURRENT -> R.string.tiltboy_setup_not_current
    }

    private fun me(context: Context) = ComponentName(context, IMEService::class.java)

    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        val me = me(context)
        return imm.enabledInputMethodList.any { it.component == me }
    }

    fun isCurrent(context: Context): Boolean {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return ComponentName.unflattenFromString(current.orEmpty()) == me(context)
    }

    fun state(context: Context): State = state(isEnabled(context), isCurrent(context))

    /** The next step: turn TiltBoy on in the system's list, else open the keyboard picker. */
    fun next(context: Context) {
        if (!isEnabled(context)) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else {
            context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }
}
