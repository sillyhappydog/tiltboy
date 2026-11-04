package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.PadRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Amendment 19: the TiltBoy settings home holds exactly the items of the spec, in order, in
 * Japanese and English; 「済み」 only when TiltBoy is enabled and current; the one guide switch's
 * migration; 細かい調整 and このアプリについて hold what the spec lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TiltBoySettingsTest {
    private lateinit var app: Context
    private lateinit var store: SharedPreferences

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        store = PreferenceManager.getDefaultSharedPreferences(app)
        store.edit().clear().commit()
    }

    private fun localized(language: String): Context {
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return app.createConfigurationContext(config)
    }

    @Suppress("RestrictedApi")
    private fun inflate(context: Context, xml: Int): PreferenceScreen =
        PreferenceManager(context).inflateFromResource(context, xml, null)

    /** Every preference in order, categories as "# title", items as their titles. */
    private fun outline(group: PreferenceGroup): List<String> = buildList {
        for (i in 0 until group.preferenceCount) {
            val p: Preference = group.getPreference(i)
            if (p is PreferenceCategory) {
                add("# ${p.title}")
                addAll(outline(p))
            } else if (p.title != null) {
                add(p.title.toString())
            }
        }
    }

    private fun keys(group: PreferenceGroup): List<String> = buildList {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            p.key?.let(::add)
            if (p is PreferenceGroup) addAll(keys(p))
        }
    }

    @Test
    fun the_home_holds_exactly_the_items_in_order_in_japanese() {
        assertEquals(
            listOf(
                "# はじめに", "ボタンの並び", "キーボードを使えるようにする", "試し打ち・コントローラーの確認",
                "# 打ち方", "使うモード", "画面に案内を出す",
                "# ことば", "単語の登録", "変換の学習を消す",
                "# そのほか", "細かい調整", "このアプリについて",
            ),
            outline(inflate(localized("ja"), R.xml.pref_tiltboy_home)),
        )
    }

    @Test
    fun the_home_holds_exactly_the_items_in_order_in_english() {
        assertEquals(
            listOf(
                "# Getting started", "Button layout", "Set up the keyboard", "Try it and check the controller",
                "# Typing", "Modes", "Show the guide",
                "# Words", "Add words", "Clear learned conversions",
                "# More", "Fine tuning", "About",
            ),
            outline(inflate(localized("en"), R.xml.pref_tiltboy_home)),
        )
    }

    @Test
    fun the_home_summaries_follow_the_mock() {
        val ja = localized("ja")
        val home = inflate(ja, R.xml.pref_tiltboy_home)
        assertEquals("ここで打って、スティックやボタンが届いているか見られます", home.findPreference<Preference>("tiltboy_try")?.summary)
        assertEquals("名前など、変換で出ないことばを登録", home.findPreference<Preference>("tiltboy_words")?.summary)
        assertEquals("手ざわり（倒す量など）・ボタンの割り当て", home.findPreference<Preference>("tiltboy_fine_tuning")?.summary)
        assertEquals("バージョン・ライセンス", home.findPreference<Preference>("tiltboy_about")?.summary)
        assertTrue("no controller-input switch", home.findPreference<Preference>(PadPreferences.ENABLED) == null)
        assertTrue("one guide switch", home.findPreference<Preference>(PadPreferences.GUIDE) != null)
    }

    @Test
    fun done_only_when_enabled_and_current() {
        assertEquals(TiltBoySetup.State.DONE, TiltBoySetup.state(enabled = true, current = true))
        assertEquals(TiltBoySetup.State.NOT_CURRENT, TiltBoySetup.state(enabled = true, current = false))
        assertEquals(TiltBoySetup.State.NOT_ENABLED, TiltBoySetup.state(enabled = false, current = false))
        assertEquals(TiltBoySetup.State.NOT_ENABLED, TiltBoySetup.state(enabled = false, current = true))
        val ja = localized("ja")
        val en = localized("en")
        assertEquals("済み", ja.getString(TiltBoySetup.summary(TiltBoySetup.state(true, true))))
        assertEquals("Done", en.getString(TiltBoySetup.summary(TiltBoySetup.state(true, true))))
        for (state in listOf(TiltBoySetup.state(true, false), TiltBoySetup.state(false, false), TiltBoySetup.state(false, true))) {
            assertFalse("済み only when enabled and current: $state", ja.getString(TiltBoySetup.summary(state)).contains("済み"))
            assertFalse("Done only when enabled and current: $state", en.getString(TiltBoySetup.summary(state)).contains("Done"))
        }
        assertTrue("says what is missing", ja.getString(TiltBoySetup.summary(TiltBoySetup.State.NOT_ENABLED)).contains("有効"))
        assertTrue("says what is missing", ja.getString(TiltBoySetup.summary(TiltBoySetup.State.NOT_CURRENT)).contains("切り替え"))
        // This test process has not enabled TiltBoy as a keyboard.
        assertFalse(TiltBoySetup.state(app) == TiltBoySetup.State.DONE)
    }

    @Test
    fun fine_tuning_holds_the_sliders_roles_face_layout_and_stick_source_only() {
        val ja = localized("ja")
        val screen = inflate(ja, R.xml.pref_pad)
        val k = keys(screen)
        for (key in listOf("pad_left_select_threshold", "pad_left_release_threshold", "pad_flick_threshold", "pad_flick_return_threshold",
            "pad_trigger_threshold", "pad_repeat_delay_ms", "pad_repeat_interval_ms", "pad_roles_category", "pad_face_layout", "pad_stick_source")) {
            assertTrue("$key on 細かい調整", key in k)
        }
        for (key in listOf(PadPreferences.ENABLED, PadPreferences.SHOW_GUIDE, PadPreferences.FULL_GUIDE, PadPreferences.MODES, "pad_diagnostics_preference")) {
            assertFalse("$key is not on 細かい調整", key in k)
        }
        val categories = (0 until screen.preferenceCount).map { screen.getPreference(it).title.toString() }
        assertEquals(listOf("手ざわりの調整", "ボタンの割り当て", "ボタンとスティック"), categories)
    }

    @Test
    fun about_holds_version_licences_dictionary_licences_and_attribution() {
        val ja = localized("ja")
        val about = inflate(ja, R.xml.pref_tiltboy_about)
        assertEquals(listOf("バージョン", "オープンソースライセンス", "辞書のライセンス"), outline(about))
        assertTrue(about.findPreference<Preference>("tiltboy_attribution")?.summary.toString().contains("KazumaProject"))
        val en = inflate(localized("en"), R.xml.pref_tiltboy_about)
        assertEquals(listOf("Version", "Open-source licences", "Dictionary licences"), outline(en))
    }

    // ------------------------------------------------------------------ migration

    private fun guideAfterMigration(show: Boolean?, full: Boolean?): Boolean {
        store.edit().clear().apply {
            show?.let { putBoolean(PadPreferences.SHOW_GUIDE, it) }
            full?.let { putBoolean(PadPreferences.FULL_GUIDE, it) }
        }.commit()
        val on = PadPreferences.guide(store)
        assertEquals("stored", on, store.getBoolean(PadPreferences.GUIDE, !on))
        assertEquals("the old show key follows", on, store.getBoolean(PadPreferences.SHOW_GUIDE, !on))
        assertEquals("the old full key follows", on, store.getBoolean(PadPreferences.FULL_GUIDE, !on))
        return on
    }

    @Test
    fun the_guide_switch_is_on_if_either_old_switch_was_on() {
        assertTrue("fresh install", guideAfterMigration(null, null))
        assertTrue(guideAfterMigration(true, true))
        assertTrue(guideAfterMigration(true, false))
        assertTrue(guideAfterMigration(false, true))
        assertFalse(guideAfterMigration(false, false))
        assertTrue("full unset (default on)", guideAfterMigration(false, null))
    }

    @Test
    fun the_guide_switch_sets_both_old_keys_and_the_ime_reads_it() {
        PadPreferences.setGuide(store, false)
        assertFalse(PadPreferences.showGuide(app))
        assertFalse(PadPreferences.fullGuide(app))
        assertFalse(store.getBoolean(PadPreferences.SHOW_GUIDE, true))
        assertFalse(store.getBoolean(PadPreferences.FULL_GUIDE, true))
        PadPreferences.setGuide(store, true)
        assertTrue(PadPreferences.showGuide(app))
        assertTrue(PadPreferences.fullGuide(app))
        // A stored old value no longer decides once the switch exists.
        store.edit().putBoolean(PadPreferences.SHOW_GUIDE, false).commit()
        assertTrue(PadPreferences.guide(store))
    }

    @Test
    fun pad_enabled_false_is_ignored_and_the_migration_keeps_roles() {
        store.edit().putBoolean(PadPreferences.ENABLED, false).putBoolean(PadPreferences.SHOW_GUIDE, false)
            .putBoolean(PadPreferences.FULL_GUIDE, false).commit()
        assertTrue(PadPreferences.isEnabled(app))
        PadPreferences.migrateRoles(store)
        assertFalse("both old guide switches off: the new one is off", PadPreferences.guide(store))
        assertTrue("controller input stays on", PadPreferences.isEnabled(app))
    }

    // ------------------------------------------------------------------ amendment 24

    @Test
    fun b_role_text_says_it_deletes_while_converting_too() {
        val ja = localized("ja").getString(R.string.pad_role_backspace)
        val en = localized("en").getString(R.string.pad_role_backspace)
        assertEquals("1 文字消す（変換中も変換をやめて 1 文字消す）", ja)
        assertEquals("Delete one character (while converting too: leaves the conversion and deletes)", en)
        // The role list shows it for BACKSPACE (B's default role).
        assertEquals(R.string.pad_role_backspace, com.kazumaproject.markdownhelperkeyboard.pad.ui.PadPreferenceFragment.roleLabel(PadRole.BACKSPACE))
        assertEquals(PadRole.BACKSPACE, PadConfig().roleOf(PadButton.B))
        // Not in the guide: no guide word mentions the conversion.
        val words = com.kazumaproject.pad_input.PadWord.entries.map { localized("ja").getString(R.string::class.java.getField(it.resourceName).getInt(null)) }
        assertFalse(words.joinToString(), words.any { it.contains("変換をやめて") })
    }

    // ------------------------------------------------------------------ amendment 23

    @Test
    fun the_roles_text_says_what_the_dpad_does_while_converting() {
        val ja = localized("ja").getString(R.string.pad_roles_category_summary)
        val en = localized("en").getString(R.string.pad_roles_category_summary)
        assertTrue(ja, ja.contains("十字キーはカーソル移動。変換中: ←→ 候補、↑ 範囲を長く、↓ 範囲を短く"))
        assertTrue(en, en.contains("D-pad: cursor. While converting: ←→ candidates, ↑ longer range, ↓ shorter range"))
        // Not in the guide: no guide word mentions the range.
        val words = com.kazumaproject.pad_input.PadWord.entries.map { localized("ja").getString(R.string::class.java.getField(it.resourceName).getInt(null)) }
        assertFalse(words.joinToString(), words.any { it.contains("範囲") })
    }

    // ------------------------------------------------------------------ amendment 20

    @Test
    fun the_guide_summary_names_select_and_no_touch_on_the_guide() {
        val ja = inflate(localized("ja"), R.xml.pref_tiltboy_home).findPreference<Preference>(PadPreferences.GUIDE)?.summary.toString()
        val en = inflate(localized("en"), R.xml.pref_tiltboy_home).findPreference<Preference>(PadPreferences.GUIDE)?.summary.toString()
        assertTrue(ja, ja.contains("Select"))
        assertTrue(en, en.contains("Select"))
        assertFalse("no 'a touch brings the keys back'", ja.contains("触") || ja.contains("タップ"))
        assertFalse("no 'a touch brings the keys back'", en.contains("tap", ignoreCase = true) || en.contains("touching", ignoreCase = true))
    }

    @Test
    fun a_v7_select_mode_switch_becomes_the_touch_keyboard() {
        // v7 stored roles version 6 and (untouched) no role keys: Select was モード by default.
        store.edit().putInt(PadPreferences.ROLES_VERSION, 6).putStringSet(PadPreferences.MODES, setOf("kana", "english", "number")).commit()
        PadPreferences.migrateRoles(store)
        assertEquals("TOUCH_KEYBOARD", store.getString(PadPreferences.roleKey(PadButton.SELECT), null))
        assertEquals(PadConfig.ROLES_VERSION, store.getInt(PadPreferences.ROLES_VERSION, 0))
        assertEquals(PadRole.TOUCH_KEYBOARD, PadPreferences.config(store).roleOf(PadButton.SELECT))
        // Stored explicitly as MODE_SWITCH: the same; on another button: the next mode.
        store.edit().clear().putInt(PadPreferences.ROLES_VERSION, 6).putString(PadPreferences.roleKey(PadButton.SELECT), "MODE_SWITCH")
            .putString(PadPreferences.roleKey(PadButton.R1), "MODE_SWITCH").commit()
        PadPreferences.migrateRoles(store)
        assertEquals("TOUCH_KEYBOARD", store.getString(PadPreferences.roleKey(PadButton.SELECT), null))
        assertEquals("MODE_NEXT", store.getString(PadPreferences.roleKey(PadButton.R1), null))
        // A customised Select (隠す) stays.
        store.edit().clear().putInt(PadPreferences.ROLES_VERSION, 6).putString(PadPreferences.roleKey(PadButton.SELECT), "HIDE_KEYBOARD").commit()
        PadPreferences.migrateRoles(store)
        assertEquals("HIDE_KEYBOARD", store.getString(PadPreferences.roleKey(PadButton.SELECT), null))
    }

    @Test
    fun the_modes_offer_kana_english_and_numbers_only_and_a_stored_emoji_is_dropped() {
        val ja = localized("ja")
        assertEquals(listOf("かな", "英字", "数字"), ja.resources.getStringArray(R.array.pad_modes_entries).toList())
        assertEquals(listOf("kana", "english", "number"), ja.resources.getStringArray(R.array.pad_modes_values).toList())
        store.edit().putStringSet(PadPreferences.MODES, setOf("kana", "emoji", "number")).commit()
        PadPreferences.migrateRoles(store)
        assertEquals(setOf("kana", "number"), store.getStringSet(PadPreferences.MODES, null))
        assertEquals(listOf(PadMode.KANA, PadMode.NUMBER), PadPreferences.modes(store))
        store.edit().clear().putStringSet(PadPreferences.MODES, setOf("emoji")).commit()
        PadPreferences.migrateRoles(store)
        assertEquals("nothing left: all three", setOf("kana", "english", "number"), store.getStringSet(PadPreferences.MODES, null))
    }
}
