package com.kazumaproject.pad_input

import com.kazumaproject.pad_input.GuideLayout.Align
import com.kazumaproject.pad_input.GuideLayout.BoxStyle
import com.kazumaproject.pad_input.GuideLayout.GuideCanvas
import com.kazumaproject.pad_input.GuideLayout.Primitive
import com.kazumaproject.pad_input.GuideLayout.Result
import com.kazumaproject.pad_input.GuideLayout.TextStyle
import kotlin.math.min

/**
 * The controller-map guide (amendments 5, 9 and 10): the full-size guide that replaces the
 * touch keys while the controller is in use. Laid out as the device is held, with **nothing but
 * button names, short function labels and the characters**:
 *
 * - left column, top to bottom: L2 and L1 pills (stacked, so it does not matter which shoulder
 *   is outer on the device), the D-pad centred in the gap, the 3×3 grid, the bar
 *   (「L3 わをん 記号」, the tenkey's bottom row as one wide key);
 * - bottom middle: the Select and Start pills side by side; the rest of the middle stays empty;
 * - right column: R2 and R1 pills, the face buttons in their printed positions centred in the
 *   gap with their function beside each, the flick cross aligned row for row with the grid.
 *   While the bar is active the cross becomes the bar's 3×3 star and the grid is dimmed.
 * - かな with sticks (amendment 16, v5) has its own placement ([Kana]): no D-pad, no bar, the 3×3
 *   centre-left and the pentagon next to it. Amendment 18 (v7): 英字 and 数字 with sticks draw
 *   with it too (one layout for every text mode), with the touch digits in the left column, the
 *   L3 / R3 pills at the bottom, Select | Start at the top centre and bigger face buttons. This
 *   left-column layout stays for the emoji wheel, the emoji list and the D-pad fallback.
 *
 * No mode name, no preview, no captions, no sentences. Labels come from the live roles
 * ([PadGuideState.labels]); a role set to NONE shows just the dimmed name. Designed at
 * 1280 × 385 px (the RG477V's key area, `2026-11-01_guide_mock_L3bar_*.png`); other canvases
 * scale the whole map evenly.
 */
object GuideMap {
    const val BASE_W = 1280f
    const val BASE_H = 385f

    // Base sizes (px at 1280 × 385), from the mock-ups.
    const val GRID_CHAR = 26f
    const val PETAL_CHAR = 18f
    const val CROSS_CHAR = 28f
    const val FUNC = 17f
    const val NAME = 13f

    /** Positions at 1280 × 385 (amendment 10 mock-ups), exposed for the spacing test. */
    object Geometry {
        const val PILL_W = 172f
        const val PILL_H = 37f
        const val LEFT_PILL_X = 80f
        const val RIGHT_PILL_X = 1030f
        const val PILL1_Y = 6f
        const val PILL2_Y = 46f
        const val PILL_BOTTOM = PILL2_Y + PILL_H
        const val DPAD_CX = 166f
        const val DPAD_CY = 148.5f
        const val DPAD_ARM = 85f
        const val DPAD_THICK = 28f
        const val DPAD_TOP = DPAD_CY - DPAD_ARM / 2f
        const val DPAD_BOTTOM = DPAD_CY + DPAD_ARM / 2f
        const val DPAD_WORD_X = 220f
        const val GRID_X = 45f
        const val GRID_TOP = 213f
        const val CELL_W = 78f
        const val CELL_H = 38f
        const val STEP_X = 82f
        const val STEP_Y = 41f
        const val BAR_Y = 336f
        const val BAR_W = 2 * STEP_X + CELL_W
        const val FACE_CX = 1115f
        const val FACE_CY = 147f
        const val FACE_OFF = 30f
        const val FACE_R = 15f
        const val FACE_TOP = FACE_CY - FACE_OFF - FACE_R
        const val FACE_BOTTOM = FACE_CY + FACE_OFF + FACE_R
        const val CROSS_CX = 1115f
        const val CROSS_W = 70f
        const val CROSS_STEP_X = 72f
        const val CROSS_TOP = GRID_TOP
        const val CIRCLE_R = 21f
        const val SELECT_X = 460f
        const val START_X = 650f
        /** Neighbouring elements keep at least this much apart (the user's feedback on the drafts). */
        const val MIN_GAP = 18f
        /** The middle column above the Select / Start line holds nothing. */
        const val MIDDLE_LEFT = 400f
        const val MIDDLE_RIGHT = 880f
    }

