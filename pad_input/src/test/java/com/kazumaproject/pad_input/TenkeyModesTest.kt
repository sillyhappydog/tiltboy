package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendment 14: 英字 and 数字 use the controller's own four-way tables ([FourWayLayouts]). The
 * left stick picks the cell, a right-stick tilt types one of its four characters (← ↑ → ↓).
 * Amendment 18: X types the space (R3 steps the mode), and the symbols that are not on the grid
 * are on the R2 layer (the left stick flicks them); the D-pad fallback keeps them on its L1 bar.
 * Kana keeps Sumire's tenkey table (amendment 6).
 */
class TenkeyModesTest {

    private fun driver(mode: PadMode): PadDriver {
        val d = PadDriver()
        d.left(0.1f, 0f) // a real session has moved a stick
        when (mode) {
            PadMode.ENGLISH -> d.nextMode()
            PadMode.NUMBER -> { d.nextMode(); d.nextMode() }
            else -> {}
        }
        assertEquals(mode, d.engine.padMode)
        d.clear()
        return d
    }

    /** Everything typed, a Space action as " ". */
    private fun PadDriver.text(): String = actions.joinToString("") {
        when (it) {
            is PadAction.KanaKey -> it.char?.toString().orEmpty()
            is PadAction.InsertText -> it.text
            PadAction.Space -> " "
            else -> ""
        }
    }

    // ------------------------------------------------------------------ the tables

    @Test
    fun english_table_is_the_approved_one() {
        assertEquals(
            listOf("abcd", "efgh", "ijkl", "mnop", "qrst", "uvwx", "yz'-", ",.?!", "@/:#"),
            KanaGrid.MAIN.map { KanaGrid.label(it, PadMode.ENGLISH) },
        )
    }

    @Test
    fun number_table_is_the_approved_one() {
        assertEquals(
            listOf("1234", "5678", "90.,", "+-*/", "(=)%", "¥\$€&", "!?'\"", "~@_#", "<:>;"),
            KanaGrid.MAIN.map { KanaGrid.label(it, PadMode.NUMBER) },
        )
    }

    /** User, 2026-11-03: paired brackets go on ← / → of their cell, like the R2 layer's ( ) and [ ]. */
    @Test
    fun paired_brackets_sit_left_and_right() {
        val cells = FourWayLayouts.cells(PadMode.NUMBER)!!
        for ((open, close) in listOf('(' to ')', '<' to '>')) {
            val cell = cells.single { open in it }
            assertEquals("$cell: $open on ←", 0, cell.indexOf(open))
            assertEquals("$cell: $close on →", 2, cell.indexOf(close))
        }
    }

    @Test
    fun every_cell_has_four_distinct_characters_and_no_centre_character() {
        for (mode in listOf(PadMode.ENGLISH, PadMode.NUMBER)) {
            val all = FourWayLayouts.cells(mode)!!.joinToString("")
            assertEquals("$mode: 9 cells × 4", 36, all.length)
            assertEquals("$mode: no character twice on the grid", 36, all.toSet().size)
            for (key in KanaGrid.MAIN) {
                assertEquals("$mode $key: no tap character", null, KanaGrid.charFor(key, GestureType.Tap, mode))
                val cell = KanaGrid.label(key, mode)
                assertEquals(cell.toList(), listOf(Dir4.LEFT, Dir4.UP, Dir4.RIGHT, Dir4.DOWN).map { KanaGrid.charFor(key, it.gesture, mode) })
                assertEquals(
                    KanaGrid.FlickChars(FourWayLayouts.SPACE_GLYPH, cell[0].toString(), cell[1].toString(), cell[2].toString(), cell[3].toString()),
                    KanaGrid.flickChars(key, mode),
                )
            }
        }
    }

    @Test
    fun bars_hold_nine_symbols_that_are_not_on_their_grid() {
        for (mode in listOf(PadMode.ENGLISH, PadMode.NUMBER)) {
            val bar = KanaGrid.bar(mode)
            val grid = FourWayLayouts.cells(mode)!!.joinToString("").toSet()
            assertEquals("$mode: centre + 8 directions", Sector9.entries.toSet(), bar.slots.keys)
            assertEquals("$mode: nine different characters", 9, bar.slots.values.map { it.char }.toSet().size)
            for ((sector, slot) in bar.slots) {
                assertTrue("$mode bar $sector '${slot.char}' is already on the grid", slot.char !in grid)
                assertEquals("$mode bar has no touch key", null, slot.key)
                assertTrue("$mode bar $sector is a symbol", !slot.char.isLetterOrDigit() && !slot.char.isWhitespace())
            }
        }
        assertEquals("\"", KanaGrid.bar(PadMode.ENGLISH).charAt(Sector9.CENTER))
        assertEquals("°", KanaGrid.bar(PadMode.NUMBER).charAt(Sector9.CENTER))
        assertEquals("the kana bar does not change", "わ", KanaGrid.bar(PadMode.KANA).charAt(Sector9.CENTER))
    }

