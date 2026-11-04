package com.kazumaproject.pad_input

import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop

/**
 * What a held hold-role does to the character being typed (amendments 6 and 7). Every kana
 * transform is the touch ゛゜小 key's own table (`tenkey/.../CharExtension.kt`: flick left =
 * 濁点, flick right = 半濁点, flick top = 小文字), so touch and controller never disagree.
 * A transform that does not apply to a character leaves it unchanged (L1 + あ → あ).
 */
object HoldTransforms {

    fun apply(role: PadRole?, c: Char, mode: PadMode): Char = when (role) {
        PadRole.HOLD_DAKUTEN -> when (mode) {
            PadMode.KANA -> c.getDakutenFlickLeft() ?: c
            PadMode.ENGLISH -> c.uppercaseChar()
            else -> c
        }
        PadRole.HOLD_HANDAKUTEN -> if (mode == PadMode.KANA) c.getDakutenFlickRight() ?: c else c
        PadRole.HOLD_SMALL -> if (mode == PadMode.KANA) c.getDakutenFlickTop() ?: c else c
        PadRole.HOLD_SMALL_OR_HANDAKUTEN -> if (mode == PadMode.KANA) c.getDakutenFlickTop() ?: c.getDakutenFlickRight() ?: c else c
        PadRole.HOLD_UPPERCASE -> if (mode == PadMode.ENGLISH) c.uppercaseChar() else c
        else -> c
    }

    fun apply(role: PadRole?, s: String, mode: PadMode): String =
        if (role == null || s.isEmpty()) s else buildString { for (ch in s) append(apply(role, ch, mode)) }

    /** Whether a hold role does anything at all in this mode (else the guide shows なし). Stick layout; the D-pad fallback has its own table. */
    fun applies(role: PadRole, mode: PadMode): Boolean = when (role) {
        PadRole.HOLD_DAKUTEN -> mode == PadMode.KANA || mode == PadMode.ENGLISH
        // Amendment 22: in 英字 they switch to the 数字 layout while held (not a character transform).
        PadRole.HOLD_HANDAKUTEN, PadRole.HOLD_SMALL, PadRole.HOLD_SMALL_OR_HANDAKUTEN -> mode == PadMode.KANA || mode == PadMode.ENGLISH
        PadRole.HOLD_UPPERCASE -> mode == PadMode.ENGLISH
        // Amendment 18: the layer in every text mode (記号; かな's was や/わ until amendment 21); the bar
        // and the centre character are gone (removed roles).
        PadRole.YA_WA_LAYER -> mode == PadMode.KANA || mode == PadMode.ENGLISH || mode == PadMode.NUMBER
        PadRole.BAR, PadRole.TAP_CENTER -> false
        else -> true
    }
}
