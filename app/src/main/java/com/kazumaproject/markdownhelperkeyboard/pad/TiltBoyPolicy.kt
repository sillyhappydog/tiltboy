package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.SharedPreferences
import com.kazumaproject.markdownhelperkeyboard.BuildConfig

/**
 * TiltBoy (amendment 19): every Sumire setting TiltBoy pins, in one place.
 *
 * TiltBoy hides Sumire's settings screens instead of deleting them (so upstream can still be
 * merged). What those screens controlled is pinned here and applied when the setting is read:
 * [wrap] puts a [SharedPreferences] view in front of the default store, so a pinned key reads as
 * its pinned value and a [defaulted] key reads as Sumire's own default, whatever an earlier
 * version stored. Writes go to the real store unchanged. Sumire reads its settings through
 * `AppPreference` (one hook there) and the IME's runtime preferences (one hook in IMEService).
 *
 * Active in the TiltBoy variant (liteFdroid, [BuildConfig.IS_FDROID]); the other variants keep
 * Sumire's behaviour, so Sumire's own tests stay meaningful there.
 */
object TiltBoyPolicy {
    /** The policy applies (TiltBoy build). Tests may switch it. */
    @Volatile
    var active: Boolean = BuildConfig.IS_FDROID

    /** Controller input is always on in TiltBoy; a stored `pad_enabled = false` is ignored. */
    const val PAD_ENABLED = true

    /**
     * The touch keyboard is Sumire's 12-key (かな). Its own switch key goes あ → A → 1: 英字 shows
     * QWERTY ([TENKEY_ENGLISH_QWERTY]), 数字 is the 12-key's number keyboard, and QWERTY's switch
     * key returns to it. No 五十音, モールス, スミレ入力, custom keyboard, split keyboard or
     * QWERTY-for-kana (romaji) keyboard is in the order.
     */
    const val KEYBOARD_ORDER_JSON = "[\"TENKEY\"]"

    /** Key → the value it always reads as. */
    val pinned: Map<String, Any> = linkedMapOf(
        // Touch keyboards: かな = 12-key, 英字 = QWERTY, 数字 = the 12-key's numbers.
        "keyboard_order_preference" to KEYBOARD_ORDER_JSON,
        "save_last_used_keyboard" to false,
        "save_last_used_keyboard_int" to 0,
        "tenkey_kana_english_qwerty_preference" to true,
        "tablet_tenkey_kana_english_qwerty_preference" to true,
        "tenkey_use_three_state_keyboard_preference" to true,
        "tenkey_switch_number_to_qwerty_number_preference" to false,
        "qwerty_switch_number_key_without_number_preference" to false,
        "qwerty_show_switch_romaji_english_preference" to false,
        "landscape_force_qwerty_preference" to false,
        "landscape_force_qwerty_romaji_preference" to false,
        "switch_qwerty_keyboard_password_preference" to false,
        "tablet_use_gojuon_layout_preference" to false,
        "keyboard_floating_preference" to false,
        // The toolbar / shortcut row over the keys, clipboard history.
        "shortcut_toolbar_visibility_preference" to false,
        "shortcut_toolbar_integrated_in_suggestion_preference" to false,
        "clipboard_history_preference" to false,
        // Voice input: no voice key anywhere (RECORD_AUDIO is removed from the manifest too).
        "physical_toolbar_show_voice" to false,
        // AI conversion (zenz, Gemma).
        "enable_ai_conversion_zenz_preference" to false,
        "enable_zenz_rerank_preference" to false,
        "enable_zenz_right_context_preference" to false,
        "zenz_floating_candidates_enabled" to false,
        "conversion_key_long_press_ai_conversion_preference" to false,
        "gemma_translation_enable_preference" to false,
        "gemma_floating_enabled" to false,
        // Inline (autofill) suggestions.
        "inline_suggestion_enabled_preference" to false,
        // Amendment 20: Start opens Sumire's emoji list; the symbol keyboard's first page is the emoji one.
        "symbol_mode_preference" to "EMOJI",
        // Sumire's settings homes are hidden; the "new" one has no bottom bar (MainActivity).
        "setting_use_new_home_screen_preference" to true,
        // v14: no fullscreen extract UI. On the AYN Thor (landscape) Android put a big text field and
        // 「完了」 over the app whenever the keyboard opened; the guide is meant to sit under the app.
        "allow_fullscreen_mode_preference" to false,
    )

    /** English shows QWERTY on touch (the controller keeps typing on the 12-key, see IMEService). */
    val TENKEY_ENGLISH_QWERTY: Boolean get() = pinned["tenkey_kana_english_qwerty_preference"] == true

