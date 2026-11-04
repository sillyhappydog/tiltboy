package com.kazumaproject.pad_input

/**
 * The controller's own 英字 and 数字 layouts (amendment 14). Controller mode only: the touch
 * tenkey keeps Sumire's tables, and amendment 6's "same as the touch table" rule is lifted for
 * these two modes.
 *
 * The left stick picks one of nine cells (neutral = the centre cell), and a right-stick tilt
 * types one of the cell's four characters: ← first, ↑ second, → third, ↓ fourth (the kana order
 * い う え お). There is no centre character and no click (amendment 18: R3 steps the text mode,
 * X types a space). On a stick the four tilts are cheap and the click is the most expensive
 * input, so every cell is full and nothing needs a click.
 *
 * Amendment 18: the symbols that are not on a grid are on the R2 layer ([layer]): R2 held, the
 * left stick flicks them, as かな's や/わ. They are the v3–v6 L3 bars without their centre slot.
 * The bars themselves ([bar], centre included) stay for the D-pad fallback (L1 held).
 *
 * Each mode has one table here, used by the engine, the guide and the corner overlay alike.
 */
object FourWayLayouts {
    /** What the D-pad fallback's cross centre shows in 英字 / 数字: the character A types there (a space). */
    const val SPACE_GLYPH = "␣"

    /** Row-major like [KanaGrid.MAIN]: each cell lists its characters in ← ↑ → ↓ order. */
    val ENGLISH: List<String> = listOf(
        "abcd", "efgh", "ijkl",
        "mnop", "qrst", "uvwx",
        "yz'-", ",.?!", "@/:#",
    )

    /** Same shape for numbers and the symbols that go with them. */
    val NUMBER: List<String> = listOf(
        "1234", "5678", "90.,",
        "+-*/", "(=)%", "¥\$€&",
        "!?'\"", "~@_#", "<:>;",
    )

    /**
     * The bar in 英字 (L3 until v6; the D-pad fallback's L1 bar since amendment 18): symbols that are not on the 英字 grid, centre first (R3 while the bar
     * is active), then ← ↑ → ↓ and the diagonals. Chosen for English text, URLs and markdown:
     * quotes, brackets, `;`, `_ = &` (URL queries), `* +`.
     */
    val ENGLISH_BAR: Map<Sector9, Char> = linkedMapOf(
        Sector9.CENTER to '"',
        Sector9.LEFT to '(', Sector9.UP to ';', Sector9.RIGHT to ')', Sector9.DOWN to '_',
        Sector9.UP_LEFT to '&', Sector9.UP_RIGHT to '*', Sector9.DOWN_LEFT to '=', Sector9.DOWN_RIGHT to '+',
    )

    /**
     * The bar in 数字 (L3 until v6; the D-pad fallback's L1 bar since amendment 18): the ASCII symbols the 数字 grid does not have (`[ ] { } ^ | \` and the
     * backquote), brackets as mirrored pairs, plus `°` in the centre.
     */
    val NUMBER_BAR: Map<Sector9, Char> = linkedMapOf(
        Sector9.CENTER to '°',
        Sector9.LEFT to '[', Sector9.UP to '^', Sector9.RIGHT to ']', Sector9.DOWN to '|',
        Sector9.UP_LEFT to '{', Sector9.UP_RIGHT to '}', Sector9.DOWN_LEFT to '\\', Sector9.DOWN_RIGHT to '`',
    )

    /**
     * Amendment 18: the R2 layer of 英字 (label 記号 / "Symbols"): ↖ & ↑ ; ↗ * / ← ( · ) / ↙ = ↓ _ ↘ +.
     * The 英字 bar without its centre (`"` is still on the 数字 grid, `!?'"`).
     */
    val ENGLISH_LAYER: Map<Sector9, Char> = ENGLISH_BAR.filterKeys { it != Sector9.CENTER }

    /**
     * Amendment 18: the R2 layer of 数字 (label 記号 / "Symbols"): ↖ { ↑ ^ ↗ } / ← [ · ] / ↙ \ ↓ | ↘ `.
     * The 数字 bar without its centre: `°` is not typable in stick mode any more.
     */
    val NUMBER_LAYER: Map<Sector9, Char> = NUMBER_BAR.filterKeys { it != Sector9.CENTER }

    /** The R2 layer of a mode (empty map when the mode does not use these layouts). */
    fun layer(mode: PadMode): Map<Sector9, Char> = when (mode) {
        PadMode.ENGLISH -> ENGLISH_LAYER
        PadMode.NUMBER -> NUMBER_LAYER
        else -> emptyMap()
    }

    /** The nine cells of a mode, or null when the mode does not use these layouts (kana, emoji). */
    fun cells(mode: PadMode): List<String>? = when (mode) {
        PadMode.ENGLISH -> ENGLISH
        PadMode.NUMBER -> NUMBER
        else -> null
    }

    /** True for 英字 and 数字. */
    fun applies(mode: PadMode): Boolean = cells(mode) != null

    /** The bar of a mode (empty map when the mode does not use these layouts). */
    fun bar(mode: PadMode): Map<Sector9, Char> = when (mode) {
        PadMode.ENGLISH -> ENGLISH_BAR
        PadMode.NUMBER -> NUMBER_BAR
        else -> emptyMap()
    }

    /** The cell's characters (← ↑ → ↓) for a left-stick position. */
    fun cell(mode: PadMode, sector: Sector9): String? = cells(mode)?.getOrNull(sector.index)

    /** The character a right-stick tilt types on the cell at [sector]. */
    fun charAt(mode: PadMode, sector: Sector9, dir: Dir4): Char? = cell(mode, sector)?.getOrNull(dir.ordinal)

    /** Every character of a mode's grid and bar (the D-pad fallback; for the coverage tests). */
    fun allChars(mode: PadMode): Set<Char> =
        (cells(mode).orEmpty().joinToString("") + bar(mode).values.joinToString("")).toSet()

    /** Amendment 18: every character of a mode's grid and R2 layer (stick mode; for the ASCII-95 test). */
    fun stickChars(mode: PadMode): Set<Char> =
        (cells(mode).orEmpty().joinToString("") + layer(mode).values.joinToString("")).toSet()
}