    /**
     * Amendment 16, the かな guide with sticks (v5). Placement of the user's v4 guide
     * (`2026-11-01_rg477v_v4_guide_user.png`, measured): shoulder pills at the far left and right,
     * the 3×3 centre-left (x 369–632, y 59 to the old L3 bar's bottom at 293), the pentagon next to
     * it (centre 819, 181; ring radius 112; round cells of radius 30 at 81 px; dot radius 5), ABXY
     * under the right pills, Select | Start at the bottom; no D-pad and no L3 bar. Contents of the
     * v5 mocks (`2026-11-01_guide_mock_kana_v5_*.png`): 32 px kana, 「記号」 22 px, the centre あ a
     * disc. The grid takes the height the L3 bar left: three rows of 74 px, 6 px apart.
     */
    object Kana {
        const val GRID_X = 369f
        const val CELL_W = 84f
        const val STEP_X = 89.5f
        const val GRID_TOP = 59f
        const val CELL_H = 74f
        const val STEP_Y = 80f
        const val GRID_RIGHT = GRID_X + 2 * STEP_X + CELL_W
        const val GRID_BOTTOM = GRID_TOP + 2 * STEP_Y + CELL_H
        /** The centre (あ) key is a disc, the size of a pentagon cell. */
        const val CENTRE_D = 60f
        const val CHAR = 32f
        const val SYMBOL_CHAR = 22f
        const val PENT_CX = 819f
        const val PENT_CY = 181f
        const val RING_R = 112f
        const val RING_STROKE = 2f
        const val REACH = 81f
        const val CELL_R = 30f
        const val DOT_R = 5f
        const val RING_LEFT = PENT_CX - RING_R - RING_STROKE / 2f
        const val RING_RIGHT = PENT_CX + RING_R + RING_STROKE / 2f
        const val RING_TOP = PENT_CY - RING_R - RING_STROKE / 2f
        const val RING_BOTTOM = PENT_CY + RING_R + RING_STROKE / 2f
        /** Amendment 18: the 4-character labels of 英字 / 数字 (bold, fitted into the cell). */
        const val FOUR_CHAR = 22f

        // ---- amendment 18 (v7), `2026-11-02_guide_mock_v7.py`
        /** Select | Start at the top centre, 140 px wide, on the L2 / R2 pills' row. */
        const val SELECT_X = 546f
        const val START_X = 696f
        const val MID_PILL_W = 140f
        /** The L3 pill centred under the grid, the R3 pill under the ring, on the old Select / Start row. */
        const val L3_CX = (GRID_X + GRID_RIGHT) / 2f
        const val R3_CX = PENT_CX
        /** Face buttons: radius 30, 64 px from the cluster centre; letters 20 px, role words 24 px bold. */
        const val FACE_CX = 1113f
        const val FACE_CY = 234f
        const val FACE_OFF = 64f
        const val FACE_R = 30f
        const val FACE_LETTER = 20f
        const val FACE_WORD = 24f
        /** X's word above it and B's below: word centre this far from the circle's edge (the mock's 22). */
        const val FACE_WORD_DY = 22f
        /** Y's word to the left and A's to the right: this far from the circle's edge. */
        const val FACE_WORD_DX = 8f
        const val FACE_TOP = FACE_CY - FACE_OFF - FACE_R
        const val FACE_LEFT = FACE_CX - FACE_OFF - FACE_R
        const val FACE_RIGHT = FACE_CX + FACE_OFF + FACE_R
        /** The touch digits (1–9, 0 under 8): cells 64 × 62, 8 apart, columns centred on x 166. */
        const val DIGIT_W = 64f
        const val DIGIT_H = 62f
        const val DIGIT_GAP = 8f
        const val DIGIT_CX = 166f
        const val DIGIT_X = DIGIT_CX - (3 * DIGIT_W + 2 * DIGIT_GAP) / 2f
        const val DIGIT_TOP = 99f
        const val DIGIT_ZERO_Y = 309f
        const val DIGIT_CHAR = 28f
        const val DIGIT_RADIUS = 10f
        /** The digits' outline: 2 px in the ring's colour at 1280 × 385. */
        const val DIGIT_STROKE = 2f
    }

    /** One touch digit cell, in canvas pixels (amendment 18). */
    data class DigitCell(val digit: Char, val x: Float, val y: Float, val w: Float, val h: Float) {
        val centreX: Float get() = x + w / 2f
        val centreY: Float get() = y + h / 2f
        fun contains(px: Float, py: Float): Boolean = px >= x && px < x + w && py >= y && py < y + h
    }

