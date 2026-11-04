package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.GestureType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Amendment 15: the kana right stick as a pentagon. Each kana key has five characters, so the
 * right stick reads five sectors of 72° instead of four tilts plus a click (R3). The apex is
 * straight up and types the あ段; then, clockwise: い, う, え, お.
 *
 * | slot | character | sector centre (0° = right, counter-clockwise) | sector       | in practice |
 * |------|-----------|-----------------------------------------------|--------------|-------------|
 * | 0    | あ段      | 90°                                           | 54° … 126°   | ↑           |
 * | 1    | い段      | 18°                                           | −18° … 54°   | →           |
 * | 2    | う段      | −54° (306°)                                   | 270° … 342°  | ↘           |
 * | 3    | え段      | −126° (234°)                                  | 198° … 270°  | ↙           |
 * | 4    | お段      | 162°                                          | 126° … 198°  | ←           |
 *
 * The nearest sector centre wins; straight down (270°) is the う/え boundary and needs nothing
 * special (it falls to え). Slot *n* types what the touch key types with its *n*-th gesture
 * (tap, left, up, right, down), so the や key gives や 「 ゆ 」 よ with no per-key table.
 * 英字 / 数字 keep four tilts (amendment 18: on the same ring), the R2 layer its eight directions.
 */
object KanaPentagon {
    const val SLOTS = 5
    const val SECTOR_DEG = 72.0

    /** Sector centres in slot order, degrees (0 = right, counter-clockwise). */
    val CENTRES_DEG: List<Double> = listOf(90.0, 18.0, -54.0, -126.0, 162.0)

    /** The touch key's gesture each slot types: tap (あ段), left (い段), up (う段), right (え段), down (お段). */
    val GESTURES: List<GestureType> = listOf(
        GestureType.Tap, GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom,
    )

    /** The slot whose sector centre is nearest to [angleDeg] (0 = right, 90 = up). */
    fun slotAt(angleDeg: Double): Int {
        // Clockwise offset from straight up, in [0, 360).
        val d = ((90.0 - angleDeg) % 360.0 + 360.0) % 360.0
        return ((d + SECTOR_DEG / 2.0) / SECTOR_DEG).toInt() % SLOTS
    }

    /** The slot for a stick sample (Android axes: y positive = down). */
    fun slotOf(x: Float, y: Float): Int = slotAt(StickGeometry.angleDeg(x, y).toDouble())

    fun gesture(slot: Int): GestureType = GESTURES[slot]

    /** The slot that types a touch gesture (tap = 0 … flick ↓ = 4), or -1. */
    fun slotOf(gesture: GestureType): Int = GESTURES.indexOf(gesture)

    /** The direction a slot is in practice, for the diagnostics log. */
    fun arrow(slot: Int): String = when (slot) {
        0 -> "↑"; 1 -> "→"; 2 -> "↘"; 3 -> "↙"; else -> "←"
    }

    /** Unit offset of a slot's centre for the guides: x right, y DOWN (screen coordinates). */
    fun offset(slot: Int): Pair<Float, Float> {
        val rad = Math.toRadians(CENTRES_DEG[slot])
        return cos(rad).toFloat() to -sin(rad).toFloat()
    }
}
