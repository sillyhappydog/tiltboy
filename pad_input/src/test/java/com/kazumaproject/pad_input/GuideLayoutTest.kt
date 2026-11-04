package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Readability rules for the wheel guides, checked at the RG477V's sizes
 * (assumed 1280×960 px, density 2.0 → the keyboard overlay band is 912×250 px,
 * the diagnostics band 1280×280 px).
 */
class GuideLayoutTest {

    // Keyboard overlay band in wheel modes: 96 % of the width × 27 % of the short side.
    private val overlay = GuideLayout.GuideCanvas(1229f, 259f, 2f)
    private val diagnostics = GuideLayout.GuideCanvas(1280f, 280f, 2f)
    private val kanaBox = GuideLayout.GuideCanvas(441f, 247f, 2f)

    private fun state(mode: PadMode, shift: Boolean, petal: Sector9, layout: FaceLayout = FaceLayout.NINTENDO): PadGuideState {
        // Amendment 20: the emoji wheel is the D-pad fallback's only (R3; the D-pad picks the petal, L1 is page 2).
        val wheel = mode == PadMode.EMOJI
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = if (wheel) StickSource.DPAD_FALLBACK else StickSource.STICKS, faceLayout = layout)))
        d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍"))
        when (mode) {
            PadMode.ENGLISH -> d.nextMode()
            PadMode.NUMBER -> { d.nextMode(); d.nextMode() }
            PadMode.EMOJI -> d.tap(PadButton.R3)
            else -> {}
        }
        if (wheel) {
            if (shift) d.press(PadButton.L1)
            if (petal.row == 0) d.press(PadButton.DPAD_UP)
            if (petal.row == 2) d.press(PadButton.DPAD_DOWN)
            if (petal.col == 0) d.press(PadButton.DPAD_LEFT)
            if (petal.col == 2) d.press(PadButton.DPAD_RIGHT)
            return d.engine.guide
        }
        d.holdSector(petal)
        return d.engine.guide
    }

    /** Since amendment 6 the emoji wheel is the only wheel; English and numbers are tenkey grids. */
    private fun allWheelStates(): List<Pair<String, PadGuideState>> = buildList {
        for (shift in listOf(false, true)) add("EMOJI shift=$shift" to state(PadMode.EMOJI, shift, Sector9.UP_RIGHT))
    }

    @Test
    fun wheel_characters_are_grouped_per_petal() {
        for ((name, s) in allWheelStates()) for (canvas in listOf(overlay, diagnostics)) {
            val texts = GuideLayout.layout(s, canvas).texts.filter { it.petal >= 0 }
            assertEquals("$name: 8 petals × 4 chars", 32, texts.size)
            for (t in texts) {
                val own = texts.filter { it !== t && it.petal == t.petal }.minOf { hypot(it.x - t.x, it.y - t.y) }
                val other = texts.filter { it.petal != t.petal }.minOf { hypot(it.x - t.x, it.y - t.y) }
                assertTrue("$name ${t.text}: own=$own other=$other", other > own * 1.3f)
            }
        }
    }

    @Test
    fun wheel_characters_are_not_tiny() {
        // The kana guide's characters (user: readable) are cell*0.5 in a 441 px box: about 36 px.
        val kana = GuideLayout.layout(state(PadMode.KANA, false, Sector9.UP), kanaBox).texts
            .first { it.style == GuideLayout.TextStyle.SELECTED }
        for ((name, s) in allWheelStates()) {
            val r = GuideLayout.layout(s, overlay)
            val small = r.texts.filter { it.petal >= 0 }.minOf { it.size }
            val big = r.texts.filter { it.style == GuideLayout.TextStyle.ACCENT }.minOf { it.size }
            // The small grid is the map; emoji glyphs are wider than letters, so they get 0.55×.
            val minRatio = 0.55f
            assertTrue("$name: small wheel chars ${small}px vs kana ${kana.size}px", small >= kana.size * minRatio)
            assertTrue("$name: enlarged petal chars ${big}px must be at least the kana size", big >= kana.size)
            assertTrue("$name: enlarged petal chars at least 20 dp", big >= 20f * overlay.density)
        }
    }

    @Test
    fun selected_petal_is_highlighted_and_repeated_large_as_a_flick_cross() {
        val s = state(PadMode.EMOJI, false, Sector9.UP_RIGHT) // petal 1 = 🔥 👍 + defaults
        val r = GuideLayout.layout(s, overlay)
        assertEquals(1, r.boxes.count { it.style == GuideLayout.BoxStyle.PETAL_SELECTED })
        assertEquals(7, r.boxes.count { it.style == GuideLayout.BoxStyle.PETAL })
        val petal = s.petals!![1]
        val big = r.texts.filter { it.style == GuideLayout.TextStyle.ACCENT && it.petal < 0 && it.text in petal }.sortedBy { it.y }
        assertEquals(petal.sorted(), big.map { it.text }.sorted())
        assertEquals("top emoji of the petal", petal[Daisywheel.POS_TOP], big.first().text)
        // Amendment 20: the wheel is the D-pad fallback's only, so the printed button names label the cross
        // (Nintendo: X above the top character, B below the bottom one); no stick arrows.
        val names = r.texts.filter { it.style == GuideLayout.TextStyle.LABEL && it.text in setOf("A", "B", "X", "Y") }
        assertEquals(setOf("A", "B", "X", "Y"), names.map { it.text }.toSet())
        val topChar = big.first()
        val up = names.first { it.text == "X" }
        assertTrue(up.y < topChar.y && kotlin.math.abs(up.x - topChar.x) < 1f)
        assertTrue(names.first { it.text == "B" }.y > big.last().y)
        assertTrue("no stick arrows", r.texts.none { it.style == GuideLayout.TextStyle.LABEL && it.text in setOf("←", "↑", "→", "↓") })
        assertTrue("no sentences on the keyboard", r.texts.none { it.text.contains("スティック") || it.text.contains("選ぶ") })
    }

    // pending_flick_highlights_the_emoji_it_will_type was removed by amendment 20: the emoji wheel is
    // the D-pad fallback's only, where nothing is flicked with the right stick.

    @Test
    fun fallback_labels_the_cross_with_the_printed_button_names() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK, faceLayout = FaceLayout.NINTENDO)))
        d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍"))
        d.tap(PadButton.R3); d.press(PadButton.DPAD_UP); d.press(PadButton.DPAD_RIGHT)
        val r = GuideLayout.layout(d.engine.guide, overlay)
        val labels = r.texts.filter { it.style == GuideLayout.TextStyle.LABEL && it.text in setOf("A", "B", "X", "Y") }
        assertEquals(setOf("A", "B", "X", "Y"), labels.map { it.text }.toSet())
        val big = r.texts.filter { it.style == GuideLayout.TextStyle.ACCENT && it.petal < 0 && it.text != "" }.sortedBy { it.y }
        val xLabel = labels.first { it.text == "X" }   // Nintendo: X at the top
        assertTrue(xLabel.y < big.first().y && kotlin.math.abs(xLabel.x - big.first().x) < 1f)
        assertTrue(r.texts.none { it.text in setOf("←", "↑", "→", "↓") && it.style == GuideLayout.TextStyle.LABEL })
    }

    @Test
    fun english_and_number_use_the_kana_shape() {
        for (mode in listOf(PadMode.ENGLISH, PadMode.NUMBER)) {
            val s = state(mode, false, Sector9.UP)
            assertTrue(s.isTenkey)
            val r = GuideLayout.layout(s, kanaBox)
            assertEquals("$mode: 9 grid cells", 9, r.boxes.count { it.style == GuideLayout.BoxStyle.CELL || it.style == GuideLayout.BoxStyle.CELL_SELECTED })
            assertTrue("$mode: flick cross present", r.texts.count { it.style == GuideLayout.TextStyle.NORMAL || it.style == GuideLayout.TextStyle.SELECTED } >= 12)
            assertTrue(r.boxes.none { it.style == GuideLayout.BoxStyle.PETAL })
        }
        val english = GuideLayout.layout(state(PadMode.ENGLISH, false, Sector9.UP), kanaBox)
        assertTrue(english.texts.any { it.text == "efgh" && it.style == GuideLayout.TextStyle.SELECTED })
        assertTrue(english.texts.any { it.text == "e" } && english.texts.any { it.text == "h" })
    }

    @Test
    fun compact_guides_draw_only_characters_and_role_words() {
        val s = state(PadMode.KANA, false, Sector9.UP)
        for (canvas in listOf(GuideLayout.GuideCanvas(1280f, 377f, 2f), kanaBox, diagnostics)) {
            val r = GuideLayout.layout(s, canvas)
            val allowed = s.gridLabels.toSet() + setOf(s.flickChars!!.center, s.flickChars!!.left, s.flickChars!!.up, s.flickChars!!.right, s.flickChars!!.down)
            for (t in r.texts) assertTrue("kana compact: '${t.text}'", t.text in allowed)
        }
        val e = state(PadMode.EMOJI, false, Sector9.UP_RIGHT)
        val r = GuideLayout.layout(e, overlay)
        val emoji = e.petals!!.flatten().toSet()
        val legendWords = e.legend.flatMap { it.split(" · ") }.toSet()
        for (t in r.texts) {
            val ok = t.text in emoji || t.text in setOf("A", "B", "X", "Y") || t.text.split(" · ").all { it in legendWords }
            assertTrue("emoji compact: '${t.text}'", ok)
        }
    }

    @Test
    fun bar_active_shows_the_bars_star_in_the_compact_guide() {
        // Amendment 18: with sticks the 英字 bar is gone (its symbols are on the R2 layer); the D-pad
        // fallback keeps it on L1 held.
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.tap(PadButton.R1)                       // the fallback's mode button: 英字
        d.press(PadButton.DPAD_UP)
        d.press(PadButton.L1)
        val s = d.engine.guide
        assertTrue(s.barActive)
        val r = GuideLayout.layout(s, kanaBox)
        for (c in listOf("\"", "(", ";", ")", "_", "&", "*", "=", "+")) assertTrue("bar char $c", r.texts.any { it.text == c })
        assertTrue("grid dimmed", r.texts.filter { it.text in setOf("abcd", "efgh", "ijkl") }.all { it.style == GuideLayout.TextStyle.DIM })
        assertTrue("no selected cell", r.boxes.none { it.style == GuideLayout.BoxStyle.CELL_SELECTED })
        val allowed = s.gridLabels.toSet() + s.barChars!!.values
        for (t in r.texts) assertTrue("compact bar: '${t.text}'", t.text in allowed)
    }

    @Test
    fun everything_stays_inside_the_canvas() {
        for ((name, s) in allWheelStates() + listOf("kana" to state(PadMode.KANA, false, Sector9.CENTER))) {
            for (canvas in listOf(overlay, diagnostics, kanaBox)) {
                val r = GuideLayout.layout(s, canvas)
                for (b in r.boxes) assertTrue("$name box $b", b.x >= -0.5f && b.y >= -0.5f && b.x + b.w <= canvas.width + 0.5f && b.y + b.h <= canvas.height + 0.5f)
                for (t in r.texts) assertTrue("$name text $t", t.y - t.size / 2 >= -1f && t.y + t.size / 2 <= canvas.height + 1f && t.x >= 0f && t.x <= canvas.width)
            }
        }
    }

    @Test
    fun no_petal_selected_shows_a_hint_instead_of_characters() {
        val s = state(PadMode.EMOJI, false, Sector9.CENTER)
        val r = GuideLayout.layout(s, overlay)
        assertTrue(r.texts.none { it.style == GuideLayout.TextStyle.ACCENT })
        assertTrue("no sentences on the keyboard", r.texts.none { it.text.contains("スティック") })
        assertTrue(s.isEmoji)
        assertEquals(InputMode.ModeJapanese, s.mode)
    }
}
