package com.kazumaproject.pad_input

import kotlin.math.cos
import kotlin.math.sin

/**
 * Amendment 21 (v9): the や/わ grid key's right stick, a ring of six 60° sectors. Every other kana
 * key keeps the pentagon ([KanaPentagon]); arming, re-aim, latching, the return band and the
 * pending highlight are the same, only the sector count and the centres differ.
 *
 * | slot | sector centre (0° = right, counter-clockwise) | sector        | kana | with R1 |
 * |------|-----------------------------------------------|---------------|------|---------|
 * | 0    | 90°   ↑                                       | 60° … 120°    | や   | ゃ      |
 * | 1    | 30°   ↗ (more right than up)                  | 0° … 60°      | ゆ   | ゅ      |
 * | 2    | −30°  ↘ (more right than down)                | −60° … 0°     | よ   | ょ      |
 * | 3    | −90°  ↓                                       | −120° … −60°  | わ   | ゎ      |
 * | 4    | −150° ↙                                       | 180° … −120°  | を   | を      |
 * | 5    | 150°  ↖                                       | 120° … 180°   | ん   | ん      |
 *
 * The nearest sector centre wins; a sample exactly on a boundary goes to the next slot clockwise
 * (60° → ゆ, 0° → よ, −60° → わ, −120° → を, 180° → ん, 120° → や; [slotAt]). The characters are the
 * touch や / わ keys' own ([KanaGrid.YA_WA_SLOTS]).
 */
object KanaHexagon {
    const val SLOTS = 6
    const val SECTOR_DEG = 60.0

    /** Sector centres in slot order, degrees (0 = right, counter-clockwise). */
    val CENTRES_DEG: List<Double> = listOf(90.0, 30.0, -30.0, -90.0, -150.0, 150.0)

    /** The slot whose sector centre is nearest to [angleDeg] (0 = right, 90 = up). */
    fun slotAt(angleDeg: Double): Int {
        // Clockwise offset from straight up, in [0, 360).
        val d = ((90.0 - angleDeg) % 360.0 + 360.0) % 360.0
        return ((d + SECTOR_DEG / 2.0) / SECTOR_DEG).toInt() % SLOTS
    }

    /** The slot for a stick sample (Android axes: y positive = down). */
    fun slotOf(x: Float, y: Float): Int = slotAt(StickGeometry.angleDeg(x, y).toDouble())

    /** The direction a slot is in practice, for the diagnostics log. */
    fun arrow(slot: Int): String = when (slot) {
        0 -> "↑"; 1 -> "↗"; 2 -> "↘"; 3 -> "↓"; 4 -> "↙"; else -> "↖"
    }

    /** Unit offset of a slot's centre for the guides: x right, y DOWN (screen coordinates). */
    fun offset(slot: Int): Pair<Float, Float> {
        val rad = Math.toRadians(CENTRES_DEG[slot])
        return cos(rad).toFloat() to -sin(rad).toFloat()
    }
}
