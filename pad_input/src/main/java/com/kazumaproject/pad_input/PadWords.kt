package com.kazumaproject.pad_input

/**
 * Every word the controller guide can show (amendment 12). The words themselves live in the
 * app's resources, `values/pad_strings.xml` (English, the default) and
 * `values-ja/pad_strings.xml`, as `pad_word_<name in lower case>`; the engine never holds a
 * literal. The app builds a [PadWords] from its (possibly per-app-locale) resources, the JVM
 * tests and the guide renders parse the same XML files with [PadWords.fromAndroidXml].
 */
enum class PadWord {
    /** R3 (MODE_NEXT) in the emoji wheel: the petal's first emoji (amendment 18 keeps v6's R3 there). */
    TAP_EMOJI,
    CONFIRM, INSERT, BACKSPACE, SPACE,
    /** CONVERT_NEXT in kana (and the emoji wheel). */
    CONVERT,
    /** CONVERT_NEXT in 英字 / 数字: steps through Sumire's suggestions. */
    NEXT,
    PREVIOUS, MODE,
    /** The D-pad fallback's R1 in the emoji modes: back to the text mode (MODE_SWITCH there until v7). */
    TO_TEXT,
    HIDE, EMOJI, LIST, BACK,
    DAKUTEN, UPPERCASE, HANDAKUTEN, SMALL, SMALL_OR_HANDAKUTEN,
    BAR_KANA, BAR_ENGLISH, BAR_NUMBER, BAR_EMOJI,
    /**
     * Amendment 21: the R2 layer's word in every text mode, 「記号」 / "Symbols" (v5–v8 かな said
     * や/わ there; the 記号 grid key is gone). Amendment 22: the 半濁点 / 小文字 roles in 英字, 「123」.
     */
    SYMBOLS, NUMBER_HOLD,
    /** Amendment 18: L3 / R3 (MODE_PREVIOUS / MODE_NEXT) name the mode they go to: かなへ 英字へ 数字へ / To kana, To ABC, To 123. */
    TO_KANA, TO_ENGLISH, TO_NUMBER,
    /** Amendment 20: Select (TOUCH_KEYBOARD), the touch keyboard: 「タッチ」 / "Touch". */
    TOUCH,
    CURSOR, MOVE, KEY, PREVIOUS_CATEGORY, NEXT_CATEGORY,
    // D-pad fallback table
    VOWEL_A, VOWEL_I, VOWEL_U, VOWEL_E, VOWEL_O,
    CHAR_CENTER, CHAR_LEFT, CHAR_UP, CHAR_RIGHT, CHAR_DOWN,
    EMOJI_LEFT, EMOJI_UP, EMOJI_RIGHT, EMOJI_DOWN;

    /** The string resource that holds this word. */
    val resourceName: String get() = "pad_word_" + name.lowercase()
}

/** One language's [PadWord] table. A missing word reads as its resource name (never blank). */
class PadWords(private val table: Map<PadWord, String>, private val tag: String = "") {
    operator fun get(word: PadWord): String = table[word] ?: word.resourceName

    fun has(word: PadWord): Boolean = table.containsKey(word)

    override fun toString(): String = "PadWords($tag, ${table.size} words)"

    companion object {
        /** No resources (engine tests that do not look at labels): every word reads as its key. */
        val KEYS = PadWords(emptyMap(), "keys")

        private val STRING = Regex("<string\\s+name=\"([a-z0-9_]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

        /** Every `<string>` of an Android strings file, unescaped (\' \" \n &amp; &lt; &gt;). */
        fun parseAndroidStrings(xml: String): Map<String, String> =
            STRING.findAll(xml).associate { m ->
                m.groupValues[1] to m.groupValues[2]
                    .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            }

        /** The [PadWord]s found in an Android strings file (used by the JVM tests and the guide renders). */
        fun fromAndroidXml(xml: String, tag: String): PadWords {
            val strings = parseAndroidStrings(xml)
            return PadWords(PadWord.entries.mapNotNull { w -> strings[w.resourceName]?.let { w to it } }.toMap(), tag)
        }
    }
}
