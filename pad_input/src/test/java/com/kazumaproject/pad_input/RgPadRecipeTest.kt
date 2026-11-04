package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device recipes of the v7 report, run through the engine token by token as Hermes's
 * `rg_pad.py` sends them (one axis per token: `LX-` / `LX+` / `LX0` / `LX=0.4`; `+R2` / `-R2`
 * hold and release; a bare `R3` taps; `sleep=ms`). Amendment 18: L3 / R3 mode steps and the
 * 英字 / 数字 R2 layer.
 */
class RgPadRecipeTest {

    private class Run {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.STICKS)))
        private var lx = 0f; private var ly = 0f; private var rx = 0f; private var ry = 0f

        fun run(recipe: String): Run {
            for (token in recipe.trim().split(Regex("\\s+"))) step(token)
            return this
        }

        private var hx = 0; private var hy = 0

        private fun sleep(ms: Long) {
            var left = ms
            while (left > 0) {
                val step = minOf(left, d.engine.config.repeatIntervalMs)
                d.time += step; left -= step
                if (d.engine.isRepeating) d.poll()
            }
        }

        private fun step(t: String) {
            // Amendment 24: a sleep polls the engine's repeat as PadController does (every
            // repeatIntervalMs while it repeats), so `+B sleep=1000 -B` repeats the backspace.
            if (t.startsWith("sleep=")) { sleep(t.removePrefix("sleep=").toLong()); return }
            // Amendment 23: the D-pad hat (HX- / HX+ / HX0, HY- = up / HY+ = down / HY0), turned into
            // D-pad presses and releases the way PadController.hatButton does.
            val hat = Regex("^(HX|HY)(-|\\+|0)$").find(t)
            if (hat != null) {
                val v = when (hat.groupValues[2]) { "-" -> -1; "+" -> 1; else -> 0 }
                val x = hat.groupValues[1] == "HX"
                val old = if (x) hx else hy
                fun button(value: Int) = when {
                    x && value < 0 -> PadButton.DPAD_LEFT; x && value > 0 -> PadButton.DPAD_RIGHT
                    value < 0 -> PadButton.DPAD_UP; value > 0 -> PadButton.DPAD_DOWN; else -> null
                }
                if (v != old) {
                    button(old)?.let { d.release(it) }
                    button(v)?.let { d.press(it) }
                    if (x) hx = v else hy = v
                }
                return
            }
            val axis = Regex("^(LX|LY|RX|RY)(-|\\+|0|=(-?[0-9.]+))$").find(t)
            if (axis != null) {
                val v = when (axis.groupValues[2]) { "-" -> -1f; "+" -> 1f; "0" -> 0f; else -> axis.groupValues[3].toFloat() }
                when (axis.groupValues[1]) { "LX" -> lx = v; "LY" -> ly = v; "RX" -> rx = v; else -> ry = v }
                d.sticks(lx, ly, rx, ry)
                return
            }
            when (t[0]) {
                '+' -> d.press(PadButton.valueOf(t.substring(1)))
                '-' -> d.release(PadButton.valueOf(t.substring(1)))
                else -> d.tap(PadButton.valueOf(t))
            }
        }

        val text: String get() = d.actions.joinToString("") {
            when (it) {
                is PadAction.KanaKey -> it.char?.toString().orEmpty()
                is PadAction.InsertText -> it.text
                PadAction.Space -> " "
                else -> ""
            }
        }
        val mode: PadMode get() = d.engine.padMode
    }

    @Test
    fun r3_l3_mode_steps() {
        // かな → R3 → 英字 (centre cell qrst, right ↑ = r) → R3 → 数字 (centre (=)%, ↑ = =) → R3 → かな (↑ = あ).
        val r = Run().run("R3 RY- RY0 R3 RY- RY0 R3 RY- RY0")
        assertEquals("r=あ", r.text)
        assertEquals(PadMode.KANA, r.mode)
        // L3: かな → 数字 → 英字.
        val l = Run().run("L3 RY- RY0 L3 RY- RY0")
        assertEquals("=r", l.text)
        assertEquals(PadMode.ENGLISH, l.mode)
        // Guard: the right stick at 0.4 when R3 is clicked → ignored (no mode change, nothing typed).
        val g = Run().run("RX=0.4 R3 RX0")
        assertEquals("", g.text); assertEquals(PadMode.KANA, g.mode)
        // A hard flick that pushes R3 in: the flick types (→ い段 on the あ key), no mode change.
        val f = Run().run("RX+ R3 RX0")
        assertEquals("い", f.text); assertEquals(PadMode.KANA, f.mode)
        // The left stick out (さ key) when L3 is clicked → ignored.
        val gl = Run().run("LY- L3 LY0")
        assertEquals(PadMode.KANA, gl.mode)
    }

    @Test
    fun english_layer_recipes() {
        val r = Run().run(
            "R3 " +
                "+R2 LX=-0.7 LY=-0.7 LX=-0.4 LY=-0.4 LX0 LY0 -R2 " +   // ↖ &
                "+R2 LY- LY0 -R2 " +                                   // ↑ ;
                "+R2 LX=0.7 LY=-0.7 LX=0.4 LY=-0.4 LX0 LY0 -R2 " +     // ↗ *
                "+R2 LX- LX0 -R2 " +                                   // ← (
                "+R2 LX+ LX0 -R2 " +                                   // → )
                "+R2 LX=-0.7 LY=0.7 LX=-0.4 LY=0.4 LX0 LY0 -R2 " +     // ↙ =
                "+R2 LY+ LY0 -R2 " +                                   // ↓ _
                "+R2 LX=0.7 LY=0.7 LX=0.4 LY=0.4 LX0 LY0 -R2",         // ↘ +
        )
        assertEquals("&;*()=_+", r.text)
        assertEquals(PadMode.ENGLISH, r.mode)
        // ← then rotated to ↙ (re-aim): =.
        assertEquals("=", Run().run("R3 +R2 LX- LY=0.7 LX=-0.7 LX=-0.4 LY=0.4 LX0 LY0 -R2").text)
    }

    @Test
    fun number_layer_recipes() {
        val r = Run().run(
            "L3 " +
                "+R2 LX=-0.7 LY=-0.7 LX=-0.4 LY=-0.4 LX0 LY0 -R2 " +   // ↖ {
                "+R2 LY- LY0 -R2 " +                                   // ↑ ^
                "+R2 LX=0.7 LY=-0.7 LX=0.4 LY=-0.4 LX0 LY0 -R2 " +     // ↗ }
                "+R2 LX- LX0 -R2 " +                                   // ← [
                "+R2 LX+ LX0 -R2 " +                                   // → ]
                "+R2 LX=-0.7 LY=0.7 LX=-0.4 LY=0.4 LX0 LY0 -R2 " +     // ↙ \
                "+R2 LY+ LY0 -R2 " +                                   // ↓ |
                "+R2 LX=0.7 LY=0.7 LX=0.4 LY=0.4 LX0 LY0 -R2",         // ↘ `
        )
        assertEquals("{^}[]\\|`", r.text)
        assertEquals(PadMode.NUMBER, r.mode)
    }

    // ---- v9 (amendments 21 and 22): the recipes of the v9 report, token for token.

    /** Left stick on ↘ (the や/わ key), one right-stick token run, left stick home. */
    private fun yaWa(right: String) = "LX=0.7 LY=0.7 $right LX0 LY0"

    @Test
    fun v9_ya_wa_key_six_slots() {
        val slots = listOf(
            "RY- RY0" to "や",
            "RX=0.87 RY=-0.5 RX=0.3 RY=-0.2 RX0 RY0" to "ゆ",
            "RX=0.87 RY=0.5 RX=0.3 RY=0.2 RX0 RY0" to "よ",
            "RY+ RY0" to "わ",
            "RX=-0.87 RY=0.5 RX=-0.3 RY=0.2 RX0 RY0" to "を",
            "RX=-0.87 RY=-0.5 RX=-0.3 RY=-0.2 RX0 RY0" to "ん",
        )
        for ((recipe, kana) in slots) {
            val r = Run().run(yaWa(recipe))
            assertEquals(recipe, kana, r.text)
            assertEquals(PadMode.KANA, r.mode)
        }
        // R1 held: ゃ (and ゅ ょ ゎ the same way).
        assertEquals("ゃ", Run().run("+R1 " + yaWa("RY- RY0") + " -R1").text)
        assertEquals("ょ", Run().run("+R1 " + yaWa("RX=0.87 RY=0.5 RX=0.3 RY=0.2 RX0 RY0") + " -R1").text)
        // Re-aim on the ring: ↑ out, rolled over ↖ to ↙ → を.
        assertEquals("を", Run().run(yaWa("RY- RX=-0.87 RY=-0.5 RY=0 RY=0.5 RX=-0.3 RY=0.2 RX0 RY0")).text)
        // all six in one go
        assertEquals("やゆよわをん", Run().run(slots.joinToString(" ") { yaWa(it.first) }).text)
    }

    @Test
    fun v9_kana_r2_symbols() {
        val recipes = listOf(
            "+R2 LX=-0.7 LY=-0.7 LX=-0.4 LY=-0.4 LX0 LY0 -R2" to "〜",
            "+R2 LY- LY0 -R2" to "。",
            "+R2 LX=0.7 LY=-0.7 LX=0.4 LY=-0.4 LX0 LY0 -R2" to "…",
            "+R2 LX- LX0 -R2" to "ー",
            "+R2 LX+ LX0 -R2" to "、",
            "+R2 LX=-0.7 LY=0.7 LX=-0.4 LY=0.4 LX0 LY0 -R2" to "？",
            "+R2 LY+ LY0 -R2" to "・",
            "+R2 LX=0.7 LY=0.7 LX=0.4 LY=0.4 LX0 LY0 -R2" to "！",
        )
        for ((recipe, c) in recipes) assertEquals(recipe, c, Run().run(recipe).text)
        // ← out, rotated to ↙ (re-aim) → ？.
        assertEquals("？", Run().run("+R2 LX- LY=0.7 LX=-0.7 LX=-0.4 LY=0.4 LX0 LY0 -R2").text)
    }

    @Test
    fun v9_gakkou() {
        // が = L2 + か (↖) + ↑, っ = R1 + た (↗) + ↘ (う段), こ = か + ←, う = あ + ↘; then Y (変換) and A (決定).
        val r = Run().run(
            "+L2 LX=-0.7 LY=-0.7 RY- RY0 -L2 " +
                "+R1 LX=0.7 RX=0.59 RY=0.81 RX=0.3 RY=0.2 RX0 RY0 -R1 " +
                "LX=-0.7 RX- RX0 " +
                "LX0 LY0 RX=0.59 RY=0.81 RX=0.3 RY=0.2 RX0 RY0 " +
                "Y A",
        )
        assertEquals("がっこう", r.text)
        assertEquals(listOf(PadAction.ConvertOrNextCandidate, PadAction.Confirm), r.d.actions.takeLast(2))
    }

    @Test
    fun v9_english_abc123_with_l1() {
        // R3 → 英字; left ↖ (abcd); ← a, ↑ b, → c; L1 held: ← 1, ↑ 2, → 3.
        val r = Run().run(
            "R3 LX=-0.7 LY=-0.7 RX- RX0 RY- RY0 RX+ RX0 " +
                "+L1 RX- RX0 RY- RY0 RX+ RX0 -L1 LX0 LY0",
        )
        assertEquals("abc123", r.text)
        assertEquals(PadMode.ENGLISH, r.mode)
    }

    @Test
    fun v9_english_l1_r2_brace_and_l1_released_early() {
        // L1 + R2 + left ↖ → { (数字's 記号 layer) in 英字.
        assertEquals("{", Run().run("R3 +L1 +R2 LX=-0.7 LY=-0.7 LX=-0.4 LY=-0.4 LX0 LY0 -R2 -L1").text)
        // L1 let go while the right stick is still out: the digit still goes in (↖ cell, ↑ = 2).
        val early = Run().run("R3 LX=-0.7 LY=-0.7 +L1 RY- -L1 RY0 LX0 LY0")
        assertEquals("2", early.text)
        assertEquals(PadMode.ENGLISH, early.mode)
        // And with a pause before the release (sleep in ms, nothing changes).
        assertEquals("2", Run().run("R3 LX=-0.7 LY=-0.7 +L1 RY- sleep=300 -L1 sleep=300 RY0 LX0 LY0").text)
        // L1 pressed after the flick armed: the letter (b).
        assertEquals("b", Run().run("R3 LX=-0.7 LY=-0.7 RY- +L1 RY0 -L1 LX0 LY0").text)
    }

    // ---- v10 (amendment 23): the recipe of the v10 report, token for token.

    /** はしをわたる in かな: は (→ key, ↑), し (↑ key, →), を わ (↘ key, ↙ and ↓), た (↗ key, ↑), る (↓ key, ↘). */
    private val hashiwowataru =
        "LX+ RY- RY0 LX0 " +                                                  // は
            "LY- RX+ RX0 LY0 " +                                              // し
            "LX=0.7 LY=0.7 RX=-0.87 RY=0.5 RX=-0.3 RY=0.2 RX0 RY0 " +         // を (や/わ key, ↙)
            "RY+ RY0 LX0 LY0 " +                                              // わ (や/わ key, ↓)
            "LX=0.7 LY=-0.7 RY- RY0 LX0 LY0 " +                               // た
            "LY+ RX=0.59 RY=0.81 RX=0.3 RY=0.2 RX0 RY0 LY0"                  // る (ら key, ↘ う段)

    @Test
    fun v10_conversion_range_with_the_dpad() {
        val r = Run().run(
            hashiwowataru + " Y " +
                "HY+ HY0 HY+ HY0 HY+ HY0 HY+ HY0 " +                          // ↓ ×4: はし | をわたる
                "HY- HY0 " +                                                  // ↑: はしを | わたる
                "HX+ HX0 HX- HX0 " +                                          // → next candidate, ← back
                "A Y A",                                                      // commit はし…; convert the rest; commit
        )
        assertEquals("はしをわたる", r.text)
        assertEquals(PadMode.KANA, r.mode)
        val down = PadAction.Dpad(Dir4.DOWN); val up = PadAction.Dpad(Dir4.UP)
        assertEquals(
            listOf(
                PadAction.ConvertOrNextCandidate, down, down, down, down, up,
                PadAction.Dpad(Dir4.RIGHT), PadAction.Dpad(Dir4.LEFT),
                PadAction.Confirm, PadAction.ConvertOrNextCandidate, PadAction.Confirm,
            ),
            r.d.actions.filter { it !is PadAction.KanaKey && it !is PadAction.InsertText },
        )
        // The hat with candidates shown (the IME's state while converting): the same D-pad actions.
        val shown = Run().also { it.d.engine.candidatesShown = true }.run("HY+ HY0 HY- HY0")
        assertEquals(listOf(down, up), shown.d.actions)
        // Held for a while: one action on the press; the release does nothing.
        assertEquals(listOf(down), Run().run("HY+ sleep=900 HY0").d.actions)
    }

    // ---- v11 (amendment 24): the recipes of the v11 report, token for token.

    /** がっこ: が = L2 + か (↖) + ↑, っ = R1 + た (↗) + ↘ (う段), こ = か + ←; the left stick ends at rest. */
    private val gakko =
        "+L2 LX=-0.7 LY=-0.7 RY- RY0 -L2 " +
            "+R1 LX=0.7 RX=0.59 RY=0.81 RX=0.3 RY=0.2 RX0 RY0 -R1 " +
            "LX=-0.7 RX- RX0 LX0 LY0"

    /** う on the あ key at rest (↘ う段, back through the 0.3 band). */
    private val u = "RX=0.59 RY=0.81 RX=0.3 RY=0.2 RX0 RY0"

    private val dpadAndButtons: (PadAction) -> Boolean = { it !is PadAction.KanaKey && it !is PadAction.InsertText }

    @Test
    fun v11_gakkoo_y_b_then_u_y_a() {
        // がっこお (お = あ key + ←), Y, B → がっこ; う, Y → 学校, A.
        val r = Run().run("$gakko RX- RX0 sleep=1000 Y sleep=800 B sleep=800 $u sleep=1000 Y sleep=800 A")
        assertEquals("がっこおう", r.text)
        assertEquals(
            listOf(PadAction.ConvertOrNextCandidate, PadAction.Backspace, PadAction.ConvertOrNextCandidate, PadAction.Confirm),
            r.d.actions.filter(dpadAndButtons),
        )
        assertEquals(PadMode.KANA, r.mode)
    }

    @Test
    fun v11_hashiwowataru_y_down4_b_y_a() {
        // はしをわたる, Y, ↓ ×4 (はし | をわたる), B → は | をわたる; Y converts は; A.
        val r = Run().run(
            hashiwowataru + " sleep=1000 Y sleep=800 " +
                "HY+ HY0 sleep=600 HY+ HY0 sleep=600 HY+ HY0 sleep=600 HY+ HY0 sleep=800 " +
                "B sleep=800 Y sleep=800 A",
        )
        assertEquals("はしをわたる", r.text)
        val down = PadAction.Dpad(Dir4.DOWN)
        assertEquals(
            listOf(PadAction.ConvertOrNextCandidate, down, down, down, down, PadAction.Backspace, PadAction.ConvertOrNextCandidate, PadAction.Confirm),
            r.d.actions.filter(dpadAndButtons),
        )
        // B right after the 4th ↓ (no wait): the same actions in the same order.
        val quick = Run().run(hashiwowataru + " sleep=1000 Y sleep=800 HY+ HY0 sleep=600 HY+ HY0 sleep=600 HY+ HY0 sleep=600 HY+ HY0 B")
        assertEquals(listOf(PadAction.ConvertOrNextCandidate, down, down, down, down, PadAction.Backspace), quick.d.actions.filter(dpadAndButtons))
    }

    @Test
    fun v11_gakkou_y_dpad_left_then_b_held() {
        // がっこう, Y, → ← (next, previous candidate), B held 1 s: the press, then repeats every 60 ms after 400 ms.
        val r = Run().run("$gakko $u sleep=1000 Y sleep=800 HX+ HX0 sleep=600 HX- HX0 sleep=600 +B sleep=1000 -B")
        assertEquals("がっこう", r.text)
        val buttons = r.d.actions.filter(dpadAndButtons)
        assertEquals(listOf(PadAction.ConvertOrNextCandidate, PadAction.Dpad(Dir4.RIGHT), PadAction.Dpad(Dir4.LEFT)), buttons.take(3))
        val deletes = buttons.drop(3)
        assertTrue("only backspaces after ←: $deletes", deletes.all { it == PadAction.Backspace })
        assertTrue("the press and several repeats: ${deletes.size}", deletes.size >= 8)
        // Let go: no more.
        val count = r.d.actions.size
        r.run("sleep=1000")
        assertEquals(count, r.d.actions.size)
        // A short tap: one backspace only.
        assertEquals(listOf(PadAction.Backspace), Run().run("B sleep=1000").d.actions)
    }

    @Test
    fun v9_r2_alone_types_nothing() {
        val r = Run().run("R2 +R2 LX=0.2 LX0 -R2")
        assertEquals("", r.text)
        assertEquals(PadMode.KANA, r.mode)
    }
}
