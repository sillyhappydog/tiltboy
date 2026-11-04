package com.kazumaproject.pad_input

/**
 * Tunable values. Stick values are normalised to -1..1 (Android `MotionEvent.getAxisValue`).
 * Defaults were chosen for a typical handheld stick: they are tunable from settings.
 */
data class PadConfig(
    /** Left stick: radius at which a key is selected (leaving neutral). */
    val leftSelectThreshold: Float = 0.45f,
    /** Left stick: radius below which the stick is neutral again (hysteresis, must be <= select). */
    val leftReleaseThreshold: Float = 0.30f,
    /** Right stick: radius that counts as "flicked". */
    val flickThreshold: Float = 0.60f,
    /** Right stick: radius at which the flick is finished and the character is entered. */
    val flickReturnThreshold: Float = 0.30f,
    /** Analog triggers (L2/R2 as axes): pressed above this, released below 0.6 × this. */
    val triggerThreshold: Float = 0.50f,
    /** Backspace held: first repeat after this many ms ... */
    val repeatDelayMs: Long = 400L,
    /** ... then one repeat every this many ms. */
    val repeatIntervalMs: Long = 60L,
    val stickSource: StickSource = StickSource.AUTO,
    /** Button roles (every mode). Hold roles act while held. */
    val roles: Map<PadButton, PadRole> = DEFAULT_ROLES,
    /** A TOUCH_KEYBOARD button (Select; until v7 MODE_SWITCH) held this long hides the keyboard instead (amendment 7). */
    val longPressMs: Long = 500L,
    /** Where the printed face buttons sit on the device: decides where the guide draws each letter (roles stay on the key codes). */
    val faceLayout: FaceLayout = FaceLayout.XBOX,
    /**
     * The text modes L3 / R3 step through (amendments 12 and 18; a Select tap cycled them until v7),
     * in this order (setting 「使うモード」 / "Modes"). Emoji stays on Start. Empty is treated as all three.
     */
    val modes: List<PadMode> = ALL_TEXT_MODES,
    /** The guide's words in the UI language (amendment 12); [PadWords.KEYS] when no resources are loaded. */
    val words: PadWords = PadWords.KEYS,
) {
    /** The role of a button; a role removed in v7 ([PadRole.isRemoved]) reads as [PadRole.NONE]. */
    fun roleOf(button: PadButton): PadRole = roles[button]?.takeUnless { it.isRemoved } ?: PadRole.NONE

    /** The enabled text modes in the fixed order かな → 英字 → 数字 (never empty). */
    fun textModes(): List<PadMode> = ALL_TEXT_MODES.filter { it in modes }.ifEmpty { ALL_TEXT_MODES }

    /**
     * Amendment 18: the text mode one step from [from] through [textModes], with wrap-around
     * ([forward] = R3 / MODE_NEXT, else L3 / MODE_PREVIOUS). Null when there is nothing to step to
     * (one text mode). A mode that is not enabled steps from the first enabled one.
     */
    fun stepMode(from: PadMode, forward: Boolean): PadMode? {
        val modes = textModes()
        if (modes.size <= 1 && from in modes) return null
        val i = modes.indexOf(from)
        if (i < 0) return modes.first()
        return modes[(i + (if (forward) 1 else modes.size - 1)) % modes.size]
    }

    /** The button that currently has a role, or null. */
    fun buttonFor(role: PadRole): PadButton? = roles.entries.firstOrNull { it.value == role }?.key

    companion object {
        val ALL_TEXT_MODES: List<PadMode> = listOf(PadMode.KANA, PadMode.ENGLISH, PadMode.NUMBER)

        /**
         * The modes a fresh install starts with (amendment 12): all three for a Japanese system
         * language, 英字 and 数字 for anything else. An install that already had Sumire Pad
         * settings keeps all three.
         */
        fun defaultModes(systemLanguage: String, existingInstall: Boolean): List<PadMode> = when {
            existingInstall -> ALL_TEXT_MODES
            systemLanguage.lowercase().startsWith("ja") -> ALL_TEXT_MODES
            else -> listOf(PadMode.ENGLISH, PadMode.NUMBER)
        }

        /**
         * v8 defaults (amendment 20): A = 決定, Y = 変換 (英字: 候補), B = 消す, X = 空白,
         * L2 held = 濁点 (英字: 大文字), L1 held = 半濁点, R2 held = the layer (かな や/わ, 英字 / 数字
         * 記号: the left stick types it), R1 held = 小文字, L3 = the previous text mode, R3 = the next
         * text mode, Select = タッチ (the touch keyboard; long press: 隠す), Start = 絵文字 (the list).
         */
        val DEFAULT_ROLES: Map<PadButton, PadRole> = mapOf(
            PadButton.A to PadRole.CONFIRM,
            PadButton.Y to PadRole.CONVERT_NEXT,
            PadButton.B to PadRole.BACKSPACE,
            PadButton.X to PadRole.SPACE,
            PadButton.R2 to PadRole.YA_WA_LAYER,
            PadButton.L2 to PadRole.HOLD_DAKUTEN,
            PadButton.R1 to PadRole.HOLD_SMALL,
            PadButton.L1 to PadRole.HOLD_HANDAKUTEN,
            PadButton.SELECT to PadRole.TOUCH_KEYBOARD,
            PadButton.START to PadRole.EMOJI_JUMP,
            PadButton.L3 to PadRole.MODE_PREVIOUS,
            PadButton.R3 to PadRole.MODE_NEXT,
        )

        /** The v7 default map (amendment 18, roles version 6): as v8 except Select = モード (MODE_SWITCH, removed in v8). */
        val V7_DEFAULT_ROLES: Map<PadButton, PadRole> = DEFAULT_ROLES + (PadButton.SELECT to PadRole.MODE_SWITCH)

        /**
         * The v5 / v6 default map (amendments 16 and 17, roles version 5): as v7 except L3 = the bar
         * and R3 = the centre character (both removed in v7).
         */
        val V5_DEFAULT_ROLES: Map<PadButton, PadRole> = V7_DEFAULT_ROLES + mapOf(
            PadButton.L3 to PadRole.BAR,
            PadButton.R3 to PadRole.TAP_CENTER,
        )

        /**
         * The whole v4 default map (amendments 10 and 11, used by roles version 4 = apk v2–v4):
         * R2 = 変換, L1 held = 濁点, L2 held = 半濁点, everything else as today.
         */
        val V4_DEFAULT_ROLES: Map<PadButton, PadRole> = mapOf(
            PadButton.A to PadRole.CONFIRM,
            PadButton.Y to PadRole.CONVERT_NEXT,
            PadButton.B to PadRole.BACKSPACE,
            PadButton.X to PadRole.SPACE,
            PadButton.R2 to PadRole.CONVERT_NEXT,
            PadButton.L2 to PadRole.HOLD_HANDAKUTEN,
            PadButton.R1 to PadRole.HOLD_SMALL,
            PadButton.L1 to PadRole.HOLD_DAKUTEN,
            PadButton.SELECT to PadRole.MODE_SWITCH,
            PadButton.START to PadRole.EMOJI_JUMP,
            PadButton.L3 to PadRole.BAR,
            PadButton.R3 to PadRole.TAP_CENTER,
        )

        /** The buttons whose default changed from v4 to v5 (L1, L2, R2). */
        val V5_CHANGED: Set<PadButton> = V5_DEFAULT_ROLES.keys.filter { V5_DEFAULT_ROLES[it] != V4_DEFAULT_ROLES[it] }.toSet()

        /**
         * Defaults of builds before roles version 4, per stored version, for the buttons whose
         * default changed on the way to the v4 map. A button is listed under every version whose
         * default differs from v4's, so a migration from any version finds it.
         */
        val PREVIOUS_DEFAULTS: Map<Int, Map<PadButton, PadRole>> = mapOf(
            // v1 (apk/sumire-pad-v1.apk)
            1 to mapOf(
                PadButton.L1 to PadRole.BAR, // stored as SHIFT (the bottom row)
                PadButton.L2 to PadRole.PREVIOUS_CANDIDATE,
                PadButton.R1 to PadRole.MODE_SWITCH,
                PadButton.SELECT to PadRole.HIDE_KEYBOARD,
                PadButton.R3 to PadRole.EMOJI_JUMP,
                PadButton.START to PadRole.NONE,
                PadButton.A to PadRole.TAP_CENTER,
                PadButton.Y to PadRole.CONFIRM,
                PadButton.L3 to PadRole.NONE,
            ),
            // v2 intermediate builds (amendments 4 and 5)
            2 to mapOf(
                PadButton.L1 to PadRole.BAR,
                PadButton.L2 to PadRole.PREVIOUS_CANDIDATE,
                PadButton.R1 to PadRole.MODE_SWITCH,
                PadButton.SELECT to PadRole.HIDE_KEYBOARD,
                PadButton.A to PadRole.TAP_CENTER,
                PadButton.Y to PadRole.CONFIRM,
                PadButton.L3 to PadRole.NONE,
            ),
            // v2 amendments 7–9 (stored HOLD_BOTTOM_ROW on L2)
            3 to mapOf(
                PadButton.L2 to PadRole.BAR,
                PadButton.R1 to PadRole.HOLD_SMALL_OR_HANDAKUTEN,
                PadButton.A to PadRole.TAP_CENTER,
                PadButton.Y to PadRole.CONFIRM,
                PadButton.L3 to PadRole.NONE,
            ),
        )

        /** Current version of the stored role defaults (see [migrateRoles]): 6 = amendment 18 (v7), 7 = amendment 20 (v8). */
        const val ROLES_VERSION = 7

        /**
         * Upgrade stored roles from [fromVersion]. Returns the roles to store.
         *
         * - Versions 1–3 → 4, per button (amendments 4, 7, 10, 11): a button still on the default of
         *   its version (or never stored) moves to the v4 default; anything the user changed stays.
         * - 4 → 5 (amendment 16), the whole map: an install whose roles (after the step above) still
         *   equal the v4 defaults moves to the v5 defaults (L2 濁点, L1 半濁点, R2 や・わ). A map the
         *   user customised in any way is kept as it is.
         * - 5 → 6 (amendment 18, v7), the whole map again: roles that still equal the v5 / v6
         *   defaults move to the v7 defaults (L3 the previous mode, R3 the next mode). A customised
         *   map is kept, except that a button set to a removed role ([PadRole.isRemoved]: the bar,
         *   the centre character) gets its v7 default. A v7 map is left alone.
         * - 6 → 7 (amendment 20, v8), per button: Select on モード (MODE_SWITCH, the v7 default, stored
         *   or not) gets the touch-keyboard role ([PadRole.TOUCH_KEYBOARD]); another button on
         *   MODE_SWITCH gets the next-mode role ([PadRole.MODE_NEXT], the closest one left). Every other
         *   role stays.
         */
        fun migrateRoles(saved: Map<PadButton, PadRole?>, fromVersion: Int): Map<PadButton, PadRole> {
            val out = LinkedHashMap<PadButton, PadRole>()
            if (fromVersion < 4) {
                val old = PREVIOUS_DEFAULTS.filterKeys { it >= fromVersion }
                val changed = old.values.flatMap { it.keys }.toSet()
                for (button in changed) {
                    val stored = saved[button]
                    val oldDefaults = old.mapNotNull { it.value[button] }
                    if (stored == null || stored in oldDefaults) out[button] = V4_DEFAULT_ROLES.getValue(button)
                }
            }
            if (fromVersion < 5) {
                val effective = V4_DEFAULT_ROLES.mapValues { (button, v4) -> out[button] ?: saved[button] ?: v4 }
                if (effective == V4_DEFAULT_ROLES) for (button in V5_CHANGED) out[button] = V5_DEFAULT_ROLES.getValue(button)
            }
            if (fromVersion < 6) {
                val effective = V5_DEFAULT_ROLES.mapValues { (button, v5) -> out[button] ?: saved[button] ?: v5 }
                if (effective == V5_DEFAULT_ROLES) {
                    for ((button, role) in V7_DEFAULT_ROLES) if (role != V5_DEFAULT_ROLES[button]) out[button] = role
                } else {
                    for ((button, role) in effective) if (role == PadRole.TAP_CENTER || role == PadRole.BAR) out[button] = V7_DEFAULT_ROLES[button] ?: PadRole.NONE
                }
            }
            if (fromVersion < 7) {
                val effective = V7_DEFAULT_ROLES.mapValues { (button, v7) -> out[button] ?: saved[button] ?: v7 }
                for ((button, role) in effective) {
                    if (role == PadRole.MODE_SWITCH) out[button] = if (button == PadButton.SELECT) PadRole.TOUCH_KEYBOARD else PadRole.MODE_NEXT
                }
            }
            return out
        }

        /** v1 → current, kept for the tests and callers of the first migration. */
        fun migrateRolesFromV1(saved: Map<PadButton, PadRole?>): Map<PadButton, PadRole> = migrateRoles(saved, 1)
    }
}
