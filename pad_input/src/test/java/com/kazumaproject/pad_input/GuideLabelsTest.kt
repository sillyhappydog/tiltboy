package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amendments 5, 9, 10, 11 and 18: the role → label table behind the controller map. */
class GuideLabelsTest {

    private val ja = TestWords.ja
    private fun L(role: PadRole, mode: PadMode) = GuideLabels.roleLabel(role, mode, ja)
    private fun cfg(roles: Map<PadButton, PadRole> = PadConfig.DEFAULT_ROLES) = PadConfig(roles = roles, words = ja)

    @Test
    fun every_role_has_a_label_in_every_mode() {
        // A role that does something in a mode has a label; one that does nothing has none.
        for (role in PadRole.entries) for (mode in PadMode.entries) {
            val l = L(role, mode)
            val does = when (role) {
                PadRole.NONE -> false
                // Amendments 18 and 20: removed roles read as nothing.
                PadRole.TAP_CENTER, PadRole.BAR, PadRole.MODE_SWITCH -> false
                PadRole.MODE_PREVIOUS, PadRole.MODE_NEXT -> mode != PadMode.EMOJI_LIST
                // Amendment 18: the layer in every text mode (amendment 21: 記号 everywhere).
                PadRole.YA_WA_LAYER -> !mode.isEmoji
                else -> !role.isTransform || HoldTransforms.applies(role, mode)
            }
            assertEquals("$role in $mode: '$l'", does, l.isNotBlank())
        }
        assertEquals(GuideLabels.NONE, L(PadRole.NONE, PadMode.KANA))
        assertEquals(GuideLabels.NONE, L(PadRole.TAP_CENTER, PadMode.KANA))
        assertEquals("英字へ", L(PadRole.MODE_NEXT, PadMode.KANA))
        assertEquals("数字へ", L(PadRole.MODE_NEXT, PadMode.ENGLISH))
        assertEquals("かなへ", L(PadRole.MODE_NEXT, PadMode.NUMBER))
        assertEquals("数字へ", L(PadRole.MODE_PREVIOUS, PadMode.KANA))
        assertEquals("かなへ", L(PadRole.MODE_PREVIOUS, PadMode.ENGLISH))
        assertEquals("英字へ", L(PadRole.MODE_PREVIOUS, PadMode.NUMBER))
        assertEquals("R3 in the emoji wheel keeps its v6 job", "左の絵文字", L(PadRole.MODE_NEXT, PadMode.EMOJI))
        assertEquals("L3 in the emoji wheel keeps its v6 job", "次の 32 個", L(PadRole.MODE_PREVIOUS, PadMode.EMOJI))
        assertEquals("one text mode: nothing to step to", GuideLabels.NONE, GuideLabels.roleLabel(PadRole.MODE_NEXT, PadMode.KANA, ja, listOf(PadMode.KANA)))
        assertEquals("To ABC", GuideLabels.roleLabel(PadRole.MODE_NEXT, PadMode.KANA, TestWords.en))
        assertEquals("To 123", GuideLabels.roleLabel(PadRole.MODE_NEXT, PadMode.ENGLISH, TestWords.en))
        assertEquals("To kana", GuideLabels.roleLabel(PadRole.MODE_NEXT, PadMode.NUMBER, TestWords.en))
        assertEquals("決定", L(PadRole.CONFIRM, PadMode.KANA))
        assertEquals("入れる", L(PadRole.CONFIRM, PadMode.EMOJI_LIST))
        assertEquals("変換", L(PadRole.CONVERT_NEXT, PadMode.KANA))
        assertEquals(GuideLabels.NONE, L(PadRole.BAR, PadMode.KANA))
        assertEquals("amendment 21", "記号", L(PadRole.YA_WA_LAYER, PadMode.KANA))
        assertEquals("記号", L(PadRole.YA_WA_LAYER, PadMode.ENGLISH))
        assertEquals("記号", L(PadRole.YA_WA_LAYER, PadMode.NUMBER))
        assertEquals("Symbols", GuideLabels.roleLabel(PadRole.YA_WA_LAYER, PadMode.ENGLISH, TestWords.en))
        assertEquals(GuideLabels.NONE, L(PadRole.YA_WA_LAYER, PadMode.EMOJI))
        assertEquals(GuideLabels.NONE, L(PadRole.BAR, PadMode.ENGLISH))
        assertEquals("濁点", L(PadRole.HOLD_DAKUTEN, PadMode.KANA))
        assertEquals("大文字", L(PadRole.HOLD_DAKUTEN, PadMode.ENGLISH))
        assertEquals(GuideLabels.NONE, L(PadRole.HOLD_DAKUTEN, PadMode.NUMBER))
        assertEquals("半濁点", L(PadRole.HOLD_HANDAKUTEN, PadMode.KANA))
        // Amendment 22: 「123」 in 英字 (数字's layout while held), nothing in 数字.
        assertEquals("123", L(PadRole.HOLD_HANDAKUTEN, PadMode.ENGLISH))
        assertEquals("123", L(PadRole.HOLD_SMALL, PadMode.ENGLISH))
        assertEquals(GuideLabels.NONE, L(PadRole.HOLD_HANDAKUTEN, PadMode.NUMBER))
        assertEquals(GuideLabels.NONE, L(PadRole.HOLD_SMALL, PadMode.NUMBER))
        assertEquals("小文字", L(PadRole.HOLD_SMALL, PadMode.KANA))
        assertEquals("小文字・゜", L(PadRole.HOLD_SMALL_OR_HANDAKUTEN, PadMode.KANA))
        assertEquals("123", L(PadRole.HOLD_SMALL_OR_HANDAKUTEN, PadMode.ENGLISH))
        assertEquals("123", GuideLabels.roleLabel(PadRole.HOLD_HANDAKUTEN, PadMode.ENGLISH, TestWords.en))
        // Amendment 20: Select's モード is gone; Select is 「タッチ」 (the touch keyboard) everywhere.
        assertEquals(GuideLabels.NONE, L(PadRole.MODE_SWITCH, PadMode.KANA))
        assertEquals("タッチ", L(PadRole.TOUCH_KEYBOARD, PadMode.KANA))
        assertEquals("タッチ", L(PadRole.TOUCH_KEYBOARD, PadMode.NUMBER))
        assertEquals("Touch", GuideLabels.roleLabel(PadRole.TOUCH_KEYBOARD, PadMode.ENGLISH, TestWords.en))
        assertEquals("絵文字", L(PadRole.EMOJI_JUMP, PadMode.KANA))
        assertEquals("一覧", L(PadRole.EMOJI_JUMP, PadMode.EMOJI))
        assertEquals("戻る", L(PadRole.EMOJI_JUMP, PadMode.EMOJI_LIST))
    }