    /** The digit cells at 1280 × 385 (base coordinates): 1–9 as a phone pad, 0 under 8. */
    private val DIGIT_BASE: List<DigitCell> = buildList {
        val k = Kana
        for (i in 0 until 9) {
            add(DigitCell('1' + i, k.DIGIT_X + (i % 3) * (k.DIGIT_W + k.DIGIT_GAP), k.DIGIT_TOP + (i / 3) * (k.DIGIT_H + k.DIGIT_GAP), k.DIGIT_W, k.DIGIT_H))
        }
        add(DigitCell('0', k.DIGIT_X + (k.DIGIT_W + k.DIGIT_GAP), k.DIGIT_ZERO_Y, k.DIGIT_W, k.DIGIT_H))
    }

    /** Scale and offset of the map in a canvas: the 1280 × 385 design scaled evenly and centred. */
    private fun frame(c: GuideCanvas): Triple<Float, Float, Float> {
        val k = min(c.width / BASE_W, c.height / BASE_H)
        return Triple(k, (c.width - BASE_W * k) / 2f, (c.height - BASE_H * k) / 2f)
    }

    /**
     * Amendment 18: the touch digit cells in canvas pixels, exactly the rectangles [layout] draws
     * (at every scale), for the view's hit test.
     */
    fun digitCells(c: GuideCanvas): List<DigitCell> {
        val (k, ox, oy) = frame(c)
        return DIGIT_BASE.map { DigitCell(it.digit, ox + it.x * k, oy + it.y * k, it.w * k, it.h * k) }
    }

    /** The digit under a touch at ([x], [y]) in canvas pixels, or null (between cells, or a state without digits). */
    fun digitAt(s: PadGuideState, c: GuideCanvas, x: Float, y: Float): Char? =
        if (!s.kanaLayout) null else digitCells(c).firstOrNull { it.contains(x, y) }?.digit

    /**
     * TiltBoy v15 (user, 2026-11-04): a vertical slider in the free column left of the touch
     * digits sets the guide's opacity, so the text behind it can be read. Top = opaque, bottom =
     * [MIN] (never invisible). Touch only, like the digits; the slider itself is always drawn
     * opaque. Base px at 1280 × 385 (the column is x 0–62 there, the digits start at 62).
     */
    object Opacity {
        const val MIN = 0.2f
        const val MIN_PERCENT = 20
        const val ICON_CY = 40f
        const val ICON_R = 11f
        const val TRACK_TOP = 100f
        const val TRACK_BOTTOM = 350f
        const val TRACK_W = 6f
        const val KNOB_R = 17f

        fun clamp(v: Float): Float = if (v.isNaN()) 1f else v.coerceIn(MIN, 1f)
        fun toPercent(v: Float): Int = Math.round(clamp(v) * 100f).coerceIn(MIN_PERCENT, 100)
        fun fromPercent(p: Int): Float = p.coerceIn(MIN_PERCENT, 100) / 100f
    }

    /** The opacity slider in canvas pixels. The touch zone is the whole column left of [zoneRight]. */
    data class OpacitySlider(
        val cx: Float, val zoneRight: Float, val iconCy: Float, val iconR: Float,
        val trackTop: Float, val trackBottom: Float, val trackW: Float, val knobR: Float,
    ) {
        fun knobY(opacity: Float): Float =
            trackBottom - (trackBottom - trackTop) * (Opacity.clamp(opacity) - Opacity.MIN) / (1f - Opacity.MIN)
        fun opacityAt(y: Float): Float =
            Opacity.clamp(Opacity.MIN + (1f - Opacity.MIN) * (trackBottom - y) / (trackBottom - trackTop))
        fun inZone(x: Float, y: Float, height: Float): Boolean = x >= 0f && x < zoneRight && y >= 0f && y < height
    }

    /**
     * The slider at this canvas: centred in the column between the left edge and the digits
     * minus the minimum gap; the touch zone reaches half that gap closer to the digits.
     */
    fun opacitySlider(c: GuideCanvas): OpacitySlider {
        val (k, ox, oy) = frame(c)
        val digitsLeft = ox + Kana.DIGIT_X * k
        val drawRight = digitsLeft - Geometry.MIN_GAP * k
        val o = Opacity
        return OpacitySlider(
            cx = drawRight / 2f,
            zoneRight = digitsLeft - Geometry.MIN_GAP * k / 2f,
            iconCy = oy + o.ICON_CY * k, iconR = o.ICON_R * k,
            trackTop = oy + o.TRACK_TOP * k, trackBottom = oy + o.TRACK_BOTTOM * k,
            trackW = o.TRACK_W * k, knobR = o.KNOB_R * k,
        )
    }

