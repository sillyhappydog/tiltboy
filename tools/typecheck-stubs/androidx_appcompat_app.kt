// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.appcompat.app

abstract class ActionBar {
    abstract var title: CharSequence?
    abstract fun setDisplayHomeAsUpEnabled(showHomeAsUp: Boolean)
    abstract fun show()
}
open class AppCompatActivity : androidx.fragment.app.FragmentActivity() {
    val supportActionBar: ActionBar? get() = null
}
