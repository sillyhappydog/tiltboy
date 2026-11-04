package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode

/** Physical controller buttons, independent of Android key codes. */
enum class PadButton {
    A, B, X, Y,
    L1, R1, L2, R2, L3, R3,
    START, SELECT,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT;

    val isDpad: Boolean get() = this == DPAD_UP || this == DPAD_DOWN || this == DPAD_LEFT || this == DPAD_RIGHT
    val isFace: Boolean get() = this == A || this == B || this == X || this == Y

    /** Short label as printed on most pads (for guides and settings). */
    val label: String
        get() = when (this) {
            START -> "Start"
            SELECT -> "Select"
            DPAD_UP -> "D-pad ↑"; DPAD_DOWN -> "D-pad ↓"; DPAD_LEFT -> "D-pad ←"; DPAD_RIGHT -> "D-pad →"
            else -> name
        }
}

/** Four flick directions. Same meaning as a phone flick (left = い段, up = う段, ...). */
enum class Dir4 {
    LEFT, UP, RIGHT, DOWN;

    val gesture: GestureType
        get() = when (this) {
            LEFT -> GestureType.FlickLeft
            UP -> GestureType.FlickTop
            RIGHT -> GestureType.FlickRight
            DOWN -> GestureType.FlickBottom
        }
}

/** Nine stick positions as the 3x3 grid. `col`/`row` are 0..2; CENTER is the neutral stick. */
enum class Sector9(val col: Int, val row: Int) {
    UP_LEFT(0, 0), UP(1, 0), UP_RIGHT(2, 0),
    LEFT(0, 1), CENTER(1, 1), RIGHT(2, 1),
    DOWN_LEFT(0, 2), DOWN(1, 2), DOWN_RIGHT(2, 2);

    val index: Int get() = row * 3 + col

    /** Daisywheel petal index: 0 = up, clockwise to 7 = up-left. null when neutral. */
    val petal: Int?
        get() = when (this) {
            UP -> 0; UP_RIGHT -> 1; RIGHT -> 2; DOWN_RIGHT -> 3
            DOWN -> 4; DOWN_LEFT -> 5; LEFT -> 6; UP_LEFT -> 7
            CENTER -> null
        }

    companion object {
        fun fromIndex(index: Int): Sector9 = entries.first { it.index == index }
    }
}

/** The engine's own mode. The first three mirror Sumire's InputMode; the emoji modes are ours. */
enum class PadMode {
    KANA, ENGLISH, NUMBER,
    /**
     * Recent-emoji Daisywheel. Amendment 20: only in the D-pad fallback (R3); with sticks Start opens
     * [EMOJI_LIST] directly and no mode goes to the wheel.
     */
    EMOJI,
    /** Sumire's full emoji keyboard, driven by the D-pad (amendment 20: and the left stick). */
    EMOJI_LIST;

    val isEmoji: Boolean get() = this == EMOJI || this == EMOJI_LIST

    /** The Sumire input mode this pad mode shows, or null for the emoji modes. */
    val inputMode: InputMode?
        get() = when (this) {
            KANA -> InputMode.ModeJapanese
            ENGLISH -> InputMode.ModeEnglish
            NUMBER -> InputMode.ModeNumber
            EMOJI, EMOJI_LIST -> null
        }

    companion object {
        fun from(mode: InputMode): PadMode = when (mode) {
            InputMode.ModeJapanese -> KANA
            InputMode.ModeEnglish -> ENGLISH
            InputMode.ModeNumber -> NUMBER
        }
    }
}

/** Commands for Sumire's full emoji keyboard (EMOJI_LIST mode). */
enum class EmojiKeyboardCommand { OPEN, CLOSE, MOVE_LEFT, MOVE_UP, MOVE_RIGHT, MOVE_DOWN, NEXT_TAB, PREVIOUS_TAB, SELECT }

/**
 * What a button means. The user can remap every button in settings; the same map applies in
 * every mode (a role that has nothing to do in the current mode does nothing and is shown as なし).
 */
