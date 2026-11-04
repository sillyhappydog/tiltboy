package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.InputMode

/** Everything the on-screen guide needs to draw itself. Produced by [PadInputEngine.guide]. */
data class PadGuideState(
    val padMode: PadMode,
    /** Sumire input mode shown underneath (kana / english / number); for emoji modes the mode we will return to. */
    val mode: InputMode,
    val fallback: Boolean,
    /**
     * The bar is active (amendment 10): a BAR button is held, or was tapped and the next
     * character comes from the bar. Tenkey: the grid is dimmed and the right stick reads the bar's
     * nine characters; emoji wheel: page 2.
     */
    val barActive: Boolean,
    /** Tenkey modes (kana / English / number): the 9 keys of the grid; null on the emoji wheel. */
    val gridKeys: List<Key>?,
    /** Labels of [gridKeys] with the active hold transform applied (か → が while 濁点 is held). */
    val gridLabels: List<String> = emptyList(),
    /** True where a hold transform is active but did not change the key (drawn dimmed), or everywhere while the bar is active. */
    val gridDim: List<Boolean> = emptyList(),
    /** The key the left stick points at; null while the bar is active (the stick is ignored then). */
    val selectedKey: Key?,
    val selectedSector: Sector9,
    /** Centre + flick characters of the selected key, transform applied; null on the emoji wheel and while the bar is active. */
    val flickChars: KanaGrid.FlickChars?,
    /** Right stick currently pushed in this direction, waiting to come back (4-way; follows a re-aim, amendment 17). */
    val pendingFlick: Dir4?,
    /** Right stick currently pushed in this 8-way direction on the bar. */
    val pendingBarFlick: Sector9? = null,
    /** Emoji wheel: the 8 petals of 4 emoji (left, top, right, bottom); null otherwise. */
    val petals: List<List<String>>?,
    val selectedPetal: Int?,
    /** Printed button at each physical position: where the guide draws each letter. */
    val faceLayout: FaceLayout = FaceLayout.XBOX,
    /** Button roles in this mode, e.g. "A 決定", "Y / R2 変換", for the emoji band. */
    val legend: List<String> = emptyList(),
    /** Controller map (amendment 5): function of every control, from the live roles. */
    val labels: GuideLabels.Labels = GuideLabels.Labels(emptyMap(), ""),
    /** Buttons currently held (live highlight on the map). */
    val heldButtons: Set<PadButton> = emptySet(),
    /** The current selection: the key's label (transformed) or the petal's 4 emoji (kept for the diagnostics log). */
    val preview: String = "",
    /** The hold transform currently applied (first pressed wins), or null. */
    val holdRole: PadRole? = null,
    /** The bar's word for this mode (わをん 記号 / 記号 / 0 記号 / 次の 32 個), empty when no button has the BAR role. */
    val barLabel: String = "",
    /** Printed names of the BAR buttons ("L3"), empty when none. */
    val barButtons: String = "",
    /** The bar's nine characters by direction, hold transform applied; null off the tenkey. */
    val barChars: Map<Sector9, String>? = null,
    /**
     * Amendment 15: kana with the pentagon right stick (the only kana right stick since amendment
     * 18; not in the D-pad fallback). The right side draws the five characters of [flickChars] in
     * slot order on a ring; there is no centre character. 英字 / 数字 (false) draw their four
     * characters on the same ring at ← ↑ → ↓ (amendment 18).
     */
    val pentagon: Boolean = false,
    /** Pentagon slot (0 = あ段 … 4 = お段) the right stick is pushed toward, waiting to come back. */
    val pendingSlot: Int? = null,
    /**
     * [flickChars] in slot order (centre, left, up, right, down): true where a hold transform is
     * active but leaves that character unchanged (drawn dimmed). Empty when no hold is active.
     */
    val flickDim: List<Boolean> = emptyList(),
    /**
     * Amendment 16: the や/わ layer is showing (R2 held in かな, or a left-stick layer flick still
     * out). [gridLabels] then hold the layer (amendment 17: や ゆ よ / "" "" "" / わ を ん; the empty
     * slots are drawn as empty dimmed cells, the centre one with a dot), [selectedKey] is null and
     * the right side (the ring, amendment 18) is drawn faint.
     */
    val layer: Boolean = false,
    /** The layer cell the left stick is aimed at (armed, waiting to come back; follows a re-aim). */
    val pendingLayer: Sector9? = null,
    /**
     * Amendment 18: the touch digit cell the finger is on (highlighted), set by the view from its
     * own touch state; null otherwise. Only the かな layout ([kanaLayout]) draws the digits.
     */
    val touchedDigit: Char? = null,
    /**
     * Amendment 21: the や/わ key is selected (or a flick that started on it is out): the ring draws
     * [hexagonChars] as six round cells at [KanaHexagon]'s angles instead of the pentagon;
     * [pendingSlot] and [flickDim] are then hexagon slots (0 = ↑ や … 5 = ↖ ん).
     */
    val hexagon: Boolean = false,
    /** Amendment 21: the six characters of the や/わ key in [KanaHexagon] slot order, 小文字 applied (R1: ゃ ゅ ょ ゎ). */
    val hexagonChars: List<String> = emptyList(),
    /**
     * Amendment 22: 英字 with L1 / R1 held (or a flick latched with it still out): the grid, the
     * ring and the R2 layer show 数字's characters; the L2 pill is empty ([labels]).
     */
    val numberLayout: Boolean = false,
) {
    val isTenkey: Boolean get() = gridKeys != null
    val isEmoji: Boolean get() = padMode.isEmoji
    val isEmojiList: Boolean get() = padMode == PadMode.EMOJI_LIST

    /**
     * Amendment 16: the kana guide of the stick layout (the v5 placement). Amendment 18: 英字 and
     * 数字 with sticks draw with it too (and every one of them has the touch digits). The emoji
     * modes and the D-pad fallback keep the older left-column map.
     */
    val kanaLayout: Boolean get() = !fallback && gridKeys != null &&
        (padMode == PadMode.KANA || padMode == PadMode.ENGLISH || padMode == PadMode.NUMBER)

    /** 英字 / 数字 (amendment 14's four-way tables): four characters per key, ← ↑ → ↓ (amendment 22: also 英字 showing 数字's). */
    val isFourWay: Boolean get() = padMode == PadMode.ENGLISH || padMode == PadMode.NUMBER
}
