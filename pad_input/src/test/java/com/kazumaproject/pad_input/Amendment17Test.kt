package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 17 (v6), the test list of the amendment, with the user's decision on it (the 記号 key
 * is 。 、 ！ ？ ー, the や/わ layer's middle row is empty). Amendment 21 (v9) moved both: the R2
 * layer is 記号 in かな (。、！？ー keep their directions there) and や/わ is the ↘ grid key:
 *  - re-aim by rotating, for every flick: while armed, each sample at a radius ≥ 0.6 sets the
 *    target to its own sector; samples between 0.3 and 0.6 (the way back) do not; under 0.3 the
 *    current target is typed (an empty slot: nothing). The guide's pending highlight follows;
 *  - the R2 layer is 「や/わ」: ↖ や ↑ ゆ ↗ よ / (nothing) (nothing) (nothing) / ↙ わ ↓ を ↘ ん;
 *  - the 記号 key: ↑ 。 → 、 ↘ ！ ↙ ？ ← ー (amendment 18 removed the cross setting);
 *  - centring by ink bounds ([GuideLayout.inkOrigin]).
 */
class Amendment17Test {

    private fun pentagon(): PadDriver = PadDriver().also { it.left(0.05f, 0f); it.leftRelease(); it.clear() }

    // Pentagon slot centres: ↑ (あ段) → (い段) ↘ (う段) ↙ (え段) ← (お段).
    private val UP = 90.0
    private val RIGHT = 18.0
    private val DOWN_RIGHT = -54.0
    private val DOWN_LEFT = -126.0
    private val LEFT = 162.0

    // ---- stick helpers (0° = right, 90° = up, counter-clockwise; y of the stick is down)

    private fun at(deg: Double, r: Float): Pair<Float, Float> {
        val (x, y) = PadDriver.unitAt(deg)
        return x * r to y * r
    }

    /** Right stick out toward [deg] (0.5 then [r]): arms the flick. */
    private fun PadDriver.rightOut(deg: Double, r: Float = 0.95f) {
        at(deg, 0.5f).let { (x, y) -> right(x, y) }
        at(deg, r).let { (x, y) -> right(x, y) }
    }

    /** Rotate the right stick along the rim from [from] to [to] in [steps] samples at radius [r]. */
    private fun PadDriver.rightRim(from: Double, to: Double, steps: Int = 8, r: Float = 0.95f) {
        for (i in 1..steps) at(from + (to - from) * i / steps, r).let { (x, y) -> right(x, y) }
    }

    /** Bring the right stick home from [deg] (0.4, then 0). */
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

    // ------------------------------------------------------------------ 1. re-aim, right stick

