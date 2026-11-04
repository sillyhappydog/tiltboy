// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

abstract class AsyncPreferenceFragment : androidx.fragment.app.Fragment() {
    protected open val preferencesXmlRes: Int = 0
    protected open fun onPreferencesReady(savedInstanceState: android.os.Bundle?, rootKey: String?) {}
    fun <T : androidx.preference.Preference> findPreference(key: CharSequence): T? = null
    val preferenceManager: androidx.preference.PreferenceManager get() = throw UnsupportedOperationException()
}
internal fun androidx.fragment.app.Fragment.navigateSafely(resId: Int): Boolean = true