    private class Painter(val out: MutableList<Primitive>, val k: Float, val ox: Float, val oy: Float) {
        fun x(v: Float) = ox + v * k
        fun y(v: Float) = oy + v * k
        fun s(v: Float) = v * k
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float, style: BoxStyle) {
            out += Primitive.Box(x(x), y(y), s(w), s(h), s(r), style)
        }
        fun text(x: Float, y: Float, size: Float, t: String, style: TextStyle, align: Align = Align.CENTER, petal: Int = -1, ink: Boolean = false) {
            if (t.isEmpty()) return
            out += Primitive.Text(x(x), y(y), s(size), t, style, petal, align, ink)
        }
        fun fit(t: String, size: Float, maxW: Float): Float {
            val w = GuideLayout.textWidth(t, size)
            return if (w <= maxW) size else size * maxW / w
        }
    }

    fun layout(s: PadGuideState, c: GuideCanvas): Result {
        val g = Geometry
        val out = ArrayList<Primitive>()
        val (k, ox, oy) = frame(c)
        val p = Painter(out, k, ox, oy)
        out += Primitive.Box(0f, 0f, c.width, c.height, 0f, BoxStyle.BACKGROUND)
        // Amendment 18: every text mode with sticks draws the かな layout.
        if (s.kanaLayout) {
            kanaLayout(s, p)
            return Result(out)
        }
        val lb = s.labels
        val held = s.heldButtons
        val list = s.padMode == PadMode.EMOJI_LIST
        fun func(b: PadButton) = lb.buttons[b] ?: GuideLabels.NONE
        val centreChar = when {
            !s.isTenkey -> s.selectedPetal?.let { s.petals?.getOrNull(it)?.getOrNull(Daisywheel.POS_LEFT) }.orEmpty()
            s.barActive -> s.barChars?.get(Sector9.CENTER).orEmpty()
            else -> s.flickChars?.center.orEmpty()
        }
        /** A TAP_CENTER button shows the character it types, like the cross centre. */
        fun shown(b: PadButton): String {
            val f = func(b)
            return if (b in lb.tapButtons && f != GuideLabels.NONE && centreChar.isNotEmpty() && !list) centreChar else f
        }

        /** Pill: printed name at the left (small, muted), function at the right. NONE: dimmed name only. */
        fun pill(x: Float, y: Float, name: String, b: PadButton) {
            val f = shown(b)
            val isHeld = b in held
            val none = f == GuideLabels.NONE
            p.box(x, y, g.PILL_W, g.PILL_H, 8f, if (isHeld) BoxStyle.PILL_HELD else BoxStyle.PILL)
            p.text(x + 15f, y + g.PILL_H / 2f, NAME, name, if (isHeld) TextStyle.SELECTED else if (none) TextStyle.DIM else TextStyle.LABEL, Align.LEFT)
            if (!none) {
                val room = g.PILL_W - 30f - GuideLayout.textWidth(name, NAME) - 8f
                p.text(x + g.PILL_W - 15f, y + g.PILL_H / 2f, p.fit(f, FUNC, room), f, if (isHeld) TextStyle.SELECTED else TextStyle.FUNC, Align.RIGHT)
            }
        }

        // ---- shoulders stacked: L2 over L1, R2 over R1; Select / Start side by side at the bottom middle
        pill(g.LEFT_PILL_X, g.PILL1_Y, "L2", PadButton.L2)
        pill(g.LEFT_PILL_X, g.PILL2_Y, "L1", PadButton.L1)
        pill(g.RIGHT_PILL_X, g.PILL1_Y, "R2", PadButton.R2)
        pill(g.RIGHT_PILL_X, g.PILL2_Y, "R1", PadButton.R1)
        pill(g.SELECT_X, g.BAR_Y, "Select", PadButton.SELECT)
        pill(g.START_X, g.BAR_Y, "Start", PadButton.START)

        // ---- D-pad centred between the L1 pill and the grid, with its one word (not in the かな layout)
        val dpadHeld = held.any { it.isDpad }
        val dStyle = if (dpadHeld) BoxStyle.PILL_HELD else BoxStyle.CELL
        p.box(g.DPAD_CX - g.DPAD_THICK / 2f, g.DPAD_TOP, g.DPAD_THICK, g.DPAD_ARM, 4f, dStyle)
        p.box(g.DPAD_CX - g.DPAD_ARM / 2f, g.DPAD_CY - g.DPAD_THICK / 2f, g.DPAD_ARM, g.DPAD_THICK, 4f, dStyle)
        if (lb.dpad.isNotEmpty() && lb.dpad != GuideLabels.NONE) p.text(g.DPAD_WORD_X, g.DPAD_CY, FUNC, lb.dpad, TextStyle.FUNC, Align.LEFT)

        // ---- face buttons at their printed positions, centred between R1 and the cross, function beside each
        fun face(pos: FacePosition, cx: Float, cy: Float, labelRight: Boolean) {
            val b = s.faceLayout.buttonAt(pos)
            val f = shown(b)
            val isHeld = b in held
            val none = f == GuideLabels.NONE
            p.box(cx - g.FACE_R, cy - g.FACE_R, 2 * g.FACE_R, 2 * g.FACE_R, g.FACE_R, if (isHeld) BoxStyle.FACE_HELD else BoxStyle.FACE)
            p.text(cx, cy, NAME, b.name, if (isHeld) TextStyle.SELECTED else if (none) TextStyle.DIM else TextStyle.LABEL)
            if (!none) {
                if (labelRight) p.text(cx + g.FACE_R + 9f, cy, p.fit(f, FUNC, 150f), f, TextStyle.FUNC, Align.LEFT)
                else p.text(cx - g.FACE_R - 9f, cy, p.fit(f, FUNC, 150f), f, TextStyle.FUNC, Align.RIGHT)
            }
        }
        face(FacePosition.TOP, g.FACE_CX, g.FACE_CY - g.FACE_OFF, labelRight = true)
        face(FacePosition.LEFT, g.FACE_CX - g.FACE_OFF, g.FACE_CY, labelRight = false)
        face(FacePosition.RIGHT, g.FACE_CX + g.FACE_OFF, g.FACE_CY, labelRight = true)
        face(FacePosition.BOTTOM, g.FACE_CX, g.FACE_CY + g.FACE_OFF, labelRight = true)

        // The emoji list is driven by the D-pad alone: no grid, no bar, no cross.
        if (list) return Result(out)

        // ---- left stick: the 3×3 grid, centre as a circle; dimmed while the bar is active
        val gx = g.GRID_X; val gy = g.GRID_TOP; val cw = g.CELL_W; val ch = g.CELL_H
        val circle = ch + 6f
        if (s.isTenkey) {
            val keys = s.gridKeys.orEmpty()
            for (i in 0 until 9) {
                val col = i % 3; val row = i / 3
                val x0 = gx + col * g.STEP_X; val y0 = gy + row * g.STEP_Y
                val key = keys.getOrNull(i) ?: continue
                val selected = !s.barActive && key == s.selectedKey && (i == s.selectedSector.index || s.fallback)
                val label = s.gridLabels.getOrNull(i) ?: KanaGrid.label(key, s.padMode)
                val dim = s.gridDim.getOrNull(i) ?: false
                if (i == 4) {
                    // The centre key as a circle; 英字 / 数字 cells hold four characters, so a pill of the cell's width.
                    if (label.length > 2) p.box(x0, y0, cw, ch, ch / 2f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CIRCLE)
                    else p.box(x0 + (cw - circle) / 2f, y0 - 3f, circle, circle, circle / 2f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CIRCLE)
                }
                else p.box(x0, y0, cw, ch, 8f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
                val style = if (selected) TextStyle.SELECTED else if (dim) TextStyle.DIM else TextStyle.NORMAL
                p.text(x0 + cw / 2f, y0 + ch / 2f, p.fit(label, GRID_CHAR, cw - 8f), label, style)
            }
        } else {
            val petals = s.petals.orEmpty()
            for (i in 0 until 9) {
                val col = i % 3; val row = i / 3
                val x0 = gx + col * g.STEP_X; val y0 = gy + row * g.STEP_Y
                if (i == 4) { p.box(x0 + (cw - circle) / 2f, y0 - 3f, circle, circle, circle / 2f, BoxStyle.CIRCLE); continue }
                val petal = Sector9.fromIndex(i).petal ?: continue
                val selected = petal == s.selectedPetal
                p.box(x0, y0, cw, ch, 8f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
                val label = petals.getOrNull(petal)?.joinToString("") ?: ""
                p.text(x0 + cw / 2f, y0 + ch / 2f, p.fit(label, PETAL_CHAR, cw - 6f), label, if (selected) TextStyle.SELECTED else TextStyle.NORMAL, petal = petal)
            }
        }

        // ---- the bar under the grid: button name left, its word in the middle; lit while active
        if (s.barButtons.isNotEmpty()) {
            val lit = s.barActive
            p.box(gx, g.BAR_Y, g.BAR_W, ch, 8f, if (lit) BoxStyle.PILL_HELD else BoxStyle.PILL)
            val nameW = GuideLayout.textWidth(s.barButtons, NAME)
            p.text(gx + 15f, g.BAR_Y + ch / 2f, NAME, s.barButtons, if (lit) TextStyle.SELECTED else TextStyle.LABEL, Align.LEFT)
            val labelLeft = gx + 15f + nameW + 10f
            p.text((labelLeft + gx + g.BAR_W - 10f) / 2f, g.BAR_Y + ch / 2f, p.fit(s.barLabel, FUNC, gx + g.BAR_W - 10f - labelLeft), s.barLabel, if (lit) TextStyle.SELECTED else TextStyle.FUNC)
        }

        // ---- right stick: the flick cross on the grid's rows; the bar's 3×3 star while the bar is active
        val rcx = g.CROSS_CX; val rw = g.CROSS_W
        val rowY = floatArrayOf(gy + ch / 2f, gy + g.STEP_Y + ch / 2f, gy + 2 * g.STEP_Y + ch / 2f)
        fun crossCell(cx: Float, cy: Float, t: String, hot: Boolean) {
            p.box(cx - rw / 2f, cy - ch / 2f, rw, ch, 8f, if (hot) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
            p.text(cx, cy, p.fit(t, CROSS_CHAR, rw - 8f), t, if (hot) TextStyle.SELECTED else TextStyle.NORMAL)
        }
        val centreHeld = held.any { it in lb.tapButtons && func(it) != GuideLabels.NONE }
        if (s.isTenkey && s.barActive) {
            val bc = s.barChars.orEmpty()
            for ((sector, t) in bc) {
                if (sector == Sector9.CENTER) continue
                crossCell(rcx + (sector.col - 1) * g.CROSS_STEP_X, rowY[sector.row], t, s.pendingBarFlick == sector)
            }
        } else {
            val up: String; val left: String; val right: String; val down: String
            if (s.isTenkey) {
                val fc = s.flickChars
                left = fc?.left.orEmpty(); up = fc?.up.orEmpty(); right = fc?.right.orEmpty(); down = fc?.down.orEmpty()
            } else {
                val petal = s.selectedPetal?.let { s.petals?.getOrNull(it) }
                left = petal?.getOrNull(Daisywheel.POS_LEFT).orEmpty()
                up = petal?.getOrNull(Daisywheel.POS_TOP).orEmpty()
                right = petal?.getOrNull(Daisywheel.POS_RIGHT).orEmpty()
                down = petal?.getOrNull(Daisywheel.POS_BOTTOM).orEmpty()
            }
            val pending = s.pendingFlick
            crossCell(rcx, rowY[0], up, pending == Dir4.UP)
            crossCell(rcx - g.CROSS_STEP_X, rowY[1], left, pending == Dir4.LEFT)
            crossCell(rcx + g.CROSS_STEP_X, rowY[1], right, pending == Dir4.RIGHT)
            crossCell(rcx, rowY[2], down, pending == Dir4.DOWN)
        }
        val r = g.CIRCLE_R
        p.box(rcx - r, rowY[1] - r, 2 * r, 2 * r, r, if (centreHeld) BoxStyle.CELL_SELECTED else BoxStyle.CIRCLE)
        p.text(rcx, rowY[1], p.fit(centreChar, CROSS_CHAR, 2 * r - 4f), centreChar, if (centreHeld) TextStyle.SELECTED else TextStyle.NORMAL)
        return Result(out)
    }

    /**
     * Amendment 16: the かな grid and the right stick with sticks, at [Kana]'s placement; amendment
     * 18 (v7): every text mode with sticks (英字 / 数字 too), plus the touch digits, the L3 / R3
     * pills, Select | Start at the top centre and the bigger face buttons.
     *
     * The grid is the mode's 3×3 (centre key a disc; かな 「や/わ」 smaller (amendment 21); 英字 / 数字 four
     * characters per cell, bold, fitted, centred by ink), or the R2 layer while it is held (empty
     * dimmed cells for the empty slots, a dot in the centre one, the cell the left flick is aimed at
     * highlighted). The right stick is the ring next to it: かな the pentagon (five cells; the や/わ key
     * six, amendment 21), 英字 /
     * 数字 four cells at ← ↑ → ↓; a dot in the centre, no centre character; faint while the layer is
     * up. The highlight of a pending flick follows the stick (amendment 17: re-aim).
     */
    private fun kanaLayout(s: PadGuideState, p: Painter) {
        val g = Geometry
        val k = Kana
        val lb = s.labels
        val held = s.heldButtons
        fun func(b: PadButton) = lb.buttons[b] ?: GuideLabels.NONE

        /** Pill: printed name at the left (small, muted), function at the right. NONE: an empty, dimmed pill with the name. */
        fun pill(x: Float, y: Float, w: Float, name: String, b: PadButton) {
            val f = func(b)
            val isHeld = b in held
            val none = f == GuideLabels.NONE
            p.box(x, y, w, g.PILL_H, 8f, if (isHeld) BoxStyle.PILL_HELD else if (none) BoxStyle.PILL_EMPTY else BoxStyle.PILL)
            p.text(x + 15f, y + g.PILL_H / 2f, NAME, name, if (isHeld) TextStyle.SELECTED else if (none) TextStyle.DIM else TextStyle.LABEL, Align.LEFT)
            if (!none) {
                val room = w - 30f - GuideLayout.textWidth(name, NAME) - 8f
                p.text(x + w - 15f, y + g.PILL_H / 2f, p.fit(f, FUNC, room), f, if (isHeld) TextStyle.SELECTED else TextStyle.FUNC, Align.RIGHT)
            }
        }
        // ---- shoulders stacked at the far left and right; Select | Start at the top centre; L3 / R3 under the sticks
        pill(g.LEFT_PILL_X, g.PILL1_Y, g.PILL_W, "L2", PadButton.L2)
        pill(g.LEFT_PILL_X, g.PILL2_Y, g.PILL_W, "L1", PadButton.L1)
        pill(g.RIGHT_PILL_X, g.PILL1_Y, g.PILL_W, "R2", PadButton.R2)
        pill(g.RIGHT_PILL_X, g.PILL2_Y, g.PILL_W, "R1", PadButton.R1)
        pill(k.SELECT_X, g.PILL1_Y, k.MID_PILL_W, "Select", PadButton.SELECT)
        pill(k.START_X, g.PILL1_Y, k.MID_PILL_W, "Start", PadButton.START)
        pill(k.L3_CX - g.PILL_W / 2f, g.BAR_Y, g.PILL_W, "L3", PadButton.L3)
        pill(k.R3_CX - g.PILL_W / 2f, g.BAR_Y, g.PILL_W, "R3", PadButton.R3)

        // ---- face buttons (amendment 18: radius 30, 64 apart from the centre, 20 px letters, 24 px bold words)
        fun face(pos: FacePosition, dx: Int, dy: Int) {
            val b = s.faceLayout.buttonAt(pos)
            val f = func(b)
            val isHeld = b in held
            val none = f == GuideLabels.NONE
            val cx = k.FACE_CX + dx * k.FACE_OFF
            val cy = k.FACE_CY + dy * k.FACE_OFF
            val r = k.FACE_R
            p.box(cx - r, cy - r, 2 * r, 2 * r, r, if (isHeld) BoxStyle.FACE_HELD else BoxStyle.FACE)
            p.text(cx, cy, k.FACE_LETTER, b.name, if (isHeld) TextStyle.SELECTED else if (none) TextStyle.DIM else TextStyle.LABEL)
            if (none) return
            when {
                // Y's word to the left, clear of the ring by the minimum gap.
                dx < 0 -> {
                    val right = cx - r - k.FACE_WORD_DX
                    p.text(right, cy, p.fit(f, k.FACE_WORD, right - (k.RING_RIGHT + g.MIN_GAP)), f, TextStyle.NORMAL, Align.RIGHT)
                }
                // A's word to the right, inside the key area.
                dx > 0 -> {
                    val left = cx + r + k.FACE_WORD_DX
                    p.text(left, cy, p.fit(f, k.FACE_WORD, BASE_W - 6f - left), f, TextStyle.NORMAL, Align.LEFT)
                }
                // X's word above, B's below.
                else -> p.text(cx, cy + dy * (r + k.FACE_WORD_DY), p.fit(f, k.FACE_WORD, 150f), f, TextStyle.NORMAL)
            }
        }
        face(FacePosition.TOP, 0, -1)
        face(FacePosition.LEFT, -1, 0)
        face(FacePosition.RIGHT, 1, 0)
        face(FacePosition.BOTTOM, 0, 1)

        // ---- the touch digits in the left column (outlined: the only touch targets on the guide)
        for (cell in DIGIT_BASE) {
            val touched = cell.digit == s.touchedDigit
            p.box(cell.x, cell.y, cell.w, cell.h, k.DIGIT_RADIUS, if (touched) BoxStyle.CELL_SELECTED else BoxStyle.DIGIT)
            p.text(cell.centreX, cell.centreY, k.DIGIT_CHAR, cell.digit.toString(), if (touched) TextStyle.SELECTED else TextStyle.NORMAL)
        }

        // ---- the 3×3 grid
        val keys = s.gridKeys.orEmpty()
        val four = s.isFourWay
        for (i in 0 until 9) {
            val col = i % 3; val row = i / 3
            val x0 = k.GRID_X + col * k.STEP_X; val y0 = k.GRID_TOP + row * k.STEP_Y
            val cx = x0 + k.CELL_W / 2f; val cy = y0 + k.CELL_H / 2f
            val label = s.gridLabels.getOrNull(i).orEmpty()
            val dim = s.gridDim.getOrNull(i) ?: false
            if (s.layer) {
                val hot = s.pendingLayer?.index == i
                if (label.isEmpty()) {
                    // Amendment 17: a slot that types nothing is an empty cell in the centre disc's
                    // dimmed colour; the centre keeps its small dot. Lit while the left stick is aimed at it.
                    p.box(x0, y0, k.CELL_W, k.CELL_H, 10f, if (hot) BoxStyle.CELL_SELECTED else BoxStyle.CELL_EMPTY)
                    if (i == 4) p.box(cx - k.DOT_R, cy - k.DOT_R, 2 * k.DOT_R, 2 * k.DOT_R, k.DOT_R, BoxStyle.DOT)
                    continue
                }
                p.box(x0, y0, k.CELL_W, k.CELL_H, 10f, if (hot) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
                p.text(cx, cy, p.fit(label, k.CHAR, k.CELL_W - 10f), label, if (hot) TextStyle.SELECTED else if (dim) TextStyle.DIM else TextStyle.NORMAL)
                continue
            }
            val key = keys.getOrNull(i) ?: continue
            val selected = key == s.selectedKey && i == s.selectedSector.index
            if (i == 4) {
                val d = k.CENTRE_D
                p.box(cx - d / 2f, cy - d / 2f, d, d, d / 2f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CIRCLE)
            } else {
                p.box(x0, y0, k.CELL_W, k.CELL_H, 10f, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
            }
            // Amendment 21: the や/わ key's 「や/わ」 at the size the v5–v8 「記号」 label had (22 px).
            val yaWa = !four && key == KanaGrid.YA_WA_KEY
            val size = when {
                four -> k.FOUR_CHAR
                yaWa -> k.SYMBOL_CHAR
                else -> k.CHAR
            }
            val room = if (i == 4) k.CENTRE_D - 6f else k.CELL_W - 10f
            p.text(cx, cy, p.fit(label, size, room), label, if (selected) TextStyle.SELECTED else if (dim) TextStyle.DIM else TextStyle.NORMAL, ink = four || yaWa)
        }

        // ---- the right stick, next to the grid: the ring, its dot, and the round cells
        val fc = s.flickChars
        val faint = s.layer
        p.box(k.PENT_CX - k.RING_R, k.PENT_CY - k.RING_R, 2 * k.RING_R, 2 * k.RING_R, k.RING_R, BoxStyle.RING)
        p.box(k.PENT_CX - k.DOT_R, k.PENT_CY - k.DOT_R, 2 * k.DOT_R, 2 * k.DOT_R, k.DOT_R, BoxStyle.DOT)
        val cr = k.CELL_R
        fun ringCell(dx: Float, dy: Float, t: String, hot: Boolean, dim: Boolean) {
            val x = k.PENT_CX + dx * k.REACH
            val y = k.PENT_CY + dy * k.REACH
            val lit = hot && !faint
            p.box(x - cr, y - cr, 2 * cr, 2 * cr, cr, if (lit) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
            p.text(x, y, p.fit(t, k.CHAR, 2 * cr - 6f), t, when {
                faint -> TextStyle.FAINT
                lit -> TextStyle.SELECTED
                dim -> TextStyle.DIM
                else -> TextStyle.NORMAL
            })
        }
        // Amendment 21: the や/わ key, six round cells at 90° 30° −30° −90° −150° 150°, the pentagon's
        // reach and cell radius (neighbours 81 px apart centre to centre, 21 px clear at 1280 × 385).
        if (s.hexagon) {
            for (slot in 0 until KanaHexagon.SLOTS) {
                val (dx, dy) = KanaHexagon.offset(slot)
                ringCell(dx, dy, s.hexagonChars.getOrNull(slot).orEmpty(), slot == s.pendingSlot, s.flickDim.getOrNull(slot) == true)
            }
            return
        }
        if (s.pentagon) {
            for (slot in 0 until KanaPentagon.SLOTS) {
                val (dx, dy) = KanaPentagon.offset(slot)
                ringCell(dx, dy, fc?.slot(slot).orEmpty(), slot == s.pendingSlot, s.flickDim.getOrNull(slot) == true)
            }
            return
        }
        // Amendment 18, 英字 / 数字: the cell's four characters at ← ↑ → ↓ (slots 1–4), no centre.
        val pending = s.pendingFlick
        ringCell(-1f, 0f, fc?.left.orEmpty(), pending == Dir4.LEFT, s.flickDim.getOrNull(1) == true)
        ringCell(0f, -1f, fc?.up.orEmpty(), pending == Dir4.UP, s.flickDim.getOrNull(2) == true)
        ringCell(1f, 0f, fc?.right.orEmpty(), pending == Dir4.RIGHT, s.flickDim.getOrNull(3) == true)
        ringCell(0f, 1f, fc?.down.orEmpty(), pending == Dir4.DOWN, s.flickDim.getOrNull(4) == true)
    }
}
