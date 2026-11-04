package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.pad_input.GuideLayout
import com.kazumaproject.pad_input.GuideMap
import com.kazumaproject.pad_input.GuidePlacement
import com.kazumaproject.pad_input.PadButton
import com.kazumaproject.pad_input.PadConfig
import com.kazumaproject.pad_input.PadGuideState
import com.kazumaproject.pad_input.PadInputEngine
import com.kazumaproject.pad_input.PadMode
import com.kazumaproject.pad_input.StickSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Amendments 3 and 4: lays out a keyboard window at the RG477V's size (1280 × 960, density 2)
 * with an 85 px bottom inset, attaches the guide the way IMEService does, and checks that the
 * guide and every one of its characters lie inside the keyboard's visible bounds — for both
 * placements: (a) the corner overlay and (b) the full-size guide over the touch keys.
 *
 * The keyboard root is modelled the way Sumire builds it: a direct child of the IME's
 * container, anchored at the bottom, with the window's bottom inset as the root's own
 * bottom padding (`mainView.root.setPadding(0, 0, 0, systemBottomInset)`), and the key view
 * a bottom-anchored child of the root.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h480dp-land-xhdpi")
class PadGuideFitTest {

    private val screenW = 1280
    private val screenH = 960
    private val inset = 85
    private val keyboardTop = 250
    private val keyRowsHeight = 377

    private fun engineState(mode: PadMode, shift: Boolean, x: Float, y: Float): PadGuideState {
        // Amendment 20: the emoji wheel is the D-pad fallback's only (R3; the D-pad picks the petal, L1 = page 2).
        val wheel = mode == PadMode.EMOJI
        val e = PadInputEngine(PadConfig(stickSource = if (wheel) StickSource.DPAD_FALLBACK else StickSource.STICKS, words = PadText.words(ApplicationProvider.getApplicationContext<Context>())))
        e.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍"))
        var t = 0L
        fun button(b: PadButton, down: Boolean) { t += 16; e.onButton(b, down, t) }
        // Amendment 20: R3 steps the text modes (Select no longer cycles them).
        when (mode) {
            PadMode.ENGLISH -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.NUMBER -> repeat(2) { button(PadButton.R3, true); button(PadButton.R3, false) }
            PadMode.EMOJI -> { button(PadButton.R3, true); button(PadButton.R3, false) }
            else -> {}
        }
        if (wheel) {
            if (shift) button(PadButton.L1, true)
            if (y < -0.3f) button(PadButton.DPAD_UP, true)
            if (y > 0.3f) button(PadButton.DPAD_DOWN, true)
            if (x < -0.3f) button(PadButton.DPAD_LEFT, true)
            if (x > 0.3f) button(PadButton.DPAD_RIGHT, true)
            return e.guide
        }
        // Amendment 18: the second set is R2 in every text mode (記号; amendment 21: in かな too).
        if (shift) button(PadButton.R2, true)
        t += 16; e.onSticks(x, y, 0f, 0f, t)
        return e.guide
    }

    private fun states() = listOf(
        "kana" to engineState(PadMode.KANA, false, 0f, -1f),
        "english_shift" to engineState(PadMode.ENGLISH, true, 0f, 0f),
        "number" to engineState(PadMode.NUMBER, false, 0f, 1f),
        "kana_shift" to engineState(PadMode.KANA, true, -1f, 0f),
        "english" to engineState(PadMode.ENGLISH, false, 0.7f, -0.7f),
        "number_shift" to engineState(PadMode.NUMBER, true, 1f, 0f),
        "emoji" to engineState(PadMode.EMOJI, false, 0f, 1f),
    )