    @Test
    fun english_grid_and_layer_cover_the_printable_ascii_a_user_needs() {
        // Letters (upper case with L2), the space (X) and the punctuation of text, URLs, e-mail
        // addresses and markdown. Digits live in 数字; the rare symbols too (next test). Amendment
        // 18: `"` left 英字 with the bar's centre; it is on the 数字 grid (!?'").
        val needed = ('a'..'z').toList() + " .,?!'-:;()@/#&*_=+".toList()
        val have = FourWayLayouts.stickChars(PadMode.ENGLISH) + ' '
        val missing = needed.filter { it !in have }
        assertTrue("missing in 英字: $missing", missing.isEmpty())
        assertTrue('"' in FourWayLayouts.cells(PadMode.NUMBER)!!.joinToString(""))
        for (c in 'a'..'z') assertEquals("L1 gives upper case", c.uppercaseChar(), HoldTransforms.apply(PadRole.HOLD_DAKUTEN, c, PadMode.ENGLISH))
    }

    @Test
    fun english_and_number_together_cover_every_printable_ascii_character() {
        // Amendment 18: in stick mode = the two grids + the two R2 layers + upper case (L2) + space (X).
        val stick = FourWayLayouts.stickChars(PadMode.ENGLISH) + FourWayLayouts.stickChars(PadMode.NUMBER) + ' ' +
            ('a'..'z').map { it.uppercaseChar() }
        val missing = (0x20..0x7E).map { it.toChar() }.filter { it !in stick }
        assertTrue("printable ASCII missing from 英字 + 数字 in stick mode: $missing", missing.isEmpty())
        assertTrue("° is not typable in stick mode any more", '°' !in FourWayLayouts.stickChars(PadMode.NUMBER))
        // The D-pad fallback (grids + bars) too.
        val fallback = FourWayLayouts.allChars(PadMode.ENGLISH) + FourWayLayouts.allChars(PadMode.NUMBER) + ' ' +
            ('a'..'z').map { it.uppercaseChar() }
        assertTrue((0x20..0x7E).map { it.toChar() }.all { it in fallback })
    }

    @Test
    fun kana_still_equals_the_touch_tenkey_table() {
        val keyMap = KeyMap()
        // The や/わ key (amendment 21) has no touch key of its own; KanaInputTest checks it against the や and わ keys.
        for (key in KanaGrid.MAIN - KanaGrid.YA_WA_KEY) for (g in PadDriver.GESTURES) {
            val info = keyMap.getKeyInfoJapanese(key, false) as? KeyInfo.KeyTapFlickInfo
            val expected = when (g) {
                GestureType.Tap -> info?.tap
                GestureType.FlickLeft -> info?.flickLeft
                GestureType.FlickTop -> info?.flickTop
                GestureType.FlickRight -> info?.flickRight
                else -> info?.flickBottom
            }
            assertEquals("$key $g", expected, KanaGrid.charFor(key, g, PadMode.KANA))
        }
    }

    @Test
    fun every_four_way_character_is_typed_by_its_cell_and_tilt() {
        for (mode in listOf(PadMode.ENGLISH, PadMode.NUMBER)) {
            val d = driver(mode)
            for (sector in Sector9.entries) for (dir in Dir4.entries) {
                d.clear()
                d.holdSector(sector)
                d.flick(dir)
                val expected = FourWayLayouts.charAt(mode, sector, dir)!!
                assertEquals("$mode $sector $dir", listOf<PadAction>(PadAction.InsertText(expected.toString())), d.actions)
            }
            d.leftRelease()
            // Amendment 18: the layer, R2 held and the left stick flicked toward each direction.
            for ((sector, c) in FourWayLayouts.layer(mode)) {
                d.clear()
                d.layer(sector)
                assertEquals("$mode layer $sector", listOf<PadAction>(PadAction.InsertText(c.toString())), d.actions)
            }
            assertEquals("8 directions, no centre", 8, FourWayLayouts.layer(mode).size)
        }
    }

    // ------------------------------------------------------------------ typing with gestures only

