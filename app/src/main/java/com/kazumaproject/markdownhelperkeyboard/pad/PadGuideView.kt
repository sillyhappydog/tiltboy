package com.kazumaproject.markdownhelperkeyboard.pad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kazumaproject.pad_input.FourWayLayouts
import com.kazumaproject.pad_input.GuideLayout
import com.kazumaproject.pad_input.GuideMap
import com.kazumaproject.pad_input.GuidePlacement
import com.kazumaproject.pad_input.PadGuideState

/**
 * On-screen controller guide. All geometry comes from [GuideLayout] (pure Kotlin, unit-tested
 * and rendered headless for the report); this view only paints the primitives into whatever
 * size its container gives it, so the same view works as the keyboard overlay, as a full-size
 * replacement for the touch keys, or inside the diagnostics screen.
 *
 * As the keyboard overlay ([attachAsOverlay]) it follows the keyboard root's laid-out bounds
 * minus the root's padding (Sumire puts the window's bottom inset there), so it always stays
 * inside the visible keyboard (amendment 3). The view never consumes touches.
 *
 * Amendment 18: in the full placement the controller map's touch digits (1–9, 0) are touch
 * targets. A finger down and up inside the same digit cell types it ([onDigitTapped]); the cell
 * is highlighted while the finger is on it. Amendment 20: any other touch does nothing (it is
 * swallowed; the guide stays). The hit test uses [GuideMap.digitAt], the rectangles the map draws.
 *
 * TiltBoy v15: in the full placement's text-mode layout the free column left of the digits holds
 * an opacity slider ([GuideMap.opacitySlider], touch only). The guide is drawn at [guideOpacity]
 * (down to [GuideMap.Opacity.MIN]); the slider itself stays opaque. The service hides what lies
 * under the guide inside the keyboard, so the app behind shows through.
 */
