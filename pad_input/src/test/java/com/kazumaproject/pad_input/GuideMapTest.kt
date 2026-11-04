package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendments 5, 9, 10, 16 and 18: the controller-map guide. Every label inside the container, no two
 * labels overlapping, the mock-up sizes as minimums at 1280 × 385, labels following the live roles,
 * nothing but names / functions / characters, and the spacing of the mock-ups. Amendment 16: かな with
 * sticks at the user's v4 placement ([GuideMap.Kana]); amendment 18 (v7): 英字 and 数字 too, with the
 * touch digits, the L3 / R3 pills, Select | Start at the top centre and the bigger face buttons
 * (`2026-11-02_guide_mock_v7.py`).
 */
class GuideMapTest {

    private val full = GuideLayout.GuideCanvas(1280f, 385f, 2f)
    private val small = GuideLayout.GuideCanvas(960f, 289f, 2f)

    private fun driver(config: PadConfig = PadConfig(stickSource = StickSource.STICKS, faceLayout = FaceLayout.NINTENDO)) =
        PadDriver(PadInputEngine(config))

    private val remapped = PadConfig(
        stickSource = StickSource.STICKS, faceLayout = FaceLayout.NINTENDO,
        roles = PadConfig.DEFAULT_ROLES + mapOf(PadButton.Y to PadRole.BACKSPACE, PadButton.B to PadRole.CONFIRM, PadButton.R3 to PadRole.NONE),
    )

