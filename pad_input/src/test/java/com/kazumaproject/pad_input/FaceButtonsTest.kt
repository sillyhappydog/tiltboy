package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amendment 11: A = 決定, Y = 変換, B = 消す, X = 空白. Amendment 16: か is ↖, R2 is や・わ. Amendment 18: no button types a centre character (R3 steps the mode). */
class FaceButtonsTest {

    private fun kana(): PadDriver = PadDriver().also { it.left(0.1f, 0f) }

    @Test
    fun kikaki_with_Y_to_convert_and_A_to_confirm() {
        val d = kana()
        d.left(-0.707f, -0.707f); d.kana(GestureType.FlickLeft)     // き
        d.kanaTap()                              // か (↑)
        d.kana(GestureType.FlickLeft)            // き
        assertEquals("きかき", d.typed())
        d.tap(PadButton.Y); d.tap(PadButton.Y)   // 変換, 次の候補
        d.tap(PadButton.A)                       // 決定
        assertEquals(
            listOf(
                PadAction.KanaKey(Key.KeyKA, GestureType.FlickLeft, 'き'),
                PadAction.KanaKey(Key.KeyKA, GestureType.Tap, 'か'),
                PadAction.KanaKey(Key.KeyKA, GestureType.FlickLeft, 'き'),
                PadAction.ConvertOrNextCandidate, PadAction.ConvertOrNextCandidate, PadAction.Confirm,
            ),
            d.actions,
        )
    }

    @Test
    fun a_sends_confirm_even_with_nothing_composing() {
        // The engine always reports 決定; the IME turns it into Enter when nothing is composing.
        val d = kana()
        d.tap(PadButton.A)
        assertEquals(listOf<PadAction>(PadAction.Confirm), d.actions)
        assertTrue(d.notes.last().contains("confirm"))
    }

    @Test
    fun no_button_types_a_centre_character_any_more() {
        // Amendment 18: TAP_CENTER is a removed role; a stored one reads as nothing.
        val d = kana()
        d.left(-0.707f, -0.707f)
        d.tap(PadButton.A); d.tap(PadButton.R3)
        assertEquals("", d.typed())
        assertEquals(0, PadConfig.DEFAULT_ROLES.values.count { it == PadRole.TAP_CENTER })
        val back = PadDriver(PadInputEngine(PadConfig(roles = PadConfig.DEFAULT_ROLES + mapOf(PadButton.A to PadRole.TAP_CENTER))))
        back.left(-0.707f, -0.707f); back.tap(PadButton.A)
        assertEquals("", back.typed())
        assertEquals(PadRole.NONE, back.engine.config.roleOf(PadButton.A))
    }

    @Test
    fun labels_are_kettei_and_henkan() {
        val g = kana().engine.guide
        assertEquals("決定", g.labels.buttons[PadButton.A])
        assertEquals("変換", g.labels.buttons[PadButton.Y])
        assertEquals("amendment 21: R2 is 記号", "記号", g.labels.buttons[PadButton.R2])
        assertEquals("消す", g.labels.buttons[PadButton.B])
        assertEquals("空白", g.labels.buttons[PadButton.X])
    }

    @Test
    fun a_inserts_in_the_emoji_list_as_confirm_and_follows_a_remap() {
        val d = kana()
        d.tap(PadButton.START)
        d.clear()
        d.tap(PadButton.A)
        assertEquals(listOf<PadAction>(PadAction.EmojiKeyboard(EmojiKeyboardCommand.SELECT)), d.actions)
        assertEquals("入れる", d.engine.guide.labels.buttons[PadButton.A])
        // A CONFIRM moved to L1 inserts too; amendment 20: X and Y do nothing in the list, whatever their role.
        val moved = PadDriver(PadInputEngine(PadConfig(roles = PadConfig.DEFAULT_ROLES + mapOf(PadButton.L1 to PadRole.CONFIRM, PadButton.Y to PadRole.CONFIRM, PadButton.A to PadRole.NONE))))
        moved.left(0.1f, 0f)
        moved.tap(PadButton.START); moved.clear()
        moved.tap(PadButton.L1)
        assertEquals(listOf<PadAction>(PadAction.EmojiKeyboard(EmojiKeyboardCommand.SELECT)), moved.actions)
        assertEquals("入れる", moved.engine.guide.labels.buttons[PadButton.L1])
        moved.clear()
        moved.tap(PadButton.Y)
        assertTrue(moved.actions.isEmpty())
        assertEquals(GuideLabels.NONE, moved.engine.guide.labels.buttons[PadButton.Y])
    }

    @Test
    fun untouched_face_buttons_migrate_and_a_changed_one_survives() {
        val v1 = PadConfig.migrateRoles(mapOf(PadButton.A to PadRole.TAP_CENTER, PadButton.Y to PadRole.CONFIRM), fromVersion = 1)
        assertEquals(PadRole.CONFIRM, v1[PadButton.A])
        assertEquals(PadRole.CONVERT_NEXT, v1[PadButton.Y])
        val changed = PadConfig.migrateRoles(mapOf(PadButton.A to PadRole.SPACE, PadButton.Y to PadRole.CONFIRM), fromVersion = 3)
        assertTrue("the user's A stays", PadButton.A !in changed)
        assertEquals(PadRole.CONVERT_NEXT, changed[PadButton.Y])
    }
}
