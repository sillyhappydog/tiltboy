// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
@file:Suppress("unused")
package androidx.core.graphics

class Insets private constructor(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    companion object {
        @JvmField val NONE: Insets = Insets(0, 0, 0, 0)
        @JvmStatic fun of(left: Int, top: Int, right: Int, bottom: Int): Insets = Insets(left, top, right, bottom)
    }
}
