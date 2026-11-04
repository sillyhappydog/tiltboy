package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kana mode: every key × (tap + 4 flicks), the や/わ key's six slots and the R2 記号 layer against
 * Sumire's own 12-key tables. Amendment 21 (v9) grid: ↖ か ↑ さ ↗ た / ← な ・あ → は / ↙ ま ↓ ら
 * ↘ や/わ. Amendment 18: kana is typed on the pentagon (↑ あ段 → い段 ↘ う段 ↙ え段 ← お段,
 * [PadDriver.kana]); the や/わ key on its six-slot ring ([PadDriver.yaWa]).
 */
class KanaInputTest {

    private val keyMap = KeyMap()

    private fun expectedChar(key: Key, gesture: GestureType): Char? {
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

    private fun sectorOf(key: Key): Sector9 =
        Sector9.fromIndex(KanaGrid.MAIN.indexOf(key))

    @Test
    fun grid_is_the_v9_layout_with_a_in_the_centre() {
        assertEquals(listOf('か', 'さ', 'た', 'な', 'あ', 'は', 'ま', 'ら'),
            KanaGrid.MAIN.filter { it != KanaGrid.YA_WA_KEY }.map { KanaGrid.charFor(it, GestureType.Tap) })
        assertEquals("the stick at rest is the あ key", Key.KeyA, KanaGrid.keyFor(Sector9.CENTER))
        assertEquals(Key.KeyKA, KanaGrid.keyFor(Sector9.UP_LEFT))
        assertEquals(Key.KeySA, KanaGrid.keyFor(Sector9.UP))
        assertEquals(Key.KeyTA, KanaGrid.keyFor(Sector9.UP_RIGHT))
        assertEquals(Key.KeyNA, KanaGrid.keyFor(Sector9.LEFT))
        assertEquals("は is →", Key.KeyHA, KanaGrid.keyFor(Sector9.RIGHT))
        assertEquals(Key.KeyMA, KanaGrid.keyFor(Sector9.DOWN_LEFT))
        assertEquals("ら is ↓", Key.KeyRA, KanaGrid.keyFor(Sector9.DOWN))
        assertEquals("や/わ is ↘ (amendment 21)", KanaGrid.YA_WA_KEY, KanaGrid.keyFor(Sector9.DOWN_RIGHT))
        assertEquals("や/わ", KanaGrid.label(KanaGrid.YA_WA_KEY))
        assertTrue("the touch や and わ keys are not grid keys of their own", Key.KeyYA !in KanaGrid.MAIN && Key.KeyWA !in KanaGrid.MAIN)
        // The D-pad fallback keeps the v4 grid.
        assertEquals(listOf('あ', 'か', 'さ', 'た', 'な', 'は', 'ま', 'や', 'ら'),
            KanaGrid.keys(PadMode.KANA, fallback = true).map { KanaGrid.charFor(it, GestureType.Tap) })
    }

    @Test
    fun every_main_key_tap_and_four_flicks_match_sumire_tables() {
        var checked = 0
        for (key in KanaGrid.MAIN - KanaGrid.YA_WA_KEY) {
            for (gesture in PadDriver.GESTURES) {
                val d = PadDriver()
                d.holdSector(sectorOf(key))
                d.kana(gesture)
                d.leftRelease()
                val keys = d.kanaKeys()
                assertEquals("$key $gesture should emit exactly one key", 1, keys.size)
                assertEquals(key, keys[0].key)
                assertEquals(gesture, keys[0].gesture)
                assertEquals("$key $gesture", expectedChar(key, gesture), keys[0].char)
                checked++
            }
        }
        assertEquals(40, checked)
        // Amendment 21: the や/わ key's six slots go out as the touch や / わ key that types each one.
        val expected = listOf(Key.KeyYA to GestureType.Tap, Key.KeyYA to GestureType.FlickTop, Key.KeyYA to GestureType.FlickBottom,
            Key.KeyWA to GestureType.Tap, Key.KeyWA to GestureType.FlickLeft, Key.KeyWA to GestureType.FlickTop)
        for (slot in 0 until KanaHexagon.SLOTS) {
            val d = PadDriver()
            d.yaWa(slot)
            val keys = d.kanaKeys()
            assertEquals("slot $slot", 1, keys.size)
            assertEquals(expected[slot], keys[0].key to keys[0].gesture)
            assertEquals(expectedChar(expected[slot].first, expected[slot].second), keys[0].char)
        }
    }

    @Test
    fun the_r2_symbols_layer_matches_sumires_punctuation_and_wa_keys() {
        // Amendment 21: R2 in かな = 記号, eight directions, the centre empty.
        var checked = 0
        for ((sector, c) in KanaGrid.KANA_SYMBOLS) {
            val d = PadDriver()
            d.press(PadButton.R2)
            d.flickLeft8(sector)
            d.release(PadButton.R2)
            assertEquals("layer $sector", c.toString(), d.typed())
            val slot = KanaGrid.touchSlotFor(c)
            if (slot != null) {
                val keys = d.kanaKeys()
                assertEquals("layer $sector comes from a touch key", slot.key, keys.single().key)
                assertEquals(slot.gesture, keys[0].gesture)
                assertEquals(expectedChar(slot.key, slot.gesture), keys[0].char)
            } else {
                assertEquals(listOf<PadAction>(PadAction.InsertText(c.toString())), d.actions)
            }
            checked++
        }
        assertEquals(8, checked)
        assertEquals("〜。…ー、？・！", listOf(Sector9.UP_LEFT, Sector9.UP, Sector9.UP_RIGHT, Sector9.LEFT, Sector9.RIGHT, Sector9.DOWN_LEFT, Sector9.DOWN, Sector9.DOWN_RIGHT).joinToString("") { KanaGrid.KANA_SYMBOLS.getValue(it).toString() })
        assertTrue("nothing in the centre", Sector9.CENTER !in KanaGrid.KANA_SYMBOLS)
        assertEquals("only ・ has no touch key", listOf('・'), KanaGrid.KANA_SYMBOLS.values.filter { KanaGrid.touchSlotFor(it) == null })
        // わ key: わ を ん ー 〜 ; punctuation key: 、 。 ？ ！ …
        assertEquals(listOf('わ', 'を', 'ん', 'ー', '〜'), PadDriver.GESTURES.map { expectedChar(Key.KeyWA, it) })
        assertEquals(listOf('、', '。', '？', '！', '…'), PadDriver.GESTURES.map { expectedChar(Key.KeyKutouten, it) })
        // ゛゜小 has no character of its own; the hold roles (L2 / L1 / R1) cover it on the controller.
        PadDriver.GESTURES.forEach { assertNull(expectedChar(Key.KeyDakutenSmall, it)) }
    }

    @Test
    fun example_shi_is_up_then_flick_left() {
        val d = PadDriver()
        d.left(0f, -1f)          // ↑ さ
        d.flickAt(18.0)          // → い段
        assertEquals("し", d.typed())
    }

    @Test
    fun types_kyou_ha_ii_tenki_as_kana_sequence() {
        val d = PadDriver()
        // き
        d.left(-0.707f, -0.707f); d.kana(GestureType.FlickLeft)
        // ょ = R1 (小文字) + the や/わ key ↘ (amendment 21)
        d.press(PadButton.R1); d.yaWa(2); d.release(PadButton.R1)
        // う (centre あ key)
        d.leftRelease()
        d.kana(GestureType.FlickTop)
        // は
        d.left(1f, 0f); d.kanaTap()
        // い い
        d.leftRelease(); d.kana(GestureType.FlickLeft); d.kana(GestureType.FlickLeft)
        // て
        d.left(0.707f, -0.707f); d.kana(GestureType.FlickRight)
        // ん = the や/わ key ↖
        d.yaWa(5)
        // き
        d.left(-0.707f, -0.707f); d.kana(GestureType.FlickLeft)
        assertEquals("きょうはいいてんき", d.typed())
        assertTrue("ょ is the transformed character", d.actions.contains(PadAction.InsertText("ょ")))
        val n = d.kanaKeys().first { it.char == 'ん' }
        assertEquals("ん is the わ key's flick ↑, as on the touch keyboard", Key.KeyWA, n.key)
        assertEquals(GestureType.FlickTop, n.gesture)
    }

    @Test
    fun neutral_left_stick_types_a_row() {
        val d = PadDriver()
        for (g in PadDriver.GESTURES) d.kana(g)
        assertEquals("あいうえお", d.typed())
    }

    @Test
    fun button_roles_in_kana_mode() {
        val d = PadDriver()
        d.left(0.1f, 0f) // a real session has moved a stick (AUTO would otherwise fall back after 8 blind presses)
        d.tap(PadButton.A); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.X); d.tap(PadButton.R2); d.tap(PadButton.L2); d.tap(PadButton.L1); d.tap(PadButton.R1); d.tap(PadButton.SELECT)
        assertEquals(
            "R2 / L2 / L1 / R1 are holds and type nothing by themselves",
            // Amendment 20: a Select tap is the touch keyboard (no mode switch).
            listOf(PadAction.Confirm, PadAction.ConvertOrNextCandidate, PadAction.Backspace, PadAction.Space, PadAction.TouchKeyboard),
            d.actions,
        )
        d.engine.mode = com.kazumaproject.core.domain.state.InputMode.ModeJapanese
        d.clear()
        // Amendment 18: L3 / R3 type nothing; with the stick at rest they step the mode.
        d.left(0f, 0f)
        d.tap(PadButton.L3)
        assertEquals("an L3 tap types nothing, it goes to 数字", listOf<PadAction>(PadAction.SwitchMode(com.kazumaproject.core.domain.state.InputMode.ModeNumber)), d.actions)
        assertTrue("nothing armed", !d.engine.barActive)
        d.clear()
        d.tap(PadButton.R3)
        assertEquals(listOf<PadAction>(PadAction.SwitchMode(com.kazumaproject.core.domain.state.InputMode.ModeJapanese)), d.actions)
        assertEquals(PadMode.KANA, d.engine.padMode)
        assertEquals("", d.typed())
    }