    @Test
    fun pentagon_ka_up_out_rotate_to_down_right_types_ku() {
        val d = pentagon()
        d.holdSector(Sector9.UP_LEFT)                  // か
        d.rightOut(UP)
        assertEquals("armed on か (↑)", 0, d.engine.guide.pendingSlot)
        d.rightRim(UP, RIGHT, steps = 4)
        assertEquals("the highlight follows to → (き)", 1, d.engine.guide.pendingSlot)
        d.rightRim(RIGHT, DOWN_RIGHT, steps = 4)
        assertEquals("…and on to ↘ (く)", 2, d.engine.guide.pendingSlot)
        assertTrue("nothing typed while out", d.actions.isEmpty())
        d.rightHome(DOWN_RIGHT)
        assertEquals("く", d.typed())
        assertEquals(PadAction.KanaKey(Key.KeyKA, GestureType.FlickTop, 'く'), d.actions.single())
        assertNull(d.engine.guide.pendingSlot)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("re-aimed") && it.contains("↘") })
    }

    @Test
    fun pentagon_rotating_all_the_way_round_types_where_it_ends() {
        val d = pentagon()
        d.holdSector(Sector9.UP_LEFT)
        d.rightOut(UP)
        d.rightRim(UP, LEFT - 360.0, steps = 20)       // clockwise past → ↘ ↙ round to ← (お段)
        d.rightHome(LEFT)
        assertEquals("こ", d.typed())
        // Counter-clockwise from ↘ back to ↑.
        d.clear()
        d.rightOut(DOWN_RIGHT)
        d.rightRim(DOWN_RIGHT, UP, steps = 8)
        d.rightHome(UP)
        assertEquals("か", d.typed())
    }

    @Test
    fun the_return_path_does_not_re_aim() {
        val d = pentagon()
        d.holdSector(Sector9.UP_LEFT)
        d.rightOut(DOWN_RIGHT)                         // く
        at(200.0, 0.45f).let { (x, y) -> d.right(x, y) }   // between 0.3 and 0.6, in お's sector: ignored
        at(250.0, 0.35f).let { (x, y) -> d.right(x, y) }   // in え's sector: ignored
        assertEquals("still ↘", 2, d.engine.guide.pendingSlot)
        d.right(0.05f, 0.1f)                           // under 0.3
        assertEquals("く", d.typed())
        // Just past the threshold counts as out at the rim (≥ 0.6) and re-aims.
        d.clear()
        d.rightOut(DOWN_RIGHT)
        at(LEFT, 0.62f).let { (x, y) -> d.right(x, y) }
        d.right(0f, 0f)
        assertEquals("こ", d.typed())
    }

    @Test
    fun re_aim_keeps_the_hold_latched_at_arming() {
        val d = pentagon()
        d.press(PadButton.L2)                          // 濁点
        d.holdSector(Sector9.UP_LEFT)
        d.rightOut(UP)                                 // が armed
        d.rightRim(UP, 45.0, steps = 3)
        d.release(PadButton.L2)                        // let go mid-rotation
        d.rightRim(45.0, RIGHT, steps = 3)
        assertEquals(1, d.engine.guide.pendingSlot)
        assertEquals("the guide still shows the latched 濁点", "ぎ", d.engine.guide.flickChars!!.slot(1))
        d.rightHome(RIGHT)
        assertEquals("ぎ", d.typed())
        // A hold pressed mid-rotation does not change the flick either.
        d.clear()
        d.rightOut(UP)
        d.rightRim(UP, RIGHT, steps = 3)
        d.press(PadButton.L2)
        d.rightHome(RIGHT)
        d.release(PadButton.L2)
        assertEquals("き", d.typed())
    }

    @Test
    fun re_aim_keeps_the_key_latched_at_arming() {
        val d = pentagon()
        d.holdSector(Sector9.UP_LEFT)                  // か
        d.rightOut(UP)
        d.holdSector(Sector9.RIGHT)                    // the left stick moves to は while the right one rotates
        d.rightRim(UP, DOWN_RIGHT, steps = 6)
        d.rightHome(DOWN_RIGHT)
        assertEquals("く", d.typed())
    }

    // ------------------------------------------------------------------ 2. the R2 layer (amendment 21: 記号 in かな too)

    @Test
    fun layer_tilde_out_rotate_to_down_types_middle_dot() {
        val d = pentagon()
        d.press(PadButton.R2)
        d.leftOutAt(135.0)                             // ↖ 〜
        assertEquals(Sector9.UP_LEFT, d.engine.guide.pendingLayer)
        d.leftRim(135.0, 270.0, steps = 9)             // counter-clockwise past ← and ↙ to ↓
        assertEquals("the highlight follows to ↓", Sector9.DOWN, d.engine.guide.pendingLayer)
        assertEquals("・", d.engine.guide.preview)
        d.leftHome(270.0)
        d.release(PadButton.R2)
        assertEquals("・", d.typed())
    }

    @Test
    fun layer_left_and_right_type_and_the_centre_is_empty() {
        val d = pentagon()
        d.press(PadButton.R2)
        d.flickLeft8(Sector9.RIGHT)                    // → 、
        d.flickLeft8(Sector9.LEFT)                     // ← ー
        assertEquals("、ー", d.typed())
        // Out toward ← and rotated onto ↙: ？ (amendment 21's re-aim test).
        d.clear()
        d.leftOutAt(180.0)
        assertEquals(Sector9.LEFT, d.engine.guide.pendingLayer)
        assertEquals("ー", d.engine.guide.preview)
        d.leftRim(180.0, 225.0, steps = 3)
        d.leftHome(225.0)
        assertEquals("？", d.typed())
        d.release(PadButton.R2)
        assertEquals("the centre of the layer is empty", "", KanaGrid.layerLabels(PadMode.KANA)[4])
    }

    @Test
    fun layer_return_path_does_not_re_aim() {
        val d = pentagon()
        d.press(PadButton.R2)
        d.leftOutAt(315.0)                             // ↘ ！
        at(270.0, 0.5f).let { (x, y) -> d.left(x, y) } // ↓ but on the way back: ignored
        at(225.0, 0.35f).let { (x, y) -> d.left(x, y) }
        d.left(0f, 0f)
        d.release(PadButton.R2)
        assertEquals("！", d.typed())
    }

    @Test
    fun layer_ignores_r1_and_is_latched_at_arming() {
        // Amendment 21: R1 has no effect while R2 is held in かな.
        val d = pentagon()
        d.press(PadButton.R1); d.press(PadButton.R2)
        d.flickLeft8(Sector9.UP_RIGHT)
        assertEquals("…", d.typed())
        d.release(PadButton.R1)
        // R2 let go mid-rotation: the layer is latched: ↑ 。 armed, rotated to ↗ ….
        d.clear()
        d.leftOutAt(90.0)
        d.release(PadButton.R2)
        d.leftRim(90.0, 45.0, steps = 3)
        assertTrue(d.engine.guide.layer)
        assertEquals(Sector9.UP_RIGHT, d.engine.guide.pendingLayer)
        d.leftHome(45.0)
        assertEquals("…", d.typed())
    }

    @Test
    fun layer_labels_and_role_name() {
        val d = pentagon()
        d.press(PadButton.R2)
        val g = d.engine.guide
        assertEquals(listOf("〜", "。", "…", "ー", "", "、", "？", "・", "！"), g.gridLabels)
        assertEquals("記号", g.labels.buttons[PadButton.R2])
        assertEquals("記号", GuideLabels.roleLabel(PadRole.YA_WA_LAYER, PadMode.KANA, TestWords.ja))
        assertEquals("Symbols", GuideLabels.roleLabel(PadRole.YA_WA_LAYER, PadMode.KANA, TestWords.en))
        // R1 + R2: nothing changes, nothing is dimmed.
        d.press(PadButton.R1)
        assertEquals(g.gridLabels, d.engine.guide.gridLabels)
        assertTrue(d.engine.guide.gridDim.none { it })
        // The settings name says 記号 in both languages.
        assertTrue(TestWords.xml("values-ja").contains("<string name=\"pad_role_ya_wa_layer\">記号（"))
        assertTrue(TestWords.xml("values").contains("<string name=\"pad_role_ya_wa_layer\">Symbols ("))
    }

    // ------------------------------------------------------------------ 3. 英字 / 数字 / the bar / 絵文字

    @Test
    fun english_abcd_left_out_rotate_to_down_types_d() {
        val d = pentagon()
        d.nextMode(); d.clear()             // 英字
        d.holdSector(Sector9.UP_LEFT)                  // abcd
        d.rightOut(180.0)
        assertEquals(Dir4.LEFT, d.engine.guide.pendingFlick)
        d.rightRim(180.0, 270.0, steps = 6)
        assertEquals(Dir4.DOWN, d.engine.guide.pendingFlick)
        d.rightHome(270.0)
        assertEquals("d", d.typed())
        // 数字: 1234 ← (1) rotated to ↑ (2).
        d.nextMode(); d.clear()
        d.holdSector(Sector9.UP_LEFT)
        d.rightOut(180.0)
        d.rightRim(180.0, 90.0, steps = 6)
        d.rightHome(90.0)
        assertEquals("2", d.typed())
    }

    @Test
    fun english_symbols_layer_re_aims_over_eight_directions() {
        // Amendment 18: the 英字 bar's symbols are on the R2 layer (the left stick), re-aimed like や/わ.
        val d = pentagon()
        d.nextMode(); d.clear()             // 英字
        d.press(PadButton.R2)
        d.leftOutAt(90.0)                              // ↑ ;
        assertEquals(Sector9.UP, d.engine.guide.pendingLayer)
        d.leftRim(90.0, 45.0, steps = 2)
        assertEquals(Sector9.UP_RIGHT, d.engine.guide.pendingLayer)
        d.leftRim(45.0, -45.0, steps = 4)              // past → to ↘
        assertEquals(Sector9.DOWN_RIGHT, d.engine.guide.pendingLayer)
        d.leftHome(-45.0)
        d.release(PadButton.R2)
        assertEquals("+", d.typed())
        // The return path does not re-aim on the layer either.
        d.clear()
        d.press(PadButton.R2)
        d.leftOutAt(180.0)                             // ← (
        at(135.0, 0.5f).let { (x, y) -> d.left(x, y) }
        d.left(0f, 0f)
        d.release(PadButton.R2)
        assertEquals("(", d.typed())
    }

    // emoji_wheel_flick_re_aims was removed by amendment 20: the emoji wheel is the D-pad fallback's
    // only (no right-stick flick there); Start opens Sumire's emoji list instead.

    // ------------------------------------------------------------------ 4. the v6–v8 記号 key, now the R2 layer (amendment 21)

    @Test
    fun the_v8_symbol_directions_live_on_the_r2_layer() {
        // Amendment 21: the 記号 key is gone; 。、！？ー keep their directions on R2 + the left stick
        // (↑ 。 → 、 ↘ ！ ↙ ？ ← ー), and 〜 … ・ are reachable again.
        val p = pentagon()
        p.leftRelease()
        for (s in listOf(Sector9.UP, Sector9.RIGHT, Sector9.DOWN_RIGHT, Sector9.DOWN_LEFT, Sector9.LEFT)) p.layer(s)
        assertEquals("。、！？ー", p.typed())
        p.clear()
        for (s in listOf(Sector9.UP_LEFT, Sector9.UP_RIGHT, Sector9.DOWN)) p.layer(s)
        assertEquals("〜…・", p.typed())
        // The ↘ key's ring is the や/わ key's six slots now, no symbols.
        p.holdSector(Sector9.DOWN_RIGHT)
        assertEquals(listOf("や", "ゆ", "よ", "わ", "を", "ん"), p.engine.guide.hexagonChars)
    }

    @Test
    fun ge_e_mu_with_r2_left() {
        // ゲーム: L2 + か (↖) + ↙ = げ, R2 + left ← = ー, ま (↙) + ↘ = む.
        val d = pentagon()
        d.press(PadButton.L2); d.holdSector(Sector9.UP_LEFT); d.flickAt(DOWN_LEFT); d.release(PadButton.L2)
        d.leftRelease(); d.layer(Sector9.LEFT)
        d.holdSector(Sector9.DOWN_LEFT); d.flickAt(DOWN_RIGHT)
        assertEquals("げーむ", d.typed())
        assertEquals(PadAction.KanaKey(Key.KeyWA, GestureType.FlickRight, 'ー'), d.actions[1])
    }

    // ------------------------------------------------------------------ 5. the guide follows the target

    @Test
    fun the_guide_highlight_follows_a_rotation() {
        val full = GuideLayout.GuideCanvas(1280f, 385f, 2f)
        val d = pentagon()
        d.holdSector(Sector9.UP_RIGHT)                 // た
        d.rightOut(UP)
        fun ring(r: GuideLayout.Result, c: String) = r.texts.single { it.text == c && it.x > GuideMap.Kana.GRID_RIGHT }
        var r = GuideLayout.layout(d.engine.guide, full, map = true)
        assertEquals(GuideLayout.TextStyle.SELECTED, ring(r, "た").style)
        d.rightRim(UP, LEFT, steps = 3)                // counter-clockwise to ← (と)
        r = GuideLayout.layout(d.engine.guide, full, map = true)
        assertEquals(GuideLayout.TextStyle.SELECTED, ring(r, "と").style)
        assertEquals(GuideLayout.TextStyle.NORMAL, ring(r, "た").style)
        assertEquals("one lit pentagon cell", 1, r.boxes.count { it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x > GuideMap.Kana.GRID_RIGHT })
        // The R2 layer grid (amendment 21: 記号): the cell the stick is aimed at is lit; only the centre is empty.
        val l = pentagon()
        l.press(PadButton.R2)
        l.leftOutAt(180.0)
        r = GuideLayout.layout(l.engine.guide, full, map = true)
        assertEquals(1, r.boxes.count { it.style == GuideLayout.BoxStyle.CELL_EMPTY })
        val lit = r.boxes.filter { it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x < GuideMap.Kana.GRID_RIGHT }
        assertEquals(1, lit.size)
        assertEquals("the ← cell", GuideMap.Kana.GRID_X, lit[0].x, 0.01f)
        assertEquals(GuideLayout.TextStyle.SELECTED, r.texts.single { it.text == "ー" }.style)
        l.leftRim(180.0, 225.0, steps = 2)
        r = GuideLayout.layout(l.engine.guide, full, map = true)
        assertEquals(GuideLayout.TextStyle.SELECTED, r.texts.single { it.text == "？" }.style)
    }

    // ------------------------------------------------------------------ 6. ink centring

    @Test
    fun ink_origin_centres_the_ink_box() {
        // A glyph whose ink sits low and left in its em box (。 at 32 px: ink 2..12 × -9..1).
        val (x, baseline) = GuideLayout.inkOrigin(100f, 50f, 2f, -9f, 12f, 1f)
        assertEquals(93f, x, 0.001f)
        assertEquals(54f, baseline, 0.001f)
        // The ink box drawn at that origin is centred on (100, 50).
        assertEquals(100f, x + (2f + 12f) / 2f, 0.001f)
        assertEquals(50f, baseline + (-9f + 1f) / 2f, 0.001f)
        val one = GuideLayout.Primitive.Text(0f, 0f, 32f, "。", GuideLayout.TextStyle.NORMAL)
        assertTrue(GuideLayout.inkCentred(one))
        assertTrue(GuideLayout.inkCentred(one.copy(text = "ゃ")))
        assertTrue("words keep the font metrics", !GuideLayout.inkCentred(one.copy(text = "記号")))
        assertTrue("left / right aligned labels keep the font metrics", !GuideLayout.inkCentred(one.copy(align = GuideLayout.Align.LEFT)))
    }
}
