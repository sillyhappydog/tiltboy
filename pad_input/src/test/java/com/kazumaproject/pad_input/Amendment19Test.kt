package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 19: `_` (and other low horizontal strokes) are centred horizontally only and keep the
 * font baseline, so the 英字 R2 layer's underscore no longer reads as a minus.
 */
class Amendment19Test {
    private fun text(s: String, ink: Boolean = false, align: GuideLayout.Align = GuideLayout.Align.CENTER) =
        GuideLayout.Primitive.Text(100f, 50f, 22f, s, GuideLayout.TextStyle.NORMAL, align = align, ink = ink)

    @Test
    fun only_single_low_strokes_keep_the_baseline() {
        assertTrue(GuideLayout.keepsBaseline(text("_")))
        assertTrue(GuideLayout.keepsBaseline(text("\uFF3F")))
        for (s in listOf("-", "=", "+", "&", "a", "\u30FC")) assertFalse("'$s' stays ink-centred", GuideLayout.keepsBaseline(text(s)))
        assertFalse("a 4-character ink label is centred as a group", GuideLayout.keepsBaseline(text("~@_#", ink = true)))
        assertFalse("left-aligned text is not centred at all", GuideLayout.keepsBaseline(text("_", align = GuideLayout.Align.LEFT)))
    }

    @Test
    fun the_english_r2_layer_has_its_underscore_on_the_baseline() {
        val e = PadInputEngine(PadConfig(stickSource = StickSource.STICKS), com.kazumaproject.core.domain.state.InputMode.ModeJapanese)
        var t = 0L
        // 英字: R3 (amendment 20: Select no longer cycles the modes).
        t += 16; e.onButton(PadButton.R3, true, t); t += 16; e.onButton(PadButton.R3, false, t)
        t += 16; e.onButton(PadButton.R2, true, t)
        val r = GuideLayout.layout(e.guide, GuideLayout.GuideCanvas(1280f, 385f, 2f), map = true)
        val underscore = r.texts.single { it.text == "_" }
        assertTrue(GuideLayout.keepsBaseline(underscore))
        assertEquals("the minus stays ink-centred", false, r.texts.filter { it.text == "-" }.any { GuideLayout.keepsBaseline(it) })
    }

    @Test
    fun baseline_origin_centres_the_ink_horizontally_and_keeps_the_metric_baseline() {
        // Ink 2..12 wide, font ascent 18 / descent 4 around a cell centre (100, 50).
        val (x, baseline) = GuideLayout.baselineOrigin(100f, 50f, left = 2f, right = 12f, ascent = 18f, descent = 4f)
        assertEquals(93f, x, 0.001f)            // ink centre 7 → 100
        assertEquals(57f, baseline, 0.001f)      // the centred line of text's baseline: 50 + (18 - 4) / 2
        // Ink centring would lift a low stroke (ink 1..3 below the baseline) to the cell centre: baseline 48.
        val (_, inkBaseline) = GuideLayout.inkOrigin(100f, 50f, 2f, 1f, 12f, 3f)
        assertEquals(48f, inkBaseline, 0.001f)
        assertTrue("the fix keeps the stroke lower than ink centring would", baseline > inkBaseline)
    }
}