    @Test
    fun dpad_moves_cursor_or_candidates() {
        val d = PadDriver()
        d.tap(PadButton.DPAD_LEFT); d.tap(PadButton.DPAD_RIGHT); d.tap(PadButton.DPAD_UP); d.tap(PadButton.DPAD_DOWN)
        assertEquals(listOf(Dir4.LEFT, Dir4.RIGHT, Dir4.UP, Dir4.DOWN), d.actions.map { (it as PadAction.Dpad).direction })
        d.clear()
        d.engine.candidatesShown = true
        d.tap(PadButton.DPAD_RIGHT)
        assertEquals(PadAction.Dpad(Dir4.RIGHT), d.actions.single())
        assertTrue(d.notes.last().contains("candidate"))
    }

    @Test
    fun guide_reflects_selection_and_pending_flick() {
        val d = PadDriver()
        d.left(1f, 0f)
        var g = d.engine.guide
        assertEquals(Key.KeyHA, g.selectedKey)
        assertEquals(KanaGrid.FlickChars("は", "ひ", "ふ", "へ", "ほ"), g.flickChars)
        assertNull(g.pendingSlot)
        d.right(0.707f, 0.707f)
        g = d.engine.guide
        assertEquals("↘ = う段", 2, g.pendingSlot)
        d.right(0.3f, 0.3f); d.right(0f, 0f)
        assertNull(d.engine.guide.pendingSlot)
        assertEquals("ふ", d.typed())
        // Amendment 21: the や/わ key, 「や/わ」 on the grid, six slots on the ring.
        d.left(0.707f, 0.707f)
        g = d.engine.guide
        assertEquals(KanaGrid.YA_WA_KEY, g.selectedKey)
        assertEquals("や/わ", g.gridLabels[8])
        assertTrue(g.hexagon)
        assertTrue(!g.pentagon)
        assertEquals(listOf("や", "ゆ", "よ", "わ", "を", "ん"), g.hexagonChars)
        // No bar in kana: L3 lights nothing and dims nothing (its pill is lit while held).
        d.leftRelease()
        d.press(PadButton.L3)
        g = d.engine.guide
        assertTrue(!g.barActive && !g.layer)
        assertEquals("", g.barButtons)
        assertEquals(Key.KeyA, g.selectedKey)
        assertTrue(PadButton.L3 in g.heldButtons)
        d.release(PadButton.L3)
    }
}
