// Sumire Pad: compile-time stub for tools/typecheck-app.sh only (never packaged).
// Signatures mirror the real library for the members the pad code uses.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.recyclerview.widget

open class RecyclerView(context: android.content.Context) : android.view.ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
    class State
    abstract class ViewHolder(val itemView: android.view.View)
    abstract class ItemDecoration {
        open fun onDrawOver(c: android.graphics.Canvas, parent: RecyclerView, state: State) {}
    }
    fun findViewHolderForAdapterPosition(position: Int): ViewHolder? = null
}
