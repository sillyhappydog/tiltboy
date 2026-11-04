package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 3: the guide must fit inside the keyboard's visible area. Modelled on the RG477V
 * screenshot of 2026-11-01: window 1280 × 960 landscape, density 2.0, keyboard root from
 * y = 250 to the window bottom with an 85 px bottom inset (visible keyboard = 0,250 – 1280,875).
 */
class GuidePlacementTest {

    private val density = 2f
    private val shortSide = 960
    private val visible = GuidePlacement.Rect(0, 250, 1280, 875)

    private fun state(mode: PadMode, shift: Boolean, sector: Sector9): PadGuideState {
        // Amendment 20: the emoji wheel is the D-pad fallback's only (R3, the D-pad picks the petal, L1 the bar).
        val wheel = mode == PadMode.EMOJI
        val e = PadInputEngine(PadConfig(stickSource = if (wheel) StickSource.DPAD_FALLBACK else StickSource.STICKS, words = TestWords.ja))
        e.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍"))
        var t = 0L
        fun button(b: PadButton, down: Boolean) { t += 16; e.onButton(b, down, t) }
        // Amendment 20: R3 steps the text modes (Select no longer cycles them).
        when (mode) {
            PadMode.ENGLISH -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.NUMBER -> repeat(2) { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.EMOJI -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            else -> {}
        }
        if (wheel) {
            if (shift) button(PadButton.L1, true)
            if (sector.row == 0) button(PadButton.DPAD_UP, true)
            if (sector.row == 2) button(PadButton.DPAD_DOWN, true)
            if (sector.col == 0) button(PadButton.DPAD_LEFT, true)
            if (sector.col == 2) button(PadButton.DPAD_RIGHT, true)
            return e.guide
        }
        if (shift) button(PadButton.L2, true)
        val x = (sector.col - 1).toFloat()
        val y = (sector.row - 1).toFloat()
        t += 16; e.onSticks(x, y, 0f, 0f, t)
        return e.guide
    }

    private val states = listOf(
        "kana" to state(PadMode.KANA, false, Sector9.UP),
        "kana_shift" to state(PadMode.KANA, true, Sector9.LEFT),
        "kana_down" to state(PadMode.KANA, false, Sector9.DOWN_RIGHT),
        "english" to state(PadMode.ENGLISH, false, Sector9.UP_RIGHT),
        "number_shift" to state(PadMode.NUMBER, true, Sector9.RIGHT),
        "emoji" to state(PadMode.EMOJI, false, Sector9.DOWN),
        "emoji_shift" to state(PadMode.EMOJI, true, Sector9.DOWN),
    )

    @Test
    fun corner_frame_is_inside_the_visible_keyboard_at_1280x960_with_85px_inset() {
        for (kana in listOf(true, false)) {
            val f = GuidePlacement.cornerFrame(visible, kana, shortSide, density)
            assertTrue("frame $f", visible.contains(f))
            assertTrue("bottom margin", f.bottom <= visible.bottom - 16)
        }
        // Kana keeps v1's size when there is room (441 × 247 at density 2).
        val kana = GuidePlacement.cornerFrame(visible, true, shortSide, density)
        assertEquals(441, kana.width)
        assertEquals(246, kana.height)
        assertEquals(875 - 16, kana.bottom)
        assertEquals(1280 - 8, kana.right)
    }

    @Test
    fun every_character_lies_inside_the_visible_keyboard() {
        for ((name, s) in states) {
            val f = GuidePlacement.cornerFrame(visible, s.isTenkey, shortSide, density)
            val r = GuideLayout.layout(s, GuideLayout.GuideCanvas(f.width.toFloat(), f.height.toFloat(), density))
            for (t in r.texts) {
                val b = GuidePlacement.textBounds(t, f.left, f.top)
                assertTrue("$name: '${t.text}' at $b leaves visible $visible", visible.contains(b))
            }
            for (box in r.boxes) {
                val b = GuidePlacement.Rect(f.left + box.x.toInt(), f.top + box.y.toInt(), f.left + (box.x + box.w).toInt(), f.top + (box.y + box.h).toInt())
                assertTrue("$name: box $b leaves visible $visible", visible.contains(b))
            }
        }
    }

    @Test
    fun frame_shrinks_when_the_visible_keyboard_is_small() {
        // Only the key rows visible (377 px, as measured on the screenshot) and a narrow phone.
        for (v in listOf(GuidePlacement.Rect(0, 498, 1280, 875), GuidePlacement.Rect(0, 1200, 720, 1500), GuidePlacement.Rect(0, 0, 300, 120))) {
            for (kana in listOf(true, false)) {
                val f = GuidePlacement.cornerFrame(v, kana, minOf(v.width, 960), density)
                assertTrue("$v kana=$kana → $f", v.contains(f) && !f.isEmpty)
                assertTrue("height cap $f in $v", f.height <= v.height * GuidePlacement.MAX_HEIGHT_OF_VISIBLE + 1)
            }
        }
    }

    @Test
    fun empty_visible_area_gives_an_empty_frame() {
        val f = GuidePlacement.cornerFrame(GuidePlacement.Rect(10, 10, 10, 300), true, 960, density)
        assertTrue(f.isEmpty)
    }

    @Test
    fun text_bounds_follow_alignment() {
        val c = GuideLayout.Primitive.Text(100f, 50f, 20f, "あい", GuideLayout.TextStyle.NORMAL)
        val l = GuideLayout.Primitive.Text(100f, 50f, 20f, "あい", GuideLayout.TextStyle.NORMAL, align = GuideLayout.Align.LEFT)
        val w = GuideLayout.textWidth("あい", 20f) * 1.08f
        assertEquals((100f - w / 2f).toInt(), GuidePlacement.textBounds(c, 0, 0).left)
        assertEquals(100, GuidePlacement.textBounds(l, 0, 0).left)
        assertEquals(40, GuidePlacement.textBounds(c, 0, 0).top)
        val r = GuideLayout.Primitive.Text(100f, 50f, 20f, "あい", GuideLayout.TextStyle.NORMAL, align = GuideLayout.Align.RIGHT)
        assertEquals((100f - w).toInt(), GuidePlacement.textBounds(r, 0, 0).left)
    }
}