    private fun layoutWindow(container: View) {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, screenW, screenH)
        shadowOf(Looper.getMainLooper()).idle() // the guide re-places itself after the root's layout
        container.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, screenW, screenH)
    }

    private class Window(ctx: Context, inset: Int, screenH: Int, keyboardTop: Int, keyRowsHeight: Int) {
        val container = FrameLayout(ctx)
        val root = FrameLayout(ctx).apply { setPadding(0, 0, 0, inset) }
        val keyView = View(ctx)
        init {
            container.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, screenH - keyboardTop, Gravity.BOTTOM))
            root.addView(keyView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyRowsHeight, Gravity.BOTTOM))
        }
    }

    private fun assertTextsInside(name: String, s: PadGuideState, guide: PadGuideView, density: Float, visible: GuidePlacement.Rect) {
        val r = GuideLayout.layout(s, GuideLayout.GuideCanvas(guide.width.toFloat(), guide.height.toFloat(), density), map = guide.isFullPlacement)
        assertTrue("$name: no characters", r.texts.isNotEmpty())
        for (t in r.texts) {
            if (t.text.isBlank()) continue
            val b = GuidePlacement.textBounds(t, guide.left, guide.top)
            assertTrue("$name: '${t.text}' at $b leaves visible keyboard $visible", visible.contains(b))
        }
    }

    @Test
    fun corner_overlay_and_all_its_characters_stay_inside_the_visible_keyboard() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val density = ctx.resources.displayMetrics.density
        assertEquals(2f, density, 0.01f)
        val w = Window(ctx, inset, screenH, keyboardTop, keyRowsHeight)
        val guide = PadGuideView(ctx)
        guide.attachAsOverlay(w.container, w.root)
        for ((name, s) in states()) {
            guide.render(s)
            layoutWindow(w.container)
            val visibleBottom = w.root.bottom - w.root.paddingBottom
            assertEquals(screenH - inset, visibleBottom)
            val visible = GuidePlacement.Rect(w.root.left, w.root.top, w.root.right, visibleBottom)
            val frame = GuidePlacement.Rect(guide.left, guide.top, guide.right, guide.bottom)
            assertTrue("$name: guide $frame outside visible keyboard $visible", visible.contains(frame) && !frame.isEmpty)
            assertNotNull(guide.overlayFrame)
            assertEquals(frame, guide.overlayFrame)
            assertTextsInside(name, s, guide, density, visible)
        }
    }

    @Test
    fun full_guide_covers_exactly_the_key_area_and_its_characters_stay_inside_it() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val density = ctx.resources.displayMetrics.density
        val w = Window(ctx, inset, screenH, keyboardTop, keyRowsHeight)
        val guide = PadGuideView(ctx).apply { opaque = true }
        guide.attachAsFull(w.root) { w.keyView }
        for ((name, s) in states()) {
            guide.render(s)
            layoutWindow(w.container)
            // Same rectangle as the key view (root coordinates), hence above the inset.
            assertEquals("$name left", w.keyView.left, guide.left)
            assertEquals("$name top", w.keyView.top, guide.top)
            assertEquals("$name right", w.keyView.right, guide.right)
            assertEquals("$name bottom", w.keyView.bottom, guide.bottom)
            assertEquals(screenH - keyboardTop - inset, guide.bottom)
            assertEquals(keyRowsHeight, guide.height)
            val visibleInRoot = GuidePlacement.Rect(0, 0, w.root.width, w.root.height - w.root.paddingBottom)
            assertTextsInside(name, s, guide, density, visibleInRoot)
            // Every character also stays inside the guide's own rectangle (nothing spills over the candidate bar).
            val own = GuidePlacement.Rect(guide.left, guide.top, guide.right, guide.bottom)
            assertTextsInside("$name (own frame)", s, guide, density, own)
        }
        // A touch on the full guide is swallowed (amendment 20: and nothing else happens; the guide stays).
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        assertTrue(guide.dispatchTouchEvent(down))
        assertEquals(View.VISIBLE, guide.visibility)
        down.recycle()
        guide.render(null)
        val down2 = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        assertTrue("hidden: touches pass through", !guide.dispatchTouchEvent(down2))
        down2.recycle()
    }

    /**
     * Amendment 18: a tap on a touch digit (down and up in the same cell) types it and keeps the
     * guide; a touch that leaves the cell types nothing. Amendment 20: a touch anywhere else (the
     * grid, the ring, ABXY, the empty background) does nothing at all and the guide stays.
     */
    @Test
    fun touch_digits_type_and_keep_the_guide() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val w = Window(ctx, inset, screenH, keyboardTop, keyRowsHeight)
        val guide = PadGuideView(ctx).apply { opaque = true }
        val typed = StringBuilder()
        guide.onDigitTapped = { typed.append(it) }
        guide.attachAsFull(w.root) { w.keyView }
        guide.render(engineState(PadMode.KANA, false, 0f, 0f))
        layoutWindow(w.container)
        val canvas = GuideLayout.GuideCanvas(guide.width.toFloat(), guide.height.toFloat(), ctx.resources.displayMetrics.density)
        val cells = GuideMap.digitCells(canvas).associateBy { it.digit }
        var now = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, y: Float) {
            now += 16
            val e = MotionEvent.obtain(now, now, action, x, y, 0)
            assertTrue(guide.dispatchTouchEvent(e))
            e.recycle()
        }
        for (d in listOf('7', '1', '0')) {
            val c = cells.getValue(d)
            assertEquals(d, guide.digitAt(c.centreX, c.centreY))
            touch(MotionEvent.ACTION_DOWN, c.centreX, c.centreY)
            touch(MotionEvent.ACTION_UP, c.centreX + 3f, c.centreY - 3f)
        }
        assertEquals("710", typed.toString())
        assertEquals("a digit touch keeps the guide", View.VISIBLE, guide.visibility)
        // Down on 5, up on 6: nothing typed.
        touch(MotionEvent.ACTION_DOWN, cells.getValue('5').centreX, cells.getValue('5').centreY)
        touch(MotionEvent.ACTION_MOVE, cells.getValue('6').centreX, cells.getValue('6').centreY)
        touch(MotionEvent.ACTION_UP, cells.getValue('6').centreX, cells.getValue('6').centreY)
        assertEquals("710", typed.toString())
        // Amendment 20: the grid, the ring, ABXY and the empty background: swallowed, nothing typed, the guide stays.
        val sc = guide.width / GuideMap.BASE_W
        val spots = mapOf(
            "grid" to (500f to 176f), "ring" to (GuideMap.Kana.R3_CX to 176f), "ABXY" to (1113f to 234f),
            "background" to (660f to 372f), "top corner" to (8f to 8f),
        )
        for ((what, xy) in spots) {
            val (x, y) = xy.first * sc to xy.second * sc
            assertEquals("$what is no digit", null, guide.digitAt(x, y))
            touch(MotionEvent.ACTION_DOWN, x, y)
            touch(MotionEvent.ACTION_UP, x, y)
            assertEquals("$what: nothing typed", "710", typed.toString())
            assertEquals("$what: the guide stays", View.VISIBLE, guide.visibility)
        }
        // Between two cells: no digit.
        val one = cells.getValue('1')
        assertEquals(null, guide.digitAt(one.x + one.w + 1f, one.centreY))
        // In the emoji wheel there are no digits.
        guide.render(engineState(PadMode.EMOJI, false, 0f, 0f))
        layoutWindow(w.container)
        assertEquals(null, guide.digitAt(one.centreX, one.centreY))
    }

    @Test
    fun overlay_follows_a_changed_inset_and_detaches_cleanly() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val w = Window(ctx, inset, screenH, keyboardTop, keyRowsHeight)
        val guide = PadGuideView(ctx)
        guide.attachAsOverlay(w.container, w.root)
        guide.render(engineState(PadMode.KANA, false, 0f, -1f))
        layoutWindow(w.container)
        assertEquals(screenH - inset - 16, guide.bottom)

        // Gesture navigation: smaller inset → the guide moves down with the visible bottom.
        w.root.setPadding(0, 0, 0, 30)
        layoutWindow(w.container)
        assertEquals(screenH - 30 - 16, guide.bottom)

        guide.detach()
        assertTrue(guide.parent == null)
        assertEquals(1, w.container.childCount)
    }
}
