package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 15: the kana right stick as a pentagon of five 72° sectors. ↑ あ段, then clockwise
 * → い段, ↘ う段, ↙ え段, ← お段; the nearest sector centre wins; 英字 / 数字 keep four tilts.
 * Amendment 18: the only kana right stick (the cross + click setting is gone); R3 types nothing.
 * Amendment 16: か is ↖ on the grid, the 記号 key ↘ gives 。 、 ！ ？ ー (amendment 17), L2 = 濁点.
 * Amendment 17: the slot follows the stick while it is out (re-aim), see [Amendment17Test].
 */
class KanaPentagonTest {

    private val keyMap = KeyMap()

    private fun pentagon(config: PadConfig = PadConfig()): PadDriver =
        PadDriver(PadInputEngine(config)).also { it.left(0.1f, 0f); it.clear() }

    private fun expected(key: Key, gesture: GestureType): Char? {
        val info = keyMap.getKeyInfoJapanese(key, false) as? KeyInfo.KeyTapFlickInfo ?: return null
        return when (gesture) {
            GestureType.Tap -> info.tap
            GestureType.FlickLeft -> info.flickLeft
            GestureType.FlickTop -> info.flickTop
            GestureType.FlickRight -> info.flickRight
            GestureType.FlickBottom -> info.flickBottom
            else -> null
        }
    }

    /** Push the right stick out toward [deg] without bringing it back. */
    private fun PadDriver.outAt(deg: Double, r: Float = 0.95f) {
        val (x, y) = PadDriver.unitAt(deg)
        right(x * 0.5f, y * 0.5f)
        right(x * r, y * r)
    }

    private fun PadDriver.home(deg: Double) {
        val (x, y) = PadDriver.unitAt(deg)
        right(x * 0.4f, y * 0.4f)
        right(0f, 0f)
    }

    // ------------------------------------------------------------------ geometry

