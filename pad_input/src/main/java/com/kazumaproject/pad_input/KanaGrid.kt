package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.state.GestureType

/**
 * The left-stick 3x3 grid. Kana characters come from Sumire's own `KeyMap` table, the one the
 * touch tenkey uses, so touch and controller always agree. 英字 and 数字 use the controller's own
 * four-way tables instead ([FourWayLayouts], amendment 14): the same nine positions, four
 * characters each on ← ↑ → ↓, no centre character.
 *
 * Amendment 16 (v5), kana with sticks: the stick at rest is the あ key. Amendment 21 (v9): the grid
 * reads ↖ か ↑ さ ↗ た / ← な ・あ → は / ↙ ま ↓ ら ↘ や/わ. The や/わ key ([YA_WA_KEY]) types
 * や ゆ よ わ を ん on a six-slot ring ([YA_WA_SLOTS], [KanaHexagon]), each the touch や or わ key's
 * own character. The v5–v8 記号 key and the v5–v8 R2 や/わ layer are gone; R2 in かな is the 記号
 * layer ([KANA_SYMBOLS]) like 英字's and 数字's. The D-pad fallback keeps the v4 grid
 * ([FALLBACK_KANA]) and its bar ([bar]): it has no left stick and so no layer.
 */
object KanaGrid {
    private val keyMap = KeyMap()

    /**
     * Amendment 21 (v9): the や/わ key of the kana grid (↘). It has no touch key of its own;
     * [Key.KeyYU] (a 50-on key no 12-key grid uses) only names it here and is never sent to the
     * IME: its six characters go out as the touch や / わ keys they come from ([YA_WA_SLOTS]).
     */
    val YA_WA_KEY: Key = Key.KeyYU

    /** Row-major, the v9 grid: ↖か ↑さ ↗た / ←な ・あ →は / ↙ま ↓ら ↘や/わ (英字 / 数字: the cells of [FourWayLayouts] by position). */
    val MAIN: List<Key> = listOf(
        Key.KeyKA, Key.KeySA, Key.KeyTA,
        Key.KeyNA, Key.KeyA, Key.KeyHA,
        Key.KeyMA, Key.KeyRA, YA_WA_KEY,
    )

    /** The v4 kana grid, still used by the D-pad fallback: ↖あ ↑か ↗さ / ←た ・な →は / ↙ま ↓や ↘ら. */
    val FALLBACK_KANA: List<Key> = listOf(
        Key.KeyA, Key.KeyKA, Key.KeySA,
        Key.KeyTA, Key.KeyNA, Key.KeyHA,
        Key.KeyMA, Key.KeyYA, Key.KeyRA,
    )

    /** The grid of a mode: the v4 kana grid in the D-pad fallback, else [MAIN]. */
    fun keys(mode: PadMode = PadMode.KANA, fallback: Boolean = false): List<Key> =
        if (fallback && mode == PadMode.KANA) FALLBACK_KANA else MAIN

    fun keyFor(sector: Sector9, mode: PadMode = PadMode.KANA, fallback: Boolean = false): Key = keys(mode, fallback)[sector.index]

    /** 英字 / 数字: the four characters (← ↑ → ↓) of a grid key; null off the grid or in other modes. */
    private fun fourWayCell(key: Key, mode: PadMode): String? {
        val i = MAIN.indexOf(key)
        return if (i < 0) null else FourWayLayouts.cells(mode)?.getOrNull(i)
    }

    /** One character typed through a touch key: the character, and the key and gesture that type it there. */
    data class Slot(val char: Char, val key: Key, val gesture: GestureType)

