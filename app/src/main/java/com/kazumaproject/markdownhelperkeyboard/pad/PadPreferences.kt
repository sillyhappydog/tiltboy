package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.kazumaproject.pad_input.FaceLayout
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.PadRole
import com.kazumaproject.pad_input.PadWords
import com.kazumaproject.pad_input.StickSource

/**
 * Sumire Pad settings. Stored in the app's default SharedPreferences (the same store the
 * settings screens write to), kept separate from upstream's AppPreference on purpose.
 */
object PadPreferences {
    /** Removed by amendment 19 (controller input is always on, [TiltBoyPolicy.PAD_ENABLED]); a stored value is ignored. */
    const val ENABLED = "pad_enabled"
    const val SHOW_GUIDE = "pad_show_guide"
    /** v2: while the controller is in use, a full-size guide replaces the touch keys (off = corner overlay). */
    const val FULL_GUIDE = "pad_full_guide"
    /**
     * Amendment 19: 「画面に案内を出す」 / "Show the guide", the one switch that replaced [SHOW_GUIDE]
     * and [FULL_GUIDE]: on sets both on, off sets both off ([setGuide]). Migration ([migrateGuide]):
     * on if either of the two was on.
     */
    const val GUIDE = "pad_guide"
    /** 「ボタンの並び」 for the guide's face-button diamond: auto | nintendo | xbox (amendment 5). */
    const val FACE_LAYOUT = "pad_face_layout"
    /** Version of the stored role defaults, see [PadConfig.migrateRolesFromV1]. */
    const val ROLES_VERSION = "pad_roles_version"
    const val STICK_SOURCE = "pad_stick_source"
    const val LEFT_SELECT = "pad_left_select_threshold"      // percent
    const val LEFT_RELEASE = "pad_left_release_threshold"    // percent
    const val FLICK = "pad_flick_threshold"                  // percent
    const val FLICK_RETURN = "pad_flick_return_threshold"    // percent
    const val TRIGGER = "pad_trigger_threshold"              // percent
    const val REPEAT_DELAY = "pad_repeat_delay_ms"
    const val REPEAT_INTERVAL = "pad_repeat_interval_ms"
    const val ROLE_PREFIX = "pad_role_"
    /** 「使うモード」 / "Modes" (amendment 12): the text modes L3 / R3 step through (a Select tap until v7), as a string set of [MODE_VALUES]. */
    const val MODES = "pad_modes"
    /**
     * 「かなの右スティック」 / "Kana right stick" (amendment 15), removed by amendment 18: the pentagon
     * is the only kana right stick. A stored value (also "cross") is dropped by [migrateRoles] and
     * never read: it reads as the pentagon.
     */
    const val KANA_STICK = "pad_kana_stick"
    /** v15: the full guide's opacity in percent (20–100, 100 = opaque), set by the slider at its left edge. */
    const val GUIDE_OPACITY = "pad_guide_opacity"

    /** Stored value of each text mode (same as the `pad_modes_values` array). */
    val MODE_VALUES: Map<PadMode, String> = linkedMapOf(PadMode.KANA to "kana", PadMode.ENGLISH to "english", PadMode.NUMBER to "number")

    /** Buttons whose role can be changed in settings (the roles apply in every mode). */
    val REMAPPABLE: List<PadButton> = listOf(
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y,
        PadButton.L1, PadButton.R1, PadButton.L2, PadButton.R2,
        PadButton.L3, PadButton.R3, PadButton.START, PadButton.SELECT,
    )

    fun roleKey(button: PadButton): String = ROLE_PREFIX + button.name.lowercase()

    fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** Amendment 19: always true; the removed 「コントローラーで文字を打つ」 switch's stored value is ignored. */
    @Suppress("UNUSED_PARAMETER")
    fun isEnabled(context: Context): Boolean = TiltBoyPolicy.PAD_ENABLED
    fun showGuide(context: Context): Boolean = guide(prefs(context))
    fun fullGuide(context: Context): Boolean = guide(prefs(context))

    /** The one guide switch (amendment 19), migrated from the two old ones on first read. */
    fun guide(p: SharedPreferences): Boolean {
        migrateGuide(p)
        return p.getBoolean(GUIDE, true)
    }

    /**
     * Amendment 19: the first read after the update stores [GUIDE] = on if either the old
     * 「キーボード上にコントローラーの案内を表示」 ([SHOW_GUIDE]) or 「…大きな案内を出す」 ([FULL_GUIDE])
     * was on (both default on), and puts both old keys to that value.
     */
    fun migrateGuide(p: SharedPreferences) {
        if (p.contains(GUIDE)) return
        setGuide(p, p.getBoolean(SHOW_GUIDE, true) || p.getBoolean(FULL_GUIDE, true))
    }

    /** The guide switch: on sets both old keys on, off sets both off. */
    fun setGuide(p: SharedPreferences, on: Boolean) {
        p.edit().putBoolean(GUIDE, on).putBoolean(SHOW_GUIDE, on).putBoolean(FULL_GUIDE, on).apply()
    }

    /** v15: the full guide's opacity (1 = opaque, at least [GuideMap.Opacity.MIN]). */
    fun guideOpacity(p: SharedPreferences): Float =
        com.kazumaproject.pad_input.GuideMap.Opacity.fromPercent(p.getInt(GUIDE_OPACITY, 100))

    fun setGuideOpacity(p: SharedPreferences, opacity: Float) {
        p.edit().putInt(GUIDE_OPACITY, com.kazumaproject.pad_input.GuideMap.Opacity.toPercent(opacity)).apply()
    }