enum class PadRole {
    /**
     * REMOVED in v7 (amendment 18): typed the selected key's centre character (かな あ段 with the
     * pentagon as an undrawn fallback, a space in 英字 / 数字). Kept only so a stored value can be
     * parsed and migrated ([PadConfig.migrateRoles] gives the button its v7 default); never offered
     * in settings, and [PadConfig.roleOf] reads it as [NONE].
     */
    TAP_CENTER,
    /** 決定: 確定 while composing, Enter otherwise (A by default since amendment 11); in the emoji list it inserts the selected emoji. */
    CONFIRM,
    BACKSPACE,
    SPACE,
    /** 変換 / next candidate. */
    CONVERT_NEXT,
    PREVIOUS_CANDIDATE,
    /**
     * REMOVED in v8 (amendment 20): a tap went to the next enabled text mode, held ≥ 500 ms it hid
     * the keyboard (Select by default until v7). L3 / R3 step the modes since v7. Kept only so a
     * stored value can be parsed and migrated ([PadConfig.migrateRoles]: Select's becomes
     * [TOUCH_KEYBOARD], another button's [MODE_NEXT]); never offered, read as [NONE].
     */
    MODE_SWITCH,
    HIDE_KEYBOARD,
    /**
     * Amendment 20 (Select by default): a tap switches between the guide and Sumire's touch keyboard
     * for the current mode ([PadAction.TouchKeyboard]; from the emoji list: close it, back to the text
     * mode, touch keyboard). Held ≥ [PadConfig.longPressMs]: hide the keyboard (as v7's Select).
     */
    TOUCH_KEYBOARD,
    /**
     * Start by default. Amendment 20: opens Sumire's full emoji list from a text mode; in the list it
     * closes it and goes back to the text mode the user came from. (v2–v7: the recent-emoji wheel
     * first, the list on a second press; the wheel stays in the D-pad fallback.)
     */
    EMOJI_JUMP,
    // ---- hold roles (amendments 6 and 7): they change what is typed while the button is held.
    /** Held: 濁点 (か→が …); in 英字 the same button gives upper case. */
    HOLD_DAKUTEN,
    /** Held: 半濁点 (は→ぱ …); amendment 22: in 英字 the 数字 layout ([isNumberHold]). */
    HOLD_HANDAKUTEN,
    /** Held: 小文字 (あ→ぁ, つ→っ, や→ゃ, わ→ゎ …); amendment 22: in 英字 the 数字 layout ([isNumberHold]). */
    HOLD_SMALL,
    /** Held: 小文字 where one exists, else 半濁点 (the は row); amendment 22: in 英字 the 数字 layout. */
    HOLD_SMALL_OR_HANDAKUTEN,
    /**
     * REMOVED in v7 (amendment 18): the L3 bar of 英字 / 数字 (its characters are on the R2 layer
     * now, [YA_WA_LAYER]) and the emoji wheel's page 2 (now [MODE_PREVIOUS]'s job in the wheel).
     * Kept only so stored values (`BAR`, and the older `SHIFT` / `HOLD_BOTTOM_ROW`) can be parsed and
     * migrated; never offered, read as [NONE]. The D-pad fallback keeps its fixed L1 bar.
     */
    BAR,
    /** Held: upper case in 英字. */
    HOLD_UPPERCASE,
    /**
     * The 記号 layer (R2 by default; the name is kept from amendment 16 for stored settings). While
     * held, the left stick types directly with a flick, in every text mode (amendment 18; amendment
     * 21 made かな's 記号 too):
     * かな: ↖ 〜 ↑ 。 ↗ … ← ー → 、 ↙ ？ ↓ ・ ↘ ！ ([KanaGrid.KANA_SYMBOLS]);
     * 英字: ↖ & ↑ ; ↗ * ← ( → ) ↙ = ↓ _ ↘ +; 数字: ↖ { ↑ ^ ↗ } ← [ → ] ↙ \ ↓ | ↘ `.
     * The centre is empty. No role in 絵文字.
     */
    YA_WA_LAYER,
    /**
     * Amendment 18 (L3 by default): the previous enabled text mode ([PadConfig.textModes], with
     * wrap-around; never 絵文字). Fires on release, and only when the clicked stick stayed under
     * [PadConfig.flickReturnThreshold] with no flick armed on it (a hard flick can push a stick in).
     * In the emoji wheel it keeps L3's v6 job: the next 32 emoji (tap: one, held: while held).
     */
    MODE_PREVIOUS,
    /**
     * Amendment 18 (R3 by default): the next enabled text mode, same rules as [MODE_PREVIOUS].
     * In the emoji wheel it keeps R3's v6 job: the selected petal's first emoji.
     */
    MODE_NEXT,
    NONE;

