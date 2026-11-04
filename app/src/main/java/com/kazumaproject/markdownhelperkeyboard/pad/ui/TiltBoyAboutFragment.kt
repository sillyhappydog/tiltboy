package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.os.Bundle
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely

/**
 * Amendment 19: 「このアプリについて」 / "About" (res/xml/pref_tiltboy_about.xml): the version,
 * Sumire's open-source licence screen (complete: the app, every library and every dictionary),
 * the same screen opened at its dictionaries section, and the attribution line.
 */
class TiltBoyAboutFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_tiltboy_about

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        TiltBoyControllerKeys.install(requireActivity())
        findPreference<Preference>(KEY_VERSION)?.summary = BuildConfig.VERSION_NAME
        findPreference<Preference>(KEY_LICENCES)?.setOnPreferenceClickListener {
            navigateSafely(R.id.openSourceFragment)
            true
        }
        findPreference<Preference>(KEY_DICTIONARY_LICENCES)?.setOnPreferenceClickListener {
            // The licence screen's first section is the dictionaries (OpenSourceLicenses.sections()).
            navigateSafely(R.id.openSourceFragment, Bundle().apply { putInt(EXPAND_GROUP, 0) })
            true
        }
    }

    companion object {
        const val KEY_VERSION = "tiltboy_version"
        const val KEY_LICENCES = "tiltboy_licences"
        const val KEY_DICTIONARY_LICENCES = "tiltboy_dictionary_licences"
        /** Argument read by OpenSourceFragment (one-line TiltBoy hook): the section to open. */
        const val EXPAND_GROUP = "tiltboy_expand_group"
    }
}
