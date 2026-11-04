package com.kazumaproject.pad_input

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Pure geometry for stick samples. Android axes: x right positive, y DOWN positive. */
object StickGeometry {
    fun radius(x: Float, y: Float): Float = hypot(x, y)

    /**
     * 8 sectors of 45°, centred on the axes (so "up" is -67.5°..-112.5°).
     * The caller decides whether the radius is large enough; this never returns CENTER.
     */
    fun sector8(x: Float, y: Float): Sector9 {
        var deg = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
        // 0 = right, 90 = down (screen coordinates). Normalise to [0, 360).
        if (deg < 0) deg += 360.0
        // Shift by 22.5 so each 45° bucket is centred on a compass direction.
        val bucket = ((deg + 22.5) / 45.0).toInt() % 8
        return when (bucket) {
            0 -> Sector9.RIGHT
            1 -> Sector9.DOWN_RIGHT
            2 -> Sector9.DOWN
            3 -> Sector9.DOWN_LEFT
            4 -> Sector9.LEFT
            5 -> Sector9.UP_LEFT
            6 -> Sector9.UP
            else -> Sector9.UP_RIGHT
        }
    }

    /** Direction in degrees, 0 = right, 90 = up, in [0, 360): what the diagnostics log shows for a bar flick. */
    fun angleDeg(x: Float, y: Float): Float {
        var deg = Math.toDegrees(atan2(-y.toDouble(), x.toDouble())).toFloat()
        if (deg < 0f) deg += 360f
        return deg
    }

    /** 4 sectors of 90°. On an exact diagonal the horizontal direction wins. */
    fun sector4(x: Float, y: Float): Dir4 = when {
        abs(x) >= abs(y) -> if (x < 0) Dir4.LEFT else Dir4.RIGHT
        else -> if (y < 0) Dir4.UP else Dir4.DOWN
    }

    /** Combine held D-pad buttons into a sector (diagonals when two are held). */
    fun sectorFromDpad(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Sector9 {
        val v = (if (up) -1 else 0) + (if (down) 1 else 0)
        val h = (if (left) -1 else 0) + (if (right) 1 else 0)
        return Sector9.entries.first { it.col == h + 1 && it.row == v + 1 }
    }
}
