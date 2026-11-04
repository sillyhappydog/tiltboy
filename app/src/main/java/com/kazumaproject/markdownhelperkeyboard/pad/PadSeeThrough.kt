package com.kazumaproject.markdownhelperkeyboard.pad

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup

/**
 * TiltBoy v15: while the full guide is drawn translucent (its slider below 100 %), the app
 * behind the keyboard shows through it. Everything the keyboard draws under the guide is taken
 * away: the touch key view under it gets alpha 0 (it keeps its layout, so the guide keeps its
 * frame and the keys come back unchanged), and the keyboard root's background leaves a hole at
 * the guide's frame. [update] puts both back as soon as the guide is opaque or hidden.
 */
class PadSeeThrough {

    /** Views made transparent, with the alpha they had. */
    private val hidden = LinkedHashMap<View, Float>()

    val active: Boolean get() = hidden.isNotEmpty()

    /** [guide] is a direct child of [root]; [keyView] is the touch key view it covers (or null). */
    fun update(root: ViewGroup, guide: PadGuideView?, keyView: View?) {
        val on = guide != null && guide.parent === root && guide.visibility == View.VISIBLE && guide.effectiveOpacity < 1f
        if (!on) {
            restore(root)
            return
        }
        // The key view showing can change while the guide is up: show any other one again.
        val it = hidden.entries.iterator()
        while (it.hasNext()) {
            val (v, a) = it.next()
            if (v !== keyView) { v.alpha = a; it.remove() }
        }
        if (keyView != null && keyView !in hidden) {
            hidden[keyView] = keyView.alpha
            keyView.alpha = 0f
        }
        val bg = root.background
        if (bg != null && bg !is HoleDrawable) setBackgroundKeepingPadding(root, HoleDrawable(bg, guide!!))
    }

    /** Show the hidden key views again and give [root] its own background back. */
    fun restore(root: ViewGroup?) {
        for ((v, a) in hidden) v.alpha = a
        hidden.clear()
        val bg = root?.background
        if (root != null && bg is HoleDrawable) setBackgroundKeepingPadding(root, bg.inner)
    }

    /** Sumire keeps the window's bottom inset in the root's padding; a background swap must not reset it. */
    private fun setBackgroundKeepingPadding(v: View, d: Drawable?) {
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.background = d
        // The view clears the old background's callback after the new one is set.
        if (d is HoleDrawable) d.inner.callback = d
        v.setPadding(l, t, r, b)
    }

    /** [inner] everywhere except [guide]'s frame ([guide] is a direct child of the view this is the background of). */
    class HoleDrawable(val inner: Drawable, private val guide: View) : Drawable(), Drawable.Callback {
        private val hole = Rect()

        override fun draw(canvas: Canvas) {
            if (guide.visibility != View.VISIBLE || guide.width == 0 || guide.height == 0) {
                inner.draw(canvas)
                return
            }
            hole.set(guide.left, guide.top, guide.right, guide.bottom)
            val save = canvas.save()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutRect(hole)
            } else {
                @Suppress("DEPRECATION")
                canvas.clipRect(hole, Region.Op.DIFFERENCE)
            }
            inner.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun onBoundsChange(bounds: Rect) { inner.bounds = bounds }
        override fun setAlpha(alpha: Int) { inner.alpha = alpha }
        override fun getAlpha(): Int = inner.alpha
        override fun setColorFilter(colorFilter: ColorFilter?) { inner.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun isStateful(): Boolean = inner.isStateful
        override fun onStateChange(state: IntArray): Boolean = inner.setState(state)
        override fun getPadding(padding: Rect): Boolean = inner.getPadding(padding)
        override fun getIntrinsicWidth(): Int = inner.intrinsicWidth
        override fun getIntrinsicHeight(): Int = inner.intrinsicHeight
        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            inner.setVisible(visible, restart)
            return super.setVisible(visible, restart)
        }
        override fun invalidateDrawable(who: Drawable) = invalidateSelf()
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
    }
}