class PadGuideView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var state: PadGuideState? = null
    private var layoutResult: GuideLayout.Result? = null
    private var layoutKey: Triple<PadGuideState, Int, Int>? = null

    private val boxPaints = mapOf(
        GuideLayout.BoxStyle.BACKGROUND to paint(0xCC202124.toInt()),
        GuideLayout.BoxStyle.CELL to paint(0xFF3C4043.toInt()),
        GuideLayout.BoxStyle.CELL_SELECTED to paint(0xFF8AB4F8.toInt()),
        GuideLayout.BoxStyle.PETAL to paint(0xFF34373B.toInt()),
        GuideLayout.BoxStyle.PETAL_SELECTED to paint(0xFF8AB4F8.toInt()),
        GuideLayout.BoxStyle.BIG_PETAL to paint(0xFF2B2E33.toInt()),
        GuideLayout.BoxStyle.HINT to paint(0x00000000),
        GuideLayout.BoxStyle.PILL to paint(0xFF3C4043.toInt()),
        GuideLayout.BoxStyle.PILL_HELD to paint(0xFF8AB4F8.toInt()),
        GuideLayout.BoxStyle.FACE to paint(0xFF3C4043.toInt()),
        GuideLayout.BoxStyle.FACE_HELD to paint(0xFF8AB4F8.toInt()),
        GuideLayout.BoxStyle.CIRCLE to paint(CIRCLE_COLOR),
        // Amendment 17: an empty slot of the や/わ layer, the dimmed colour of the centre disc.
        GuideLayout.BoxStyle.CELL_EMPTY to paint(CIRCLE_COLOR),
        // Amendment 15, the kana pentagon: a thin ring (stroked) and the centre dot. As on the mock,
        // the ring sits halfway between the background and the cells, the dot is a light grey.
        GuideLayout.BoxStyle.RING to paint(RING_COLOR).apply { style = Paint.Style.STROKE },
        GuideLayout.BoxStyle.DOT to paint(DOT_COLOR),
        // Amendment 18: a role with nothing to do keeps its pill, empty, in the centre disc's colour.
        GuideLayout.BoxStyle.PILL_EMPTY to paint(CIRCLE_COLOR),
        // Amendment 18: the touch digits, outlined in the ring's colour (the only touch targets).
        GuideLayout.BoxStyle.DIGIT to paint(DIGIT_OUTLINE_COLOR).apply { style = Paint.Style.STROKE },
    )
    private val textPaints = mapOf(
        GuideLayout.TextStyle.NORMAL to textPaint(Color.WHITE, bold = true),
        GuideLayout.TextStyle.SELECTED to textPaint(0xFF202124.toInt(), bold = true),
        GuideLayout.TextStyle.DIM to textPaint(0xFFC8CCD0.toInt(), bold = false),
        GuideLayout.TextStyle.ACCENT to textPaint(0xFFFDD663.toInt(), bold = true),
        GuideLayout.TextStyle.LABEL to textPaint(0xFFBDC1C6.toInt(), bold = false),
        GuideLayout.TextStyle.HEADER to textPaint(0xFFBDC1C6.toInt(), bold = false),
        GuideLayout.TextStyle.HINT to textPaint(0xFF9AA0A6.toInt(), bold = false),
        GuideLayout.TextStyle.FUNC to textPaint(Color.WHITE, bold = false),
        // Amendment 16: the pentagon / cross under the や・わ layer, barely above the cell colour (v5 mock).
        GuideLayout.TextStyle.FAINT to textPaint(FAINT_COLOR, bold = true),
    )
    private val rect = RectF()
    private val inkBounds = Rect()
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /**
     * Placement (b): paint an opaque background so the touch keys underneath are hidden, and
     * swallow touches. Amendment 20: a touch outside the touch digits does nothing at all (until v7
     * it brought the touch keys back; Select does that now).
     */
    var opaque: Boolean = false
        set(value) { field = value; invalidate() }

    /** Amendment 18: a touch digit was tapped (down and up in the same cell) in the full placement. */
    var onDigitTapped: ((Char) -> Unit)? = null

    /** v15: the guide's opacity in the full placement (1 = opaque); see [effectiveOpacity]. */
    var guideOpacity: Float = 1f
        set(value) {
            val v = GuideMap.Opacity.clamp(value)
            if (field == v) return
            field = v
            invalidate()
        }

    /** v15: the slider moved to [opacity]; [done] on finger up (store it then). */
    var onOpacityChanged: ((opacity: Float, done: Boolean) -> Unit)? = null

    /** v15: the slider is drawn and touchable (full placement, a text mode's layout). */
    val showsOpacitySlider: Boolean get() = isFullPlacement && state?.kanaLayout == true

    /** v15: the opacity actually drawn: [guideOpacity] while the slider is shown, else opaque. */
    val effectiveOpacity: Float get() = if (showsOpacitySlider) guideOpacity else 1f

    private var draggingOpacity = false
    private val sliderTrackPaint = paint(SLIDER_TRACK_COLOR)
    private val sliderFillPaint = paint(SLIDER_ACCENT_COLOR)
    private val sliderKnobPaint = paint(SLIDER_KNOB_COLOR)
    private val sliderShadowPaint = paint(SLIDER_SHADOW_COLOR)
    private val sliderRingPaint = paint(SLIDER_ACCENT_COLOR).apply { style = Paint.Style.STROKE }
    private val sliderIconPaint = paint(SLIDER_ICON_COLOR).apply { style = Paint.Style.STROKE }
    private val sliderIconFill = paint(SLIDER_ICON_COLOR)

    /** The digit cell the current touch started on (null: no touch, or it started elsewhere). */
    private var downDigit: Char? = null
    /** The digit drawn highlighted: [downDigit] while the finger is still inside its cell. */
    private var touchedDigit: Char? = null
    private val opaquePaint = paint(0xFF202124.toInt())

    // Overlay placement (a) / full placement (b): the view follows [keyboardRoot]'s layout.
    private var keyboardRoot: View? = null
    private var keyAreaProvider: (() -> View?)? = null
    private var lastFrame: GuidePlacement.Rect? = null
    private val main = Handler(Looper.getMainLooper())
    private val placeRunnable = Runnable { requestPlacement() }
    private val rootLayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        // The root is mid-layout here; move the overlay on the next loop iteration. Always
        // recompute: a padding (inset) change re-runs the root's layout with the same bounds.
        main.removeCallbacks(placeRunnable)
        main.post(placeRunnable)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun textPaint(color: Int, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textAlign = Paint.Align.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun render(newState: PadGuideState?) {
        val modeChanged = newState?.isTenkey != state?.isTenkey
        state = newState
        if (newState == null) { downDigit = null; touchedDigit = null; draggingOpacity = false }
        visibility = if (newState == null) GONE else VISIBLE
        if (newState != null && (modeChanged || lastFrame == null || isFullPlacement)) requestPlacement()
        invalidate()
    }

    /**
     * Placement (a): add this view to [container] as a sibling of [keyboardRoot] (the keyboard's
     * root view, itself a direct child of [container]) and keep it inside the root's bounds minus
     * padding, re-placing it whenever the root is laid out (insets, size, configuration).
     */
    fun attachAsOverlay(container: FrameLayout, keyboardRoot: View) {
        detach()
        this.keyboardRoot = keyboardRoot
        if (parent !== container) {
            (parent as? ViewGroup)?.removeView(this)
            container.addView(this, FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START))
        }
        keyboardRoot.addOnLayoutChangeListener(rootLayoutListener)
        requestPlacement()
    }

    /**
     * Placement (b): add this view to [keyboardRoot] itself (a FrameLayout) over the touch key
     * area, i.e. the bounds of the key view [keyArea] returns (whichever Sumire layout is
     * showing), re-placed whenever the root is laid out. The candidate bar above stays visible.
     */
    fun attachAsFull(keyboardRoot: FrameLayout, keyArea: () -> View?) {
        detach()
        this.keyboardRoot = keyboardRoot
        keyAreaProvider = keyArea
        if (parent !== keyboardRoot) {
            (parent as? ViewGroup)?.removeView(this)
            keyboardRoot.addView(this, FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START))
        }
        keyboardRoot.addOnLayoutChangeListener(rootLayoutListener)
        PadTestHooks.fullGuide = java.lang.ref.WeakReference(this)
        requestPlacement()
    }

    /** Remove the view from its container and stop following the keyboard root. */
    fun detach() {
        if (PadTestHooks.fullGuide?.get() === this) PadTestHooks.fullGuide = null
        keyboardRoot?.removeOnLayoutChangeListener(rootLayoutListener)
        keyboardRoot = null
        keyAreaProvider = null
        main.removeCallbacks(placeRunnable)
        lastFrame = null
        (parent as? ViewGroup)?.removeView(this)
    }

    val isFullPlacement: Boolean get() = keyAreaProvider != null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE || !isFullPlacement) return super.onTouchEvent(event)
        // v15: the opacity slider's column (the whole column, so the thumb need not hit the knob).
        if (event.actionMasked == MotionEvent.ACTION_DOWN && showsOpacitySlider &&
            GuideMap.opacitySlider(canvasOf(width, height)).inZone(event.x, event.y, height.toFloat())
        ) {
            draggingOpacity = true
            downDigit = null
            setTouchedDigit(null)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        if (draggingOpacity) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> slideTo(event.y, done = false)
                MotionEvent.ACTION_UP -> { draggingOpacity = false; slideTo(event.y, done = true) }
                MotionEvent.ACTION_CANCEL -> { draggingOpacity = false; onOpacityChanged?.invoke(guideOpacity, true) }
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val digit = digitAt(event.x, event.y)
                downDigit = digit
                if (digit != null) setTouchedDigit(digit)
            }
            MotionEvent.ACTION_MOVE -> downDigit?.let { d -> setTouchedDigit(if (digitAt(event.x, event.y) == d) d else null) }
            MotionEvent.ACTION_UP -> {
                val d = downDigit
                downDigit = null
                setTouchedDigit(null)
                if (d != null && digitAt(event.x, event.y) == d) onDigitTapped?.invoke(d)
            }
            MotionEvent.ACTION_CANCEL -> {
                downDigit = null
                setTouchedDigit(null)
            }
        }
        return true // never let a touch through to the keys underneath
    }

    /** The touch digit under ([x], [y]) in this view's pixels, from the rectangles the map draws (null: none). */
    fun digitAt(x: Float, y: Float): Char? {
        val s = state ?: return null
        if (!isFullPlacement || width == 0 || height == 0) return null
        return GuideMap.digitAt(s, canvasOf(width, height), x, y)
    }

    /**
     * Amendment 18: the screen position (x, y) of a touch digit's centre, or null while the map
     * shows no digits (the emulator test taps it; call on the main thread).
     */
    fun digitCentreOnScreen(digit: Char): IntArray? {
        val s = state ?: return null
        if (!isFullPlacement || !isShown || width == 0 || height == 0 || !s.kanaLayout) return null
        val cell = GuideMap.digitCells(canvasOf(width, height)).firstOrNull { it.digit == digit } ?: return null
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return intArrayOf(loc[0] + cell.centreX.toInt(), loc[1] + cell.centreY.toInt())
    }

    private fun slideTo(y: Float, done: Boolean) {
        if (width == 0 || height == 0) return
        guideOpacity = GuideMap.opacitySlider(canvasOf(width, height)).opacityAt(y)
        onOpacityChanged?.invoke(guideOpacity, done)
    }

    private fun setTouchedDigit(d: Char?) {
        if (touchedDigit == d) return
        touchedDigit = d
        invalidate()
    }

    private fun canvasOf(w: Int, h: Int) = GuideLayout.GuideCanvas(w.toFloat(), h.toFloat(), resources.displayMetrics.density)

    /** The keyboard root's bounds minus its padding, in the container's coordinates. */
    private fun visibleKeyboard(): GuidePlacement.Rect? {
        val root = keyboardRoot ?: return null
        if (root.width == 0 || root.height == 0) return null
        return GuidePlacement.Rect(
            root.left + root.paddingLeft, root.top + root.paddingTop,
            root.right - root.paddingRight, root.bottom - root.paddingBottom,
        )
    }

    /** Placement (b): the key view's bounds in the root's coordinates, or null when none is showing. */
    private fun keyAreaFrame(): GuidePlacement.Rect? {
        val v = keyAreaProvider?.invoke() ?: return null
        if (v.visibility != VISIBLE || v.width == 0 || v.height == 0) return null
        return GuidePlacement.fullFrame(GuidePlacement.Rect(v.left, v.top, v.right, v.bottom))
    }

    private fun requestPlacement() {
        val s = state ?: return
        val frame = if (isFullPlacement) {
            keyAreaFrame() ?: return
        } else {
            val visible = visibleKeyboard() ?: return
            val dm = resources.displayMetrics
            GuidePlacement.cornerFrame(visible, s.isTenkey, minOf(dm.widthPixels, dm.heightPixels), dm.density)
        }
        if (frame == lastFrame) return
        lastFrame = frame
        val lp = (layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(0, 0)
        lp.width = frame.width
        lp.height = frame.height
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = frame.left
        lp.topMargin = frame.top
        lp.rightMargin = 0
        lp.bottomMargin = 0
        layoutParams = lp
    }

    /** The frame last applied as the overlay (for tests). */
    val overlayFrame: GuidePlacement.Rect? get() = lastFrame

    override fun onDraw(canvas: Canvas) {
        val live = state ?: return
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        // v15: the guide at its opacity (one layer, so overlapping primitives do not add up).
        val alpha = Math.round(effectiveOpacity * 255f)
        val layered = alpha < 255
        if (layered) canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alpha)
        if (opaque) canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), opaquePaint)
        // Amendment 18: the digit under the finger is drawn highlighted.
        val s = if (isFullPlacement && touchedDigit != null) live.copy(touchedDigit = touchedDigit) else live
        val key = Triple(s, w, h)
        val result = if (layoutKey == key) layoutResult!! else {
            // Full placement = the controller map (amendment 5); overlay / diagnostics = the compact form.
            GuideLayout.layout(s, canvasOf(w, h), map = isFullPlacement).also {
                layoutResult = it
                layoutKey = key
            }
        }
        paintPrimitives(canvas, result.primitives)
        if (layered) canvas.restore()
        if (showsOpacitySlider) drawOpacitySlider(canvas, GuideMap.opacitySlider(canvasOf(w, h)))
    }

    /**
     * v15: the slider, always opaque: a half-filled circle above a vertical track, the part below
     * the knob in the accent colour (the more of it, the more opaque the guide), a light knob.
     */
    private fun drawOpacitySlider(canvas: Canvas, sl: GuideMap.OpacitySlider) {
        val half = sl.trackW / 2f
        val ky = sl.knobY(guideOpacity)
        rect.set(sl.cx - half, sl.trackTop, sl.cx + half, sl.trackBottom)
        canvas.drawRoundRect(rect, half, half, sliderTrackPaint)
        rect.set(sl.cx - half, ky, sl.cx + half, sl.trackBottom)
        canvas.drawRoundRect(rect, half, half, sliderFillPaint)
        val ring = maxOf(1.5f, sl.knobR / 6f)
        canvas.drawCircle(sl.cx, ky, sl.knobR + ring, sliderShadowPaint)
        canvas.drawCircle(sl.cx, ky, sl.knobR, sliderKnobPaint)
        sliderRingPaint.strokeWidth = ring
        canvas.drawCircle(sl.cx, ky, sl.knobR - ring / 2f, sliderRingPaint)
        sliderIconPaint.strokeWidth = maxOf(1.5f, sl.iconR / 4.5f)
        canvas.drawCircle(sl.cx, sl.iconCy, sl.iconR, sliderIconPaint)
        rect.set(sl.cx - sl.iconR, sl.iconCy - sl.iconR, sl.cx + sl.iconR, sl.iconCy + sl.iconR)
        canvas.drawArc(rect, 90f, 180f, true, sliderIconFill)
    }

    /**
     * Paint [primitives] (from [GuideLayout]) onto [canvas]. A centred one-character label is placed
     * by its ink bounds (`Paint.getTextBounds`, amendment 17), everything else by the font metrics.
     */
    fun paintPrimitives(canvas: Canvas, primitives: List<GuideLayout.Primitive>) {
        for (p in primitives) {
            when (p) {
                is GuideLayout.Primitive.Box -> {
                    val paint = boxPaints[p.style] ?: continue
                    if (paint.color == 0) continue
                    rect.set(p.x, p.y, p.x + p.w, p.y + p.h)
                    // The ring's stroke scales with it: 2 px at the 157 px ring of the 1280 × 385 map.
                    if (p.style == GuideLayout.BoxStyle.RING) paint.strokeWidth = ringStroke(p.w)
                    // Amendment 18: the digits' outline, 2 px at the 64 px cell of the 1280 × 385 map.
                    if (p.style == GuideLayout.BoxStyle.DIGIT) paint.strokeWidth = digitStroke(p.w)
                    canvas.drawRoundRect(rect, p.radius, p.radius, paint)
                }
                is GuideLayout.Primitive.Text -> {
                    val paint = textPaints[p.style] ?: continue
                    paint.textSize = p.size
                    paint.textAlign = when (p.align) {
                        GuideLayout.Align.LEFT -> Paint.Align.LEFT
                        GuideLayout.Align.RIGHT -> Paint.Align.RIGHT
                        GuideLayout.Align.CENTER -> Paint.Align.CENTER
                    }
                    // 英字 / 数字 cross centre (amendment 14): a font without ␣ would show tofu; draw the mark.
                    if (p.text == FourWayLayouts.SPACE_GLYPH && !paint.hasGlyph(p.text)) {
                        drawSpaceMark(canvas, p, paint)
                        continue
                    }
                    // Amendment 19: a low horizontal stroke (`_`) is centred horizontally only and keeps the baseline.
                    if (GuideLayout.keepsBaseline(p)) {
                        paint.getTextBounds(p.text, 0, p.text.length, inkBounds)
                        if (!inkBounds.isEmpty) {
                            paint.textAlign = Paint.Align.LEFT
                            val fm = paint.fontMetrics
                            val (x, baseline) = GuideLayout.baselineOrigin(
                                p.x, p.y, inkBounds.left.toFloat(), inkBounds.right.toFloat(), -fm.ascent, fm.descent,
                            )
                            canvas.drawText(p.text, x, baseline, paint)
                            continue
                        }
                    }
                    // Amendment 17: one character (。 、 か …) is centred on (p.x, p.y) by its ink box.
                    if (GuideLayout.inkCentred(p)) {
                        paint.getTextBounds(p.text, 0, p.text.length, inkBounds)
                        if (!inkBounds.isEmpty) {
                            paint.textAlign = Paint.Align.LEFT
                            val (x, baseline) = GuideLayout.inkOrigin(
                                p.x, p.y, inkBounds.left.toFloat(), inkBounds.top.toFloat(), inkBounds.right.toFloat(), inkBounds.bottom.toFloat(),
                            )
                            canvas.drawText(p.text, x, baseline, paint)
                            continue
                        }
                    }
                    // Place the glyph box's vertical centre at p.y.
                    val fm = paint.fontMetrics
                    val baseline = p.y - (fm.ascent + fm.descent) / 2f
                    canvas.drawText(p.text, p.x, baseline, paint)
                }
            }
        }
    }

    /** The open-box space mark (␣) drawn with lines, centred like the text would be. */
    private fun drawSpaceMark(canvas: Canvas, p: GuideLayout.Primitive.Text, paint: Paint) {
        val w = p.size * 0.62f
        val h = p.size * 0.22f
        val cx = when (p.align) {
            GuideLayout.Align.LEFT -> p.x + w / 2f
            GuideLayout.Align.RIGHT -> p.x - w / 2f
            GuideLayout.Align.CENTER -> p.x
        }
        val top = p.y + p.size * 0.05f
        markPaint.color = paint.color
        markPaint.strokeWidth = maxOf(1.5f, p.size * 0.08f)
        val l = cx - w / 2f
        val r = cx + w / 2f
        canvas.drawLine(l, top, l, top + h, markPaint)
        canvas.drawLine(l, top + h, r, top + h, markPaint)
        canvas.drawLine(r, top + h, r, top, markPaint)
    }

    companion object {
        const val RING_COLOR = 0xFF2E3135.toInt()
        /** v15: the opacity slider (the mock's colours). */
        const val SLIDER_TRACK_COLOR = 0xFF464A52.toInt()
        const val SLIDER_ACCENT_COLOR = 0xFF8AB4F8.toInt()
        const val SLIDER_KNOB_COLOR = 0xFFECEDF0.toInt()
        const val SLIDER_SHADOW_COLOR = 0x66000000
        const val SLIDER_ICON_COLOR = 0xFFC8CBD2.toInt()
        /** The grid's centre disc, and (amendment 17) the empty cells of the や/わ layer. */
        const val CIRCLE_COLOR = 0xFF2B2E33.toInt()
        const val DOT_COLOR = 0xFF6E7176.toInt()
        /** Amendment 16: characters of the pentagon / cross while the や・わ layer is up (the v5 R2 mock: 90, 92, 99). */
        const val FAINT_COLOR = 0xFF5A5C63.toInt()

        /** Stroke width of the pentagon's ring (amendment 15): 2 px at the 157 px ring of the 1280 × 385 map, never under 1.5 px. */
        fun ringStroke(ringDiameter: Float): Float = maxOf(1.5f, ringDiameter / 78.5f)

        /** Amendment 18: the touch digits' outline, the mock's ring colour (#3a3d42), a little lighter than the ring. */
        const val DIGIT_OUTLINE_COLOR = 0xFF3A3D42.toInt()

        /** Amendment 18: the digit outline, [GuideMap.Kana.DIGIT_STROKE] (2 px) at the 64 px cell of the 1280 × 385 map, never under 1.5 px. */
        fun digitStroke(cellWidth: Float): Float = maxOf(1.5f, GuideMap.Kana.DIGIT_STROKE * cellWidth / GuideMap.Kana.DIGIT_W)
    }
}
