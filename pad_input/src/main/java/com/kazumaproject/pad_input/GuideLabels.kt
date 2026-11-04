package com.kazumaproject.pad_input

/**
 * Labels for the controller-map guide (amendment 5), generated from the live [PadConfig] roles
 * so that a remap in settings shows up the next time the guide draws. Mode-dependent since
 * amendments 6/7: a role that has nothing to do in the current mode has no label ([NONE]; the
 * map draws just the dimmed button name). One short word each (amendment 9); 決定 / 変換 for
 * A / Y (amendment 11). The words come from [PadConfig.words] (amendment 12: English or
 * Japanese resources), never from literals here.
 */
object GuideLabels {
    /** "No function here": the map shows only the dimmed button name, the legend leaves it out. */
    const val NONE = ""

    /** The buttons the map shows a function for. */
    val MAPPED: List<PadButton> = listOf(
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y,
        PadButton.L1, PadButton.L2, PadButton.R1, PadButton.R2,
        PadButton.L3, PadButton.R3, PadButton.START, PadButton.SELECT,
    )

    /** The bar's word in a mode (わをん 記号 / Symbols / Next 32): the D-pad fallback's L1 bar, the emoji wheel's page 2. */
    fun barWord(mode: PadMode, w: PadWords): String = when (mode) {
        PadMode.KANA -> w[PadWord.BAR_KANA]
        PadMode.ENGLISH -> w[PadWord.BAR_ENGLISH]
        PadMode.NUMBER -> w[PadWord.BAR_NUMBER]
        PadMode.EMOJI -> w[PadWord.BAR_EMOJI]
        PadMode.EMOJI_LIST -> NONE
    }

    /** The word for going to a text mode (amendment 18: 「かなへ」「英字へ」「数字へ」 / "To kana", "To ABC", "To 123"). */
    fun toModeWord(mode: PadMode, w: PadWords): String = when (mode) {
        PadMode.KANA -> w[PadWord.TO_KANA]
        PadMode.ENGLISH -> w[PadWord.TO_ENGLISH]
        PadMode.NUMBER -> w[PadWord.TO_NUMBER]
        else -> NONE
    }

    /**
     * Short label of a role in a mode, or [NONE] when the role does nothing there. [modes] are the
     * enabled text modes: L3 / R3 (amendment 18) name the mode one step back / forward through them.
     */
    fun roleLabel(role: PadRole, mode: PadMode, w: PadWords, modes: List<PadMode> = PadConfig.ALL_TEXT_MODES): String = when (role) {
        // Removed in v7 (amendment 18) and v8 (amendment 20, MODE_SWITCH): read as NONE.
        PadRole.TAP_CENTER, PadRole.BAR, PadRole.MODE_SWITCH -> NONE
        PadRole.MODE_PREVIOUS, PadRole.MODE_NEXT -> when (mode) {
            // In the emoji wheel L3 / R3 keep their v6 jobs: the next 32 emoji, the petal's first emoji.
            PadMode.EMOJI -> if (role == PadRole.MODE_PREVIOUS) w[PadWord.BAR_EMOJI] else w[PadWord.TAP_EMOJI]
            PadMode.EMOJI_LIST -> NONE
            else -> PadConfig(modes = modes).stepMode(mode, forward = role == PadRole.MODE_NEXT)?.let { toModeWord(it, w) } ?: NONE
        }
        PadRole.CONFIRM -> if (mode == PadMode.EMOJI_LIST) w[PadWord.INSERT] else w[PadWord.CONFIRM]
        PadRole.BACKSPACE -> w[PadWord.BACKSPACE]
        PadRole.SPACE -> w[PadWord.SPACE]
        PadRole.CONVERT_NEXT -> when (mode) {
            PadMode.ENGLISH, PadMode.NUMBER -> w[PadWord.NEXT]
            else -> w[PadWord.CONVERT]
        }
        PadRole.PREVIOUS_CANDIDATE -> w[PadWord.PREVIOUS]
        PadRole.HIDE_KEYBOARD -> w[PadWord.HIDE]
        // Amendment 20: Select 「タッチ」 / "Touch" (the touch keyboard), in every mode.
        PadRole.TOUCH_KEYBOARD -> w[PadWord.TOUCH]
        PadRole.EMOJI_JUMP -> when (mode) {
            PadMode.EMOJI -> w[PadWord.LIST]
            PadMode.EMOJI_LIST -> w[PadWord.BACK]
            else -> w[PadWord.EMOJI]
        }
        PadRole.HOLD_DAKUTEN -> when (mode) { PadMode.KANA -> w[PadWord.DAKUTEN]; PadMode.ENGLISH -> w[PadWord.UPPERCASE]; else -> NONE }
        // Amendment 22: the 半濁点 / 小文字 roles read 「123」 / "123" in 英字 (数字's layout while held).
        PadRole.HOLD_HANDAKUTEN -> when (mode) { PadMode.KANA -> w[PadWord.HANDAKUTEN]; PadMode.ENGLISH -> w[PadWord.NUMBER_HOLD]; else -> NONE }
        PadRole.HOLD_SMALL -> when (mode) { PadMode.KANA -> w[PadWord.SMALL]; PadMode.ENGLISH -> w[PadWord.NUMBER_HOLD]; else -> NONE }
        PadRole.HOLD_SMALL_OR_HANDAKUTEN -> when (mode) { PadMode.KANA -> w[PadWord.SMALL_OR_HANDAKUTEN]; PadMode.ENGLISH -> w[PadWord.NUMBER_HOLD]; else -> NONE }
        PadRole.HOLD_UPPERCASE -> if (mode == PadMode.ENGLISH) w[PadWord.UPPERCASE] else NONE
        // Amendment 18: the layer in every text mode; amendment 21: 記号 / "Symbols" in かな too.
        PadRole.YA_WA_LAYER -> when (mode) {
            PadMode.KANA, PadMode.ENGLISH, PadMode.NUMBER -> w[PadWord.SYMBOLS]
            else -> NONE
        }
        PadRole.NONE -> NONE
    }

