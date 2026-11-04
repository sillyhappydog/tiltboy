package com.kazumaproject.markdownhelperkeyboard.pad

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.pad_input.FaceLayout
import com.kazumaproject.pad_input.GuideLayout
import com.kazumaproject.pad_input.GuideMap
import com.kazumaproject.pad_input.GuidePlacement
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadWords
import com.kazumaproject.pad_input.PadGuideState
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.PadRole
import com.kazumaproject.pad_input.Sector9
import com.kazumaproject.pad_input.StickSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless render of the guide (same [GuideLayout] primitives the Android view paints) with
 * Java2D, at the RG477V's sizes: window 1280×960 landscape, density assumed 2.0, keyboard root
 * from y = 250 with an 85 px bottom inset (visible keyboard 0,250–1280,875; key rows 498–875 as
 * measured on the 2026-11-01 screenshot, i.e. 1280 × 377).
 *
 * Per scene: `corner` = placement (a), the compact overlay at the size [GuidePlacement] gives it;
 * `full` = placement (b), the controller map in the touch keys' rectangle; `diag` = the
 * diagnostics band (compact form). `guide_screen_*.png` / `guide_screenfull_*.png` show the two
 * placements on a mock of the whole screen, navigation area included.
 *
 * `guide_map_*.png` are the controller-map cases amendment 5 asks for (kana, kana + L1, 英字,
 * 数字・記号, 絵文字, the emoji list, a remapped config) at 1280 × 385 and at 960 × 289.
 *
 * Writes PNGs to `build/guide-renders/` (or `-Dpad.guide.render.dir=…`); tools/render-guides.sh
 * copies them into docs/cloud-log/assets/ for the report.
 *
 * Amendment 17: a centred one-character label is drawn by its ink bounds, like PadGuideView
 * ([GuideLayout.inkOrigin]); [singleCharactersAreCentredByInk] measures the rendered pixels.
 *
 * Amendment 18 (v7): 英字 and 数字 draw with the かな layout (touch digits, L3 / R3 pills, Select |
 * Start at the top, bigger ABXY); `guide_map_{kana,english,number,english_r2,number_r2}_*` are the
 * renders the amendment asks for, at 1280 and 960.
 */
class PadGuideRenderTest {

    private data class Scene(val name: String, val mode: PadMode, val shift: Boolean, val sector: Sector9)

    private val scenes = listOf(
        Scene("kana", PadMode.KANA, false, Sector9.UP),
        Scene("kana_shift", PadMode.KANA, true, Sector9.LEFT),
        Scene("english", PadMode.ENGLISH, false, Sector9.UP_RIGHT),
        Scene("english_shift", PadMode.ENGLISH, true, Sector9.UP_RIGHT),
        Scene("number", PadMode.NUMBER, false, Sector9.RIGHT),
        Scene("number_shift", PadMode.NUMBER, true, Sector9.RIGHT),
        Scene("emoji", PadMode.EMOJI, false, Sector9.DOWN),
        Scene("emoji_shift", PadMode.EMOJI, true, Sector9.DOWN),
        Scene("english_neutral", PadMode.ENGLISH, false, Sector9.CENTER),
    )

    private val density = 2f
    private val screenW = 1280
    private val screenH = 960
    private val inset = 85
    private val keyboardTop = 250
    private val keyRowsTop = 498
    private val visible = GuidePlacement.Rect(0, keyboardTop, screenW, screenH - inset)
    private val keyArea = GuidePlacement.Rect(0, keyRowsTop, screenW, screenH - inset)
    private val diagnostics = GuideLayout.GuideCanvas(1256f, 280f, density)   // diagnostics band (140 dp)
    private val mapFull = GuideLayout.GuideCanvas(1280f, 385f, density)       // key area of the mock-ups
    private val mapSmall = GuideLayout.GuideCanvas(960f, 289f, density)

    private val history = listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍")
    /** The guide's words from the app's own string files (Gradle runs in app/, tools/render-guides.sh in the repo root). */
    private fun words(dir: String): PadWords {
        val f = listOf("src/main/res/$dir/pad_strings.xml", "app/src/main/res/$dir/pad_strings.xml").map { File(it) }.first { it.isFile }
        return PadWords.fromAndroidXml(f.readText(), dir)
    }
    private val ja = words("values-ja")
    private val en = words("values")
    private val nintendo = PadConfig(stickSource = StickSource.STICKS, faceLayout = FaceLayout.NINTENDO, words = ja)
    private val nintendoEn = nintendo.copy(words = en, modes = listOf(PadMode.ENGLISH, PadMode.NUMBER))

    private fun engine(config: PadConfig = nintendo, mode: InputMode = InputMode.ModeJapanese) = PadInputEngine(config, mode).also { it.setEmojiHistory(history) }

