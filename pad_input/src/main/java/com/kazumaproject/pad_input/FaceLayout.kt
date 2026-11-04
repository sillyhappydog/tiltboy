package com.kazumaproject.pad_input

/** Physical position of a face button on the controller. */
enum class FacePosition {
    TOP, LEFT, RIGHT, BOTTOM;

    /** Index into a Daisywheel petal string / emoji cell list ([Daisywheel.POS_LEFT] …). */
    val petalIndex: Int
        get() = when (this) {
            LEFT -> Daisywheel.POS_LEFT
            TOP -> Daisywheel.POS_TOP
            RIGHT -> Daisywheel.POS_RIGHT
            BOTTOM -> Daisywheel.POS_BOTTOM
        }
}

/**
 * Which printed button sits at which physical position. In the wheel modes a character's
 * position inside a petal is the position of the button that types it, so this is what makes
 * "the top button types the top character" true on every controller.
 *
 * - Nintendo arrangement (RG477V, Switch): X top, Y left, A right, B bottom.
 * - Xbox arrangement: Y top, X left, B right, A bottom (Valve's Daisywheel assumes this).
 */
data class FaceLayout(
    val top: PadButton,
    val left: PadButton,
    val right: PadButton,
    val bottom: PadButton,
) {
    init {
        require(setOf(top, left, right, bottom).size == 4) { "each position needs a different button" }
    }

    fun buttonAt(position: FacePosition): PadButton = when (position) {
        FacePosition.TOP -> top
        FacePosition.LEFT -> left
        FacePosition.RIGHT -> right
        FacePosition.BOTTOM -> bottom
    }

    fun positionOf(button: PadButton): FacePosition? = when (button) {
        top -> FacePosition.TOP
        left -> FacePosition.LEFT
        right -> FacePosition.RIGHT
        bottom -> FacePosition.BOTTOM
        else -> null
    }

    val name: String
        get() = when (this) {
            NINTENDO -> "nintendo"
            XBOX -> "xbox"
            else -> "custom"
        }

    companion object {
        val NINTENDO = FaceLayout(top = PadButton.X, left = PadButton.Y, right = PadButton.A, bottom = PadButton.B)
        val XBOX = FaceLayout(top = PadButton.Y, left = PadButton.X, right = PadButton.B, bottom = PadButton.A)

        fun byName(name: String?): FaceLayout? = when (name?.lowercase()) {
            "nintendo" -> NINTENDO
            "xbox" -> XBOX
            else -> null
        }

        /** Build from stored button names; null if any name is missing or the set is not four distinct face buttons. */
        fun fromNames(top: String?, left: String?, right: String?, bottom: String?): FaceLayout? {
            fun parse(s: String?) = PadButton.entries.firstOrNull { it.name == s && it.isFace }
            val t = parse(top) ?: return null
            val l = parse(left) ?: return null
            val r = parse(right) ?: return null
            val b = parse(bottom) ?: return null
            if (setOf(t, l, r, b).size != 4) return null
            return FaceLayout(t, l, r, b)
        }
    }
}