    /** Hold roles act while held and never type by themselves. */
    val isHold: Boolean
        get() = this == HOLD_DAKUTEN || this == HOLD_HANDAKUTEN || this == HOLD_SMALL ||
            this == HOLD_SMALL_OR_HANDAKUTEN || this == HOLD_UPPERCASE || this == YA_WA_LAYER

    /** Hold roles that transform the typed character (as opposed to swapping in the layer). */
    val isTransform: Boolean get() = isHold && this != YA_WA_LAYER

    /** The 小文字 roles: the only transforms that apply on the や/わ key's ring (amendment 21; v5–v8 the や・わ layer). */
    val isSmall: Boolean get() = this == HOLD_SMALL || this == HOLD_SMALL_OR_HANDAKUTEN

    /**
     * Amendment 22: the roles that, held in 英字, switch the grid, the ring and the R2 layer to
     * 数字's (L1 半濁点 and R1 小文字 by default). No new role: the idle kana roles get a 英字 meaning,
     * as [HOLD_DAKUTEN] gives upper case there.
     */
    val isNumberHold: Boolean get() = this == HOLD_HANDAKUTEN || this == HOLD_SMALL || this == HOLD_SMALL_OR_HANDAKUTEN

    /** The two mode-step roles of amendment 18. */
    val isModeStep: Boolean get() = this == MODE_PREVIOUS || this == MODE_NEXT

    /** Roles removed in v7 (amendment 18) and v8 (amendment 20, [MODE_SWITCH]): parsed for the migration only, never assigned. */
    val isRemoved: Boolean get() = this == TAP_CENTER || this == BAR || this == MODE_SWITCH

    companion object {
        /** Parse a stored name; v1/v2 stored `SHIFT`, v2 amendment 7 `HOLD_BOTTOM_ROW`, for what became [BAR]. */
        fun byName(name: String?): PadRole? = when (name) {
            null -> null
            "SHIFT", "HOLD_BOTTOM_ROW" -> BAR
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * What the keyboard should do. The IME service translates these into Sumire's own
 * key handling (the same functions the touch keyboard calls), so conversion,
 * learning and settings behave exactly as with touch input.
 */
sealed class PadAction {
    /**
     * One 12-key (tenkey) key with a tap or flick gesture, exactly as Sumire's
     * `FlickListener.onFlick(gesture, key, char)` would report it.
     * `char` is the resolved character from Sumire's own table, or null for the
     * 「゛゜小」 key (Sumire derives that character from the previous one).
     */
    data class KanaKey(val key: Key, val gesture: GestureType, val char: Char?) : PadAction()

    /** Insert text as typed: a hold-transformed character (が, っ, A) or a bar character with no table key. */
    data class InsertText(val text: String) : PadAction()

    data object ConvertOrNextCandidate : PadAction()
    data object PreviousCandidate : PadAction()
    data object Confirm : PadAction()
    data object Backspace : PadAction()
    data object Space : PadAction()
    data class SwitchMode(val mode: InputMode) : PadAction()

    /** D-pad: move the cursor, or move through candidates while they are shown. */
    data class Dpad(val direction: Dir4) : PadAction()
    data object HideKeyboard : PadAction()

    /**
     * Amendment 20 (Select tap): switch between the guide and Sumire's touch keyboard for the current
     * mode. The host knows which of the two is up and shows the other one.
     */
    data object TouchKeyboard : PadAction()

    /** Commit one emoji (recorded in Sumire's usage history, like a tap on the emoji keyboard). */
    data class InsertEmoji(val emoji: String) : PadAction()

    /** Drive Sumire's full emoji keyboard. */
    data class EmojiKeyboard(val command: EmojiKeyboardCommand) : PadAction()
}

/** The result of feeding one event to the engine: zero or more actions plus a human-readable note for the diagnostics screen. */
data class PadDecision(val actions: List<PadAction>, val note: String) {
    companion object {
        val NONE = PadDecision(emptyList(), "")
        fun of(action: PadAction, note: String) = PadDecision(listOf(action), note)
    }
}

/** Where stick motion comes from. */
enum class StickSource { AUTO, STICKS, DPAD_FALLBACK }
