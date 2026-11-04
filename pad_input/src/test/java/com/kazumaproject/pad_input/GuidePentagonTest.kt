package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Amendments 15 and 16: the kana pentagon in the guides. Amendment 16 placement (the user's v4
 * guide, `assets/2026-11-01_rg477v_v4_guide_user.png`): the ring next to the 3×3, centred at
 * ([GuideMap.Kana.PENT_CX], [GuideMap.Kana.PENT_CY]); five round cells (あ段 on top, then
 * clockwise), a dot in the centre, no centre character; holds show the changed characters and dim
 * the rest; the や・わ layer draws it faint; the corner overlay follows.
 */
class GuidePentagonTest {

    private val full = GuideLayout.GuideCanvas(1280f, 385f, 2f)
    private val small = GuideLayout.GuideCanvas(960f, 289f, 2f)
    private val kanaBox = GuideLayout.GuideCanvas(441f, 247f, 2f)
    private val g = GuideMap.Geometry
    private val k = GuideMap.Kana

    private fun driver() = PadDriver(
        PadInputEngine(PadConfig(stickSource = StickSource.STICKS, faceLayout = FaceLayout.NINTENDO)),
    )

    /** The か key (↖) selected. */
    private fun idle(): PadGuideState = driver().let { d -> d.holdSector(Sector9.UP_LEFT); d.engine.guide }

    private fun ring(r: GuideLayout.Result) = r.boxes.single { it.style == GuideLayout.BoxStyle.RING }
    private fun cells(r: GuideLayout.Result) = r.boxes.filter {
        (it.style == GuideLayout.BoxStyle.CELL || it.style == GuideLayout.BoxStyle.CELL_SELECTED) && abs(it.w - it.h) < 0.01f && it.x > k.GRID_RIGHT
    }

