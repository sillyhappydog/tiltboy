package com.kazumaproject.markdownhelperkeyboard.pad

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.pad_input.PadAction
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.StickSource
import com.kazumaproject.tenkey.extensions.getDakutenFlickLeft
import com.kazumaproject.tenkey.extensions.getDakutenFlickRight
import com.kazumaproject.tenkey.extensions.getDakutenFlickTop
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 濁点・半濁点・小文字 through the controller (amendments 7 and 10): the engine applies Sumire's
 * own character functions (the touch ゛゜小 key's table) the moment the character is typed and
 * reports the result as InsertText; the IME appends it. This test replays that on a composing
 * string and checks the result against the same functions applied to the plain character.
 */
class PadDakutenTest {

    /** What IMEService.dakutenSmallLetter does with the last composing character. */
    private fun applyDakuten(text: StringBuilder, gesture: GestureType) {
        val last = text.lastOrNull() ?: return
        val replaced = when (gesture) {
            GestureType.Tap, GestureType.FlickBottom -> last.getDakutenSmallChar()
            GestureType.FlickLeft -> last.getDakutenFlickLeft()
            GestureType.FlickRight -> last.getDakutenFlickRight()
            GestureType.FlickTop -> last.getDakutenFlickTop()
            else -> null
        } ?: return
        text.setLength(text.length - 1)
        text.append(replaced)
    }

    /** Amendment 18: the kana pentagon is the only kana right stick (↑ あ段 → い段 ↘ う段 ↙ え段 ← お段). */
    private class Session {
        val engine = PadInputEngine(PadConfig(stickSource = StickSource.STICKS))
        val text = StringBuilder()
        var t = 0L

        fun apply(actions: List<PadAction>) {
            for (a in actions) when (a) {
                is PadAction.KanaKey -> if (a.key == Key.KeyDakutenSmall) applyDakutenTo(a.gesture) else a.char?.let { text.append(it) }
                is PadAction.InsertText -> text.append(a.text)
                else -> {}
            }
        }

        private fun applyDakutenTo(gesture: GestureType) {
            val last = text.lastOrNull() ?: return
            val replaced = when (gesture) {
                GestureType.Tap, GestureType.FlickBottom -> last.getDakutenSmallChar()
                GestureType.FlickLeft -> last.getDakutenFlickLeft()
                GestureType.FlickRight -> last.getDakutenFlickRight()
                GestureType.FlickTop -> last.getDakutenFlickTop()
                else -> null
            } ?: return
            text.setLength(text.length - 1)
            text.append(replaced)
        }

        fun left(x: Float, y: Float) { t += 16; apply(engine.onSticks(x, y, 0f, 0f, t).actions) }
        /** Right stick out toward (x, y) (full axis values, y down) and back, the left stick held at (lx, ly). */
        fun flickXY(x: Float, y: Float, lx: Float, ly: Float) {
            t += 16; apply(engine.onSticks(lx, ly, x, y, t).actions)
            t += 16; apply(engine.onSticks(lx, ly, 0f, 0f, t).actions)
        }
        /** Amendment 16: the left stick out toward (x, y) and back, the right stick at rest (the や・わ layer while R2 is held). */
        fun layerFlick(x: Float, y: Float) {
            t += 16; apply(engine.onSticks(x * 0.95f, y * 0.95f, 0f, 0f, t).actions)
            t += 16; apply(engine.onSticks(0f, 0f, 0f, 0f, t).actions)
        }
        fun press(b: PadButton) { t += 16; apply(engine.onButton(b, true, t).actions) }
        fun release(b: PadButton) { t += 16; apply(engine.onButton(b, false, t).actions) }
        fun tap(b: PadButton) { press(b); release(b) }
    }

    @Test
    fun dakuten_handakuten_and_small_kana_via_the_hold_roles() {
        // Amendment 16 defaults: L2 濁点, L1 半濁点, R1 小文字; amendment 21: や/わ is the ↘ grid key (six slots).
        val s = Session()
        // か → が : L2 held, か key (↖)
        s.press(PadButton.L2); s.left(-0.707f, -0.707f); s.flickXY(0f, -1f, -0.707f, -0.707f); s.release(PadButton.L2)
        assertEquals('か'.getDakutenFlickLeft().toString(), s.text.toString())
        // は → ぱ : L1 held, は key (→)
        s.press(PadButton.L1); s.left(1f, 0f); s.flickXY(0f, -1f, 1f, 0f); s.release(PadButton.L1)
        assertEquals("が" + 'は'.getDakutenFlickRight(), s.text.toString())
        // つ → っ : R1 held, flick ↘ (う段) on た (↗)
        s.press(PadButton.R1); s.left(0.707f, -0.707f); s.flickXY(1f, 1f, 0.707f, -0.707f); s.release(PadButton.R1)
        assertEquals("がぱ" + 'つ'.getDakutenFlickTop(), s.text.toString())
        // よ → ょ : R1 held, the や/わ key (↘), right stick ↘ (−30°)
        s.left(0.707f, 0.707f)
        s.press(PadButton.R1); s.flickXY(0.87f, 0.5f, 0.707f, 0.707f); s.release(PadButton.R1)
        assertEquals("がぱっ" + 'よ'.getDakutenFlickTop(), s.text.toString())
        // わ → ゎ : R1 held, the や/わ key, right stick ↓
        s.press(PadButton.R1); s.flickXY(0f, 1f, 0.707f, 0.707f); s.release(PadButton.R1)
        assertEquals("がぱっょ" + 'わ'.getDakutenFlickTop(), s.text.toString())
        assertEquals("がぱっょゎ", s.text.toString())
    }

    /**
     * Amendment 15, the sentence on the kana pentagon (the only kana right stick since amendment 18):
     * ↑ あ段, → い段, ↘ う段, ↙ え段, ← お段 (the device recipe's full-axis values). Amendment 16 grid and R2 layer.
     */
    @Test
    fun kyou_ha_ii_tenki_on_the_pentagon() {
        val s = Session()
        s.left(-0.707f, -0.707f); s.flickXY(1f, 0f, -0.707f, -0.707f)   // き (か key →)
        s.left(0.707f, 0.707f)
        s.press(PadButton.R1); s.flickXY(0.87f, 0.5f, 0.707f, 0.707f); s.release(PadButton.R1)  // ょ (R1 + や/わ ↘, amendment 21)
        s.left(0f, 0f)
        s.flickXY(1f, 1f, 0f, 0f)                                        // う (あ key ↘)
        s.left(1f, 0f); s.flickXY(0f, -1f, 1f, 0f)                       // は (は key ↑)
        s.left(0f, 0f); s.flickXY(1f, 0f, 0f, 0f); s.flickXY(1f, 0f, 0f, 0f) // いい
        s.left(0.707f, -0.707f); s.flickXY(-1f, 1f, 0.707f, -0.707f)     // て (た key ↙)
        s.left(0.707f, 0.707f); s.flickXY(-0.87f, -0.5f, 0.707f, 0.707f)  // ん (や/わ ↖)
        s.left(-0.707f, -0.707f); s.flickXY(1f, 0f, -0.707f, -0.707f)   // き
        assertEquals("きょうはいいてんき", s.text.toString())
        // ↑ types the あ段 (amendment 18: R3 no longer does), and L2 + か + ← is ご.
        val r = Session()
        r.left(-0.707f, -0.707f); r.flickXY(0f, -1f, -0.707f, -0.707f)
        r.press(PadButton.L2); r.flickXY(-1f, 0f, -0.707f, -0.707f); r.release(PadButton.L2)
        assertEquals("かご", r.text.toString())
    }

    @Test
    fun sumire_character_functions_used_by_the_ime() {
        val sb = StringBuilder("か"); applyDakuten(sb, GestureType.FlickLeft); assertEquals("が", sb.toString())
        val sb2 = StringBuilder("ひ"); applyDakuten(sb2, GestureType.FlickRight); assertEquals("ぴ", sb2.toString())
        val sb3 = StringBuilder("あ"); applyDakuten(sb3, GestureType.FlickTop); assertEquals("ぁ", sb3.toString())
        val sb4 = StringBuilder("な"); applyDakuten(sb4, GestureType.FlickLeft); assertEquals("unchanged when no dakuten form", "な", sb4.toString())
    }
}