    /**
     * Amendment 21: the や/わ key's six characters in [KanaHexagon] slot order (↑ や, ↗ ゆ, ↘ よ,
     * ↓ わ, ↙ を, ↖ ん), each the touch や or わ key's own (key, gesture). With R1 (小文字) the first
     * four become ゃ ゅ ょ ゎ; を and ん stay.
     */
    val YA_WA_SLOTS: List<Slot> by lazy {
        fun slot(key: Key, g: GestureType) = Slot(charFor(key, g) ?: error("no character for $key $g"), key, g)
        listOf(
            slot(Key.KeyYA, GestureType.Tap),          // ↑ や
            slot(Key.KeyYA, GestureType.FlickTop),     // ↗ ゆ
            slot(Key.KeyYA, GestureType.FlickBottom),  // ↘ よ
            slot(Key.KeyWA, GestureType.Tap),          // ↓ わ
            slot(Key.KeyWA, GestureType.FlickLeft),    // ↙ を
            slot(Key.KeyWA, GestureType.FlickTop),     // ↖ ん
        )
    }

    /** The や/わ key's label: 「や/わ」 (its ↑ and ↓ characters). */
    val YA_WA_LABEL: String by lazy { "${YA_WA_SLOTS[0].char}/${YA_WA_SLOTS[3].char}" }

    /** Sumire's table entry for the key in this mode, or null when the mode has none (kana ゛゜小, English a/A, the や/わ key). */
    fun info(key: Key, mode: PadMode = PadMode.KANA): KeyInfo.KeyTapFlickInfo? {
        if (key == YA_WA_KEY) return null
        val info = when (mode) {
            PadMode.ENGLISH -> keyMap.getKeyInfoEnglish(key, false)
            PadMode.NUMBER -> keyMap.getKeyInfoNumber(key, false)
            else -> keyMap.getKeyInfoJapanese(key, false)
        }
        return info as? KeyInfo.KeyTapFlickInfo
    }

    /**
     * The character a key and gesture type in this mode, or null when there is none. Kana: Sumire's
     * table (some flicks are empty there; the や/わ key has no gestures, its ring is [YA_WA_SLOTS]).
     * 英字 / 数字: the four-way table; a tap has no character.
     */
    fun charFor(key: Key, gesture: GestureType, mode: PadMode = PadMode.KANA): Char? {
        if (FourWayLayouts.applies(mode)) {
            val dir = dirOf(gesture) ?: return null
            return fourWayCell(key, mode)?.getOrNull(dir.ordinal)
        }
        if (key == YA_WA_KEY) return null
        val info = info(key, mode) ?: return null
        return when (gesture) {
            GestureType.Tap -> info.tap
            GestureType.FlickLeft -> info.flickLeft
            GestureType.FlickTop -> info.flickTop
            GestureType.FlickRight -> info.flickRight
            GestureType.FlickBottom -> info.flickBottom
            else -> null
        }
    }

    /** The flick direction of a gesture, or null for a tap. */
    fun dirOf(gesture: GestureType): Dir4? = when (gesture) {
        GestureType.FlickLeft -> Dir4.LEFT
        GestureType.FlickTop -> Dir4.UP
        GestureType.FlickRight -> Dir4.RIGHT
        GestureType.FlickBottom -> Dir4.DOWN
        else -> null
    }

    /** Label shown on the guide's grid for a key in a mode (英字 / 数字: the cell's four characters; the や/わ key 「や/わ」). */
    fun label(key: Key, mode: PadMode = PadMode.KANA): String {
        fourWayCell(key, mode)?.let { return it }
        if (key == YA_WA_KEY) return YA_WA_LABEL
        return info(key, mode)?.tap?.toString() ?: "?"
    }

    /** Centre + 4 flick characters for the guide, in Dir4 order null-safe. */
    data class FlickChars(val center: String, val left: String, val up: String, val right: String, val down: String) {
        fun map(f: (String) -> String) = FlickChars(f(center), f(left), f(up), f(right), f(down))

        /** The character of a pentagon slot (amendment 15): 0 = centre (あ段), 1 = left, 2 = up, 3 = right, 4 = down. */
        fun slot(i: Int): String = when (i) {
            0 -> center; 1 -> left; 2 -> up; 3 -> right; else -> down
        }

        /** The five characters in slot order. */
        val slots: List<String> get() = listOf(center, left, up, right, down)
    }