    private fun state(sc: Scene): PadGuideState {
        // Amendment 20: the emoji wheel is the D-pad fallback's only (R3; the D-pad picks the petal, L1 = page 2).
        val wheel = sc.mode == PadMode.EMOJI
        val e = engine(if (wheel) nintendo.copy(stickSource = StickSource.DPAD_FALLBACK) else nintendo)
        var t = 0L
        fun button(b: PadButton, down: Boolean) { t += 16; e.onButton(b, down, t) }
        // Amendment 20: R3 steps the text modes (Select no longer cycles them).
        when (sc.mode) {
            PadMode.ENGLISH -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.NUMBER -> repeat(2) { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.EMOJI -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            else -> {}
        }
        if (wheel) {
            if (sc.shift) button(PadButton.L1, true)
            if (sc.sector.row == 0) button(PadButton.DPAD_UP, true)
            if (sc.sector.row == 2) button(PadButton.DPAD_DOWN, true)
            if (sc.sector.col == 0) button(PadButton.DPAD_LEFT, true)
            if (sc.sector.col == 2) button(PadButton.DPAD_RIGHT, true)
            return e.guide
        }
        // Amendment 18: the second set is R2 in the text modes (記号; amendment 21: in かな too).
        if (sc.shift) button(PadButton.R2, true)
        val (x, y) = when (sc.sector) {
            Sector9.CENTER -> 0f to 0f
            else -> ((sc.sector.col - 1).toFloat() * if (sc.sector.row != 1) 0.707f else 1f) to ((sc.sector.row - 1).toFloat() * if (sc.sector.col != 1) 0.707f else 1f)
        }
        t += 16; e.onSticks(x, y, 0f, 0f, t)
        return e.guide
    }

    /** The controller-map cases of amendments 5, 9 and 10 (idle, L1 / L2 / R1 / L3 held, every mode, remapped). */
    private fun mapCases(): List<Pair<String, PadGuideState>> {
        val out = ArrayList<Pair<String, PadGuideState>>()
        var t = 0L
        fun PadInputEngine.tap(b: PadButton) { t += 16; onButton(b, true, t); t += 16; onButton(b, false, t) }
        fun PadInputEngine.left(x: Float, y: Float) { t += 16; onSticks(x, y, 0f, 0f, t) }
        fun PadInputEngine.hold(b: PadButton) { t += 16; onButton(b, true, t) }
        // Amendment 16 (v5): かな at the user's v4 placement. idle = the stick at rest on あ;
        // R2 held = the 記号 layer since amendment 21 (a left flick out toward ↙ highlights ？); R1 + R2 = the same;
        // L2 held = 濁点 on か (↖); L1 = 半濁点 on は; R1 = 小文字 on た; L3 does nothing in かな.
        engine().let { e -> e.left(0f, 0f); out += "kana" to e.guide }
        engine().let { e -> e.hold(PadButton.R2); out += "kana_r2" to e.guide }
        engine().let { e -> e.hold(PadButton.R2); e.left(-0.5f, 0.5f); e.left(-0.68f, 0.68f); out += "kana_r2_out" to e.guide }
        engine().let { e -> e.hold(PadButton.R1); e.hold(PadButton.R2); out += "kana_r1r2" to e.guide }
        engine().let { e -> e.hold(PadButton.L2); e.left(-0.707f, -0.707f); out += "kana_l2" to e.guide }   // 濁点
        engine().let { e -> e.hold(PadButton.L1); e.left(1f, 0f); out += "kana_l1" to e.guide }             // 半濁点
        engine().let { e -> e.hold(PadButton.R1); e.left(0.707f, -0.707f); out += "kana_r1" to e.guide }     // 小文字
        // Amendment 21 (v9): the や/わ key (↘) selected = the v9 kana mock; R2 held after it = the v9 kana R2 mock
        // (記号 on the grid, the や/わ key's six slots faint on the ring); R1 on it (ゃ ゅ ょ ゎ).
        engine().let { e -> e.left(0.707f, 0.707f); out += "kana_yawa" to e.guide }
        engine().let { e -> e.left(0.707f, 0.707f); e.hold(PadButton.R2); out += "kana_yawa_r2" to e.guide }
        engine().let { e -> e.hold(PadButton.R1); e.left(0.707f, 0.707f); out += "kana_yawa_r1" to e.guide }
        // Amendment 22 (v9): 英字 with L1 held = the v9 en_l1 mock (↖ 1234 selected); R1 + R2 = 数字's 記号 layer.
        engine().let { e -> e.tap(PadButton.R3); e.hold(PadButton.L1); e.left(-0.707f, -0.707f); out += "english_l1" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.hold(PadButton.R1); e.hold(PadButton.R2); out += "english_r1_r2" to e.guide }
        engine(nintendoEn, InputMode.ModeEnglish).let { e -> e.hold(PadButton.L1); e.left(-0.707f, -0.707f); out += "english_l1_en" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.left(-1f, 0f); out += "english" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.hold(PadButton.L2); e.left(-1f, 0f); out += "english_l2" to e.guide }  // 大文字
        // Amendment 18: R2 is the 記号 layer in 英字 / 数字 (a left flick out toward ↖ highlights &).
        engine().let { e -> e.tap(PadButton.R3); e.hold(PadButton.R2); out += "english_r2" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.hold(PadButton.R2); e.left(-0.5f, -0.5f); e.left(-0.68f, -0.68f); out += "english_r2_out" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.tap(PadButton.R3); e.left(0f, -1f); out += "number" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.tap(PadButton.R3); e.hold(PadButton.R2); out += "number_r2" to e.guide }
        // Amendment 18: the touch digit 7 under the finger; L3 held (its pill lit); one mode only (empty L3 / R3 pills).
        engine().let { e -> e.left(0f, 0f); out += "kana_touch7" to e.guide.copy(touchedDigit = '7') }
        engine().let { e -> e.hold(PadButton.L3); out += "kana_l3" to e.guide }
        engine(nintendo.copy(modes = listOf(PadMode.KANA))).let { e -> e.left(0f, 0f); out += "kana_only" to e.guide }
        // The states of the v7 mocks (か / abcd selected), for the mock-vs-render comparison in the report.
        engine().let { e -> e.left(-0.707f, -0.707f); out += "kana_ka" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.left(-0.707f, -0.707f); out += "english_abcd" to e.guide }
        // Amendment 20: the emoji wheel is the D-pad fallback's only; Start opens the list at once.
        engine(nintendo.copy(stickSource = StickSource.DPAD_FALLBACK)).let { e -> e.tap(PadButton.R3); e.hold(PadButton.DPAD_UP); out += "emoji" to e.guide }
        engine().let { e -> e.tap(PadButton.START); out += "emoji_list" to e.guide }
        val remapped = nintendo.copy(roles = PadConfig.DEFAULT_ROLES + mapOf(PadButton.Y to PadRole.BACKSPACE, PadButton.B to PadRole.CONFIRM, PadButton.R3 to PadRole.NONE))
        engine(remapped).let { e -> e.left(0f, -1f); out += "remapped" to e.guide }
        engine().let { e -> e.tap(PadButton.R3); e.left(1f, 0f); t += 16; e.onSticks(1f, 0f, 0f, -1f, t); out += "english_flick" to e.guide }
        // Amendment 12: the English UI (英字 + 数字 only, the default for a non-Japanese system).
        engine(nintendoEn, InputMode.ModeEnglish).let { e -> e.left(-1f, 0f); out += "english_en" to e.guide }
        engine(nintendoEn, InputMode.ModeEnglish).let { e -> e.hold(PadButton.L2); e.left(-1f, 0f); out += "english_l2_en" to e.guide }
        engine(nintendoEn, InputMode.ModeEnglish).let { e -> e.hold(PadButton.R2); out += "english_r2_en" to e.guide }
        engine(nintendoEn, InputMode.ModeNumber).let { e -> e.left(0f, -1f); out += "number_en" to e.guide }
        engine(nintendoEn, InputMode.ModeNumber).let { e -> e.hold(PadButton.R2); out += "number_r2_en" to e.guide }
        engine(nintendoEn.copy(stickSource = StickSource.DPAD_FALLBACK), InputMode.ModeEnglish).let { e -> e.tap(PadButton.R3); e.hold(PadButton.DPAD_UP); out += "emoji_en" to e.guide }
        // Amendment 15: kana on the pentagon (the only kana right stick since amendment 18); the pending slot.
        engine().let { e -> e.left(-0.707f, -0.707f); t += 16; e.onSticks(-0.707f, -0.707f, 0.7f, 0.7f, t); out += "kana_pending" to e.guide }
        // The English UI's kana guide (Ya/Wa, Sym).
        engine(nintendo.copy(words = en), InputMode.ModeJapanese).let { e -> e.left(0f, 0f); out += "kana_en" to e.guide }
        // Amendment 17: re-aim by rotating. か key, ↑ out, rotated along the rim to ↘ (く lit);
        // amendment 21: the や/わ key, ↑ out rotated to ↖ (ん lit); the 記号 layer aimed at → (、) and
        // rotated onto ↘ (！).
        fun PadInputEngine.right(deg: Double, r: Float = 0.95f, lx: Float = 0f, ly: Float = 0f) {
            val rad = Math.toRadians(deg); t += 16; onSticks(lx, ly, (Math.cos(rad) * r).toFloat(), (-Math.sin(rad) * r).toFloat(), t)
        }
        fun PadInputEngine.leftAt(deg: Double, r: Float = 0.95f) {
            val rad = Math.toRadians(deg); t += 16; onSticks((Math.cos(rad) * r).toFloat(), (-Math.sin(rad) * r).toFloat(), 0f, 0f, t)
        }
        engine().let { e ->
            e.left(-0.707f, -0.707f); e.right(90.0, 0.5f, -0.707f, -0.707f); e.right(90.0, lx = -0.707f, ly = -0.707f)
            out += "kana_reaim_up" to e.guide
            for (deg in listOf(60.0, 30.0, 0.0, -30.0, -54.0)) e.right(deg, lx = -0.707f, ly = -0.707f)
            out += "kana_reaim_downright" to e.guide
        }
        engine().let { e ->
            e.left(0.707f, 0.707f); e.right(90.0, 0.5f, 0.707f, 0.707f); e.right(90.0, lx = 0.707f, ly = 0.707f)
            for (deg in listOf(126.0, 150.0)) e.right(deg, lx = 0.707f, ly = 0.707f)
            out += "kana_yawa_reaim" to e.guide
        }
        engine().let { e ->
            e.hold(PadButton.R2); e.leftAt(0.0, 0.5f); e.leftAt(0.0)
            out += "kana_r2_empty_out" to e.guide
            for (deg in listOf(-20.0, -45.0)) e.leftAt(deg)
            out += "kana_r2_reaim" to e.guide
        }
        return out
    }

    @Test
    fun renderAllGuides() {
        val dir = File(System.getProperty("pad.guide.render.dir") ?: "build/guide-renders").apply { mkdirs() }
        var count = 0
        for (sc in scenes) {
            val s = state(sc)
            val corner = GuidePlacement.cornerFrame(visible, s.isTenkey, minOf(screenW, screenH), density)
            val canvases = listOf(
                Triple("corner", GuideLayout.GuideCanvas(corner.width.toFloat(), corner.height.toFloat(), density), false),
                Triple("full", GuideLayout.GuideCanvas(keyArea.width.toFloat(), keyArea.height.toFloat(), density), true),
                Triple("diag", diagnostics, false),
            )
            for ((where, canvas, map) in canvases) {
                write(renderCanvas(s, canvas, map), File(dir, "guide_${sc.name}_$where.png"))
                count++
            }
            if (sc.name in setOf("kana", "english", "emoji")) {
                write(screenMock(s, corner, full = false), File(dir, "guide_screen_${sc.name}.png"))
                write(screenMock(s, keyArea, full = true), File(dir, "guide_screenfull_${sc.name}.png"))
                count += 2
            }
        }
        for ((name, s) in mapCases()) {
            write(renderCanvas(s, mapFull, map = true, opaque = true), File(dir, "guide_map_${name}_1280.png"))
            write(renderCanvas(s, mapSmall, map = true, opaque = true), File(dir, "guide_map_${name}_960.png"))
            count += 2
        }
        assertTrue(count >= 71)
        println("guide renders written to ${dir.absolutePath}")
    }

    /**
     * Amendment 17: every cell that draws one character has its ink centred on the cell's centre,
     * within 2 px: the pentagon, the grid and the や/わ layer, the 記号 key's 。 、 ！ ？ ー above all.
     * Amendment 18: also 英字 / 数字 (their ring cells, the 4-character grid labels, the 記号 layers)
     * and the touch digits. Measures the rendered pixels (white text on the dark cells) of the Java2D render,
     * which places glyphs with the same [GuideLayout.inkOrigin] as PadGuideView.
     */
    @Test
    fun singleCharactersAreCentredByInk() {
        var t = 0L
        fun PadInputEngine.left(x: Float, y: Float) { t += 16; onSticks(x, y, 0f, 0f, t) }
        fun PadInputEngine.tap(b: PadButton) { t += 16; onButton(b, true, t); t += 16; onButton(b, false, t) }
        val cases = listOf(
            "yawa_ring" to engine().also { it.left(0.707f, 0.707f) }.guide,
            "kana_idle" to engine().also { it.left(0f, 0f) }.guide,
            "kana_ka" to engine().also { it.left(-0.707f, -0.707f) }.guide,
            "kana_r2" to engine().also { t += 16; it.onButton(PadButton.R2, true, t) }.guide,
            "english" to engine().also { it.tap(PadButton.R3); it.left(-0.707f, -0.707f) }.guide,
            "english_r2" to engine().also { it.tap(PadButton.R3); t += 16; it.onButton(PadButton.R2, true, t) }.guide,
            "number" to engine().also { it.tap(PadButton.R3); it.tap(PadButton.R3); it.left(0f, 0f) }.guide,
            "number_r2" to engine().also { it.tap(PadButton.R3); it.tap(PadButton.R3); t += 16; it.onButton(PadButton.R2, true, t) }.guide,
            // Amendment 22: 英字 with L1 held (数字's grid and ring).
            "english_l1" to engine().also { it.tap(PadButton.R3); t += 16; it.onButton(PadButton.L1, true, t); it.left(-0.707f, -0.707f) }.guide,
        )
        val dir = File(System.getProperty("pad.guide.render.dir") ?: "build/guide-renders").apply { mkdirs() }
        val checked = LinkedHashMap<String, String>()
        val cellStyles = setOf(GuideLayout.BoxStyle.CELL, GuideLayout.BoxStyle.CIRCLE, GuideLayout.BoxStyle.CELL_EMPTY, GuideLayout.BoxStyle.DIGIT)
        for ((name, s) in cases) for (canvas in listOf(mapFull, mapSmall)) {
            val r = GuideLayout.layout(s, canvas, map = true)
            val img = renderCanvas(s, canvas, map = true, opaque = true)
            write(img, File(dir, "ink_${name}_${canvas.width.toInt()}.png"))
            for (text in r.texts) {
                if (!GuideLayout.inkCentred(text) || text.style != GuideLayout.TextStyle.NORMAL) continue
                // The cell the character sits in: the smallest cell box around its anchor.
                val cell = r.boxes.filter { it.style in cellStyles && text.x in it.x..(it.x + it.w) && text.y in it.y..(it.y + it.h) }
                    .minByOrNull { it.w * it.h } ?: continue
                assertEquals("$name ${text.text}: anchored on the cell centre", cell.x + cell.w / 2f, text.x, 0.5f)
                assertEquals("$name ${text.text}: anchored on the cell centre", cell.y + cell.h / 2f, text.y, 0.5f)
                // Ink = near-white pixels inside the cell (the cells, the ring and the dots are all darker).
                var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
                for (y in cell.y.toInt()..(cell.y + cell.h).toInt().coerceAtMost(img.height - 1))
                    for (x in cell.x.toInt()..(cell.x + cell.w).toInt().coerceAtMost(img.width - 1)) {
                        val rgb = img.getRGB(x, y)
                        val v = maxOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
                        if (v >= 0xA0) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
                    }
                if (minX > maxX) { println("ink: $name ${text.text}: no ink drawn (font without the glyph?)"); continue }
                val inkX = (minX + maxX + 1) / 2f
                val inkY = (minY + maxY + 1) / 2f
                val dx = inkX - text.x; val dy = inkY - text.y
                checked["$name/${canvas.width.toInt()}/${text.text}"] = "%+.1f,%+.1f".format(dx, dy)
                if (GuideLayout.keepsBaseline(text)) {
                    // Amendment 19: `_` is centred horizontally only; it keeps the baseline (lower half, see underscore test).
                    assertTrue("$name ${canvas.width.toInt()}: '${text.text}' ink is off by $dx px horizontally", kotlin.math.abs(dx) <= 2f)
                    continue
                }
                assertTrue("$name ${canvas.width.toInt()}: '${text.text}' ink centre is off by ($dx, $dy) px", kotlin.math.abs(dx) <= 2f && kotlin.math.abs(dy) <= 2f)
            }
        }
        println("ink offsets (px): $checked")
        // Amendment 21: the や/わ key's six cells, and its grid label 「や/わ」.
        for (size in listOf(1280, 960)) for (c in listOf("や", "ゆ", "よ", "わ", "を", "ん")) {
            assertTrue("the や/わ key's $c was measured on the ring at $size", checked.keys.any { it.startsWith("yawa_ring/$size/") && it.endsWith("/$c") })
        }
        assertTrue("「や/わ」 measured", checked.keys.any { it.startsWith("kana_idle/1280/") && it.endsWith("/や/わ") })
        for (size in listOf(1280, 960)) {
            for (c in listOf("&", ";", "*", "(", ")", "=", "_", "+")) assertTrue("英字 layer $c measured at $size", checked.keys.any { it.startsWith("english_r2/$size/") && it.endsWith("/$c") })
            for (c in listOf("{", "^", "}", "[", "]", "|", "`")) assertTrue("数字 layer $c measured at $size", checked.keys.any { it.startsWith("number_r2/$size/") && it.endsWith("/$c") })
            for (c in listOf("a", "b", "c", "d", "efgh", "qrst", "@/:#")) assertTrue("英字 $c measured at $size", checked.keys.any { it.startsWith("english/$size/") && it.endsWith("/$c") })
            for (c in ('0'..'9').map { it.toString() }) assertTrue("digit $c measured at $size", checked.keys.any { it.startsWith("kana_idle/$size/") && it.endsWith("/$c") })
        }
        // Amendment 21: R2 in かな = 記号 (。 、 above all: the ones that sat off-centre on the v5 device).
        for (size in listOf(1280, 960)) for (c in listOf("〜", "。", "…", "ー", "、", "？", "・", "！")) {
            assertTrue("かな layer $c measured at $size", checked.keys.any { it.startsWith("kana_r2/$size/") && it.endsWith("/$c") })
        }
        for (c in listOf("か", "さ", "た", "な", "は", "ま", "ら")) assertTrue("grid $c measured", checked.keys.any { it.startsWith("kana_idle/1280/") && it.endsWith("/$c") })
        // For the report: where the v5 metric centring put 。 and 、 with this font (ink centre minus cell centre).
        for (c in listOf("。", "、")) {
            val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
            val g = graphics(img)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(GuideMap.Kana.CHAR)
            val fm = g.fontMetrics
            val ink = TextLayout(c, g.font, g.fontRenderContext).bounds
            val oldDx = -fm.stringWidth(c) / 2f + ink.centerX.toFloat()
            val oldDy = (fm.ascent - fm.descent) / 2f + ink.centerY.toFloat()
            g.dispose()
            println("v5 metric centring of $c at ${GuideMap.Kana.CHAR} px (Java2D font): ink centre off by (%+.1f, %+.1f) px".format(oldDx, oldDy))
        }
    }

    /**
     * Amendment 19: on the RG477V the 英字 R2 layer's `_` was ink-centred vertically and read as a
     * minus. It now keeps the font baseline: its ink sits in the lower half of its cell (and stays
     * centred horizontally), at both map sizes. Writes `underscore_<size>.png`.
     */
    @Test
    fun underscoreSitsInTheLowerHalfOfItsCell() {
        var t = 0L
        val e = engine().also { t += 16; it.onButton(PadButton.R3, true, t); t += 16; it.onButton(PadButton.R3, false, t); t += 16; it.onButton(PadButton.R2, true, t) }
        val s = e.guide
        val dir = File(System.getProperty("pad.guide.render.dir") ?: "build/guide-renders").apply { mkdirs() }
        val cellStyles = setOf(GuideLayout.BoxStyle.CELL, GuideLayout.BoxStyle.CIRCLE, GuideLayout.BoxStyle.CELL_EMPTY)
        for (canvas in listOf(mapFull, mapSmall)) {
            val r = GuideLayout.layout(s, canvas, map = true)
            val underscore = r.texts.single { it.text == "_" }
            assertTrue("`_` keeps the baseline", GuideLayout.keepsBaseline(underscore))
            val cell = r.boxes.filter { it.style in cellStyles && underscore.x in it.x..(it.x + it.w) && underscore.y in it.y..(it.y + it.h) }
                .minBy { it.w * it.h }
            val img = renderCanvas(s, canvas, map = true, opaque = true)
            write(img, File(dir, "underscore_${canvas.width.toInt()}.png"))
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
            for (y in cell.y.toInt()..(cell.y + cell.h).toInt().coerceAtMost(img.height - 1))
                for (x in cell.x.toInt()..(cell.x + cell.w).toInt().coerceAtMost(img.width - 1)) {
                    val rgb = img.getRGB(x, y)
                    val v = maxOf(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
                    if (v >= 0xA0) { minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
                }
            assertTrue("`_` drew ink at ${canvas.width.toInt()}", minX <= maxX)
            val mid = cell.y + cell.h / 2f
            println("underscore at ${canvas.width.toInt()}: cell y ${cell.y}..${cell.y + cell.h} (middle $mid), ink y $minY..$maxY, x $minX..$maxX")
            assertTrue("`_` ink (y $minY..$maxY) must sit in the lower half of its cell (middle $mid) at ${canvas.width.toInt()}", minY > mid)
            assertTrue("`_` ink stays inside its cell", maxY <= cell.y + cell.h)
            val dx = (minX + maxX + 1) / 2f - (cell.x + cell.w / 2f)
            assertTrue("`_` is centred horizontally (off by $dx px)", kotlin.math.abs(dx) <= 2f)
        }
    }

    private fun renderCanvas(s: PadGuideState, canvas: GuideLayout.GuideCanvas, map: Boolean, opaque: Boolean = map): BufferedImage {
        val img = BufferedImage(canvas.width.toInt(), canvas.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = graphics(img)
        // Keyboard-like backdrop so the translucent panel reads as it does on the device; the full
        // placement paints an opaque dark background like PadGuideView.opaque.
        g.color = if (opaque) OPAQUE_BG else KEYBOARD_BG
        g.fillRect(0, 0, img.width, img.height)
        paint(g, GuideLayout.layout(s, canvas, map), 0f, 0f)
        g.dispose()
        return img
    }

    /**
     * The whole 1280×960 window: app area, keyboard root (toolbar + candidates + keys), navigation
     * area. [full] = placement (b): the controller map takes the key area (opaque); else the corner overlay.
     */
    private fun screenMock(s: PadGuideState, frame: GuidePlacement.Rect, full: Boolean): BufferedImage {
        val img = BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB)
        val g = graphics(img)
        g.color = Color(0xF8, 0xF9, 0xFF); g.fillRect(0, 0, screenW, keyboardTop)
        g.color = KEYBOARD_BG; g.fillRect(0, keyboardTop, screenW, screenH - keyboardTop)
        // Key rows: 4 rows × 5 columns, like Sumire's 12-key layout on the screenshot.
        val rows = 4; val cols = 5
        val rowH = (keyArea.height - 8) / rows.toFloat(); val colW = (screenW - 8) / cols.toFloat()
        g.color = Color(0xFA, 0xFA, 0xFF)
        if (!full) for (r in 0 until rows) for (c in 0 until cols) {
            g.fill(RoundRectangle2D.Float(4 + c * colW + 4, keyRowsTop + 4 + r * rowH + 4, colW - 8, rowH - 8, 16f, 16f))
        }
        // Candidate bar stand-in above the keys.
        g.color = Color(0xFF, 0xFF, 0xFF)
        g.fillRect(0, keyRowsTop - 72, screenW, 68)
        g.color = Color(0x60, 0x60, 0x60)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(26f)
        g.drawString("の　ノ　之　野　乃　（候補欄はそのまま）", 24f, keyRowsTop - 28f)
        g.color = Color.BLACK; g.fillRect(0, screenH - inset, screenW, inset)
        g.color = Color(0x80, 0x80, 0x80)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(22f)
        g.drawString("ナビゲーション領域（y = ${screenH - inset}〜${screenH - 1}、画面には映らない）", 20f, screenH - inset / 2f + 8f)
        // Visible-bottom line and the guide at its frame.
        if (full) { g.color = OPAQUE_BG; g.fillRect(frame.left, frame.top, frame.width, frame.height) }
        paint(g, GuideLayout.layout(s, GuideLayout.GuideCanvas(frame.width.toFloat(), frame.height.toFloat(), density), map = full), frame.left.toFloat(), frame.top.toFloat())
        g.color = Color(0xD9, 0x3B, 0x3B)
        g.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 8f), 0f)
        g.drawLine(0, screenH - inset - 1, screenW, screenH - inset - 1)
        g.dispose()
        return img
    }

    private fun graphics(img: BufferedImage): Graphics2D = img.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    private fun write(img: BufferedImage, file: File) {
        ImageIO.write(img, "png", file)
        assertTrue(file.length() > 0)
    }

    private fun paint(g: Graphics2D, r: GuideLayout.Result, ox: Float, oy: Float) {
        for (p in r.primitives) {
            when (p) {
                is GuideLayout.Primitive.Box -> {
                    if (p.style == GuideLayout.BoxStyle.RING) {
                        // Amendment 15: the pentagon's ring is stroked, like PadGuideView paints it.
                        g.color = Color(PadGuideView.RING_COLOR, true)
                        val old = g.stroke
                        g.stroke = BasicStroke(PadGuideView.ringStroke(p.w))
                        g.draw(java.awt.geom.Ellipse2D.Float(ox + p.x, oy + p.y, p.w, p.h))
                        g.stroke = old
                        continue
                    }
                    val color = when (p.style) {
                        GuideLayout.BoxStyle.RING -> continue
                        GuideLayout.BoxStyle.DOT -> Color(PadGuideView.DOT_COLOR, true)
                        GuideLayout.BoxStyle.BACKGROUND -> Color(0x20, 0x21, 0x24, 0xCC)
                        GuideLayout.BoxStyle.CELL -> Color(0x3C, 0x40, 0x43)
                        GuideLayout.BoxStyle.CELL_SELECTED -> Color(0x8A, 0xB4, 0xF8)
                        GuideLayout.BoxStyle.PETAL -> Color(0x34, 0x37, 0x3B)
                        GuideLayout.BoxStyle.PETAL_SELECTED -> Color(0x8A, 0xB4, 0xF8)
                        GuideLayout.BoxStyle.BIG_PETAL -> Color(0x2B, 0x2E, 0x33)
                        GuideLayout.BoxStyle.HINT -> continue
                        GuideLayout.BoxStyle.PILL -> Color(0x3C, 0x40, 0x43)
                        GuideLayout.BoxStyle.PILL_HELD -> Color(0x8A, 0xB4, 0xF8)
                        GuideLayout.BoxStyle.FACE -> Color(0x3C, 0x40, 0x43)
                        GuideLayout.BoxStyle.FACE_HELD -> Color(0x8A, 0xB4, 0xF8)
                        GuideLayout.BoxStyle.CIRCLE -> Color(PadGuideView.CIRCLE_COLOR, true)
                        GuideLayout.BoxStyle.CELL_EMPTY -> Color(PadGuideView.CIRCLE_COLOR, true)
                        GuideLayout.BoxStyle.PILL_EMPTY -> Color(PadGuideView.CIRCLE_COLOR, true)
                        GuideLayout.BoxStyle.DIGIT -> {
                            // Amendment 18: the touch digits are outlined, like PadGuideView paints them.
                            g.color = Color(PadGuideView.DIGIT_OUTLINE_COLOR, true)
                            val old = g.stroke
                            g.stroke = BasicStroke(PadGuideView.digitStroke(p.w))
                            g.draw(RoundRectangle2D.Float(ox + p.x, oy + p.y, p.w, p.h, p.radius * 2, p.radius * 2))
                            g.stroke = old
                            continue
                        }
                    }
                    g.color = color
                    g.fill(RoundRectangle2D.Float(ox + p.x, oy + p.y, p.w, p.h, p.radius * 2, p.radius * 2))
                }
                is GuideLayout.Primitive.Text -> {
                    val (color, bold) = when (p.style) {
                        GuideLayout.TextStyle.NORMAL -> Color.WHITE to true
                        GuideLayout.TextStyle.SELECTED -> Color(0x20, 0x21, 0x24) to true
                        GuideLayout.TextStyle.DIM -> Color(0xC8, 0xCC, 0xD0) to false
                        GuideLayout.TextStyle.ACCENT -> Color(0xFD, 0xD6, 0x63) to true
                        GuideLayout.TextStyle.LABEL -> Color(0xBD, 0xC1, 0xC6) to false
                        GuideLayout.TextStyle.HEADER -> Color(0xBD, 0xC1, 0xC6) to false
                        GuideLayout.TextStyle.HINT -> Color(0x9A, 0xA0, 0xA6) to false
                        GuideLayout.TextStyle.FUNC -> Color.WHITE to false
                        GuideLayout.TextStyle.FAINT -> Color(PadGuideView.FAINT_COLOR, true) to true
                    }
                    // Logical font: fontconfig fallback gives Japanese (IPAGothic) and Noto Color Emoji.
                    g.font = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(p.size)
                    g.color = color
                    // Amendment 19: a low horizontal stroke (`_`) keeps the font baseline, centred horizontally only.
                    if (GuideLayout.keepsBaseline(p)) {
                        val layout = TextLayout(p.text, g.font, g.fontRenderContext)
                        val ink = layout.bounds
                        if (!ink.isEmpty) {
                            val fm = g.fontMetrics
                            val (x, baseline) = GuideLayout.baselineOrigin(ox + p.x, oy + p.y, ink.minX.toFloat(), ink.maxX.toFloat(), fm.ascent.toFloat(), fm.descent.toFloat())
                            layout.draw(g, x, baseline)
                            continue
                        }
                    }
                    // Amendment 17: one character is centred by its ink box, as PadGuideView does.
                    if (GuideLayout.inkCentred(p)) {
                        val layout = TextLayout(p.text, g.font, g.fontRenderContext)
                        val ink = layout.bounds
                        if (!ink.isEmpty) {
                            val (x, baseline) = GuideLayout.inkOrigin(ox + p.x, oy + p.y, ink.minX.toFloat(), ink.minY.toFloat(), ink.maxX.toFloat(), ink.maxY.toFloat())
                            layout.draw(g, x, baseline)
                            continue
                        }
                    }
                    val fm = g.fontMetrics
                    val textWidth = fm.stringWidth(p.text)
                    val baseline = oy + p.y + (fm.ascent - fm.descent) / 2f
                    val x = ox + when (p.align) {
                        GuideLayout.Align.LEFT -> p.x
                        GuideLayout.Align.RIGHT -> p.x - textWidth
                        GuideLayout.Align.CENTER -> p.x - textWidth / 2f
                    }
                    g.drawString(p.text, x, baseline)
                }
            }
        }
    }

    private companion object {
        val KEYBOARD_BG = Color(0xE8, 0xEA, 0xED)
        val OPAQUE_BG = Color(0x20, 0x21, 0x24)
    }
}