    @Test
    fun default_config_labels() {
        val l = GuideLabels.build(cfg(), PadMode.KANA, fallback = false, barActive = false)
        assertEquals(GuideLabels.MAPPED.toSet(), l.buttons.keys)
        assertEquals("決定", l.buttons[PadButton.A])
        assertEquals("英字へ", l.buttons[PadButton.R3])
        assertEquals("変換", l.buttons[PadButton.Y])
        assertEquals("消す", l.buttons[PadButton.B])
        assertEquals("空白", l.buttons[PadButton.X])
        // Amendment 16: L2 濁点, L1 半濁点, R2 the layer (amendment 21: 記号), R1 小文字; amendment 18: L3 the previous mode.
        assertEquals("記号", l.buttons[PadButton.R2])
        assertEquals("濁点", l.buttons[PadButton.L2])
        assertEquals("小文字", l.buttons[PadButton.R1])
        assertEquals("半濁点", l.buttons[PadButton.L1])
        assertEquals("絵文字", l.buttons[PadButton.START])
        assertEquals("タッチ", l.buttons[PadButton.SELECT])
        assertEquals("数字へ", l.buttons[PadButton.L3])
        val en = GuideLabels.build(cfg(), PadMode.ENGLISH, fallback = false, barActive = false)
        assertEquals("大文字", en.buttons[PadButton.L2])
        assertEquals("amendment 22", "123", en.buttons[PadButton.L1])
        assertEquals("amendment 22", "123", en.buttons[PadButton.R1])
        // While 数字's layout is held in 英字, L2 (大文字) has nothing to do.
        val held = GuideLabels.build(cfg(), PadMode.ENGLISH, fallback = false, barActive = false, numberLayout = true)
        assertEquals(GuideLabels.NONE, held.buttons[PadButton.L2])
        assertEquals("123", held.buttons[PadButton.L1])
        assertEquals("記号", held.buttons[PadButton.R2])
        assertEquals("候補", held.buttons[PadButton.Y])
        assertEquals("かなへ", held.buttons[PadButton.L3])
        assertEquals("記号", en.buttons[PadButton.R2])
        assertEquals("候補", en.buttons[PadButton.Y])
        assertEquals("かなへ", en.buttons[PadButton.L3])
        assertEquals("数字へ", en.buttons[PadButton.R3])
        assertEquals("カーソル", l.dpad)
    }