    fun flickChars(key: Key, mode: PadMode = PadMode.KANA): FlickChars {
        fourWayCell(key, mode)?.let { cell ->
            fun at(i: Int) = cell.getOrNull(i)?.toString().orEmpty()
            return FlickChars(FourWayLayouts.SPACE_GLYPH, at(0), at(1), at(2), at(3))
        }
        // The や/わ key reads six slots, not five gestures: its ring is [hexagonChars].
        if (key == YA_WA_KEY) return FlickChars("", "", "", "", "")
        val info = info(key, mode) ?: return FlickChars("", "", "", "", "")
        fun s(c: Char?) = c?.toString() ?: ""
        return FlickChars(s(info.tap), s(info.flickLeft), s(info.flickTop), s(info.flickRight), s(info.flickBottom))
    }

    /** Amendment 21: the や/わ key's six characters in [KanaHexagon] slot order, as strings. */
    fun hexagonChars(): List<String> = YA_WA_SLOTS.map { it.char.toString() }

    /** True for the key that reads the six-slot ring: the や/わ key of the かな grid with sticks. */
    fun isHexagon(key: Key, mode: PadMode, fallback: Boolean = false): Boolean =
        key == YA_WA_KEY && mode == PadMode.KANA && !fallback

    /** All (key, gesture) → character pairs of a mode's grid, for the tests (the や/わ key's ring: [YA_WA_SLOTS]). */
    fun allCharacters(mode: PadMode, fallback: Boolean = false): Map<Pair<Key, GestureType>, Char> {
        val out = LinkedHashMap<Pair<Key, GestureType>, Char>()
        for (key in keys(mode, fallback)) for (g in GESTURES) charFor(key, g, mode)?.let { out[key to g] = it }
        return out
    }

    // ------------------------------------------------------------------ the R2 記号 layer (amendment 21)

    /**
     * Amendment 21 (v9): the 記号 layer of かな (R2 held, the left stick flicks), the 8-way layer
     * 英字 and 数字 have had since v7. One line per direction, so swapping a slot (・ for 「 or （)
     * is a one-line change; the centre is empty.
     *
     * 。、！？ー keep the directions they had on the v6–v8 記号 key; 〜 … ・ are new. Written as
     * escapes (the engine holds no Japanese literal, PadStringsTest), the character in the comment;
     * 〜 is the touch わ key's own U+301C.
     */
    val KANA_SYMBOLS: Map<Sector9, Char> = linkedMapOf(
        Sector9.UP_LEFT to '\u301C',     // 〜
        Sector9.UP to '\u3002',          // 。
        Sector9.UP_RIGHT to '\u2026',    // …
        Sector9.LEFT to '\u30FC',        // ー
        Sector9.RIGHT to '\u3001',       // 、
        Sector9.DOWN_LEFT to '\uFF1F',   // ？
        Sector9.DOWN to '\u30FB',        // ・
        Sector9.DOWN_RIGHT to '\uFF01',  // ！
    )

    /** Kana keys whose characters a layer symbol can go out as (the touch key's own key and gesture). */
    private val SYMBOL_SOURCE_KEYS = listOf(Key.KeyKutouten, Key.KeyWA, Key.KeyYA)

    /**
     * The touch key and gesture that type [c] in かな (。 、 ？ ！ … the 、。？！ key, ー 〜 the わ key),
     * or null when no touch key has it (・ goes out as text).
     */
    fun touchSlotFor(c: Char): Slot? {
        for (key in SYMBOL_SOURCE_KEYS) for (g in GESTURES) if (charFor(key, g) == c) return Slot(c, key, g)
        return null
    }

    /**
     * The R2 layer of a text mode by direction: かな [KANA_SYMBOLS], 英字 / 数字
     * [FourWayLayouts.layer] (all 記号 since amendment 21). The centre is empty everywhere.
     */
    fun layerChars(mode: PadMode): Map<Sector9, Char> =
        if (mode == PadMode.KANA) KANA_SYMBOLS else FourWayLayouts.layer(mode)