    @Test
    fun the_pentagon_sits_next_to_the_grid_at_1280x385_with_18px_gaps() {
        val r = GuideLayout.layout(idle(), full, map = true)
        val ring = ring(r)
        // At the user's v4 guide's place: centre 819, 181, radius 112.
        assertEquals(k.PENT_CX, ring.x + ring.w / 2f, 0.01f)
        assertEquals(k.PENT_CY, ring.y + ring.h / 2f, 0.01f)
        assertEquals(2 * k.RING_R, ring.w, 0.01f)
        assertEquals(819f, k.PENT_CX, 1f); assertEquals(181f, k.PENT_CY, 1f); assertEquals(112f, k.RING_R, 1f)
        // 18 px or more from the grid, the face buttons' labels, Select / Start; inside the key area.
        assertTrue("ring vs grid", k.RING_LEFT - k.GRID_RIGHT >= g.MIN_GAP)
        assertTrue("ring vs Select / Start", g.BAR_Y - k.RING_BOTTOM >= g.MIN_GAP)
        assertTrue("ring top inside", k.RING_TOP >= 0f)
        val yLabel = r.texts.single { it.text == "変換" }
        val yLeft = yLabel.x - GuideLayout.textWidth(yLabel.text, yLabel.size)
        assertTrue("ring vs the Y label ($yLeft)", yLeft - k.RING_RIGHT >= g.MIN_GAP)
        // Five round cells, 18 px or more apart from each other, inside the ring.
        val c = cells(r)
        assertEquals(5, c.size)
        assertTrue(c.all { abs(it.w - 2 * k.CELL_R) < 0.01f })
        for (i in c.indices) for (j in i + 1 until c.size) {
            val a = c[i]; val b = c[j]
            val gap = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) - 2 * k.CELL_R
            assertTrue("cells $i/$j gap $gap", gap >= g.MIN_GAP)
        }
        for (b in c) {
            val d = hypot((b.x + b.w / 2f - k.PENT_CX).toDouble(), (b.y + b.h / 2f - k.PENT_CY).toDouble())
            assertTrue(d + k.CELL_R <= k.RING_R + 0.01)
        }
        assertTrue("a dot in the centre", r.boxes.any { it.style == GuideLayout.BoxStyle.DOT && abs(it.x + it.w / 2f - k.PENT_CX) < 0.01f && abs(it.y + it.h / 2f - k.PENT_CY) < 0.01f })
        assertTrue("no centre circle on the right", r.boxes.none { it.style == GuideLayout.BoxStyle.CIRCLE && it.x > k.GRID_RIGHT })
    }

    @Test
    fun a_row_on_top_then_clockwise_and_no_centre_character() {
        val r = GuideLayout.layout(idle(), full, map = true)
        val chars = listOf("か", "き", "く", "け", "こ").map { ch -> r.texts.single { it.text == ch && it.x > k.GRID_RIGHT } }
        assertTrue(chars.all { it.size >= GuideMap.CROSS_CHAR - 0.01f })
        // Angles around the centre (0° = right, counter-clockwise): 90, 18, -54, -126, 162.
        val angles = chars.map { Math.toDegrees(atan2(-(it.y - k.PENT_CY).toDouble(), (it.x - k.PENT_CX).toDouble())) }
        for ((i, a) in angles.withIndex()) assertEquals("slot $i", KanaPentagon.CENTRES_DEG[i], a, 0.5)
        for (t in chars) assertEquals(k.REACH.toDouble(), hypot((t.x - k.PENT_CX).toDouble(), (t.y - k.PENT_CY).toDouble()), 0.5)
        assertTrue("no character at the centre", r.texts.none { abs(it.x - k.PENT_CX) < 10f && abs(it.y - k.PENT_CY) < 10f })
        assertTrue("か appears once on the right (no centre repeat)", r.texts.count { it.text == "か" && it.x > k.GRID_RIGHT } == 1)
    }

    @Test
    fun pending_slot_is_highlighted() {
        val d = driver()
        d.holdSector(Sector9.UP_LEFT)
        val (x, y) = PadDriver.unitAt(-45.0)
        d.right(x, y)
        val r = GuideLayout.layout(d.engine.guide, full, map = true)
        val ku = r.texts.single { it.text == "く" && it.x > k.GRID_RIGHT }
        assertEquals(GuideLayout.TextStyle.SELECTED, ku.style)
        val sel = r.boxes.single { it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x > k.GRID_RIGHT }
        assertEquals(ku.x, sel.x + sel.w / 2f, 0.01f)
    }

    @Test
    fun holds_show_the_changed_characters_and_dim_the_rest() {
        val l2 = driver().let { d -> d.press(PadButton.L2); d.holdSector(Sector9.UP_LEFT); GuideLayout.layout(d.engine.guide, full, map = true) }
        for (ch in listOf("が", "ぎ", "ぐ", "げ", "ご")) assertTrue(ch, l2.texts.any { it.text == ch && it.x > k.GRID_RIGHT && it.style == GuideLayout.TextStyle.NORMAL })
        val r1 = driver().let { d -> d.press(PadButton.R1); d.holdSector(Sector9.UP_RIGHT); GuideLayout.layout(d.engine.guide, full, map = true) }
        fun style(ch: String) = r1.texts.single { it.text == ch && it.x > k.GRID_RIGHT }.style
        assertEquals(GuideLayout.TextStyle.NORMAL, style("っ"))
        assertEquals(GuideLayout.TextStyle.DIM, style("た"))
        assertEquals(GuideLayout.TextStyle.DIM, style("ち"))
        assertEquals(GuideLayout.TextStyle.DIM, style("て"))
        assertEquals(GuideLayout.TextStyle.DIM, style("と"))
    }

    @Test
    fun the_ya_wa_layer_draws_the_pentagon_faint() {
        val r = driver().let { d -> d.press(PadButton.R2); GuideLayout.layout(d.engine.guide, full, map = true) }
        assertEquals(1, r.boxes.count { it.style == GuideLayout.BoxStyle.RING })
        for (ch in listOf("あ", "い", "う", "え", "お")) assertEquals(ch, GuideLayout.TextStyle.FAINT, r.texts.single { it.text == ch && it.x > k.GRID_RIGHT }.style)
        assertTrue("nothing lit on the right", r.boxes.none { it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x > k.GRID_RIGHT })
    }

    @Test
    fun the_smaller_map_scales_evenly_and_stays_inside() {
        for (s in listOf(idle(), driver().let { d -> d.press(PadButton.L1); d.holdSector(Sector9.RIGHT); d.engine.guide })) {
            for (canvas in listOf(full, small)) {
                val r = GuideLayout.layout(s, canvas, map = true)
                for (b in r.boxes) assertTrue("box $b", b.x >= -0.5f && b.y >= -0.5f && b.x + b.w <= canvas.width + 0.5f && b.y + b.h <= canvas.height + 0.5f)
            }
        }
        val scale = small.width / full.width
        assertEquals(ring(GuideLayout.layout(idle(), full, map = true)).w * scale, ring(GuideLayout.layout(idle(), small, map = true)).w, 0.01f)
    }

    @Test
    fun the_corner_overlay_follows_the_same_shape() {
        val d = driver()
        d.holdSector(Sector9.UP_LEFT)
        for (canvas in listOf(kanaBox, GuideLayout.GuideCanvas(1256f, 280f, 2f))) {
            val r = GuideLayout.layout(d.engine.guide, canvas)
            val ring = r.boxes.single { it.style == GuideLayout.BoxStyle.RING }
            assertTrue(ring.x >= 0f && ring.y >= 0f && ring.x + ring.w <= canvas.width && ring.y + ring.h <= canvas.height)
            assertTrue(r.boxes.any { it.style == GuideLayout.BoxStyle.DOT })
            val cx = ring.x + ring.w / 2f; val cy = ring.y + ring.h / 2f
            val chars = listOf("か", "き", "く", "け", "こ").map { ch -> r.texts.filter { it.text == ch }.maxBy { it.x } }
            val angles = chars.map { Math.toDegrees(atan2(-(it.y - cy).toDouble(), (it.x - cx).toDouble())) }
            for ((i, a) in angles.withIndex()) assertEquals("slot $i", KanaPentagon.CENTRES_DEG[i], a, 0.5)
            assertTrue("no centre character", r.texts.none { abs(it.x - cx) < 5f && abs(it.y - cy) < 5f })
        }
        val (x, y) = PadDriver.unitAt(180.0)
        d.right(x, y)
        val r = GuideLayout.layout(d.engine.guide, kanaBox)
        assertEquals(GuideLayout.TextStyle.ACCENT, r.texts.filter { it.text == "こ" }.maxBy { it.x }.style)
        // The corner overlay of the v9 grid: 「や/わ」 smaller than the kana; the R2 記号 layer with a dot.
        d.right(0f, 0f); d.leftRelease()
        val idle = GuideLayout.layout(d.engine.guide, kanaBox)
        val yaWa = idle.texts.single { it.text == "や/わ" }
        val ka = idle.texts.filter { it.text == "か" }.minBy { it.x }
        assertTrue("や/わ ${yaWa.size} < か ${ka.size}", yaWa.size < ka.size)
        d.press(PadButton.R2)
        val layer = GuideLayout.layout(d.engine.guide, kanaBox)
        for (c in listOf("〜", "。", "…", "ー", "、", "？", "・", "！")) assertTrue("layer $c", layer.texts.any { it.text == c })
        assertEquals("amendment 21: only the centre is empty", 1, layer.boxes.count { it.style == GuideLayout.BoxStyle.CELL_EMPTY })
        assertEquals("the ring's dot and the layer's centre dot", 2, layer.boxes.count { it.style == GuideLayout.BoxStyle.DOT })
        assertTrue(layer.texts.filter { it.text in setOf("あ", "い", "う", "え", "お") }.all { it.style == GuideLayout.TextStyle.FAINT })
        d.leftOut(Sector9.DOWN)
        val out = GuideLayout.layout(d.engine.guide, kanaBox)
        assertEquals(GuideLayout.TextStyle.SELECTED, out.texts.single { it.text == "・" }.style)
        d.leftRelease(); d.release(PadButton.R2)
        // The や/わ key in the corner overlay: six characters on the ring at the hexagon's angles.
        d.holdSector(Sector9.DOWN_RIGHT)
        for (canvas in listOf(kanaBox, GuideLayout.GuideCanvas(1256f, 280f, 2f))) {
            val r = GuideLayout.layout(d.engine.guide, canvas)
            val ring = r.boxes.single { it.style == GuideLayout.BoxStyle.RING }
            val cx = ring.x + ring.w / 2f; val cy = ring.y + ring.h / 2f
            val chars = listOf("や", "ゆ", "よ", "わ", "を", "ん").map { ch -> r.texts.filter { it.text == ch }.maxBy { it.x } }
            val angles = chars.map { Math.toDegrees(atan2(-(it.y - cy).toDouble(), (it.x - cx).toDouble())) }
            for ((i, a) in angles.withIndex()) assertEquals("hexagon slot $i", KanaHexagon.CENTRES_DEG[i], a, 0.5)
        }
    }
}
