package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Amendment 21 (v9), the test list of the amendment: the ↘ grid key is 「や/わ」 with a six-slot
 * ring (60° sectors centred ↑ 90 や, ↗ 30 ゆ, ↘ −30 よ, ↓ −90 わ, ↙ −150 を, ↖ 150 ん; R1: ゃ ゅ ょ ゎ),
 * and R2 in かな is the 記号 layer on the left stick (↖ 〜 ↑ 。 ↗ … / ← ー → 、 / ↙ ？ ↓ ・ ↘ ！).
 */
class Amendment21Test {

    private fun kana(): PadDriver = PadDriver().also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }

    private fun at(deg: Double, r: Float): Pair<Float, Float> {
        val (x, y) = PadDriver.unitAt(deg)
        return x * r to y * r
    }

    private fun PadDriver.rightOut(deg: Double, r: Float = 0.95f) {
        at(deg, 0.5f).let { (x, y) -> right(x, y) }
        at(deg, r).let { (x, y) -> right(x, y) }
    }

    private fun PadDriver.rightRim(from: Double, to: Double, steps: Int = 8, r: Float = 0.95f) {
        for (i in 1..steps) at(from + (to - from) * i / steps, r).let { (x, y) -> right(x, y) }
    }

    private fun PadDriver.rightHome(deg: Double) {
        at(deg, 0.4f).let { (x, y) -> right(x, y) }
        right(0f, 0f)
    }

    private fun PadDriver.leftOutAt(deg: Double, r: Float = 0.95f) {
        at(deg, 0.5f).let { (x, y) -> left(x, y) }
        at(deg, r).let { (x, y) -> left(x, y) }
    }

    private fun PadDriver.leftRim(from: Double, to: Double, steps: Int = 8, r: Float = 0.95f) {
        for (i in 1..steps) at(from + (to - from) * i / steps, r).let { (x, y) -> left(x, y) }
    }

    private fun PadDriver.leftHome(deg: Double) {
        at(deg, 0.4f).let { (x, y) -> left(x, y) }
        left(0f, 0f)
    }

    // ------------------------------------------------------------------ 1. the grid and the six slots

    @Test
    fun the_down_right_key_is_ya_wa() {
        assertEquals(KanaGrid.YA_WA_KEY, KanaGrid.keyFor(Sector9.DOWN_RIGHT))
        assertEquals("や/わ", KanaGrid.YA_WA_LABEL)
        val g = kana().engine.guide
        assertEquals(listOf("か", "さ", "た", "な", "あ", "は", "ま", "ら", "や/わ"), g.gridLabels)
        assertFalse("the 記号 key is gone", g.gridLabels.contains("記号"))
    }

    @Test
    fun each_sector_centre_gives_its_kana() {
        val expected = listOf("や", "ゆ", "よ", "わ", "を", "ん")
        for (slot in 0 until KanaHexagon.SLOTS) {
            val d = kana()
            d.yaWa(slot)
            assertEquals("slot $slot at ${KanaHexagon.CENTRES_DEG[slot]}°", expected[slot], d.typed())
            assertEquals(slot, KanaHexagon.slotAt(KanaHexagon.CENTRES_DEG[slot]))
        }
        assertEquals(listOf(90.0, 30.0, -30.0, -90.0, -150.0, 150.0), KanaHexagon.CENTRES_DEG)
    }

    @Test
    fun boundaries_split_on_each_side() {
        // Boundaries at 60°, 0°, −60°, −120°, 180° and 120°: one degree on each side.
        val cases = listOf(
            61.0 to "や", 59.0 to "ゆ",
            1.0 to "ゆ", -1.0 to "よ",
            -59.0 to "よ", -61.0 to "わ",
            -119.0 to "わ", -121.0 to "を",
            -179.0 to "を", 179.0 to "ん",
            121.0 to "ん", 119.0 to "や",
        )
        for ((deg, kana) in cases) {
            val d = kana()
            d.holdSector(Sector9.DOWN_RIGHT)
            d.flickAt(deg)
            assertEquals("$deg°", kana, d.typed())
        }
        // The slot function itself, exactly on the boundaries: the next slot clockwise.
        assertEquals(listOf(1, 2, 3, 4, 5, 0), listOf(60.0, 0.0, -60.0, -120.0, 180.0, 120.0).map { KanaHexagon.slotAt(it) })
    }

    @Test
    fun the_rg_pad_axis_values_hit_their_slots() {
        // ゆ = RX=0.87 RY=-0.5 (the amendment's example), and the other five the same way.
        val values = listOf(0f to -1f, 0.87f to -0.5f, 0.87f to 0.5f, 0f to 1f, -0.87f to 0.5f, -0.87f to -0.5f)
        for ((slot, v) in values.withIndex()) assertEquals("slot $slot", slot, KanaHexagon.slotOf(v.first, v.second))
    }

    // ------------------------------------------------------------------ 2. re-aim, latch, return band

    @Test
    fun re_aim_up_out_rotated_to_down_left_types_wo() {
        val d = kana()
        d.holdSector(Sector9.DOWN_RIGHT)
        d.rightOut(90.0)                               // や
        assertEquals(0, d.engine.guide.pendingSlot)
        d.rightRim(90.0, 210.0, steps = 8)             // counter-clockwise past ん to を (−150° = 210°)
        assertEquals("the highlight follows to ↙", 4, d.engine.guide.pendingSlot)
        assertTrue(d.engine.guide.hexagon)
        d.rightHome(210.0)
        assertEquals("を", d.typed())
        assertTrue(d.notes.any { it.contains("re-aimed") && it.contains("six slots") })
    }

    @Test
    fun the_return_band_does_not_re_aim() {
        val d = kana()
        d.holdSector(Sector9.DOWN_RIGHT)
        d.rightOut(30.0)                               // ゆ
        at(-90.0, 0.5f).let { (x, y) -> d.right(x, y) } // ↓ but on the way back (0.5): ignored
        at(150.0, 0.35f).let { (x, y) -> d.right(x, y) }
        d.right(0f, 0f)
        assertEquals("ゆ", d.typed())
    }

    @Test
    fun the_key_is_latched_when_the_flick_arms() {
        // The left stick leaves や/わ after the flick armed: still the や/わ key.
        val d = kana()
        d.holdSector(Sector9.DOWN_RIGHT)
        d.rightOut(-90.0)                              // わ
        d.holdSector(Sector9.UP_LEFT)                  // か, while the right stick is out
        assertTrue("the ring stays the hexagon while the flick is out", d.engine.guide.hexagon)
        d.rightHome(-90.0)
        assertEquals("わ", d.typed())
        // And the other way: a pentagon flick armed on か stays a pentagon flick.
        d.clear()
        d.rightOut(90.0)
        d.holdSector(Sector9.DOWN_RIGHT)
        assertFalse(d.engine.guide.hexagon)
        d.rightHome(90.0)
        assertEquals("か", d.typed())
    }

    // ------------------------------------------------------------------ 3. R1, L2, L1

    @Test
    fun r1_gives_small_ya_yu_yo_wa_and_leaves_wo_n() {
        val d = kana()
        d.press(PadButton.R1)
        for (slot in 0 until KanaHexagon.SLOTS) d.yaWa(slot)
        d.release(PadButton.R1)
        assertEquals("ゃゅょゎをん", d.typed())
        assertEquals("small kana go out as text, を ん as the わ key",
            listOf<PadAction>(PadAction.InsertText("ゃ"), PadAction.InsertText("ゅ"), PadAction.InsertText("ょ"), PadAction.InsertText("ゎ"),
                PadAction.KanaKey(Key.KeyWA, GestureType.FlickLeft, 'を'), PadAction.KanaKey(Key.KeyWA, GestureType.FlickTop, 'ん')),
            d.actions)
    }

    @Test
    fun l2_and_l1_have_no_effect_on_the_ya_wa_key() {
        val d = kana()
        d.press(PadButton.L2); d.yaWa(0); d.yaWa(3); d.release(PadButton.L2)
        d.press(PadButton.L1); d.yaWa(1); d.yaWa(5); d.release(PadButton.L1)
        assertEquals("やわゆん", d.typed())
        // L2 pressed first, then R1: R1 still gives the small kana here (only the 小文字 role applies).
        d.clear()
        d.press(PadButton.L2); d.press(PadButton.R1); d.yaWa(2); d.release(PadButton.R1); d.release(PadButton.L2)
        assertEquals("ょ", d.typed())
        // The guide: L2 held on the や/わ key draws the six plain characters, not dimmed.
        d.press(PadButton.L2); d.holdSector(Sector9.DOWN_RIGHT)
        val g = d.engine.guide
        assertEquals(listOf("や", "ゆ", "よ", "わ", "を", "ん"), g.hexagonChars)
        assertTrue(g.flickDim.isEmpty())
        d.release(PadButton.L2)
    }

    @Test
    fun r1_is_latched_at_arming() {
        val d = kana()
        d.holdSector(Sector9.DOWN_RIGHT)
        d.press(PadButton.R1); d.rightOut(90.0); d.release(PadButton.R1); d.rightHome(90.0)
        assertEquals("R1 let go before the stick is home: still ゃ", "ゃ", d.typed())
        d.clear()
        d.rightOut(90.0); d.press(PadButton.R1); d.rightHome(90.0); d.release(PadButton.R1)
        assertEquals("R1 pressed after arming: や", "や", d.typed())
    }

    @Test
    fun the_guide_with_r1_on_the_ya_wa_key() {
        val d = kana()
        d.press(PadButton.R1); d.holdSector(Sector9.DOWN_RIGHT)
        val g = d.engine.guide
        assertTrue(g.hexagon)
        assertFalse(g.pentagon)
        assertEquals(listOf("ゃ", "ゅ", "ょ", "ゎ", "を", "ん"), g.hexagonChars)
        assertEquals(listOf(false, false, false, false, true, true), g.flickDim)
        assertEquals("ゃ/ゎ", g.gridLabels[8])
        d.rightOut(-30.0)
        assertEquals(2, d.engine.guide.pendingSlot)
        assertNull(d.engine.guide.pendingFlick)
    }

    // ------------------------------------------------------------------ 4. R2 = 記号 in かな

    @Test
    fun r2_types_the_eight_symbols() {
        val d = kana()
        val order = listOf(Sector9.UP_LEFT, Sector9.UP, Sector9.UP_RIGHT, Sector9.LEFT, Sector9.RIGHT, Sector9.DOWN_LEFT, Sector9.DOWN, Sector9.DOWN_RIGHT)
        for (s in order) d.layer(s)
        assertEquals("〜。…ー、？・！", d.typed())
        assertEquals("記号", d.engine.guide.labels.buttons[PadButton.R2])
        assertEquals("Symbols", PadDriver(PadInputEngine(PadConfig(words = TestWords.en))).engine.guide.labels.buttons[PadButton.R2])
    }

    @Test
    fun r2_centre_is_empty_and_alone_types_nothing() {
        val d = kana()
        d.tap(PadButton.R2)
        d.press(PadButton.R2); d.left(0.2f, 0.1f); d.left(0f, 0f); d.release(PadButton.R2)
        assertTrue(d.actions.isEmpty())
        assertNull(KanaGrid.KANA_SYMBOLS[Sector9.CENTER])
        d.press(PadButton.R2)
        assertEquals("", d.engine.guide.gridLabels[4])
        d.release(PadButton.R2)
    }

    @Test
    fun r2_re_aim_left_out_rotated_to_down_left_is_question_mark() {
        val d = kana()
        d.press(PadButton.R2)
        d.leftOutAt(180.0)                             // ← ー
        assertEquals(Sector9.LEFT, d.engine.guide.pendingLayer)
        d.leftRim(180.0, 225.0, steps = 3)             // ↙ ？
        assertEquals(Sector9.DOWN_LEFT, d.engine.guide.pendingLayer)
        d.leftHome(225.0)
        d.release(PadButton.R2)
        assertEquals("？", d.typed())
    }

    @Test
    fun a_left_stick_already_tilted_when_r2_is_pressed_is_ignored() {
        val d = kana()
        d.leftOut(Sector9.UP)                          // さ selected
        d.press(PadButton.R2)
        d.left(0f, -0.9f); d.left(-0.5f, -0.8f)        // still out, moving
        d.left(0f, 0f)                                 // home: nothing typed
        assertTrue(d.actions.isEmpty())
        d.flickLeft8(Sector9.UP)                       // now a real layer flick: 。
        d.release(PadButton.R2)
        assertEquals("。", d.typed())
    }

    @Test
    fun r1_has_no_effect_while_r2_is_held() {
        val d = kana()
        d.press(PadButton.R1); d.press(PadButton.R2)
        d.flickLeft8(Sector9.LEFT); d.flickLeft8(Sector9.DOWN)
        d.release(PadButton.R2); d.release(PadButton.R1)
        assertEquals("ー・", d.typed())
        assertTrue("R1 + R2 no longer gives small kana", d.actions.none { it == PadAction.InsertText("ゃ") })
    }

    @Test
    fun the_old_r2_ya_wa_layer_is_gone() {
        val d = kana()
        d.press(PadButton.R2)
        val g = d.engine.guide
        assertTrue(g.layer)
        assertTrue("no や/わ characters on the layer", g.gridLabels.none { it in setOf("や", "ゆ", "よ", "わ", "を", "ん") })
        d.release(PadButton.R2)
    }

    @Test
    fun under_r2_the_ring_of_the_ya_wa_key_is_faint_and_six() {
        // The layer remembers the key selected when R2 went on: here the や/わ key.
        val d = kana()
        d.holdSector(Sector9.DOWN_RIGHT)
        d.press(PadButton.R2)                          // pressed while the stick is out: blocked until home
        val g = d.engine.guide
        assertTrue(g.layer)
        assertTrue("the faint ring is the や/わ key's six slots", g.hexagon)
        assertEquals(listOf("や", "ゆ", "よ", "わ", "を", "ん"), g.hexagonChars)
        val r = GuideLayout.layout(g, GuideLayout.GuideCanvas(1280f, 385f, 2f), map = true)
        val k = GuideMap.Kana
        val ring = r.texts.filter { it.x > k.RING_LEFT && it.x < k.RING_RIGHT && it.y > k.RING_TOP && it.y < k.RING_BOTTOM }
        assertEquals(6, ring.size)
        assertTrue(ring.all { it.style == GuideLayout.TextStyle.FAINT })
        d.release(PadButton.R2)
    }

    // ------------------------------------------------------------------ 5. the guide's six cells

    @Test
    fun six_round_cells_at_the_pentagon_reach_and_none_touch() {
        for (canvas in listOf(GuideLayout.GuideCanvas(1280f, 385f, 2f), GuideLayout.GuideCanvas(960f, 289f, 2f))) {
            val d = kana()
            d.holdSector(Sector9.DOWN_RIGHT)
            val r = GuideLayout.layout(d.engine.guide, canvas, map = true)
            val scale = canvas.width / GuideMap.BASE_W
            val k = GuideMap.Kana
            val ringBox = r.boxes.single { it.style == GuideLayout.BoxStyle.RING }
            val cx = ringBox.x + ringBox.w / 2f; val cy = ringBox.y + ringBox.h / 2f
            val cells = r.boxes.filter { (it.style == GuideLayout.BoxStyle.CELL || it.style == GuideLayout.BoxStyle.CELL_SELECTED) && it.x > k.GRID_RIGHT * scale && it.x < k.RING_RIGHT * scale }
            assertEquals("${canvas.width}: six cells", 6, cells.size)
            for ((slot, c) in cells.withIndex()) {
                assertEquals("cell radius", k.CELL_R * scale, c.w / 2f, 0.01f)
                val ccx = c.x + c.w / 2f; val ccy = c.y + c.h / 2f
                val rad = Math.toRadians(KanaHexagon.CENTRES_DEG[slot])
                assertEquals("slot $slot x", cx + (cos(rad) * k.REACH * scale).toFloat(), ccx, 0.05f)
                assertEquals("slot $slot y", cy - (sin(rad) * k.REACH * scale).toFloat(), ccy, 0.05f)
            }
            for (i in cells.indices) for (j in cells.indices) if (i < j) {
                val a = cells[i]; val b = cells[j]
                val dist = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                assertTrue("${canvas.width}: cells $i and $j must not touch ($dist)", dist > a.w + 1.0)
            }
            for (c in listOf("や", "ゆ", "よ", "わ", "を", "ん")) assertTrue("ring $c", r.texts.any { it.text == c && it.x > k.GRID_RIGHT * scale })
        }
    }

    @Test
    fun the_d_pad_fallback_keeps_the_v4_grid() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.press(PadButton.DPAD_DOWN); d.tap(PadButton.A); d.tap(PadButton.X); d.release(PadButton.DPAD_DOWN)  // ↓ や key: や （
        assertEquals("や（", d.typed())
        assertFalse(d.engine.guide.hexagon)
    }
}
