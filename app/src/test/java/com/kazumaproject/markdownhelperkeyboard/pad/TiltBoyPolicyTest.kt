package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amendment 19: every value TiltBoy pins wins over a stored opposite value, read through Sumire's
 * own `AppPreference` (the one hook in its init) as the IME and the settings read it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TiltBoyPolicyTest {
    private lateinit var context: Context
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        TiltBoyPolicy.active = true
        context = ApplicationProvider.getApplicationContext()
        store = PreferenceManager.getDefaultSharedPreferences(context)
        store.edit().clear().commit()
    }

    @After
    fun tearDown() {
        TiltBoyPolicy.active = true
    }

    /** Store the opposite of every pin (and junk for the defaulted keys), then start AppPreference. */
    private fun storeOpposites() {
        val e = store.edit()
        for ((key, value) in TiltBoyPolicy.pinned) {
            when (value) {
                is Boolean -> e.putBoolean(key, !value)
                is Int -> e.putInt(key, value + 3)
                is String -> e.putString(key, "[\"GOJUON\",\"SUMIRE\",\"MORSE\",\"CUSTOM\",\"SPLIT\",\"QWERTY\"]")
            }
        }
        e.commit()
        AppPreference.init(context)
    }

    @Test
    fun the_wrapper_reads_every_pinned_value_over_a_stored_opposite() {
        storeOpposites()
        val p = TiltBoyPolicy.wrap(store)
        for ((key, value) in TiltBoyPolicy.pinned) {
            when (value) {
                is Boolean -> assertEquals(key, value, p.getBoolean(key, !value))
                is Int -> assertEquals(key, value, p.getInt(key, value + 7))
                is String -> assertEquals(key, value, p.getString(key, null))
            }
            assertTrue("$key reads as stored", p.contains(key))
            assertEquals(key, value, p.all[key])
        }
    }

    @Test
    fun touch_keyboards_are_only_the_12_key_qwerty_for_english_and_the_12_key_numbers() {
        storeOpposites()
        assertEquals(listOf(KeyboardType.TENKEY), AppPreference.keyboard_order)
        assertEquals("英字 = QWERTY", true, AppPreference.tenkey_qwerty_switch_number_layout)
        assertTrue(AppPreference.tablet_tenkey_qwerty_switch_english_layout)
        assertTrue("あ → A → 1", AppPreference.tenkey_use_three_state_keyboard_preference)
        assertFalse("数字 = the 12-key's numbers", AppPreference.tenkey_switch_number_to_qwerty_number_preference)
        assertFalse("QWERTY's switch key returns to the 12-key's numbers", AppPreference.qwerty_switch_number_key_without_number_preference)
        assertEquals("no romaji (kana on QWERTY) key", false, AppPreference.qwerty_show_switch_romaji_english_button)
        assertFalse(AppPreference.landscape_force_qwerty_preference)
        assertFalse(AppPreference.landscape_force_qwerty_romaji_preference)
        assertEquals(false, AppPreference.switch_qwerty_password)
        assertFalse(AppPreference.save_last_used_keyboard_enable_preference)
        assertEquals(0, AppPreference.save_last_used_keyboard_position_preference)
    }

    /** v14: no fullscreen extract UI (the AYN Thor showed a big field + 「完了」 over the app in landscape). */
    @Test
    fun fullscreen_extract_ui_is_off() {
        assertFalse(AppPreference.isFullscreenModeAllowed(defaultValue = true))
    }

    @Test
    fun toolbar_clipboard_history_ai_inline_suggestions_and_sumires_homes_are_pinned() {
        storeOpposites()
        assertFalse("toolbar", AppPreference.shortcut_toolbar_visibility_preference)
        assertFalse("toolbar in the candidate strip", AppPreference.shortcut_toolbar_integrated_in_suggestion_preference)
        assertEquals("clipboard history", false, AppPreference.clipboard_history_enable)
        assertFalse("zenz", AppPreference.enable_zenz_preference)
        assertFalse("Gemma", AppPreference.enable_gemma_translation_preference)
        assertFalse("inline suggestions", AppPreference.inline_suggestion_enabled_preference)
        assertTrue("no legacy bottom bar", AppPreference.setting_use_new_home_screen_preference)
    }

    @Test
    fun voice_and_runtime_preferences_are_pinned_too() {
        storeOpposites()
        val runtime = TiltBoyPolicy.wrap(store)
        assertFalse("no voice key on the hardware-keyboard toolbar", runtime.getBoolean("physical_toolbar_show_voice", true))
        assertFalse(runtime.getBoolean("gemma_floating_enabled", true))
        assertFalse(runtime.getBoolean("zenz_floating_candidates_enabled", true))
        assertFalse(runtime.getBoolean("enable_ai_conversion_zenz_preference", true))
    }

    @Test
    fun theme_sizes_feedback_tablet_and_hardware_keyboard_read_as_sumires_defaults() {
        store.edit()
            .putString("keyboard_theme_mode_preference", "custom")
            .putString(KeyboardSkinId.PREFERENCE_KEY, KeyboardSkinId.entries.last().preferenceValue)
            .putInt("custom_theme_bg_color_preference", 0x123456)
            .putInt("keyboard_height_preference", 123)
            .putInt("keyboard_width_preference", 55)
            .putBoolean("vibration_preference", false)
            .putBoolean("key_sound_preference", true)
            .putString("physical_keyboard_input_mode_preference", "direct")
            .putString("split_keyboard_main_type", "QWERTY")
            .putBoolean("learn_dictionary_preference", false)       // not pinned: passes through
            .commit()
        AppPreference.init(context)
        assertEquals("default", AppPreference.theme_mode)
        assertEquals(KeyboardSkinId.DEFAULT, AppPreference.keyboardSkin)
        assertEquals(true, AppPreference.vibration_preference)
        assertEquals(false, AppPreference.key_sound_preference)
        val p = TiltBoyPolicy.wrap(store)
        for (key in listOf("keyboard_theme_mode_preference", "custom_theme_bg_color_preference", "keyboard_height_preference",
            "keyboard_width_preference", "vibration_preference", "key_sound_preference", "physical_keyboard_input_mode_preference",
            "split_keyboard_main_type", KeyboardSkinId.PREFERENCE_KEY)) {
            assertFalse("$key reads as never stored", p.contains(key))
            assertFalse("$key not in getAll", p.all.containsKey(key))
        }
        assertEquals(-1, p.getInt("keyboard_height_preference", -1))
        assertFalse("other settings are read as stored", p.getBoolean("learn_dictionary_preference", true))
        // The real store keeps what was written (hide and pin, do not delete).
        assertEquals(123, store.getInt("keyboard_height_preference", -1))
    }

    @Test
    fun writes_go_to_the_store_and_listeners_see_the_pinned_view() {
        val p = TiltBoyPolicy.wrap(store)
        var seen: Boolean? = null
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "clipboard_history_preference") seen = prefs.getBoolean(key, true)
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        p.edit().putBoolean("clipboard_history_preference", true).commit()
        assertTrue("written to the store", store.getBoolean("clipboard_history_preference", false))
        assertEquals("the listener reads the pin", false, seen)
        p.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun the_policy_off_is_sumires_behaviour() {
        TiltBoyPolicy.active = false
        assertTrue("no wrapper", TiltBoyPolicy.wrap(store) === store)
    }

    @Test
    fun controller_input_is_always_on() {
        store.edit().putBoolean(PadPreferences.ENABLED, false).commit()
        assertTrue("pad_enabled=false is ignored", PadPreferences.isEnabled(context))
    }
}