    @Test
    fun remapped_config_changes_the_labels() {
        val l = GuideLabels.build(cfg(PadConfig.DEFAULT_ROLES + mapOf(PadButton.Y to PadRole.BACKSPACE, PadButton.B to PadRole.CONFIRM, PadButton.R3 to PadRole.NONE)), PadMode.ENGLISH, fallback = false, barActive = false)
        assertEquals("消す", l.buttons[PadButton.Y])
        assertEquals("決定", l.buttons[PadButton.B])
        assertEquals(GuideLabels.NONE, l.buttons[PadButton.R3])
        assertEquals("決定", l.buttons[PadButton.A])
        assertEquals("かなへ", l.buttons[PadButton.L3])
    }

    @Test
    fun emoji_list_and_fallback_tables() {
        val list = GuideLabels.build(cfg(), PadMode.EMOJI_LIST, fallback = false, barActive = false)
        assertEquals("移動", list.dpad)
        assertEquals("入れる", list.buttons[PadButton.A])
        assertEquals("戻る", list.buttons[PadButton.B])
        assertEquals("戻る", list.buttons[PadButton.START])
        assertEquals("前のカテゴリー", list.buttons[PadButton.L2])
        assertEquals("次のカテゴリー", list.buttons[PadButton.R2])
        // Amendment 20: Select closes the list and shows the touch keys; X, Y, L3, R3 do nothing.
        assertEquals("タッチ", list.buttons[PadButton.SELECT])
        assertEquals(GuideLabels.NONE, list.buttons[PadButton.X])
        assertEquals(GuideLabels.NONE, list.buttons[PadButton.Y])
        assertEquals(GuideLabels.NONE, list.buttons[PadButton.L3])
        assertEquals(GuideLabels.NONE, list.buttons[PadButton.R3])
        // CONFIRM moved to another button: 入れる follows it (A keeps inserting by button too), but not onto X / Y.
        val moved = GuideLabels.build(cfg(PadConfig.DEFAULT_ROLES + mapOf(PadButton.L1 to PadRole.CONFIRM, PadButton.Y to PadRole.CONFIRM, PadButton.A to PadRole.NONE)), PadMode.EMOJI_LIST, false, false)
        assertEquals("入れる", moved.buttons[PadButton.L1])
        assertEquals(GuideLabels.NONE, moved.buttons[PadButton.Y])
        val fb = GuideLabels.build(cfg(), PadMode.KANA, fallback = true, barActive = false)
        assertEquals("い段", fb.buttons[PadButton.X])
        assertEquals("お段", fb.buttons[PadButton.L2])
        assertEquals("決定", fb.buttons[PadButton.START])
        assertEquals("絵文字", fb.buttons[PadButton.R3])
        assertEquals("わをん 記号", fb.buttons[PadButton.L1])
        val fbEnglish = GuideLabels.build(cfg(), PadMode.ENGLISH, fallback = true, barActive = false)
        assertEquals("左の文字", fbEnglish.buttons[PadButton.X])
        assertEquals("下の文字", fbEnglish.buttons[PadButton.L2])
        assertTrue(GuideLabels.legend(GuideLabels.build(cfg(), PadMode.KANA, false, false)).first() == "A 決定")
    }
}