    /** The chosen arrangement, or null for 自動 (then the connected controller decides, see PadController). */
    fun faceLayoutChoice(p: SharedPreferences): FaceLayout? = FaceLayout.byName(p.getString(FACE_LAYOUT, "auto"))

    /**
     * First start of a newer build: buttons still on the default role of the stored version
     * (v1: L1 = SHIFT, L2 = 前の候補, R1 = モード, Select = 隠す, R3 = 絵文字, Start = なし) move
     * to the current defaults (amendment 7); anything the user changed stays. Amendment 16: a map
     * still equal to the v4 defaults as a whole moves to the v5 defaults (L2 濁点, L1 半濁点,
     * R2 や・わ); a customised map is kept. Amendment 18: the v5 / v6 defaults move to v7 (L3 the
     * previous mode, R3 the next mode), and a button on a removed role (the bar, the centre
     * character) gets its v7 default; the removed 十字＋押し込み setting is dropped. Amendment 20: Select's
     * モード (MODE_SWITCH) becomes タッチ (TOUCH_KEYBOARD), and a stored 絵文字 mode is dropped. Then records the version.
     */
    fun migrateRoles(p: SharedPreferences) {
        ensureModes(p)
        dropUnknownModes(p)
        migrateGuide(p)
        if (p.contains(KANA_STICK)) p.edit().remove(KANA_STICK).apply()
        val from = p.getInt(ROLES_VERSION, 1)
        if (from >= PadConfig.ROLES_VERSION) return
        val saved = HashMap<PadButton, PadRole?>()
        for (button in REMAPPABLE) {
            saved[button] = PadRole.byName(p.getString(roleKey(button), null))
        }
        val editor = p.edit()
        for ((button, role) in PadConfig.migrateRoles(saved, from)) editor.putString(roleKey(button), role.name)
        editor.putInt(ROLES_VERSION, PadConfig.ROLES_VERSION).apply()
    }

    /**
     * First run (amendment 12): store the default modes from the system language, all three for
     * Japanese, 英字 and 数字 otherwise. An install that already had Sumire Pad settings (stored
     * roles or their version) keeps all three. Must run before [migrateRoles] records a version.
     */
    fun ensureModes(p: SharedPreferences, systemLanguage: String = PadText.systemLanguage()) {
        if (p.contains(MODES)) return
        val existing = p.contains(ROLES_VERSION) || p.all.keys.any { it.startsWith(ROLE_PREFIX) || it == STICK_SOURCE }
        val modes = PadConfig.defaultModes(systemLanguage, existing)
        p.edit().putStringSet(MODES, modes.mapNotNull { MODE_VALUES[it] }.toSet()).apply()
    }

    /**
     * Amendment 20: 「使うモード」 offers only かな, 英字 and 数字. A stored set holding anything else
     * (絵文字) loses it; if nothing is left, all three.
     */
    fun dropUnknownModes(p: SharedPreferences) {
        val stored = p.getStringSet(MODES, null) ?: return
        val known = stored.filter { it in MODE_VALUES.values }.toSet()
        if (known.size == stored.size) return
        p.edit().putStringSet(MODES, known.ifEmpty { MODE_VALUES.values.toSet() }).apply()
    }

    /** The enabled text modes in the fixed order かな → 英字 → 数字 (never empty). */
    fun modes(p: SharedPreferences): List<PadMode> {
        val stored = p.getStringSet(MODES, null) ?: return PadConfig.ALL_TEXT_MODES
        return MODE_VALUES.filterValues { it in stored }.keys.toList().ifEmpty { PadConfig.ALL_TEXT_MODES }
    }

    fun config(context: Context): PadConfig = config(prefs(context), PadText.words(context))

    fun config(p: SharedPreferences, words: PadWords = PadWords.KEYS): PadConfig {
        migrateRoles(p)
        val defaults = PadConfig()
        fun pct(key: String, default: Float): Float =
            (p.getInt(key, (default * 100).toInt()).coerceIn(5, 95)) / 100f
        val roles = PadConfig.DEFAULT_ROLES.toMutableMap()
        for (button in REMAPPABLE) {
            val role = PadRole.byName(p.getString(roleKey(button), null)) ?: continue
            roles[button] = role
        }
        val source = when (p.getString(STICK_SOURCE, "auto")) {
            "sticks" -> StickSource.STICKS
            "dpad" -> StickSource.DPAD_FALLBACK
            else -> StickSource.AUTO
        }
        val leftSelect = pct(LEFT_SELECT, defaults.leftSelectThreshold)
        val flick = pct(FLICK, defaults.flickThreshold)
        return PadConfig(
            leftSelectThreshold = leftSelect,
            leftReleaseThreshold = pct(LEFT_RELEASE, defaults.leftReleaseThreshold).coerceAtMost(leftSelect),
            flickThreshold = flick,
            flickReturnThreshold = pct(FLICK_RETURN, defaults.flickReturnThreshold).coerceAtMost(flick),
            triggerThreshold = pct(TRIGGER, defaults.triggerThreshold),
            repeatDelayMs = p.getInt(REPEAT_DELAY, defaults.repeatDelayMs.toInt()).coerceIn(100, 2000).toLong(),
            repeatIntervalMs = p.getInt(REPEAT_INTERVAL, defaults.repeatIntervalMs.toInt()).coerceIn(20, 500).toLong(),
            stickSource = source,
            roles = roles,
            faceLayout = faceLayoutChoice(p) ?: FaceLayout.XBOX,
            modes = modes(p),
            words = words,
        )
    }
}
