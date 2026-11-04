package com.kazumaproject.markdownhelperkeyboard.pad

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.pad_input.PadWord
import com.kazumaproject.pad_input.PadWords

/**
 * The guide's words and the per-app language (amendment 12). Strings live in
 * `values/pad_strings.xml` (English, the default) and `values-ja/pad_strings.xml`; the app
 * declares both in `res/xml/locales_config.xml`, so Android's App languages screen (or
 * `cmd locale set-app-locales <pkg> --locales en-US`) switches them without changing the
 * system language. [localized] makes sure the keyboard service reads them in that language too.
 */
object PadText {

    /** [context] with the app's own language (Android 13+ per-app locale) when one is set. */
    fun localized(context: Context): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return context
        val locales = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
        if (locales == null || locales.isEmpty) return context
        if (context.resources.configuration.locales == locales) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(locales)
        return context.createConfigurationContext(config)
    }

    /** The system language (not the per-app one): it decides the first-run modes. */
    fun systemLanguage(): String = Resources.getSystem().configuration.locales[0]?.language.orEmpty()

    /** Every [PadWord] in the app's language. */
    fun words(context: Context): PadWords {
        val c = localized(context)
        val tag = c.resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        return PadWords(PadWord.entries.associateWith { c.getString(res(it)) }, tag)
    }

    /** The string resource of a word (`pad_word_<name>`; [PadWord.resourceName] names the same key). */
    fun res(word: PadWord): Int = when (word) {
        PadWord.TAP_EMOJI -> R.string.pad_word_tap_emoji
        PadWord.TO_KANA -> R.string.pad_word_to_kana
        PadWord.TO_ENGLISH -> R.string.pad_word_to_english
        PadWord.TO_NUMBER -> R.string.pad_word_to_number
        PadWord.TOUCH -> R.string.pad_word_touch
        PadWord.CONFIRM -> R.string.pad_word_confirm
        PadWord.INSERT -> R.string.pad_word_insert
        PadWord.BACKSPACE -> R.string.pad_word_backspace
        PadWord.SPACE -> R.string.pad_word_space
        PadWord.CONVERT -> R.string.pad_word_convert
        PadWord.NEXT -> R.string.pad_word_next
        PadWord.PREVIOUS -> R.string.pad_word_previous
        PadWord.MODE -> R.string.pad_word_mode
        PadWord.TO_TEXT -> R.string.pad_word_to_text
        PadWord.HIDE -> R.string.pad_word_hide
        PadWord.EMOJI -> R.string.pad_word_emoji
        PadWord.LIST -> R.string.pad_word_list
        PadWord.BACK -> R.string.pad_word_back
        PadWord.DAKUTEN -> R.string.pad_word_dakuten
        PadWord.UPPERCASE -> R.string.pad_word_uppercase
        PadWord.HANDAKUTEN -> R.string.pad_word_handakuten
        PadWord.SMALL -> R.string.pad_word_small
        PadWord.SMALL_OR_HANDAKUTEN -> R.string.pad_word_small_or_handakuten
        PadWord.BAR_KANA -> R.string.pad_word_bar_kana
        PadWord.BAR_ENGLISH -> R.string.pad_word_bar_english
        PadWord.BAR_NUMBER -> R.string.pad_word_bar_number
        PadWord.BAR_EMOJI -> R.string.pad_word_bar_emoji
        PadWord.SYMBOLS -> R.string.pad_word_symbols
        PadWord.NUMBER_HOLD -> R.string.pad_word_number_hold
        PadWord.CURSOR -> R.string.pad_word_cursor
        PadWord.MOVE -> R.string.pad_word_move
        PadWord.KEY -> R.string.pad_word_key
        PadWord.PREVIOUS_CATEGORY -> R.string.pad_word_previous_category
        PadWord.NEXT_CATEGORY -> R.string.pad_word_next_category
        PadWord.VOWEL_A -> R.string.pad_word_vowel_a
        PadWord.VOWEL_I -> R.string.pad_word_vowel_i
        PadWord.VOWEL_U -> R.string.pad_word_vowel_u
        PadWord.VOWEL_E -> R.string.pad_word_vowel_e
        PadWord.VOWEL_O -> R.string.pad_word_vowel_o
        PadWord.CHAR_CENTER -> R.string.pad_word_char_center
        PadWord.CHAR_LEFT -> R.string.pad_word_char_left
        PadWord.CHAR_UP -> R.string.pad_word_char_up
        PadWord.CHAR_RIGHT -> R.string.pad_word_char_right
        PadWord.CHAR_DOWN -> R.string.pad_word_char_down
        PadWord.EMOJI_LEFT -> R.string.pad_word_emoji_left
        PadWord.EMOJI_UP -> R.string.pad_word_emoji_up
        PadWord.EMOJI_RIGHT -> R.string.pad_word_emoji_right
        PadWord.EMOJI_DOWN -> R.string.pad_word_emoji_down
    }
}