    /** Everything the map prints besides the characters themselves. */
    data class Labels(
        /** Function of each button in [MAPPED] ([NONE] = no function here). */
        val buttons: Map<PadButton, String>,
        /** The D-pad's word. */
        val dpad: String,
        /** What a centre-character button reads as in this mode (the map shows the character instead). */
        val tap: String = NONE,
        /**
         * The buttons that type the centre character: in the D-pad fallback A, in the emoji wheel
         * the MODE_NEXT button (R3, amendment 18 keeps its v6 job there). The map draws that
         * character on them instead of [tap]. None in the text modes with sticks (amendment 18).
         */
        val tapButtons: Set<PadButton> = emptySet(),
    )

    /**
     * [numberLayout] (amendment 22): 英字 is showing 数字's layout (L1 / R1 held): the upper-case roles
     * do nothing then, so their pill (L2) is empty and dimmed.
     */
    fun build(config: PadConfig, mode: PadMode, fallback: Boolean, barActive: Boolean, numberLayout: Boolean = false): Labels = when {
        mode == PadMode.EMOJI_LIST -> emojiList(config, fallback)
        fallback -> fallbackLabels(mode, barActive, config.words)
        else -> stickLabels(config, mode, numberLayout)
    }

    /** Legend for the compact emoji band: buttons grouped by identical function, no-function buttons left out. */
    fun legend(labels: Labels): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (b in MAPPED) {
            val f = labels.buttons[b] ?: continue
            if (f == NONE || !seen.add(f)) continue
            val buttons = MAPPED.filter { labels.buttons[it] == f }.joinToString(" / ") { it.label }
            out += "$buttons $f"
        }
        return out
    }

    private fun stickLabels(config: PadConfig, mode: PadMode, numberLayout: Boolean = false): Labels {
        val w = config.words
        val modes = config.textModes()
        val buttons = MAPPED.associateWith { b ->
            val role = config.roleOf(b)
            // Amendment 22: no 大文字 while 数字's layout is held in 英字.
            if (numberLayout && mode == PadMode.ENGLISH && (role == PadRole.HOLD_DAKUTEN || role == PadRole.HOLD_UPPERCASE)) NONE
            else roleLabel(role, mode, w, modes)
        }
        // Amendment 18: only the emoji wheel has a centre character on a button (R3's first emoji).
        val tapButtons = if (mode == PadMode.EMOJI) MAPPED.filter { config.roleOf(it) == PadRole.MODE_NEXT }.toSet() else emptySet()
        return Labels(buttons = buttons, dpad = w[PadWord.CURSOR], tap = if (mode == PadMode.EMOJI) w[PadWord.TAP_EMOJI] else NONE, tapButtons = tapButtons)
    }

    /**
     * Sumire's full emoji keyboard, driven by the D-pad (amendment 20: and the left stick). With
     * sticks X, Y, L3 and R3 do nothing there; Select (TOUCH_KEYBOARD) closes it and shows the touch keys.
     */
    private fun emojiList(config: PadConfig, fallback: Boolean): Labels {
        val w = config.words
        val buttons = HashMap<PadButton, String>()
        for (b in MAPPED) buttons[b] = NONE
        buttons[PadButton.A] = w[PadWord.INSERT]
        buttons[PadButton.B] = w[PadWord.BACK]
        buttons[PadButton.L2] = w[PadWord.PREVIOUS_CATEGORY]
        buttons[PadButton.R2] = w[PadWord.NEXT_CATEGORY]
        if (fallback) {
            buttons[PadButton.START] = w[PadWord.BACK]; buttons[PadButton.R3] = w[PadWord.BACK]
            buttons[PadButton.R1] = w[PadWord.TO_TEXT]; buttons[PadButton.SELECT] = w[PadWord.HIDE]
        } else {
            for (b in MAPPED) {
                if (b == PadButton.A || b == PadButton.B || b == PadButton.L2 || b == PadButton.R2) continue
                if (b == PadButton.X || b == PadButton.Y || b == PadButton.L3 || b == PadButton.R3) continue
                when (config.roleOf(b)) {
                    PadRole.CONFIRM -> buttons[b] = w[PadWord.INSERT]
                    PadRole.EMOJI_JUMP -> buttons[b] = w[PadWord.BACK]
                    PadRole.TOUCH_KEYBOARD -> buttons[b] = w[PadWord.TOUCH]
                    PadRole.HIDE_KEYBOARD -> buttons[b] = w[PadWord.HIDE]
                    else -> {}
                }
            }
        }
        return Labels(buttons = buttons, dpad = w[PadWord.MOVE])
    }

    /** The fixed D-pad table (no stick motion), see PadInputEngine.onPressFallback. L1 held = the bar. */
    private fun fallbackLabels(mode: PadMode, barActive: Boolean, w: PadWords): Labels {
        val buttons = HashMap<PadButton, String>()
        when (mode) {
            PadMode.KANA -> {
                buttons[PadButton.A] = w[PadWord.VOWEL_A]; buttons[PadButton.X] = w[PadWord.VOWEL_I]
                buttons[PadButton.Y] = w[PadWord.VOWEL_U]; buttons[PadButton.B] = w[PadWord.VOWEL_E]
                buttons[PadButton.L2] = w[PadWord.VOWEL_O]
                buttons[PadButton.R2] = if (barActive) w[PadWord.SPACE] else w[PadWord.CONVERT]
            }
            PadMode.ENGLISH, PadMode.NUMBER -> {
                // Amendment 14: no centre character in 英字 / 数字; A types a space there.
                buttons[PadButton.A] = w[PadWord.SPACE]; buttons[PadButton.X] = w[PadWord.CHAR_LEFT]
                buttons[PadButton.Y] = w[PadWord.CHAR_UP]; buttons[PadButton.B] = w[PadWord.CHAR_RIGHT]
                buttons[PadButton.L2] = w[PadWord.CHAR_DOWN]
                buttons[PadButton.R2] = if (barActive) w[PadWord.SPACE] else w[PadWord.NEXT]
            }
            else -> {
                buttons[PadButton.X] = w[PadWord.EMOJI_LEFT]; buttons[PadButton.Y] = w[PadWord.EMOJI_UP]
                buttons[PadButton.B] = w[PadWord.EMOJI_RIGHT]; buttons[PadButton.A] = w[PadWord.EMOJI_DOWN]
                buttons[PadButton.L2] = w[PadWord.SPACE]
                buttons[PadButton.R2] = w[PadWord.CONVERT]
            }
        }
        buttons[PadButton.L1] = barWord(mode, w)
        buttons[PadButton.R1] = if (mode.isEmoji) w[PadWord.TO_TEXT] else w[PadWord.MODE]
        buttons[PadButton.START] = if (mode == PadMode.EMOJI) w[PadWord.LIST] else w[PadWord.CONFIRM]
        buttons[PadButton.SELECT] = if (barActive) w[PadWord.HIDE] else w[PadWord.BACKSPACE]
        buttons[PadButton.R3] = if (mode.isEmoji) w[PadWord.TO_TEXT] else w[PadWord.EMOJI]
        buttons[PadButton.L3] = NONE
        val tenkey = mode != PadMode.EMOJI
        // A types the centre character here: the map shows that character on A, as with sticks.
        return Labels(
            buttons = buttons, dpad = if (tenkey) w[PadWord.KEY] else w[PadWord.EMOJI],
            tap = if (tenkey) buttons.getValue(PadButton.A) else NONE,
            tapButtons = if (tenkey) setOf(PadButton.A) else emptySet(),
        )
    }
}
