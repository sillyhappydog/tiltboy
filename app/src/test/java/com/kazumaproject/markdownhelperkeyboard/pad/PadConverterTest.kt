package com.kazumaproject.markdownhelperkeyboard.pad

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.pad_input.PadAction
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.StickSource
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Controller → kana → Sumire's real kana-kanji converter (plain JVM, dictionaries from
 * app/src/main/assets). Confirms the controller output is what the converter expects and
 * that 「きょうはいいてんき」 converts to 今日はいい天気 among the top candidates.
 */
class PadConverterTest {

    companion object {
        private lateinit var converter: KanaKanjiEngine

        @JvmStatic
        @BeforeClass
        fun setUpEngine() {
            converter = TestEngineFactory.create()
        }
    }

    /**
     * The kana pentagon (the only kana right stick since amendment 18: ↑ あ段 → い段 ↘ う段 ↙ え段
     * ← お段) with the amendment 21 grid (↖ か ↑ さ ↗ た / ← な ・あ → は / ↘ や/わ, six slots).
     */
    private fun typeWithController(): String {
        val engine = PadInputEngine(PadConfig(stickSource = StickSource.STICKS))
        val text = StringBuilder()
        var t = 0L
        fun apply(actions: List<PadAction>) = actions.forEach { a ->
            when (a) {
                is PadAction.KanaKey -> if (a.key == Key.KeyDakutenSmall && a.gesture == GestureType.Tap) {
                    text.lastOrNull()?.getDakutenSmallChar()?.let { text.setLength(text.length - 1); text.append(it) }
                } else a.char?.let { text.append(it) }
                is PadAction.InsertText -> text.append(a.text)
                else -> {}
            }
        }
        fun sticks(lx: Float, ly: Float, rx: Float, ry: Float) { t += 16; apply(engine.onSticks(lx, ly, rx, ry, t).actions) }
        /** The right stick out toward (x, y) (y down) and back, the left stick held at (lx, ly). */
        fun flick(lx: Float, ly: Float, x: Float, y: Float) { sticks(lx, ly, x, y); sticks(lx, ly, 0f, 0f) }

        flick(-0.707f, -0.707f, 1f, 0f)           // き (か key ↖, → い段)
        engine.onButton(PadButton.R1, true, ++t)
        flick(0.707f, 0.707f, 0.87f, 0.5f)        // ょ (R1 + the や/わ key ↘, right stick ↘ −30°)
        engine.onButton(PadButton.R1, false, ++t)
        sticks(0f, 0f, 0f, 0f)
        flick(0f, 0f, 1f, 1f)                     // う (the あ key at rest, ↘ う段)
        sticks(1f, 0f, 0f, 0f); flick(1f, 0f, 0f, -1f) // は (↑ あ段)
        flick(0f, 0f, 1f, 0f); flick(0f, 0f, 1f, 0f)   // いい
        flick(0.707f, -0.707f, -1f, 1f)           // て (た key ↗, ↙ え段)
        flick(0.707f, 0.707f, -0.87f, -0.5f)      // ん (the や/わ key, ↖)
        flick(-0.707f, -0.707f, 1f, 0f)           // き
        return text.toString()
    }

    /** きかき on the kana pentagon, the default since amendment 15: か key (↖ since amendment 16), → き, ↑ か, → き. */
    private fun typeKikaki(): String {
        val engine = PadInputEngine(PadConfig(stickSource = StickSource.STICKS))
        val text = StringBuilder()
        var t = 0L
        fun apply(actions: List<PadAction>) = actions.forEach { a -> if (a is PadAction.KanaKey) a.char?.let { text.append(it) } }
        fun sticks(lx: Float, ly: Float, rx: Float, ry: Float) { t += 16; apply(engine.onSticks(lx, ly, rx, ry, t).actions) }
        val a = -0.707f
        sticks(a, a, 1f, 0f); sticks(a, a, 0f, 0f)    // き
        sticks(a, a, 0f, -1f); sticks(a, a, 0f, 0f)   // か
        sticks(a, a, 1f, 0f); sticks(a, a, 0f, 0f)    // き
        return text.toString()
    }

    /** The emulator test looks the wanted candidate up in the list: it must be there for these readings. */
    @Test
    fun kikaki_converts_to_kikaki_among_the_candidates() {
        val kana = typeKikaki()
        assertEquals("きかき", kana)
        val candidates = converter.convertWithTrace(kana).finalCandidates.map { it.candidate }
        val index = candidates.indexOf("気化器")
        println("気化器 is candidate #$index of ${candidates.size}: ${candidates.take(12)}")
        assertTrue("expected 気化器 among the candidates, got $candidates", index >= 0)
    }

    @Test
    fun controller_kana_converts_to_kyou_ha_ii_tenki() {
        val kana = typeWithController()
        assertEquals("きょうはいいてんき", kana)
        val trace = converter.convertWithTrace(kana)
        val candidates = trace.finalCandidates.map { it.candidate }
        assertTrue("converter returned candidates", candidates.isNotEmpty())
        assertTrue(
            "expected 今日はいい天気 among the top candidates, got $candidates",
            candidates.take(5).any { it == "今日はいい天気" },
        )
    }
}