    @Test
    fun hello_world_with_gestures_only() {
        val d = driver(PadMode.ENGLISH)
        d.holdSector(Sector9.UP); d.flick(Dir4.DOWN)            // h = ↑ key + ↓
        d.holdSector(Sector9.UP); d.flick(Dir4.LEFT)            // e = ↑ key + ←
        d.holdSector(Sector9.UP_RIGHT); d.flick(Dir4.DOWN)      // l = ↗ key + ↓
        d.flick(Dir4.DOWN)                                      // l
        d.holdSector(Sector9.LEFT); d.flick(Dir4.RIGHT)         // o = ← key + →
        d.leftRelease(); d.tap(PadButton.X)                     // space = X (amendment 18: R3 steps the mode)
        d.holdSector(Sector9.RIGHT); d.flick(Dir4.RIGHT)        // w = → key + →
        d.holdSector(Sector9.LEFT); d.flick(Dir4.RIGHT)         // o
        d.holdSector(Sector9.CENTER); d.flick(Dir4.UP)          // r = centre key + ↑
        d.holdSector(Sector9.UP_RIGHT); d.flick(Dir4.DOWN)      // l
        d.holdSector(Sector9.UP_LEFT); d.flick(Dir4.DOWN)       // d = ↖ key + ↓
        assertEquals("hello world", d.text())
        assertEquals("X is the Space action", 1, d.actions.count { it == PadAction.Space })
    }

    @Test
    fun hi_with_l2_for_the_capital() {
        val d = driver(PadMode.ENGLISH)
        d.press(PadButton.L2); d.holdSector(Sector9.UP); d.flick(Dir4.DOWN); d.release(PadButton.L2) // H (L2 = 大文字 since amendment 16)
        d.holdSector(Sector9.UP_RIGHT); d.flick(Dir4.LEFT)      // i
        d.holdSector(Sector9.DOWN); d.flick(Dir4.DOWN)          // !
        assertEquals("Hi!", d.text())
    }

    @Test
    fun year_2026_in_numbers_with_gestures_only() {
        val d = driver(PadMode.NUMBER)
        d.holdSector(Sector9.UP_LEFT); d.flick(Dir4.UP)         // 2 = ↖ key + ↑
        d.holdSector(Sector9.UP_RIGHT); d.flick(Dir4.UP)        // 0 = ↗ key + ↑
        d.holdSector(Sector9.UP_LEFT); d.flick(Dir4.UP)         // 2
        d.holdSector(Sector9.UP); d.flick(Dir4.UP)              // 6 = ↑ key + ↑
        assertEquals("2026", d.text())
    }

    @Test
    fun r3_types_nothing_any_more_and_x_is_the_space() {
        // Amendment 18: no space on R3 in 英字 / 数字, no あ段 in かな (the stick is out: the click is ignored).
        for (mode in listOf(PadMode.ENGLISH, PadMode.NUMBER, PadMode.KANA)) {
            val d = driver(mode)
            d.left(0f, -1f)
            d.right(0.5f, 0f)               // the right stick a little out: the click is ignored
            d.tap(PadButton.R3)
            d.right(0f, 0f)
            assertTrue("$mode: R3 types nothing ${d.actions}", d.actions.isEmpty())
            assertEquals(mode, d.engine.padMode)
            d.tap(PadButton.X)
            assertEquals("X stays space", listOf<PadAction>(PadAction.Space), d.actions)
            d.clear()
            d.tap(PadButton.A)
            assertEquals("A is 決定 (amendment 11)", listOf<PadAction>(PadAction.Confirm), d.actions)
        }
    }

