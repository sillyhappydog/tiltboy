// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror androidx.core for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.core.view

import android.view.View

var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) { visibility = if (value) View.VISIBLE else View.GONE }

fun interface OnApplyWindowInsetsListener {
    fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat
}

class WindowInsetsCompat {
    fun getInsets(typeMask: Int): androidx.core.graphics.Insets = androidx.core.graphics.Insets.NONE
    object Type {
        @JvmStatic fun ime(): Int = 8
        @JvmStatic fun systemBars(): Int = 7
    }
}

object ViewCompat {
    @JvmStatic fun setOnApplyWindowInsetsListener(v: View, listener: OnApplyWindowInsetsListener?) {}
    @JvmStatic fun getRootWindowInsets(v: View): WindowInsetsCompat? = null
    @JvmStatic fun requestApplyInsets(v: View) {}
}