    /** The cases to render: かな, 英字, 数字 (each with its holds and the R2 layer), emoji, the fallback. */
    private fun cases(): List<Pair<String, PadGuideState>> {
        val out = ArrayList<Pair<String, PadGuideState>>()
        driver().let { d -> d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍")); d.left(0f, -1f); out += "kana" to d.engine.guide }
        driver().let { d -> d.press(PadButton.L2); d.left(0f, -1f); out += "kana_l2" to d.engine.guide }
        driver().let { d -> d.press(PadButton.L1); d.left(1f, 0f); out += "kana_l1" to d.engine.guide }
        driver().let { d -> d.press(PadButton.R1); d.left(1f, 0f); out += "kana_r1" to d.engine.guide }
        driver().let { d -> d.press(PadButton.R2); out += "kana_r2" to d.engine.guide }
        driver().let { d -> d.press(PadButton.L3); d.left(0f, -1f); out += "kana_l3" to d.engine.guide }
        driver().let { d -> d.left(0f, -1f); out += "kana_touch7" to d.engine.guide.copy(touchedDigit = '7') }
        driver().let { d -> d.nextMode(); d.left(1f, 0f); out += "english" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.press(PadButton.L2); d.left(1f, 0f); out += "english_l2" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.press(PadButton.R2); out += "english_r2" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.press(PadButton.R2); d.leftOut(Sector9.UP_LEFT); out += "english_r2_out" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.nextMode(); d.left(0f, 1f); out += "number" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.nextMode(); d.press(PadButton.R2); out += "number_r2" to d.engine.guide }
        // Amendment 20: the emoji wheel is the D-pad fallback's only (R3, the D-pad picks the petal); Start opens the list.
        driver(PadConfig(stickSource = StickSource.DPAD_FALLBACK)).let { d -> d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍")); d.tap(PadButton.R3); d.press(PadButton.DPAD_UP); out += "emoji" to d.engine.guide }
        driver().let { d -> d.tap(PadButton.START); out += "emoji_list" to d.engine.guide }
        driver(remapped).let { d -> d.left(0f, -1f); out += "remapped" to d.engine.guide }
        driver().let { d -> d.nextMode(); d.left(1f, 0f); d.right(0f, -1f); out += "english_pending" to d.engine.guide }
        driver(PadConfig(stickSource = StickSource.DPAD_FALLBACK)).let { d -> d.press(PadButton.DPAD_UP); out += "fallback" to d.engine.guide }
        driver().let { d -> d.leftRelease(); out += "pentagon" to d.engine.guide }
        driver().let { d -> d.press(PadButton.L2); d.holdSector(Sector9.UP_LEFT); out += "pentagon_l2" to d.engine.guide }
        driver().let { d -> d.press(PadButton.R1); d.holdSector(Sector9.UP_RIGHT); out += "pentagon_r1" to d.engine.guide }
        driver().let { d -> d.press(PadButton.R1); d.press(PadButton.R2); d.leftOut(Sector9.UP_LEFT); out += "pentagon_r1r2_out" to d.engine.guide }
        // Amendment 21: the や/わ key (↘) with a flick out toward ↘ (よ pending), and with R1 (ゃ ゅ ょ ゎ).
        driver().let { d -> d.holdSector(Sector9.DOWN_RIGHT); d.right(0.7f, 0.7f); out += "hexagon_pending" to d.engine.guide }
        driver().let { d -> d.press(PadButton.R1); d.holdSector(Sector9.DOWN_RIGHT); out += "hexagon_r1" to d.engine.guide }
        // Amendment 22: 英字 with L1 held (数字's layout, ↖ 1234 selected).
        driver().let { d -> d.nextMode(); d.press(PadButton.L1); d.holdSector(Sector9.UP_LEFT); out += "english_l1" to d.engine.guide }
        driver(remapped).let { d -> d.holdSector(Sector9.UP_LEFT); out += "pentagon_remapped" to d.engine.guide }
        return out
    }

    private fun case(name: String) = cases().first { it.first == name }.second

    private fun bounds(t: GuideLayout.Primitive.Text): GuidePlacement.Rect {
        val w = GuideLayout.textWidth(t.text, t.size)
        val left = when (t.align) {
            GuideLayout.Align.LEFT -> t.x
            GuideLayout.Align.RIGHT -> t.x - w
            GuideLayout.Align.CENTER -> t.x - w / 2f
        }
        return GuidePlacement.Rect(left.toInt(), (t.y - t.size / 2f).toInt(), (left + w).toInt() + 1, (t.y + t.size / 2f).toInt() + 1)
    }

    private fun overlap(a: GuidePlacement.Rect, b: GuidePlacement.Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun GuideLayout.Primitive.Box.rect() = GuidePlacement.Rect(x.toInt(), y.toInt(), kotlin.math.ceil(x + w).toInt(), kotlin.math.ceil(y + h).toInt())

    @Test
    fun every_label_is_inside_the_container_and_none_overlap() {
        for ((name, s) in cases()) for (canvas in listOf(full, small)) {
            val r = GuideLayout.layout(s, canvas, map = true)
            val container = GuidePlacement.Rect(0, 0, canvas.width.toInt(), canvas.height.toInt())
            val texts = r.texts.filter { it.text.isNotBlank() }
            assertTrue("$name: no texts", texts.size > if (name == "emoji_list") 12 else 20)
            for (t in texts) assertTrue("$name ${canvas.width.toInt()}: '${t.text}' ${bounds(t)} outside", container.contains(bounds(t)))
            for (b in r.boxes) assertTrue("$name: box $b outside", b.x >= -0.5f && b.y >= -0.5f && b.x + b.w <= canvas.width + 0.5f && b.y + b.h <= canvas.height + 0.5f)
            for (i in texts.indices) for (j in i + 1 until texts.size) {
                val a = texts[i]; val b = texts[j]
                assertTrue("$name ${canvas.width.toInt()}: '${a.text}' ${bounds(a)} overlaps '${b.text}' ${bounds(b)}", !overlap(bounds(a), bounds(b)))
            }
        }
    }

    @Test
    fun in_the_kana_layout_no_text_overlaps_another_element() {
        // Amendment 18: a label sits inside its own box (cell, disc, pill, digit) or clear of every box.
        val skip = setOf(GuideLayout.BoxStyle.BACKGROUND, GuideLayout.BoxStyle.RING, GuideLayout.BoxStyle.DOT)
        for ((name, s) in cases().filter { it.second.kanaLayout }) for (canvas in listOf(full, small)) {
            val r = GuideLayout.layout(s, canvas, map = true)
            val boxes = r.boxes.filter { it.style !in skip }
            val ring = r.boxes.single { it.style == GuideLayout.BoxStyle.RING }.rect()
            for (t in r.texts.filter { it.text.isNotBlank() }) {
                val tb = bounds(t)
                val home = boxes.filter { t.x >= it.x && t.x <= it.x + it.w && t.y >= it.y && t.y <= it.y + it.h }
                    .minByOrNull { it.w * it.h }
                val where = "$name ${canvas.width.toInt()}: '${t.text}' $tb"
                if (home != null) {
                    val hb = home.rect()
                    assertTrue("$where sticks out of its box $hb", tb.left >= hb.left - 1 && tb.right <= hb.right + 1 && tb.top >= hb.top - 1 && tb.bottom <= hb.bottom + 1)
                }
                for (b in boxes) if (b !== home && overlap(tb, b.rect())) {
                    // A box inside the text's own box (the disc's highlight) is fine; anything else is not.
                    val inHome = home != null && b.x >= home.x - 0.5f && b.x + b.w <= home.x + home.w + 0.5f && b.y >= home.y - 0.5f && b.y + b.h <= home.y + home.h + 0.5f
                    assertTrue("$where overlaps $b", inHome || (home != null && home.x >= b.x - 0.5f && home.x + home.w <= b.x + b.w + 0.5f && home.y >= b.y - 0.5f && home.y + home.h <= b.y + b.h + 0.5f))
                }
                if (home == null) assertTrue("$where overlaps the ring $ring", !overlap(tb, ring))
            }
        }
    }

    @Test
    fun mockup_sizes_are_the_minimum_at_1280x385() {
        val k = GuideMap.Kana
        val kana = GuideLayout.layout(case("kana"), full, map = true)
        val grid = kana.texts.filter { it.text in setOf("か", "た", "な", "あ", "は", "ま", "ら") && it.x < k.GRID_RIGHT }
        assertEquals(7, grid.size)
        assertTrue(grid.all { it.size >= k.CHAR - 0.01f })
        val yaWa = kana.texts.single { it.text == "や/わ" && it.x < k.GRID_RIGHT }
        assertEquals("amendment 21: 「や/わ」 at the v5–v8 「記号」 size", k.SYMBOL_CHAR, yaWa.size, 0.01f)
        val ring = kana.texts.filter { it.text in setOf("さ", "し", "す", "せ", "そ") && it.x > k.GRID_RIGHT }
        assertEquals(5, ring.size)
        assertTrue(ring.all { it.size >= k.CHAR - 2.01f })
        assertTrue("function labels ≥ 13", kana.texts.filter { it.style == GuideLayout.TextStyle.FUNC }.all { it.size >= 12.9f })
        assertTrue("printed names ≥ 13", kana.texts.filter { it.style == GuideLayout.TextStyle.LABEL }.all { it.size >= 12.9f })
        assertTrue("function labels are 17", kana.texts.any { it.style == GuideLayout.TextStyle.FUNC && it.size >= GuideMap.FUNC - 0.01f })
        // 英字: four characters per cell, bold, fitted (about 21–22 px), centred by ink.
        val english = GuideLayout.layout(case("english"), full, map = true)
        val englishGrid = english.texts.filter { it.text in FourWayLayouts.ENGLISH.toSet() }
        assertEquals(9, englishGrid.map { it.text }.toSet().size)
        assertTrue(englishGrid.toString(), englishGrid.all { it.size in 18f..k.FOUR_CHAR + 0.01f && it.ink })
        // Digits 28 px.
        val digits = kana.texts.filter { it.text.length == 1 && it.text[0].isDigit() }
        assertEquals(10, digits.size)
        assertTrue(digits.all { it.size == k.DIGIT_CHAR })
        val emoji = GuideLayout.layout(case("emoji"), full, map = true)
        val petals = emoji.texts.filter { it.petal >= 0 }
        assertEquals(8, petals.size)
        assertTrue(petals.all { it.size >= GuideMap.PETAL_CHAR - 0.01f })
        // The smaller canvas scales evenly (same ratio for every size).
        val smallKana = GuideLayout.layout(case("kana"), small, map = true)
        val s = small.width / full.width
        val a = kana.texts.first { it.text == "あ" }; val a2 = smallKana.texts.first { it.text == "あ" }
        assertEquals(a.size * s, a2.size, 0.01f)
    }

    @Test
    fun abxy_at_the_new_size() {
        val k = GuideMap.Kana
        for (name in listOf("kana", "english", "number", "english_r2")) for (canvas in listOf(full, small)) {
            val sc = canvas.width / full.width
            val r = GuideLayout.layout(case(name), canvas, map = true)
            val faces = r.boxes.filter { it.style == GuideLayout.BoxStyle.FACE || it.style == GuideLayout.BoxStyle.FACE_HELD }
            assertEquals("$name: four face buttons", 4, faces.size)
            for (f in faces) assertEquals("$name: radius 30", 2 * k.FACE_R * sc, f.w, 0.01f)
            val centres = faces.map { (it.x + it.w / 2f) / sc to (it.y + it.h / 2f) / sc }.toSet()
            assertEquals("$name ${canvas.width}", setOf(1113f to 170f, 1049f to 234f, 1177f to 234f, 1113f to 298f), centres.map { (x, y) -> Math.round(x).toFloat() to Math.round(y).toFloat() }.toSet())
            val letters = r.texts.filter { it.text in setOf("A", "B", "X", "Y") }
            assertEquals(4, letters.size)
            assertTrue(letters.all { kotlin.math.abs(it.size - k.FACE_LETTER * sc) < 0.01f })
            val words = r.texts.filter { it.style == GuideLayout.TextStyle.NORMAL && it.x > k.RING_RIGHT * sc && it.y > (GuideMap.Geometry.PILL_BOTTOM) * sc }
            assertEquals("$name: four role words $words", 4, words.size)
            assertTrue(words.all { it.size <= k.FACE_WORD * sc + 0.01f && it.size >= k.FACE_WORD * sc - 0.01f })
        }
        // Nothing near the R1 pill or the ring.
        val g = GuideMap.Geometry
        assertTrue("X's word below the R1 pill", k.FACE_CY - k.FACE_OFF - k.FACE_R - k.FACE_WORD_DY - k.FACE_WORD / 2f - g.PILL_BOTTOM >= g.MIN_GAP)
        assertTrue("the cluster right of the ring", k.FACE_LEFT - k.RING_RIGHT >= g.MIN_GAP)
        assertTrue("B's word inside the key area", k.FACE_CY + k.FACE_OFF + k.FACE_R + k.FACE_WORD_DY + k.FACE_WORD / 2f <= GuideMap.BASE_H)
    }

    @Test
    fun l3_r3_pills_with_their_destination_and_select_start_at_the_top() {
        val g = GuideMap.Geometry
        val k = GuideMap.Kana
        val expected = mapOf("kana" to ("数字へ" to "英字へ"), "english" to ("かなへ" to "数字へ"), "number" to ("英字へ" to "かなへ"))
        for ((name, words) in expected) for (canvas in listOf(full, small)) {
            val sc = canvas.width / full.width
            val r = GuideLayout.layout(case(name), canvas, map = true)
            fun pillAt(x: Float, y: Float, w: Float) = r.boxes.firstOrNull { kotlin.math.abs(it.x - x * sc) < 0.5f && kotlin.math.abs(it.y - y * sc) < 0.5f && kotlin.math.abs(it.w - w * sc) < 0.5f }
            fun wordIn(b: GuideLayout.Primitive.Box?, word: String) = b != null && r.texts.any { it.text == word && it.x >= b.x && it.x <= b.x + b.w && it.y >= b.y && it.y <= b.y + b.h }
            val l3 = pillAt(500.5f - g.PILL_W / 2f, g.BAR_Y, g.PILL_W)
            val r3 = pillAt(819f - g.PILL_W / 2f, g.BAR_Y, g.PILL_W)
            assertTrue("$name: L3 ${words.first}", wordIn(l3, "L3") && wordIn(l3, words.first))
            assertTrue("$name: R3 ${words.second}", wordIn(r3, "R3") && wordIn(r3, words.second))
            val select = pillAt(546f, g.PILL1_Y, 140f)
            val start = pillAt(696f, g.PILL1_Y, 140f)
            assertTrue("$name: Select at the top", wordIn(select, "Select") && wordIn(select, "タッチ"))
            assertTrue("$name: Start at the top", wordIn(start, "Start") && wordIn(start, "絵文字"))
            assertTrue("$name: nothing else on the bottom row", r.boxes.none { kotlin.math.abs(it.y - g.BAR_Y * sc) < 0.5f && it !== l3 && it !== r3 })
        }
        assertEquals(500.5f, k.L3_CX, 0.01f)
        assertEquals(819f, k.R3_CX, 0.01f)
        // English: "To kana" / "To 123".
        val en = PadDriver(PadInputEngine(PadConfig(words = TestWords.en), com.kazumaproject.core.domain.state.InputMode.ModeEnglish)).also { it.left(0.1f, 0f) }
        val r = GuideLayout.layout(en.engine.guide, full, map = true)
        assertTrue(r.texts.any { it.text == "To kana" } && r.texts.any { it.text == "To 123" })
        // One mode: the pills are there, empty and dimmed.
        val one = PadDriver(PadInputEngine(PadConfig(modes = listOf(PadMode.KANA)))).also { it.left(0.1f, 0f) }
        val ro = GuideLayout.layout(one.engine.guide, full, map = true)
        assertEquals(2, ro.boxes.count { it.style == GuideLayout.BoxStyle.PILL_EMPTY && kotlin.math.abs(it.y - g.BAR_Y) < 0.5f })
        assertTrue(ro.texts.filter { it.text == "L3" || it.text == "R3" }.all { it.style == GuideLayout.TextStyle.DIM })
    }

    @Test
    fun labels_follow_the_live_roles() {
        val base = GuideLayout.layout(case("kana"), full, map = true)
        val re = GuideLayout.layout(case("remapped"), full, map = true)
        val k = GuideMap.Kana
        fun faceFunc(r: GuideLayout.Result, letter: String): String {
            val l = r.texts.first { it.text == letter && kotlin.math.abs(it.size - k.FACE_LETTER) < 0.01f }
            val others = r.texts.filter { it !== l && it.style == GuideLayout.TextStyle.NORMAL }
            return when (letter) {
                "Y" -> others.filter { kotlin.math.abs(it.y - l.y) < 1f && it.x < l.x }.maxByOrNull { it.x }
                "A" -> others.filter { kotlin.math.abs(it.y - l.y) < 1f && it.x > l.x }.minByOrNull { it.x }
                "X" -> others.filter { kotlin.math.abs(it.x - l.x) < 1f && it.y < l.y }.maxByOrNull { it.y }
                else -> others.filter { kotlin.math.abs(it.x - l.x) < 1f && it.y > l.y }.minByOrNull { it.y }
            }?.text ?: ""
        }
        assertEquals("変換", faceFunc(base, "Y"))
        assertEquals("消す", faceFunc(base, "B"))
        assertEquals("決定", faceFunc(base, "A"))
        assertEquals("空白", faceFunc(base, "X"))
        assertEquals("消す", faceFunc(re, "Y"))
        assertEquals("決定", faceFunc(re, "B"))
        assertTrue("R3's pill names where it goes", base.texts.any { it.text == "英字へ" })
        assertTrue("R3 → NONE: an empty, dimmed pill", re.texts.any { it.text == "R3" && it.style == GuideLayout.TextStyle.DIM } && re.texts.none { it.text == "英字へ" })
        assertTrue("NONE shows no function word", re.texts.none { it.text == "なし" })
        // Nintendo arrangement: X is the top face button.
        val letters = base.texts.filter { it.text in setOf("A", "B", "X", "Y") && it.style == GuideLayout.TextStyle.LABEL }
        assertEquals("X", letters.minByOrNull { it.y }!!.text)
        assertEquals("Y", letters.minByOrNull { it.x }!!.text)
    }

    @Test
    fun mode_specific_labels() {
        val m = cases().associate { it.first to GuideLayout.layout(it.second, full, map = true) }
        fun has(name: String, text: String) = m.getValue(name).texts.any { it.text == text }
        val k = GuideMap.Kana
        // Amendment 16 pills: L2 濁点, L1 半濁点, R2 記号 (amendment 21), R1 小文字; Y 変換; the grid's 「や/わ」.
        for (w in listOf("濁点", "半濁点", "や/わ", "小文字", "変換", "記号", "L3", "R3", "数字へ", "英字へ")) assertTrue("kana: $w", has("kana", w))
        assertTrue("no あ段 anywhere", m.values.all { r -> r.texts.none { it.text == "あ段" } })
        assertTrue(has("english", "大文字") && has("english", "候補") && has("english", "記号") && has("emoji", "次の 32 個"))
        assertTrue("NONE shows only the dimmed name", m.values.all { r -> r.texts.none { it.text == "なし" } })
        // Amendment 18: a role with nothing to do keeps its pill, empty and dimmed.
        assertEquals("number: L1, L2, R1 dimmed (R2 is 記号)", setOf("L1", "L2", "R1"), m.getValue("number").texts.filter { it.text in setOf("L1", "L2", "R1", "R2") && it.style == GuideLayout.TextStyle.DIM }.map { it.text }.toSet())
        // Amendment 22: 英字's L1 / R1 read 123; with L1 held the L1 pill is lit, L2 empty and dimmed.
        assertEquals("english: no pill empty", emptySet<String>(), m.getValue("english").texts.filter { it.text in setOf("L1", "L2", "R1", "R2") && it.style == GuideLayout.TextStyle.DIM }.map { it.text }.toSet())
        assertEquals(0, m.getValue("english").boxes.count { it.style == GuideLayout.BoxStyle.PILL_EMPTY })
        assertEquals(2, m.getValue("english").texts.count { it.text == "123" })
        val l1 = m.getValue("english_l1")
        assertEquals("english + L1: L2 empty", setOf("L2"), l1.texts.filter { it.text in setOf("L1", "L2", "R1", "R2") && it.style == GuideLayout.TextStyle.DIM }.map { it.text }.toSet())
        assertEquals(1, l1.boxes.count { it.style == GuideLayout.BoxStyle.PILL_EMPTY })
        assertTrue("L1 lit", l1.texts.any { it.text == "L1" && it.style == GuideLayout.TextStyle.SELECTED })
        for (c in listOf("1234", "5678", "90.,", "+-*/", "(=)%", "!?'\"", "<:>;")) assertTrue("english + L1: $c", l1.texts.any { it.text == c && it.x < k.GRID_RIGHT })
        assertTrue("1234 still selected (same index)", l1.texts.any { it.text == "1234" && it.style == GuideLayout.TextStyle.SELECTED })
        for (c in listOf("1", "2", "3", "4")) assertTrue("ring $c", l1.texts.any { it.text == c && it.x > k.GRID_RIGHT && it.x < k.RING_RIGHT })
        assertTrue("no letters", l1.texts.none { it.text == "abcd" || it.text == "a" })
        assertTrue("R2 stays 記号, L3 / R3 stay 英字's", l1.texts.any { it.text == "記号" } && l1.texts.any { it.text == "かなへ" } && l1.texts.any { it.text == "数字へ" })
        assertTrue(has("emoji_list", "移動") && has("emoji_list", "前のカテゴリー") && has("emoji_list", "次のカテゴリー") && has("emoji_list", "入れる") && has("emoji_list", "戻る"))
        assertTrue("the list is D-pad driven: no grid, no cross", m.getValue("emoji_list").texts.none { it.petal >= 0 } && m.getValue("emoji_list").boxes.none { it.style == GuideLayout.BoxStyle.CIRCLE })
        assertTrue("never 「—」", m.values.all { r -> r.texts.none { it.text == "—" } })
        assertTrue("no ␣ any more (R3 types no space); the uvwx cell on the ring; the first emoji on the wheel",
            m.values.all { r -> r.texts.none { it.text == "␣" } } && has("english", "u") && has("english", "x") && m.getValue("emoji").texts.count { it.text == "🐱" } >= 2)
        assertTrue(has("emoji", "一覧"))
        assertTrue("fallback keeps the D-pad table", has("fallback", "い段") && has("fallback", "キー"))
        assertTrue("no sentences, no captions", m.values.all { r -> r.texts.none { it.text.contains("スティック") || it.text.contains("選ぶ") || it.text.contains("いま") || it.text.contains("設定") || it.text.contains("押し込み") || it.text.contains("押しながら") || it.text.contains("はじく") } })
        assertTrue("no mode name, no preview caption (絵文字 remains as Start's function)", m.values.all { r -> r.texts.none { it.text in setOf("かな", "英字", "数字・記号", "絵文字一覧", "いま選んでいるキー", "いま選んでいる花びら") } })
        // The touch digits in every text mode with sticks, not in emoji or the fallback.
        for (name in listOf("kana", "english", "number", "english_r2", "number_r2")) {
            val r = m.getValue(name)
            assertEquals("$name digits", ('0'..'9').map { it.toString() }.toSet(), r.texts.filter { it.text.length == 1 && it.text[0].isDigit() && it.x < k.GRID_X }.map { it.text }.toSet())
            assertEquals("$name: ten outlined cells", 10, r.boxes.count { it.style == GuideLayout.BoxStyle.DIGIT })
        }
        for (name in listOf("emoji", "emoji_list", "fallback")) assertTrue(name, m.getValue(name).boxes.none { it.style == GuideLayout.BoxStyle.DIGIT })
        // The touched digit is highlighted.
        val t7 = m.getValue("kana_touch7")
        assertTrue(t7.texts.any { it.text == "7" && it.style == GuideLayout.TextStyle.SELECTED })
        assertEquals(9, t7.boxes.count { it.style == GuideLayout.BoxStyle.DIGIT })
        // Held L2 (濁点) lights the pill and shows the transformed grid and ring.
        val l2 = m.getValue("kana_l2")
        assertTrue(l2.boxes.any { it.style == GuideLayout.BoxStyle.PILL_HELD })
        assertTrue(l2.texts.any { it.text == "ざ" } && l2.texts.any { it.text == "じ" } && l2.texts.any { it.text == "が" } && l2.texts.none { it.text == "さ" && it.style != GuideLayout.TextStyle.DIM })
        assertTrue("unchanged keys dimmed", l2.texts.any { it.text == "あ" && it.style == GuideLayout.TextStyle.DIM })
        // Held L1 (半濁点) on は: ぱ, the rest dimmed.
        val kl1 = m.getValue("kana_l1")
        assertTrue(kl1.texts.any { it.text == "ぱ" } && kl1.texts.any { it.text == "ぴ" } && kl1.texts.none { it.text == "は" })
        assertTrue(kl1.texts.any { it.text == "か" && it.style == GuideLayout.TextStyle.DIM })
        // Held R1 (小文字) on は: nothing changes on は; ぁ on the centre key.
        val r1 = m.getValue("kana_r1")
        assertTrue(r1.texts.any { it.text == "ぁ" } && r1.texts.any { it.text == "は" } && r1.texts.any { it.text == "か" && it.style == GuideLayout.TextStyle.DIM })
        // Held L3: its pill lit, nothing else (no bar).
        val l3 = m.getValue("kana_l3")
        assertTrue(l3.texts.any { it.text == "L3" && it.style == GuideLayout.TextStyle.SELECTED })
        assertTrue(l3.texts.any { it.text == "さ" && it.style == GuideLayout.TextStyle.SELECTED })
        // Held R2 (amendment 21: 記号): the layer on the grid, the R2 pill lit, the empty centre with its dot, the ring faint.
        val r2 = m.getValue("kana_r2")
        for (c in listOf("〜", "。", "…", "ー", "、", "？", "・", "！")) assertTrue("layer $c", r2.texts.any { it.text == c && it.style == GuideLayout.TextStyle.NORMAL && it.x < k.GRID_RIGHT })
        assertEquals("only the centre is empty", 1, r2.boxes.count { it.style == GuideLayout.BoxStyle.CELL_EMPTY })
        assertTrue("R2 pill lit", r2.texts.any { it.text == "記号" && it.style == GuideLayout.TextStyle.SELECTED })
        assertTrue("no kana keys on the grid", r2.texts.none { it.text in setOf("か", "さ", "や/わ") && it.x < k.GRID_RIGHT })
        fun ringTexts(r: GuideLayout.Result) = r.texts.filter { it.x > k.RING_LEFT && it.x < k.RING_RIGHT && it.y > k.RING_TOP && it.y < k.RING_BOTTOM }
        assertTrue("the ring faint", ringTexts(r2).all { it.style == GuideLayout.TextStyle.FAINT })
        assertTrue("a centre dot", r2.boxes.any { it.style == GuideLayout.BoxStyle.DOT && kotlin.math.abs(it.x + it.w / 2f - (k.GRID_X + k.STEP_X + k.CELL_W / 2f)) < 0.5f })
        // R1 + R2 with a flick out toward ↖ (amendment 21: R1 changes nothing on 記号): 〜 highlighted.
        val r1r2 = m.getValue("pentagon_r1r2_out")
        assertTrue(r1r2.texts.any { it.text == "〜" && it.style == GuideLayout.TextStyle.SELECTED } && r1r2.texts.none { it.text == "ゃ" })
        assertEquals(1, r1r2.boxes.count { it.style == GuideLayout.BoxStyle.CELL_SELECTED })
        // The や/わ key (amendment 21): six cells on the ring, ↘ (よ) pending.
        val hex = m.getValue("hexagon_pending")
        for (c in listOf("や", "ゆ", "よ", "わ", "を", "ん")) assertTrue("ring $c", hex.texts.any { it.text == c && it.x > k.GRID_RIGHT })
        assertTrue(hex.texts.any { it.text == "よ" && it.style == GuideLayout.TextStyle.SELECTED })
        assertEquals("one lit ring cell", 1, hex.boxes.count { it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x > k.GRID_RIGHT })
        // R1 on the や/わ key: ゃ ゅ ょ ゎ, を ん dimmed; the grid key reads ゃ/ゎ.
        val hr1 = m.getValue("hexagon_r1")
        for (c in listOf("ゃ", "ゅ", "ょ", "ゎ")) assertTrue("R1 $c", hr1.texts.any { it.text == c && it.x > k.GRID_RIGHT && it.style == GuideLayout.TextStyle.NORMAL })
        assertTrue(hr1.texts.filter { it.text in setOf("を", "ん") && it.x > k.GRID_RIGHT }.all { it.style == GuideLayout.TextStyle.DIM })
        // 英字 / 数字 with R2 held: the 記号 layer (amendment 18), the centre empty with its dot, the ring faint.
        val e2 = m.getValue("english_r2")
        for (c in listOf("&", ";", "*", "(", ")", "=", "_", "+")) assertTrue("英字 layer $c", e2.texts.any { it.text == c && it.x < k.GRID_RIGHT && it.style == GuideLayout.TextStyle.NORMAL })
        assertTrue("no \" on the layer", e2.texts.none { it.text == "\"" })
        assertEquals(1, e2.boxes.count { it.style == GuideLayout.BoxStyle.CELL_EMPTY })
        assertTrue(e2.texts.any { it.text == "記号" && it.style == GuideLayout.TextStyle.SELECTED })
        assertTrue(ringTexts(e2).isNotEmpty() && ringTexts(e2).all { it.style == GuideLayout.TextStyle.FAINT })
        val e2out = m.getValue("english_r2_out")
        assertTrue(e2out.texts.any { it.text == "&" && it.style == GuideLayout.TextStyle.SELECTED })
        val n2 = m.getValue("number_r2")
        for (c in listOf("{", "^", "}", "[", "]", "\\", "|", "`")) assertTrue("数字 layer $c", n2.texts.any { it.text == c && it.x < k.GRID_RIGHT })
        assertTrue("° is gone", n2.texts.none { it.text == "°" })
        // English with L2: upper case.
        val en = m.getValue("english_l2")
        assertTrue(en.texts.any { it.text == "ABCD" } && en.texts.any { it.text == "UVWX" } && en.texts.any { it.text == "W" })
        // Pending flick highlights the ring cell (uvwx cell, flick ↑ = v).
        val pending = m.getValue("english_pending")
        assertTrue(pending.texts.any { it.text == "v" && it.style == GuideLayout.TextStyle.SELECTED })
        // 英字 / 数字: the centre key is the disc, like あ.
        assertEquals(1, m.getValue("english_pending").boxes.count { it.style == GuideLayout.BoxStyle.CIRCLE })
    }

    @Test
    fun the_map_draws_only_button_names_function_labels_and_characters() {
        val names = setOf("A", "B", "X", "Y", "L1", "L2", "R1", "R2", "L3", "R3", "Select", "Start")
        val digits = ('0'..'9').map { it.toString() }.toSet()
        for ((name, st) in cases()) {
            val r = GuideLayout.layout(st, full, map = true)
            val functions = st.labels.buttons.values.toSet() + st.labels.dpad + st.barLabel + st.barButtons
            val chars = st.gridLabels.toSet() + (st.flickChars?.let { setOf(it.center, it.left, it.up, it.right, it.down) } ?: emptySet()) +
                st.hexagonChars.toSet() +
                (st.barChars?.values?.toSet() ?: emptySet()) +
                (st.petals?.flatten()?.toSet() ?: emptySet()) + (st.petals?.map { it.joinToString("") }?.toSet() ?: emptySet()) +
                (if (st.kanaLayout) digits else emptySet())
            for (t in r.texts) {
                assertTrue("$name: unexpected text '${t.text}'", t.text in names || t.text in functions || t.text in chars)
            }
        }
    }

    @Test
    fun spacing_follows_the_mock_ups() {
        // The left-column layout (the emoji wheel, the fallback) is unchanged.
        val g = GuideMap.Geometry
        assertTrue("D-pad clear of the L1 pill", g.DPAD_TOP - g.PILL_BOTTOM >= g.MIN_GAP)
        assertTrue("D-pad clear of the grid", g.GRID_TOP - g.DPAD_BOTTOM >= g.MIN_GAP)
        assertTrue("face buttons clear of the R1 pill", g.FACE_TOP - g.PILL_BOTTOM >= g.MIN_GAP)
        assertTrue("face buttons clear of the cross", g.CROSS_TOP - g.FACE_BOTTOM >= g.MIN_GAP)
        assertEquals("D-pad centred between L1 and the grid", (g.PILL_BOTTOM + g.GRID_TOP) / 2f, g.DPAD_CY, 1.5f)
        assertEquals("ABXY centred between R1 and the cross", (g.PILL_BOTTOM + g.CROSS_TOP) / 2f, g.FACE_CY, 1.5f)
        assertEquals("the cross rows are the grid rows", g.GRID_TOP, g.CROSS_TOP, 0.01f)
        assertTrue("Select / Start side by side, centred", g.START_X > g.SELECT_X + g.PILL_W && kotlin.math.abs((g.SELECT_X + g.START_X + g.PILL_W) / 2f - GuideMap.BASE_W / 2f) < 2f)
        val r = GuideLayout.layout(case("emoji"), full, map = true)
        fun boxAt(top: Float, left: Float? = null) = r.boxes.any { kotlin.math.abs(it.y - top) < 0.5f && (left == null || kotlin.math.abs(it.x - left) < 0.5f) }
        assertTrue(boxAt(g.DPAD_TOP) && boxAt(g.BAR_Y, g.GRID_X) && boxAt(g.BAR_Y, g.SELECT_X) && boxAt(g.BAR_Y, g.START_X))
        assertTrue(boxAt(g.FACE_TOP))
        assertTrue("the bar bottom is inside the key area", g.BAR_Y + g.CELL_H <= GuideMap.BASE_H)
    }

    @Test
    fun the_kana_layout_keeps_the_users_v4_placement_with_18px_gaps() {
        val g = GuideMap.Geometry
        val k = GuideMap.Kana
        // The 3×3 centre-left where the user's v4 guide has it.
        assertEquals(369f, k.GRID_X, 1f); assertEquals(59f, k.GRID_TOP, 0.5f); assertEquals(632f, k.GRID_RIGHT, 1f); assertEquals(293f, k.GRID_BOTTOM, 1f)
        assertTrue("grid clear of the L pills", k.GRID_X - (g.LEFT_PILL_X + g.PILL_W) >= g.MIN_GAP)
        assertTrue("grid clear of the L3 / R3 row", g.BAR_Y - k.GRID_BOTTOM >= g.MIN_GAP)
        assertTrue("grid top below Select / Start", k.GRID_TOP > g.PILL1_Y + g.PILL_H)
        assertTrue("the ring clear of the grid", k.RING_LEFT - k.GRID_RIGHT >= g.MIN_GAP)
        assertTrue("the ring clear of the L3 / R3 row", g.BAR_Y - k.RING_BOTTOM >= g.MIN_GAP)
        assertTrue("the ring below Start", k.RING_TOP > g.PILL1_Y + g.PILL_H)
        assertTrue("Select / Start between the L2 and R2 pills", k.SELECT_X - (g.LEFT_PILL_X + g.PILL_W) >= g.MIN_GAP && g.RIGHT_PILL_X - (k.START_X + k.MID_PILL_W) >= g.MIN_GAP && k.START_X > k.SELECT_X + k.MID_PILL_W)
        // The digits: below the L1 pill, left of the grid, inside the key area, the L3 pill clear of them.
        assertEquals(62f, k.DIGIT_X, 0.01f)
        assertTrue(k.DIGIT_TOP - g.PILL_BOTTOM > 0f && k.DIGIT_X + 3 * k.DIGIT_W + 2 * k.DIGIT_GAP < k.GRID_X - g.MIN_GAP)
        assertTrue(k.DIGIT_ZERO_Y + k.DIGIT_H <= GuideMap.BASE_H)
        assertEquals("the 0 row is the next row of the pad", k.DIGIT_TOP + 3 * (k.DIGIT_H + k.DIGIT_GAP), k.DIGIT_ZERO_Y, 0.01f)
        assertTrue(k.L3_CX - g.PILL_W / 2f > k.DIGIT_X + 3 * k.DIGIT_W + 2 * k.DIGIT_GAP + g.MIN_GAP)
        for (name in listOf("kana", "pentagon", "kana_r2", "english", "number", "english_r2", "number_r2")) {
            val r = GuideLayout.layout(case(name), full, map = true)
            assertTrue("$name: no D-pad", r.texts.none { it.text == "カーソル" })
            fun boxAt(top: Float, left: Float) = r.boxes.any { kotlin.math.abs(it.y - top) < 0.5f && kotlin.math.abs(it.x - left) < 0.5f }
            assertTrue("$name: grid at (${k.GRID_X}, ${k.GRID_TOP})", boxAt(k.GRID_TOP, k.GRID_X))
            assertTrue("$name: Select / Start at the top", boxAt(g.PILL1_Y, k.SELECT_X) && boxAt(g.PILL1_Y, k.START_X))
            assertTrue("$name: the ring", r.boxes.any { it.style == GuideLayout.BoxStyle.RING && kotlin.math.abs(it.x + it.w / 2f - k.PENT_CX) < 0.5f })
            // Nothing of the grid / right stick reaches the L3 / R3 row.
            for (b in r.boxes.filter { it.style != GuideLayout.BoxStyle.BACKGROUND && it.x > g.LEFT_PILL_X + g.PILL_W && it.x + it.w < g.RIGHT_PILL_X && it.y < g.BAR_Y - 1f && it.y > g.PILL1_Y + 1f }) {
                assertTrue("$name: $b clear of the L3 / R3 row", g.BAR_Y - (b.y + b.h) >= g.MIN_GAP)
            }
        }
        // 英字 / 数字: four ring cells (← ↑ → ↓), no centre character; かな five.
        fun ringCells(name: String) = GuideLayout.layout(case(name), full, map = true).boxes.count {
            it.style in setOf(GuideLayout.BoxStyle.CELL, GuideLayout.BoxStyle.CELL_SELECTED) && it.x > k.GRID_RIGHT && it.x + it.w < k.RING_RIGHT + 1f
        }
        assertEquals(4, ringCells("english"))
        assertEquals(4, ringCells("number"))
        assertEquals(5, ringCells("kana"))
    }

    @Test
    fun touch_digit_cells_hit_test_at_every_scale() {
        val st = case("kana")
        for (canvas in listOf(full, small, GuideLayout.GuideCanvas(1280f, 500f, 2f))) {
            val cells = GuideMap.digitCells(canvas)
            assertEquals(10, cells.size)
            assertEquals("1234567890", cells.joinToString("") { it.digit.toString() })
            val r = GuideLayout.layout(st, canvas, map = true)
            val drawn = r.boxes.filter { it.style == GuideLayout.BoxStyle.DIGIT || it.style == GuideLayout.BoxStyle.CELL_SELECTED && it.x < GuideMap.Kana.GRID_X * canvas.width / full.width }
            for (c in cells) {
                assertEquals("${canvas.width}: centre of ${c.digit}", c.digit, GuideMap.digitAt(st, canvas, c.centreX, c.centreY))
                assertTrue("${canvas.width}: ${c.digit} drawn where it is hit", drawn.any { kotlin.math.abs(it.x - c.x) < 0.01f && kotlin.math.abs(it.y - c.y) < 0.01f && kotlin.math.abs(it.w - c.w) < 0.01f && kotlin.math.abs(it.h - c.h) < 0.01f })
            }
            // Between 1 and 2, between 1 and 4, outside the pad: nothing.
            val one = cells[0]; val four = cells[3]
            assertNull(GuideMap.digitAt(st, canvas, one.x + one.w + 1f * canvas.width / full.width, one.centreY))
            assertNull(GuideMap.digitAt(st, canvas, one.centreX, (one.y + one.h + four.y) / 2f))
            assertNull(GuideMap.digitAt(st, canvas, cells[6].centreX, cells[9].centreY))   // left of 0
            assertNull(GuideMap.digitAt(st, canvas, canvas.width / 2f, canvas.height / 2f))
        }
        // k = 1: the mock's numbers.
        val c = GuideMap.digitCells(full)
        assertEquals(62f, c[0].x, 0.01f); assertEquals(99f, c[0].y, 0.01f); assertEquals(64f, c[0].w, 0.01f); assertEquals(62f, c[0].h, 0.01f)
        assertEquals(134f, c[1].x, 0.01f); assertEquals(169f, c[3].y, 0.01f)
        assertEquals(134f, c[9].x, 0.01f); assertEquals(309f, c[9].y, 0.01f)
        // 960 wide: every coordinate × 0.75.
        val s = GuideMap.digitCells(small)
        // 960 × 289: k = 0.75, centred (the 0.125 px vertical margin).
        assertEquals(62f * 0.75f, s[0].x, 0.05f); assertEquals(309f * 0.75f + 0.125f, s[9].y, 0.05f); assertEquals(48f, s[0].w, 0.05f)
        // No digits outside the text modes with sticks.
        assertNull(GuideMap.digitAt(case("emoji"), full, c[0].centreX, c[0].centreY))
        assertNull(GuideMap.digitAt(case("fallback"), full, c[0].centreX, c[0].centreY))
    }

    @Test
    fun the_middle_column_stays_empty_above_select_and_start_outside_kana() {
        val g = GuideMap.Geometry
        for ((name, st) in cases()) {
            if (st.kanaLayout) continue
            val r = GuideLayout.layout(st, full, map = true)
            val inMiddle = r.boxes.filter { it.style != GuideLayout.BoxStyle.BACKGROUND && it.x + it.w > g.MIDDLE_LEFT && it.x < g.MIDDLE_RIGHT && it.y < g.BAR_Y - 1f }
            assertTrue("$name: boxes in the middle column", inMiddle.isEmpty())
            assertTrue("$name: texts in the middle column", r.texts.none { it.x > g.MIDDLE_LEFT && it.x < g.MIDDLE_RIGHT && it.y < g.BAR_Y - 1f })
        }
    }

    /** v15: the opacity slider sits in the free left column at every scale, clear of everything drawn. */
    @Test
    fun opacity_slider_fits_the_free_left_column() {
        val thor = GuideLayout.GuideCanvas(1920f, 506f, 2f)
        for (canvas in listOf(full, small, thor, GuideLayout.GuideCanvas(1280f, 500f, 2f))) {
            val sl = GuideMap.opacitySlider(canvas)
            val k = minOf(canvas.width / GuideMap.BASE_W, canvas.height / GuideMap.BASE_H)
            val gap = GuideMap.Geometry.MIN_GAP * k
            val right = sl.cx + sl.knobR
            assertTrue("${canvas.width}: knob inside the left edge", sl.cx - sl.knobR >= 0f)
            assertTrue("${canvas.width}: icon above the knob's top position", sl.iconCy + sl.iconR + gap <= sl.knobY(1f) - sl.knobR)
            assertTrue("${canvas.width}: knob inside at the bottom", sl.knobY(GuideMap.Opacity.MIN) + sl.knobR <= canvas.height)
            assertTrue("${canvas.width}: icon inside at the top", sl.iconCy - sl.iconR >= 0f)
            // Every box and text of every text mode stays at least the minimum gap to the right of the slider.
            for (name in listOf("kana", "english", "number")) {
                val r = GuideLayout.layout(case(name), canvas, map = true)
                val boxes = r.boxes.filter { it.style != GuideLayout.BoxStyle.BACKGROUND }
                assertTrue("${canvas.width} $name: a box overlaps the slider", boxes.all { it.x >= right + gap - 0.01f })
                assertTrue("${canvas.width} $name: a box in the touch zone", boxes.all { it.x >= sl.zoneRight })
            }
            // The touch zone ends before the first digit column.
            assertTrue(sl.zoneRight <= GuideMap.digitCells(canvas)[0].x)
        }
        // k = 1 (RG477V): centred in x 0–44, the digits start at 62.
        val rg = GuideMap.opacitySlider(full)
        assertEquals(22f, rg.cx, 0.01f); assertEquals(53f, rg.zoneRight, 0.01f)
        assertTrue(rg.inZone(5f, 10f, 385f)); assertTrue(rg.inZone(52f, 380f, 385f))
        assertTrue(!rg.inZone(54f, 200f, 385f)); assertTrue(!rg.inZone(10f, 385f, 385f))
    }

    @Test
    fun opacity_slider_maps_top_to_opaque_and_bottom_to_the_minimum() {
        val sl = GuideMap.opacitySlider(full)
        assertEquals(1f, sl.opacityAt(sl.trackTop), 0.001f)
        assertEquals(1f, sl.opacityAt(-50f), 0.001f)
        assertEquals(GuideMap.Opacity.MIN, sl.opacityAt(sl.trackBottom), 0.001f)
        assertEquals(GuideMap.Opacity.MIN, sl.opacityAt(1000f), 0.001f)
        assertEquals(0.6f, sl.opacityAt((sl.trackTop + sl.trackBottom) / 2f), 0.001f)
        for (v in listOf(0.2f, 0.35f, 0.5f, 0.8f, 1f)) assertEquals(v, sl.opacityAt(sl.knobY(v)), 0.001f)
        assertEquals(sl.trackTop, sl.knobY(1f), 0.001f)
        assertEquals(sl.trackBottom, sl.knobY(0.05f), 0.001f) // below the minimum: clamped
        // Stored as a whole percent, 20–100.
        val o = GuideMap.Opacity
        assertEquals(100, o.toPercent(1f)); assertEquals(20, o.toPercent(0f)); assertEquals(35, o.toPercent(0.349f))
        assertEquals(1f, o.fromPercent(150), 0f); assertEquals(0.2f, o.fromPercent(-3), 0f); assertEquals(0.45f, o.fromPercent(45), 0.0001f)
        assertEquals(1f, o.clamp(Float.NaN), 0f)
    }
}