    @Test
    fun d_pad_fallback_types_the_four_way_cells_and_a_space_on_a() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK)))
        d.tap(PadButton.R1)                                     // fallback: R1 = mode → 英字
        assertEquals(PadMode.ENGLISH, d.engine.padMode)
        d.clear()
        d.press(PadButton.DPAD_UP)
        d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.L2); d.tap(PadButton.A)
        d.release(PadButton.DPAD_UP)
        assertEquals("efgh ", d.text())
        assertEquals(FourWayLayouts.SPACE_GLYPH, d.engine.guide.flickChars!!.center)
    }

    // ------------------------------------------------------------------ the guide

    @Test
    fun guide_in_english_shows_the_four_characters_and_space_in_the_centre() {
        val d = driver(PadMode.ENGLISH)
        d.left(-1f, 0f) // mnop
        val g = d.engine.guide
        assertTrue(g.isTenkey)
        assertEquals(Key.KeyNA, g.selectedKey)
        assertEquals("mnop", g.preview)
        assertEquals(listOf("abcd", "efgh", "ijkl", "mnop", "qrst", "uvwx", "yz'-", ",.?!", "@/:#"), g.gridLabels)
        assertEquals("amendment 18: no centre character", KanaGrid.FlickChars("", "m", "n", "o", "p"), g.flickChars)
        assertEquals(null, g.petals)
        assertEquals("決定", g.labels.buttons[PadButton.A])
        assertEquals("空白", g.labels.buttons[PadButton.X])
        assertEquals("R3 goes to the next mode", "数字へ", g.labels.buttons[PadButton.R3])
        assertEquals("L3 goes to the previous mode", "かなへ", g.labels.buttons[PadButton.L3])
        assertEquals(emptySet<PadButton>(), g.labels.tapButtons)
        assertEquals("候補", g.labels.buttons[PadButton.Y])
        assertEquals("R2 is the symbols layer in 英字", "記号", g.labels.buttons[PadButton.R2])
        assertEquals("大文字", g.labels.buttons[PadButton.L2])
        // Amendment 22: L1 / R1 = 123 (数字's layout while held).
        assertEquals("123", g.labels.buttons[PadButton.L1])
        assertEquals("123", g.labels.buttons[PadButton.R1])
        assertEquals("no bar", "", g.barLabel)
        assertTrue(g.kanaLayout)
    }

    @Test
    fun guide_in_numbers_and_with_l2_held() {
        val n = driver(PadMode.NUMBER)
        n.left(0f, -1f)
        val g = n.engine.guide
        assertEquals(KanaGrid.FlickChars("", "5", "6", "7", "8"), g.flickChars)
        assertEquals("記号", g.labels.buttons[PadButton.R2])
        assertEquals(GuideLabels.NONE, g.labels.buttons[PadButton.L1])
        val e = driver(PadMode.ENGLISH)
        e.press(PadButton.L2); e.left(0f, -1f)
        val u = e.engine.guide
        assertEquals(listOf("ABCD", "EFGH", "IJKL", "MNOP", "QRST", "UVWX", "YZ'-", ",.?!", "@/:#"), u.gridLabels)
        assertEquals("cells without letters are dimmed", listOf(false, false, false, false, false, false, false, true, true), u.gridDim)
        assertEquals(KanaGrid.FlickChars("", "E", "F", "G", "H"), u.flickChars)
    }

    @Test
    fun the_map_draws_no_space_mark_and_keeps_x_as_a_word() {
        val d = driver(PadMode.ENGLISH)
        d.left(0f, -1f)
        val r = GuideMap.layout(d.engine.guide, GuideLayout.GuideCanvas(1280f, 385f, 2f))
        val spaceMarks = r.texts.filter { it.text == "␣" }
        assertEquals("amendment 18: no centre character, no ␣", 0, spaceMarks.size)
        assertTrue("X keeps its word", r.texts.any { it.text == "空白" })
        for (c in listOf("abcd", "efgh", "@/:#", "e", "f", "g", "h")) assertTrue("'$c' drawn", r.texts.any { it.text == c })
    }

    // ------------------------------------------------------------------ modes

    @Test
    fun mode_cycle_by_r3_returns_to_kana() {
        val d = PadDriver()
        d.left(0.1f, 0f)
        repeat(3) { d.nextMode() }
        assertEquals(
            listOf(InputMode.ModeEnglish, InputMode.ModeNumber, InputMode.ModeJapanese),
            d.actions.filterIsInstance<PadAction.SwitchMode>().map { it.mode },
        )
        assertEquals(InputMode.ModeJapanese, d.engine.mode)
    }

    @Test
    fun select_held_long_hides_the_keyboard_instead_of_switching() {
        val d = PadDriver()
        d.left(0.1f, 0f)
        d.press(PadButton.SELECT)
        assertTrue(d.actions.isEmpty())
        assertTrue(d.engine.isRepeating)
        d.time += 300; d.poll()
        assertTrue("not yet", d.actions.isEmpty())
        d.time += 250; d.poll()
        assertEquals(listOf<PadAction>(PadAction.HideKeyboard), d.actions)
        d.release(PadButton.SELECT)
        assertEquals("no mode switch after a long press", 1, d.actions.size)
        assertEquals(PadMode.KANA, d.engine.padMode)
    }

    @Test
    fun host_can_sync_mode_from_touch_keyboard() {
        val d = PadDriver()
        d.left(0.1f, 0f)
        d.engine.mode = InputMode.ModeEnglish
        d.left(0f, -1f); d.flick(Dir4.LEFT)
        assertEquals("e", d.typed())
        d.engine.mode = InputMode.ModeJapanese
        d.clear()
        d.kanaTap()
        assertEquals("さ", d.typed())
        assertNotNull(d.engine.guide.flickChars)
    }
}