    /** Exact keys that read as Sumire's default (theme, sizes, feedback, tablet / hardware keyboard / split). */
    val defaultedKeys: Set<String> = setOf(
        // Theme
        "keyboard_theme_mode_preference", "app_theme_seed_color_preference", "keyboard_skin_preference",
        "round_corner_keyboard_preference",
        // Sizes
        "candidate_letter_size_preference", "candidate_column_preference", "candidate_column_landscape_preference",
        "key_letter_size_preference", "special_key_text_size_preference", "tenkey_number_symbol_key_gap_preference",
        "shortcut_toolbar_height_dp_preference", "shortcut_toolbar_icon_size_dp_preference",
        "qwerty_special_key_icon_size_preference", "qwerty_symbol_keymap_text_size_preference",
        "flick_special_key_text_size_sp_preference", "flick_guide_text_size_sp_preference",
        // Feedback
        "vibration_preference", "vibration_timing", "key_sound_preference", "key_sound_volume_percent_preference",
        "tenkey_popup_view_style_preference", "qwerty_show_popup_window_preference",
    )

    /** Key prefixes that read as Sumire's default. */
    val defaultedPrefixes: List<String> = listOf(
        // Theme
        "custom_theme_", "theme_custom_", "liquid_glass", "suminagashi_", "keyboard_background_",
        "keyboard_touch_effect_", "keyboard_corner_", "key_popup_",
        // Sizes (keyboard, candidate strip, keys, popups)
        "keyboard_height", "keyboard_width", "keyboard_position", "keyboard_margin_", "keyboard_vertical_margin_",
        "keyboard_floating_position", "qwerty_keyboard_", "qwerty_key_", "qwerty_variation_popup_",
        "candidate_view_height", "candidate_view_empty_height", "candidate_default_",
        "flick_key_", "flick_cross_popup_", "flick_directional_popup_", "flick_standard_popup_",
        "flick_tfbi_popup_size", "flick_tfbi_popup_text_size", "tenkey_key_", "tenkey_popup_size", "tenkey_popup_text_size",
        "gojuon_key_", "gojuon_special_",
        // Tablet layout, hardware keyboard, split keyboard
        "tablet_", "physical_keyboard_", "physical_toolbar_", "split_keyboard_",
    )

    fun isPinned(key: String?): Boolean = key != null && key in pinned

    fun isDefaulted(key: String?): Boolean =
        key != null && key !in pinned && (key in defaultedKeys || defaultedPrefixes.any { key.startsWith(it) })

    /** [base] seen through the policy (or [base] itself when the policy is off). */
    fun wrap(base: SharedPreferences): SharedPreferences =
        if (!active || base is PinnedPreferences) base else PinnedPreferences(base)

    /** A read-through view of [base]: pinned keys read pinned, defaulted keys read as never stored. */
    class PinnedPreferences(val base: SharedPreferences) : SharedPreferences by base {
        private inline fun <reified T> read(key: String?, defValue: T, stored: () -> T): T {
            if (key != null) {
                val pin = pinned[key]
                if (pin != null) return pin as? T ?: defValue
                if (isDefaulted(key)) return defValue
            }
            return stored()
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = read(key, defValue) { base.getBoolean(key, defValue) }
        override fun getInt(key: String?, defValue: Int): Int = read(key, defValue) { base.getInt(key, defValue) }
        override fun getLong(key: String?, defValue: Long): Long = read(key, defValue) { base.getLong(key, defValue) }
        override fun getFloat(key: String?, defValue: Float): Float = read(key, defValue) { base.getFloat(key, defValue) }
        override fun getString(key: String?, defValue: String?): String? {
            if (key != null) {
                val pin = pinned[key]
                if (pin != null) return pin as? String ?: defValue
                if (isDefaulted(key)) return defValue
            }
            return base.getString(key, defValue)
        }
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            if (isPinned(key) || isDefaulted(key)) return defValues
            return base.getStringSet(key, defValues)
        }

        override fun contains(key: String?): Boolean = when {
            isPinned(key) -> true
            isDefaulted(key) -> false
            else -> base.contains(key)
        }

        override fun getAll(): MutableMap<String, *> {
            val all = LinkedHashMap<String, Any?>()
            for ((k, v) in base.all) if (!isDefaulted(k)) all[k] = v
            all.putAll(pinned)
            return all
        }

        private val listeners = HashMap<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

        /** Listeners get this view (not the raw store), so a listener that reads back sees the pins. */
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            val wrapped = synchronized(listeners) {
                listeners.getOrPut(listener) { SharedPreferences.OnSharedPreferenceChangeListener { _, key -> listener.onSharedPreferenceChanged(this, key) } }
            }
            base.registerOnSharedPreferenceChangeListener(wrapped)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            val wrapped = synchronized(listeners) { listeners.remove(listener) } ?: return
            base.unregisterOnSharedPreferenceChangeListener(wrapped)
        }
    }
}
