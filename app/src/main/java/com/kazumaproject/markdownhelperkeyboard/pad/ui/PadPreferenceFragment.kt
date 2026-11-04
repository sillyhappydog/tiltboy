package com.kazumaproject.markdownhelperkeyboard.pad.ui

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.pad.PadPreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.PadRole
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sumire Pad settings screen (res/xml/pref_pad.xml plus one role list per button). Amendment 12:
 * with かな off no kana-only role is offered or described.
 * Amendment 18: the 「かなの右スティック」 setting is gone (the pentagon is the only one), the removed
 * roles (the bar, the centre character) are never offered, and L3 / R3 offer 前 / 次のモードへ.
 * Amendment 20: Select's モード切替 became the touch keyboard (TOUCH_KEYBOARD); MODE_SWITCH is never offered.
 * Amendment 19: this is TiltBoy's 「細かい調整」 / "Fine tuning" page under the TiltBoy home
 * ([TiltBoyHomeFragment]), which now holds the modes, the guide switch and the diagnostics entry.
 */
@AndroidEntryPoint
class PadPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_pad

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = PadPreferences.prefs(preferenceManager.context)
        // First-run modes and the move of stored roles to the current defaults, before the lists read them.
        PadPreferences.migrateRoles(prefs)
        TiltBoyControllerKeys.install(requireActivity())
        buildRoleLists(PadMode.KANA in PadPreferences.modes(prefs))
    }

    /** One list per button; kana-only roles are left out (and described as such) while かな is off. */
    private fun buildRoleLists(kana: Boolean) {
        val category = findPreference<PreferenceCategory>("pad_roles_category") ?: return
        category.removeAll()
        category.summary = getString(if (kana) R.string.pad_roles_category_summary else R.string.pad_roles_category_summary_nokana)
        val context = preferenceManager.context
        val offered = PadRole.entries.filter { !it.isRemoved && (kana || it !in KANA_ONLY) }
        val entries: Array<CharSequence> = offered.map { getString(roleLabel(it, kana)) as CharSequence }.toTypedArray()
        val values: Array<CharSequence> = offered.map { it.name as CharSequence }.toTypedArray()
        for (button in PadPreferences.REMAPPABLE) {
            val pref = ListPreference(context).apply {
                key = PadPreferences.roleKey(button)
                title = buttonLabel(button)
                this.entries = entries
                entryValues = values
                setDefaultValue((PadConfig.DEFAULT_ROLES[button] ?: PadRole.NONE).name)
                // A kana-only role on a button while かな is off does nothing: say so instead of a blank summary.
                summaryProvider = Preference.SummaryProvider<ListPreference> { p -> p.entry ?: getString(R.string.pad_role_kana_only) }
                // Amendment 22 (v8 device check): indented like the sliders and the ボタンとスティック items.
                isIconSpaceReserved = true
            }
            category.addPreference(pref)
        }
    }

    private fun buttonLabel(button: PadButton): String = when (button) {
        PadButton.L3 -> getString(R.string.pad_button_l3)
        PadButton.R3 -> getString(R.string.pad_button_r3)
        else -> button.label
    }

    companion object {
        /**
         * Roles that only do something in かな (hidden from the lists while かな is off). Amendment 22:
         * none any more: 半濁点 / 小文字 give 数字 in 英字 (described as such while かな is off).
         */
        val KANA_ONLY: Set<PadRole> = emptySet()

        fun roleLabel(role: PadRole, kana: Boolean = true): Int = when (role) {
            // Removed by amendments 18 and 20: never offered (the migration gives the button a current role).
            PadRole.TAP_CENTER, PadRole.BAR, PadRole.MODE_SWITCH -> R.string.pad_role_none
            PadRole.MODE_PREVIOUS -> R.string.pad_role_mode_previous
            PadRole.MODE_NEXT -> R.string.pad_role_mode_next
            PadRole.CONFIRM -> R.string.pad_role_confirm
            PadRole.BACKSPACE -> R.string.pad_role_backspace
            PadRole.SPACE -> R.string.pad_role_space
            PadRole.CONVERT_NEXT -> R.string.pad_role_convert_next
            PadRole.PREVIOUS_CANDIDATE -> R.string.pad_role_previous_candidate
            PadRole.HIDE_KEYBOARD -> R.string.pad_role_hide
            PadRole.TOUCH_KEYBOARD -> R.string.pad_role_touch_keyboard
            PadRole.EMOJI_JUMP -> R.string.pad_role_emoji_jump
            PadRole.HOLD_DAKUTEN -> if (kana) R.string.pad_role_hold_dakuten else R.string.pad_role_hold_dakuten_nokana
            PadRole.HOLD_HANDAKUTEN -> if (kana) R.string.pad_role_hold_handakuten else R.string.pad_role_hold_handakuten_nokana
            PadRole.HOLD_SMALL -> if (kana) R.string.pad_role_hold_small else R.string.pad_role_hold_small_nokana
            PadRole.HOLD_SMALL_OR_HANDAKUTEN -> if (kana) R.string.pad_role_hold_small_or_handakuten else R.string.pad_role_hold_small_or_handakuten_nokana
            PadRole.HOLD_UPPERCASE -> R.string.pad_role_hold_uppercase
            // Amendment 21: 記号 in every mode.
            PadRole.YA_WA_LAYER -> R.string.pad_role_ya_wa_layer
            PadRole.NONE -> R.string.pad_role_none
        }
    }
}
