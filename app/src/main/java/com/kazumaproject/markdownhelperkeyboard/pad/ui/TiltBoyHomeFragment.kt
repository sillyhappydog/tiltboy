package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.pad.PadPreferences
import com.kazumaproject.markdownhelperkeyboard.pad.TiltBoySetup
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.navigateSafely
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Amendment 19: TiltBoy's settings home, the page the launcher opens (res/xml/pref_tiltboy_home.xml).
 *
 * はじめに: set up the keyboard (「済み」 when TiltBoy is enabled and current, [TiltBoySetup]),
 * try it / check the controller (the diagnostics screen). 打ち方: the modes, the one guide switch.
 * ことば: Sumire's user dictionary, clearing what the conversion learned (Sumire's
 * [LearnRepository.deleteAll], behind a confirm dialog). そのほか: 細かい調整 ([PadPreferenceFragment])
 * and このアプリについて ([TiltBoyAboutFragment]). Sumire's other settings are not reachable from here.
 */
@AndroidEntryPoint
class TiltBoyHomeFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_tiltboy_home

    @Inject
    lateinit var learnRepository: LearnRepository

    private var imeObserver: ContentObserver? = null

    /** Before the XML is inflated (inflation stores defaults): the modes' and the guide switch's migration. */
    override suspend fun preparePreferenceData(context: android.content.Context) {
        PadPreferences.migrateRoles(PadPreferences.prefs(context))
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val prefs = PadPreferences.prefs(context)
        // First-run modes, the role migration and the guide switch's migration (amendment 19).
        PadPreferences.migrateRoles(prefs)
        TiltBoyControllerKeys.install(requireActivity())

        findPreference<Preference>(KEY_SETUP)?.setOnPreferenceClickListener {
            TiltBoySetup.next(requireContext())
            true
        }
        findPreference<Preference>(KEY_TRY)?.setOnPreferenceClickListener {
            navigateSafely(R.id.padDiagnosticsFragment)
            true
        }
        findPreference<MultiSelectListPreference>(PadPreferences.MODES)?.let { modes ->
            modes.values = PadPreferences.modes(prefs).mapNotNull { PadPreferences.MODE_VALUES[it] }.toSet()
            modes.summaryProvider = Preference.SummaryProvider<MultiSelectListPreference> { p -> modesSummary(p.values) }
            modes.setOnPreferenceChangeListener { _, newValue ->
                @Suppress("UNCHECKED_CAST")
                val set = newValue as? Set<String> ?: emptySet()
                if (set.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.pad_modes_need_one, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    true
                }
            }
        }
        findPreference<SwitchPreferenceCompat>(PadPreferences.GUIDE)?.let { guide ->
            guide.isChecked = PadPreferences.guide(prefs)
            guide.setOnPreferenceChangeListener { _, newValue ->
                PadPreferences.setGuide(prefs, newValue == true)
                true
            }
        }
        findPreference<Preference>(KEY_WORDS)?.setOnPreferenceClickListener {
            navigateSafely(R.id.navigation_user_dictionary)
            true
        }
        findPreference<Preference>(KEY_CLEAR_LEARNING)?.setOnPreferenceClickListener {
            confirmClearLearning()
            true
        }
        findPreference<Preference>(KEY_FINE_TUNING)?.setOnPreferenceClickListener {
            navigateSafely(R.id.padPreferenceFragment)
            true
        }
        findPreference<Preference>(KEY_ABOUT)?.setOnPreferenceClickListener {
            navigateSafely(R.id.tiltBoyAboutFragment)
            true
        }
        refreshSetup()
    }

    /** 「かな・英字・数字」: the checked modes in the fixed order. */
    private fun modesSummary(values: Set<String>): String {
        val names = resources.getStringArray(R.array.pad_modes_entries)
        val keys = resources.getStringArray(R.array.pad_modes_values)
        return keys.indices.filter { keys[it] in values }.joinToString(getString(R.string.pad_modes_separator)) { names[it] }
    }

    private fun refreshSetup() {
        if (!isAdded) return
        findPreference<Preference>(KEY_SETUP)?.setSummary(TiltBoySetup.summary(TiltBoySetup.state(requireContext())))
    }

    private fun confirmClearLearning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tiltboy_clear_learning_title)
            .setMessage(R.string.tiltboy_clear_learning_message)
            .setPositiveButton(R.string.tiltboy_clear_learning_confirm) { _, _ ->
                val appContext = requireContext().applicationContext
                lifecycleScope.launch {
                    val cleared = runCatching { withContext(Dispatchers.IO) { learnRepository.deleteAll() } }.isSuccess
                    if (cleared) Toast.makeText(appContext, R.string.tiltboy_clear_learning_done, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.tiltboy_clear_learning_cancel, null)
            .show()
            .also { TiltBoyControllerKeys.installOn(it) } // v8 device check: B = やめる (cancel)
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        // The picker and the system's keyboard list change these while this page stays resumed.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshSetup()
        }
        val resolver = requireContext().contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer)
        imeObserver = observer
    }

    override fun onPause() {
        imeObserver?.let { requireContext().contentResolver.unregisterContentObserver(it) }
        imeObserver = null
        super.onPause()
    }

    companion object {
        const val KEY_SETUP = "tiltboy_setup"
        const val KEY_TRY = "tiltboy_try"
        const val KEY_WORDS = "tiltboy_words"
        const val KEY_CLEAR_LEARNING = "tiltboy_clear_learning"
        const val KEY_FINE_TUNING = "tiltboy_fine_tuning"
        const val KEY_ABOUT = "tiltboy_about"
    }
}