    /** A mode's layer in grid order (row-major), "" for the empty slots. */
    fun layerLabels(mode: PadMode): List<String> {
        val chars = layerChars(mode)
        return Sector9.entries.sortedBy { it.index }.map { chars[it]?.toString().orEmpty() }
    }

    // ------------------------------------------------------------------ the bar (amendment 10)

    /** One character of the bar and, when it comes from a touch key, that key and gesture. */
    data class BarSlot(val char: Char, val key: Key?, val gesture: GestureType?)

    /** The bar's nine characters by direction (the D-pad fallback: L1 held, A = [Sector9.CENTER]). */
    data class Bar(val slots: Map<Sector9, BarSlot>) {
        fun charAt(sector: Sector9): String = slots[sector]?.char?.toString() ?: ""
        fun labels(): Map<Sector9, String> = slots.mapValues { it.value.char.toString() }
    }

    /** Directions in the order the bar is filled: centre, ← ↑ → ↓, then ↖ ↗ ↙ ↘. */
    val BAR_ORDER: List<Sector9> = listOf(
        Sector9.CENTER, Sector9.LEFT, Sector9.UP, Sector9.RIGHT, Sector9.DOWN,
        Sector9.UP_LEFT, Sector9.UP_RIGHT, Sector9.DOWN_LEFT, Sector9.DOWN_RIGHT,
    )

    private val GESTURES = listOf(GestureType.Tap, GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom)

    /** The touch key's own character for a gesture, as a bar slot. */
    private fun slotOf(key: Key, g: GestureType, mode: PadMode): BarSlot? =
        charFor(key, g, mode)?.let { BarSlot(it, key, g) }

    /**
     * The bar of a mode. Kana (the D-pad fallback only since amendment 16) is the user's v2 table:
     * centre わ, ← を ↑ ん → ー ↓ 〜, ↖ ！ ↗ ？ ↙ 、 ↘ 。, every one of them the touch わ key's or
     * 、。 key's own character. 英字 and 数字 (amendment 14) hold symbols that are not on that
     * mode's grid, from [FourWayLayouts]; they have no touch key and go out as text.
     */
    fun bar(mode: PadMode): Bar = when (mode) {
        // The user's table, as (touch key, gesture): the わ key's five characters on the centre and
        // the four straight directions (わ を ん ー 〜), the 、。？！ key's on the diagonals
        // (↖ ！ = flick →, ↗ ？ = flick ↑, ↙ 、 = tap, ↘ 。 = flick ←).
        PadMode.KANA -> Bar(
            listOfNotNull(
                slotOf(Key.KeyWA, GestureType.Tap, mode)?.let { Sector9.CENTER to it },
                slotOf(Key.KeyWA, GestureType.FlickLeft, mode)?.let { Sector9.LEFT to it },
                slotOf(Key.KeyWA, GestureType.FlickTop, mode)?.let { Sector9.UP to it },
                slotOf(Key.KeyWA, GestureType.FlickRight, mode)?.let { Sector9.RIGHT to it },
                slotOf(Key.KeyWA, GestureType.FlickBottom, mode)?.let { Sector9.DOWN to it },
                slotOf(Key.KeyKutouten, GestureType.FlickRight, mode)?.let { Sector9.UP_LEFT to it },
                slotOf(Key.KeyKutouten, GestureType.FlickTop, mode)?.let { Sector9.UP_RIGHT to it },
                slotOf(Key.KeyKutouten, GestureType.Tap, mode)?.let { Sector9.DOWN_LEFT to it },
                slotOf(Key.KeyKutouten, GestureType.FlickLeft, mode)?.let { Sector9.DOWN_RIGHT to it },
            ).toMap(),
        )
        PadMode.ENGLISH, PadMode.NUMBER -> Bar(FourWayLayouts.bar(mode).mapValues { BarSlot(it.value, null, null) })
        else -> Bar(emptyMap())
    }
}