    @Test
    fun kana_is_always_the_pentagon() {
        // Amendment 18: no setting any more; kana with sticks reads five sectors, the fallback does not.
        assertTrue(pentagon().engine.guide.pentagon)
        assertFalse(PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK))).engine.guide.pentagon)
    }

    @Test
    fun sector_centres_and_boundaries() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(90.0, 18.0, -54.0, -126.0, 162.0).map { KanaPentagon.slotAt(it) })
        assertEquals(2, KanaPentagon.slotAt(306.0))
        assertEquals(3, KanaPentagon.slotAt(234.0))
        // The amendment's boundary tests: 53° → い, 55° → あ; 197° → お, 199° → え.
        assertEquals(1, KanaPentagon.slotAt(53.0))
        assertEquals(0, KanaPentagon.slotAt(55.0))
        assertEquals(4, KanaPentagon.slotAt(197.0))
        assertEquals(3, KanaPentagon.slotAt(199.0))
        // Other edges: 125 / 127 (あ / お), -17 / -19 (い / う), 271 / 269 (う / え).
        assertEquals(0, KanaPentagon.slotAt(125.0)); assertEquals(4, KanaPentagon.slotAt(127.0))
        assertEquals(1, KanaPentagon.slotAt(-17.0)); assertEquals(2, KanaPentagon.slotAt(-19.0))
        assertEquals(2, KanaPentagon.slotAt(271.0)); assertEquals(3, KanaPentagon.slotAt(269.0))
        // Straight down is the う/え boundary; nothing special, it falls to え.
        assertEquals(3, KanaPentagon.slotAt(270.0))
        // Stick samples (y down): ↑ (0,-1), → (1,0), ↘ (1,1), ↙ (-1,1), ← (-1,0) — the device recipe.
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0f to -1f, 1f to 0f, 1f to 1f, -1f to 1f, -1f to 0f).map { (x, y) -> KanaPentagon.slotOf(x, y) })
        assertEquals(KanaPentagon.GESTURES, (0 until 5).map { KanaPentagon.gesture(it) })
    }

    // ------------------------------------------------------------------ typing

    @Test
    fun ka_key_up_right_downright_downleft_left_types_ka_ki_ku_ke_ko() {
        val d = pentagon()
        d.left(-0.707f, -0.707f)                                   // か key
        for (deg in listOf(90.0, 0.0, -45.0, 225.0, 180.0)) d.flickAt(deg)
        assertEquals("かきくけこ", d.typed())
        assertTrue("plain kana go out as the touch key's own gestures", d.kanaKeys().map { it.gesture } == KanaPentagon.GESTURES)
        assertTrue(d.kanaKeys().all { it.key == Key.KeyKA })
    }

    @Test
    fun the_device_recipe_axes_type_the_five_kana() {
        // rg_pad.py full-axis values: ↑ RY-, → RX+, ↘ RX+ RY+, ↙ RX- RY+, ← RX-.
        val d = pentagon()
        d.left(-0.707f, -0.707f)
        for ((x, y) in listOf(0f to -1f, 1f to 0f, 1f to 1f, -1f to 1f, -1f to 0f)) { d.right(x, y); d.right(0f, 0f) }
        assertEquals("かきくけこ", d.typed())
    }

    @Test
    fun boundary_angles_53_55_197_199() {
        fun at(deg: Double): String = pentagon().let { d -> d.left(-0.707f, -0.707f); d.flickAt(deg); d.typed() }
        assertEquals("き", at(53.0))
        assertEquals("か", at(55.0))
        assertEquals("こ", at(197.0))
        assertEquals("け", at(199.0))
    }

    @Test
    fun r3_no_longer_types_the_a_row() {
        // Amendment 18: the undrawn あ段 on R3 is gone; ↑ types it.
        val d = pentagon(PadConfig(modes = listOf(PadMode.KANA)))
        d.left(-0.707f, -0.707f)
        d.tap(PadButton.R3)
        assertEquals("", d.typed())
        d.flickAt(90.0)
        assertEquals("か", d.typed())
        assertEquals(GestureType.Tap, d.kanaKeys().single().gesture)
    }

    @Test
    fun every_key_and_slot_matches_sumires_table() {
        var checked = 0
        // Amendment 21: every key but the や/わ key (its six slots: KanaInputTest, Amendment21Test).
        for (key in KanaGrid.MAIN - KanaGrid.YA_WA_KEY) for (slot in 0 until KanaPentagon.SLOTS) {
            val d = pentagon()
            d.holdSector(Sector9.fromIndex(KanaGrid.MAIN.indexOf(key)))
            d.flickAt(KanaPentagon.CENTRES_DEG[slot])
            val keys = d.kanaKeys()
            assertEquals("$key slot $slot", 1, keys.size)
            assertEquals(key, keys[0].key)
            assertEquals(KanaPentagon.gesture(slot), keys[0].gesture)
            assertEquals("$key slot $slot", expected(key, KanaPentagon.gesture(slot)), keys[0].char)
            checked++
        }
        assertEquals(40, checked)
        // The ↘ key (amendment 21: や/わ) reads six slots, not the pentagon's five: ↑ や ↘ よ ↙ を.
        val yw = pentagon(); yw.left(0.707f, 0.707f)
        for (deg in listOf(90.0, -30.0, -150.0)) yw.flickAt(deg)
        assertEquals("やよを", yw.typed())
    }

    @Test
    fun the_last_sample_out_at_the_rim_decides_the_slot() {
        // Amendment 17 replaces "the farthest sample decides": every sample ≥ 0.6 re-aims.
        val d = pentagon()
        d.left(-0.707f, -0.707f)
        d.outAt(85.0, 0.7f)          // あ sector, not far
        d.outAt(25.0, 0.95f)         // farther, in the い sector
        d.home(25.0)
        assertEquals("き", d.typed())
        d.clear()
        d.outAt(25.0, 0.95f)         // far in い
        d.outAt(85.0, 0.7f)          // less far but still ≥ 0.6, in あ: re-aimed (v5 kept い here)
        d.home(85.0)
        assertEquals("か", d.typed())
    }

    @Test
    fun holds_and_the_latch() {
        val d = pentagon()
        d.press(PadButton.L2); d.left(-0.707f, -0.707f); d.flickAt(180.0); d.release(PadButton.L2)       // L2 + か + ← = ご
        assertEquals("ご", d.typed())
        d.clear()
        d.press(PadButton.R1); d.left(0.707f, -0.707f); d.flickAt(-54.0); d.release(PadButton.R1)       // R1 + た + ↘ = っ
        assertEquals("っ", d.typed())
        d.clear()
        // L2 released before the stick returns still gives ご (amendment 14 latch).
        d.press(PadButton.L2); d.left(-0.707f, -0.707f); d.outAt(180.0); d.release(PadButton.L2); d.home(180.0)
        assertEquals("ご", d.typed())
        d.clear()
        // A hold pressed after the stick went out does not change the flick.
        d.outAt(180.0); d.press(PadButton.L2); d.home(180.0); d.release(PadButton.L2)
        assertEquals("こ", d.typed())
        d.clear()
        // The key is latched too: moving the left stick while the right one is out types from the first key.
        d.left(-0.707f, -0.707f); d.outAt(0.0); d.left(1f, 0f); d.home(0.0)
        assertEquals("き", d.typed())
    }

    @Test
    fun english_and_number_keep_four_tilts_and_the_layer_reads_eight_directions() {
        val d = pentagon()
        d.nextMode(); d.clear()            // 英字
        d.left(0f, -1f)
        d.flick(Dir4.LEFT); d.flick(Dir4.UP); d.flick(Dir4.RIGHT); d.flick(Dir4.DOWN)
        assertEquals("efgh", d.typed())
        assertFalse(d.engine.guide.pentagon)
        d.nextMode(); d.clear()            // 数字
        d.left(-0.707f, -0.707f); d.flick(Dir4.UP)
        assertEquals("2", d.typed())
        // Back in kana: no bar any more (amendment 16); the R2 layer (amendment 21: 記号) reads the left stick in eight directions.
        d.nextMode(); d.clear()
        assertEquals(PadMode.KANA, d.engine.padMode)
        d.leftRelease()
        d.press(PadButton.R2); d.flickLeft8(Sector9.UP_RIGHT); d.flickLeft8(Sector9.DOWN); d.release(PadButton.R2)
        assertEquals("…・", d.typed())
    }

    @Test
    fun the_d_pad_fallback_is_not_affected() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.press(PadButton.DPAD_UP)
        d.tap(PadButton.A); d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.L2)
        assertEquals("かきくけこ", d.typed())
        assertFalse("the fallback keeps the cross labelled with buttons", d.engine.guide.pentagon)
    }

    @Test
    fun the_log_says_pentagon() {
        val d = pentagon()
        d.left(-0.707f, -0.707f); d.flickAt(-45.0)
        assertTrue(d.notes.joinToString(" | "), d.notes.any { it.contains("pentagon") && it.contains("↘") })
        assertTrue(d.notes.any { it.contains("typed く") })
    }

    // ------------------------------------------------------------------ guide state

    @Test
    fun guide_state_follows_the_pentagon() {
        val d = pentagon()
        d.left(-0.707f, -0.707f)
        var g = d.engine.guide
        assertTrue(g.pentagon)
        assertEquals(listOf("か", "き", "く", "け", "こ"), g.flickChars!!.slots)
        assertNull(g.pendingSlot)
        assertTrue("no hold: nothing dimmed", g.flickDim.isEmpty())
        d.outAt(-45.0)
        g = d.engine.guide
        assertEquals(2, g.pendingSlot)
        assertNull("no 4-way pending flick on the pentagon", g.pendingFlick)
        d.home(-45.0)
        assertNull(d.engine.guide.pendingSlot)
        // L2 held: が ぎ ぐ げ ご, nothing dimmed.
        d.press(PadButton.L2)
        g = d.engine.guide
        assertEquals(listOf("が", "ぎ", "ぐ", "げ", "ご"), g.flickChars!!.slots)
        assertEquals(listOf(false, false, false, false, false), g.flickDim)
        d.release(PadButton.L2)
        // R1 on た: た ち っ て と, the unchanged ones dimmed.
        d.left(0.707f, -0.707f); d.press(PadButton.R1)
        g = d.engine.guide
        assertEquals(listOf("た", "ち", "っ", "て", "と"), g.flickChars!!.slots)
        assertEquals(listOf(true, true, false, true, true), g.flickDim)
        d.release(PadButton.R1)
        // Amendment 16: no bar in kana; amendment 18: L3 steps the mode on release, nothing while held.
        d.press(PadButton.L3)
        assertTrue(d.engine.guide.pentagon)
        assertFalse(d.engine.guide.barActive)
        d.release(PadButton.L3)
        assertEquals("the stick was out: the click is ignored", PadMode.KANA, d.engine.padMode)
        // R2: the や・わ layer on the grid, the pentagon still drawn (faint) with the key under the stick then.
        d.leftRelease(); d.press(PadButton.R2)
        g = d.engine.guide
        assertTrue(g.layer && g.pentagon)
        assertEquals(listOf("あ", "い", "う", "え", "お"), g.flickChars!!.slots)
    }
}
