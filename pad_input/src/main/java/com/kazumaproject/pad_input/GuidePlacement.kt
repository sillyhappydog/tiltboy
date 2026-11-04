package com.kazumaproject.pad_input

import kotlin.math.max
import kotlin.math.min

/**
 * Where the guide view goes, in the coordinates of the keyboard container. Pure Kotlin so the
 * rule can be unit-tested at the device's sizes and used by the headless renders.
 *
 * Amendment 3: on the RG477V the keyboard window is 1280 × 960 but only y < 875 is visible; the
 * last 85 px are the system navigation area, which Sumire handles by giving the keyboard root
 * a bottom padding equal to the inset. v1 anchored the guide to the window's bottom edge and
 * so lost its lower rows. The frame is now computed from the keyboard root's laid-out bounds
 * minus its padding ([visible]) and never leaves it.
 *
 * Two placements exist. [cornerFrame] is (a), the overlay in the keyboard's corner (shipped).
 * (b), the guide replacing the touch keys while the controller is in use, is just the key
 * area's own rectangle ([fullFrame]); it is rendered for the report but not wired in until
 * the user has chosen.
 */
object GuidePlacement {

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val isEmpty: Boolean get() = width <= 0 || height <= 0
        fun contains(other: Rect): Boolean =
            other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
    }

    /** Kana box: 46 % of the short screen side wide, 0.56 as tall (v1, user-approved). */
    const val KANA_WIDTH_OF_SHORT_SIDE = 0.46f
    const val KANA_ASPECT = 0.56f

    /** Wheel band: 96 % of the visible keyboard width, 27 % of the short screen side tall. */
    const val WHEEL_WIDTH_OF_VISIBLE = 0.96f
    const val WHEEL_HEIGHT_OF_SHORT_SIDE = 0.27f

    /** The overlay never covers more than this share of the visible keyboard's height. */
    const val MAX_HEIGHT_OF_VISIBLE = 0.60f

    const val MARGIN_BOTTOM_DP = 8f
    const val MARGIN_SIDE_DP = 4f

    /**
     * Placement (a): the corner overlay. [visible] is the keyboard root's bounds minus its
     * padding, in the container's coordinates. The result always lies inside [visible]; the
     * preferred size shrinks when the visible keyboard is small.
     */
    fun cornerFrame(visible: Rect, kana: Boolean, shortSidePx: Int, density: Float): Rect {
        if (visible.isEmpty) return Rect(visible.left, visible.top, visible.left, visible.top)
        val marginBottom = (MARGIN_BOTTOM_DP * density).toInt()
        val marginSide = (MARGIN_SIDE_DP * density).toInt()
        val maxW = max(1, visible.width - 2 * marginSide)
        val maxH = max(1, min((visible.height * MAX_HEIGHT_OF_VISIBLE).toInt(), visible.height - marginBottom))
        return if (kana) {
            var w = (shortSidePx * KANA_WIDTH_OF_SHORT_SIDE).toInt()
            var h = (w * KANA_ASPECT).toInt()
            if (h > maxH) { h = maxH; w = (h / KANA_ASPECT).toInt() }
            if (w > maxW) { w = maxW; h = (w * KANA_ASPECT).toInt() }
            val right = visible.right - marginSide
            val bottom = visible.bottom - marginBottom
            Rect(right - w, bottom - h, right, bottom)
        } else {
            val w = min((visible.width * WHEEL_WIDTH_OF_VISIBLE).toInt(), maxW)
            val h = min((shortSidePx * WHEEL_HEIGHT_OF_SHORT_SIDE).toInt(), maxH)
            val left = visible.left + (visible.width - w) / 2
            val bottom = visible.bottom - marginBottom
            Rect(left, bottom - h, left + w, bottom)
        }
    }

    /** Placement (b): the guide takes the touch keys' own rectangle. */
    fun fullFrame(keyArea: Rect): Rect = keyArea

    /**
     * Conservative bounding box of a text primitive drawn inside a frame whose top-left is
     * ([frameLeft], [frameTop]): CJK glyphs are taken as 1 em wide, others as 0.65 em.
     * Used by the fit tests to check that every character is inside the visible keyboard.
     */
    fun textBounds(t: GuideLayout.Primitive.Text, frameLeft: Int, frameTop: Int): Rect {
        val w = GuideLayout.textWidth(t.text, t.size) * 1.08f
        val left = when (t.align) {
            GuideLayout.Align.LEFT -> t.x
            GuideLayout.Align.RIGHT -> t.x - w
            GuideLayout.Align.CENTER -> t.x - w / 2f
        }
        return Rect(
            (frameLeft + left).toInt(), (frameTop + t.y - t.size / 2f).toInt(),
            (frameLeft + left + w).toInt() + 1, (frameTop + t.y + t.size / 2f).toInt() + 1,
        )
    }
}
