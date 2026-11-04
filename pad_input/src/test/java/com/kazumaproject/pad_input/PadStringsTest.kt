package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 12: every Sumire Pad string exists in English (values/, the default) and Japanese
 * (values-ja/), the guide's words included, and the engine holds no Japanese words itself.
 * This is the MissingTranslation check for the pad strings (they live in the app module, where
 * upstream's lint settings stay as they are).
 */
class PadStringsTest {

    private val keyPattern = Regex("<(string|string-array) name=\"([a-z0-9_]+)\"")
    private fun keys(xml: String) = keyPattern.findAll(xml).map { it.groupValues[1] + ":" + it.groupValues[2] }.toList()
    private fun japanese(s: String) = s.any { it in '぀'..'ヿ' || it in '一'..'鿿' || it in '！'..'｠' }

    @Test
    fun both_languages_have_the_same_keys() {
        val en = keys(TestWords.xml("values"))
        val ja = keys(TestWords.xml("values-ja"))
        assertEquals("no duplicates", en.size, en.toSet().size)
        assertEquals("missing in values-ja: ${en - ja.toSet()}; missing in values: ${ja - en.toSet()}", en.toSet(), ja.toSet())
        assertTrue(en.size > 130)
    }

    @Test
    fun every_guide_word_exists_in_both_languages() {
        for (w in PadWord.entries) {
            assertTrue("en ${w.resourceName}", TestWords.en.has(w))
            assertTrue("ja ${w.resourceName}", TestWords.ja.has(w))
            assertTrue("en ${w.resourceName} not blank", TestWords.en[w].isNotBlank())
        }
    }

    @Test
    fun the_english_file_is_english() {
        // Kana and kana marks may appear where they are the thing described (わをん, か→が, ゛ ゜).
        val allowedJapanese = setOf(
            "pad_word_bar_kana", "pad_word_dakuten", "pad_word_handakuten", "pad_word_small_or_handakuten",
            "pad_word_vowel_a", "pad_word_vowel_i", "pad_word_vowel_u", "pad_word_vowel_e", "pad_word_vowel_o",
            "pad_role_hold_dakuten", "pad_role_hold_handakuten", "pad_role_hold_small", "pad_role_hold_small_or_handakuten",
            "pad_role_bar", "pad_roles_category_summary", "pad_role_ya_wa_layer",
        )
        val strings = PadWords.parseAndroidStrings(TestWords.xml("values"))
        for ((k, v) in strings) if (k !in allowedJapanese) assertTrue("$k is not English: $v", !japanese(v))
        val ja = PadWords.parseAndroidStrings(TestWords.xml("values-ja"))
        assertTrue("the Japanese file is Japanese", ja.count { japanese(it.value) } > ja.size / 2)
    }

    @Test
    fun the_engine_holds_no_japanese_words() {
        // Kana characters come from Sumire's tables, words from the resources: no literal in the engine.
        val dir = listOf("pad_input/src/main/java", "src/main/java").map { java.io.File(it) }.first { it.isDirectory }
        val offenders = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) null
                else if (japanese(code.substringBefore("//"))) "${f.name}:${i + 1}: $code" else null
            }
        }.toList()
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
