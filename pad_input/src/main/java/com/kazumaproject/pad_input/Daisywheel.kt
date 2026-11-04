package com.kazumaproject.pad_input

/**
 * Slot positions inside a petal of the emoji wheel (the only wheel left since amendment 6:
 * English and numbers use Sumire's tenkey layouts like kana). The letter wheel of v1 (Valve's
 * Big Picture Daisywheel) is gone.
 */
object Daisywheel {
    /** Position inside a petal, in the order the characters are stored. */
    const val POS_LEFT = 0
    const val POS_TOP = 1
    const val POS_RIGHT = 2
    const val POS_BOTTOM = 3

    /** D-pad fallback on the emoji wheel: the printed button that picks each slot (X left, Y top, B right, A bottom). */
    fun positionOf(button: PadButton): Int? = when (button) {
        PadButton.X -> POS_LEFT
        PadButton.Y -> POS_TOP
        PadButton.B -> POS_RIGHT
        PadButton.A -> POS_BOTTOM
        else -> null
    }
}
