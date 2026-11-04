package com.kazumaproject.pad_input

import kotlin.math.max
import kotlin.math.min

/**
 * Geometry of the on-screen guide, as a list of drawing primitives. Pure Kotlin so the same
 * layout can be drawn by the Android view and rendered headless (PNG) in a JVM test, and so
 * tests can check sizes and spacing.
 *
 * Coordinates are pixels inside a canvas of [GuideCanvas.width] × [GuideCanvas.height].
 * Text `y` is the vertical centre of the glyph box; renderers derive the baseline.
 *
 * Tenkey modes (the v1 kana design, amendment 9 without header, captions or legend): 3×3 key
 * grid on the left, flick cross on the right (the bar's 3×3 star while the bar is active; the
 * five characters on a ring for the kana pentagon, amendment 15). In a wide canvas (diagnostics
 * band) both are centred.
 *
 * Emoji wheel (v2): the 8 petals as a 3×3 grid (same directions as the stick, centre cell
 * empty), each petal a separated rounded cell holding a small diamond of 4 characters; the
 * selected petal repeated enlarged on the right as a flick cross (← ↑ → ↓ = the right-stick
 * direction that types each character; in the D-pad fallback the printed button names instead);
 * then the button roles, names plus one word each.
 */
object GuideLayout {

    enum class BoxStyle {
        BACKGROUND, CELL, CELL_SELECTED, PETAL, PETAL_SELECTED, BIG_PETAL, HINT,
        /** Controller map (amendment 5): button pills, face-button circles, stick centre circles. */
        PILL, PILL_HELD, FACE, FACE_HELD, CIRCLE,
        /**
         * Amendment 15, the kana pentagon: a thin ring (stroked, not filled; the box is the circle's
         * bounding square, the stroke is centred on its edge) and the small dot in its centre.
         */
        RING, DOT,
        /**
         * Amendment 17: an empty slot of the や/わ layer (the middle row): a cell with no character,
         * in the dimmed colour of the grid's centre disc ([CIRCLE]).
         */
        CELL_EMPTY,
        /** Amendment 18: a pill whose button has nothing to do in the mode: empty, in the centre disc's dimmed colour. */
        PILL_EMPTY,
        /**
         * Amendment 18: a touch digit cell: outlined (stroked, not filled; 2 px at 1280 × 385, see
         * [GuideMap.Kana.DIGIT_STROKE]) in the ring's colour on the background.
         */
        DIGIT,
    }
    enum class TextStyle {
        NORMAL, SELECTED, DIM, ACCENT, LABEL, HEADER, HINT,
        /** Function label on the controller map: white, regular weight. */
        FUNC,
        /** Amendment 16: the pentagon / cross while the や・わ layer is up, barely above the cell colour. */
        FAINT,
    }
    enum class Align { CENTER, LEFT, RIGHT }

    sealed class Primitive {
        data class Box(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val style: BoxStyle) : Primitive()
        data class Text(
            /** Horizontal anchor: the centre for [Align.CENTER], the left edge for [Align.LEFT]. */
            val x: Float, val y: Float, val size: Float, val text: String, val style: TextStyle,
            /** Which petal (0..7) this text belongs to in the small wheel grid, else -1. Used by tests. */
            val petal: Int = -1,
            val align: Align = Align.CENTER,
            /** Amendment 18: centre this (multi-character) label by its ink box too, like a single character (英字 / 数字 cells). */
            val ink: Boolean = false,
        ) : Primitive()
    }

    data class GuideCanvas(val width: Float, val height: Float, val density: Float)

    /**
     * Amendment 17: a centred label of one character (amendment 18: or one marked [Primitive.Text.ink],
     * the 4-character cells of 英字 / 数字) is placed by its ink bounds, not by the font
     * metrics (。 and 、 sit low and left in their em box and looked off-centre on the v5 device).
     * Every renderer (PadGuideView with `Paint.getTextBounds`, the Java2D test render with the
     * glyph's visual bounds) measures the ink and asks [inkOrigin] where to draw.
     */
    fun inkCentred(t: Primitive.Text): Boolean =
        t.align == Align.CENTER && t.text.isNotEmpty() && (t.ink || t.text.codePointCount(0, t.text.length) == 1)

    /**
     * Amendment 19: glyphs whose ink is a horizontal stroke sitting low, like `_`. Ink-centred
     * vertically they read as a minus (v7 device check, 英字 R2 layer), so they are centred by
     * their ink horizontally only and keep the font baseline ([baselineOrigin]): `_`, the full-width
     * low line, the double low line and the modifier low macron.
     */
    val LOW_STROKES: Set<String> = setOf("_", "\uFF3F", "\u2017", "\u02CD")

    /** A centred label drawn with [baselineOrigin] instead of [inkOrigin] (see [LOW_STROKES]). */
    fun keepsBaseline(t: Primitive.Text): Boolean = inkCentred(t) && !t.ink && t.text in LOW_STROKES

    /**
     * Amendment 19: where to draw a [LOW_STROKES] glyph: its ink box ([left] … [right]) centred
     * horizontally on [cx]; the baseline where the font metrics put a centred line of text
     * ([ascent] above the baseline and [descent] below it, both positive), so the stroke sits low
     * in the cell as it does in text. Returns (x, baseline).
     */
    fun baselineOrigin(cx: Float, cy: Float, left: Float, right: Float, ascent: Float, descent: Float): Pair<Float, Float> =
        (cx - (left + right) / 2f) to (cy + (ascent - descent) / 2f)

    /**
     * Where to draw a left-aligned glyph whose ink box, relative to the drawing origin (x right,
     * y down, baseline at 0), is [left] … [right] × [top] … [bottom], so that the ink box's centre
     * lands on ([cx], [cy]). Returns (x, baseline).
     */
    fun inkOrigin(cx: Float, cy: Float, left: Float, top: Float, right: Float, bottom: Float): Pair<Float, Float> =
        (cx - (left + right) / 2f) to (cy - (top + bottom) / 2f)

    data class Result(val primitives: List<Primitive>) {
        val texts: List<Primitive.Text> get() = primitives.filterIsInstance<Primitive.Text>()
        val boxes: List<Primitive.Box> get() = primitives.filterIsInstance<Primitive.Box>()
    }

    /** Approximate drawn width of [text] at [size]: CJK glyphs and emoji 1 em, others 0.6 em. */
    fun textWidth(text: String, size: Float): Float {
        var w = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFE0F || cp == 0x200D) continue // variation selector / ZWJ draw nothing
            w += if (cp < 0x2E80) size * 0.6f else size
        }
        return w
    }

    /** Largest size ≤ [size] (but ≥ [minSize]) at which [text] fits in [maxWidth]. */
    private fun fitSize(text: String, size: Float, maxWidth: Float, minSize: Float): Float {
        val w = textWidth(text, size)
        return if (w <= maxWidth) size else max(minSize, size * maxWidth / w)
    }

    /** Amendment 16: the 記号 cell's word (amendment 21: 「や/わ」), relative to a grid character (22 / 32 px on the v5 mock). */
    const val SYMBOL_SCALE = 0.69f

    /**
     * [map] = the controller-map form (amendment 5) used by the full-size placement; otherwise
     * the compact form (corner overlay / diagnostics band).
     */
    fun layout(s: PadGuideState, c: GuideCanvas, map: Boolean = false): Result {
        if (map) return GuideMap.layout(s, c)
        val out = ArrayList<Primitive>()
        val w = c.width
        val h = c.height
        val pad = h * 0.04f
        out += Primitive.Box(0f, 0f, w, h, h * 0.10f, BoxStyle.BACKGROUND)

        // No header, captions or legend (amendment 9: nothing but characters and short labels).
        val top = pad * 1.5f
        val bottom = h - pad
        if (s.isTenkey) layoutKana(s, c, out, pad, top, w - pad, bottom) else layoutWheel(s, c, out, pad, top, w - pad, bottom)
        return Result(out)
    }

    // ------------------------------------------------------------------ kana (v1 design + legend)

    private fun layoutKana(s: PadGuideState, c: GuideCanvas, out: MutableList<Primitive>, left: Float, top: Float, right: Float, bottom: Float) {
        val areaW = right - left
        val areaH = bottom - top
        val gridSize = min(areaH, areaW * 0.48f)
        val cell = gridSize / 3f
        val gap = cell * 0.08f
        // In a wide canvas the grid and the flick cross sit together in the middle; in the small
        // corner box they use the full width as in v1.
        val spacing = gridSize * 0.35f
        val contentW = gridSize * 2f + spacing
        val centred = areaW > contentW
        val gx = if (centred) left + (areaW - contentW) / 2f else left
        val gy = top + (areaH - gridSize) / 2f
        val keys = s.gridKeys ?: return
        val textSize = cell * 0.5f
        fun cellAt(x: Float, y: Float, label: String, selected: Boolean, dim: Boolean, scale: Float) {
            out += Primitive.Box(x + gap, y + gap, cell - 2 * gap, cell - 2 * gap, gap * 2, if (selected) BoxStyle.CELL_SELECTED else BoxStyle.CELL)
            val size = fitSize(label, textSize * scale, cell - 3 * gap, 8f * c.density)
            out += Primitive.Text(x + cell / 2f, y + cell / 2f, size, label, if (selected) TextStyle.SELECTED else if (dim) TextStyle.DIM else TextStyle.NORMAL)
        }
        fun labelAt(i: Int) = s.gridLabels.getOrNull(i) ?: KanaGrid.label(keys[i], s.padMode)
        fun dimAt(i: Int) = s.gridDim.getOrNull(i) ?: false
        for (i in 0 until 9) {
            val x = gx + (i % 3) * cell
            val y = gy + (i / 3) * cell
            if (s.layer) {
                // Amendment 16, the や/わ layer: its characters, the cell the left stick is aimed at
                // highlighted. Amendment 17: the empty slots (the middle row) are empty dimmed cells;
                // the centre keeps its dot.
                val hot = s.pendingLayer?.index == i
                if (labelAt(i).isEmpty()) {
                    out += Primitive.Box(x + gap, y + gap, cell - 2 * gap, cell - 2 * gap, gap * 2, if (hot) BoxStyle.CELL_SELECTED else BoxStyle.CELL_EMPTY)
                    if (i == 4) {
                        val dotR = cell * 0.06f
                        out += Primitive.Box(x + cell / 2f - dotR, y + cell / 2f - dotR, 2 * dotR, 2 * dotR, dotR, BoxStyle.DOT)
                    }
                    continue
                }
                cellAt(x, y, labelAt(i), hot, dimAt(i), 1f)
                continue
            }
            // Amendment 16: the 記号 cell's word was drawn smaller than the kana; amendment 21: 「や/わ」 is.
            val scale = if (s.padMode == PadMode.KANA && keys[i] == KanaGrid.YA_WA_KEY) SYMBOL_SCALE else 1f
            cellAt(x, y, labelAt(i), !s.barActive && keys[i] == s.selectedKey, dimAt(i), scale)
        }
        val cx = if (centred) gx + gridSize + spacing + gridSize / 2f else right - gridSize / 2f
        val cy = gy + gridSize / 2f
        val step = cell * 0.95f
        /** Text style of the right side: faint while the や・わ layer is up (amendment 16). */
        fun rightStyle(accent: Boolean, dim: Boolean) = when {
            s.layer -> TextStyle.FAINT
            accent -> TextStyle.ACCENT
            dim -> TextStyle.DIM
            else -> TextStyle.NORMAL
        }
        if (s.barActive) {
            // The bar (amendment 10): its nine characters as a 3×3 star where the cross was.
            val bc = s.barChars ?: return
            for ((sector, label) in bc) {
                if (label.isEmpty()) continue
                val accent = sector == s.pendingBarFlick
                val size = fitSize(label, if (accent) cell * 0.55f else cell * 0.5f, cell * 0.9f, 8f * c.density)
                out += Primitive.Text(cx + (sector.col - 1) * step, cy + (sector.row - 1) * step, size, label, if (accent) TextStyle.ACCENT else TextStyle.NORMAL)
            }
            return
        }
        val fc = s.flickChars ?: return
        if (s.hexagon) {
            // Amendment 21: the や/わ key's six characters on the ring, や at the top, then clockwise.
            val ringR = step + cell * 0.42f
            out += Primitive.Box(cx - ringR, cy - ringR, 2 * ringR, 2 * ringR, ringR, BoxStyle.RING)
            val dotR = cell * 0.06f
            out += Primitive.Box(cx - dotR, cy - dotR, 2 * dotR, 2 * dotR, dotR, BoxStyle.DOT)
            for (slot in 0 until KanaHexagon.SLOTS) {
                val label = s.hexagonChars.getOrNull(slot).orEmpty()
                if (label.isEmpty()) continue
                val (dx, dy) = KanaHexagon.offset(slot)
                val accent = slot == s.pendingSlot
                val dim = s.flickDim.getOrNull(slot) == true
                val size = fitSize(label, if (accent) cell * 0.55f else cell * 0.5f, cell * 0.9f, 8f * c.density)
                out += Primitive.Text(cx + dx * step, cy + dy * step, size, label, rightStyle(accent, dim))
            }
            return
        }
        if (s.pentagon) {
            // Amendment 15: the five characters on a ring, あ段 at the top, then clockwise; a dot in
            // the centre and no centre character (R3 still types it).
            val ringR = step + cell * 0.42f
            out += Primitive.Box(cx - ringR, cy - ringR, 2 * ringR, 2 * ringR, ringR, BoxStyle.RING)
            val dotR = cell * 0.06f
            out += Primitive.Box(cx - dotR, cy - dotR, 2 * dotR, 2 * dotR, dotR, BoxStyle.DOT)
            for (slot in 0 until KanaPentagon.SLOTS) {
                val label = fc.slot(slot)
                if (label.isEmpty()) continue
                val (dx, dy) = KanaPentagon.offset(slot)
                val accent = slot == s.pendingSlot
                val dim = s.flickDim.getOrNull(slot) == true
                val size = fitSize(label, if (accent) cell * 0.55f else cell * 0.5f, cell * 0.9f, 8f * c.density)
                out += Primitive.Text(cx + dx * step, cy + dy * step, size, label, rightStyle(accent, dim))
            }
            return
        }
        fun put(label: String, dx: Int, dy: Int, dir: Dir4?) {
            if (label.isEmpty()) return
            val accent = dir != null && dir == s.pendingFlick
            val size = fitSize(label, if (accent) cell * 0.55f else cell * 0.5f, cell * 0.9f, 8f * c.density)
            out += Primitive.Text(cx + dx * step, cy + dy * step, size, label, rightStyle(accent, false))
        }
        put(fc.center, 0, 0, null)
        put(fc.left, -1, 0, Dir4.LEFT)
        put(fc.up, 0, -1, Dir4.UP)
        put(fc.right, 1, 0, Dir4.RIGHT)
        put(fc.down, 0, 1, Dir4.DOWN)
    }

    // ------------------------------------------------------------------ wheel (v2 design)

    /** Grid cell index (row-major 3×3) for petal 0..7; the centre (index 4) is empty. */
    private val PETAL_TO_CELL = intArrayOf(1, 2, 5, 8, 7, 6, 3, 0)

    /** Offsets of the four positions inside a petal diamond, as a fraction of the half-size. */
    private fun diamond(pos: FacePosition): Pair<Float, Float> = when (pos) {
        FacePosition.TOP -> 0f to -1f
        FacePosition.LEFT -> -1f to 0f
        FacePosition.RIGHT -> 1f to 0f
        FacePosition.BOTTOM -> 0f to 1f
    }

    private fun arrow(pos: FacePosition): String = when (pos) {
        FacePosition.TOP -> "↑"
        FacePosition.LEFT -> "←"
        FacePosition.RIGHT -> "→"
        FacePosition.BOTTOM -> "↓"
    }

    private fun positionOf(dir: Dir4): FacePosition = when (dir) {
        Dir4.UP -> FacePosition.TOP
        Dir4.LEFT -> FacePosition.LEFT
        Dir4.RIGHT -> FacePosition.RIGHT
        Dir4.DOWN -> FacePosition.BOTTOM
    }

    private fun layoutWheel(s: PadGuideState, c: GuideCanvas, out: MutableList<Primitive>, left: Float, top: Float, right: Float, bottom: Float) {
        val petals = s.petals ?: return
        val areaH = bottom - top
        val areaW = right - left
        val emoji = true
        // Small 3×3 petal grid: as tall as the band allows, never more than 42 % of the width.
        val gridSize = min(areaH, areaW * 0.42f)
        val cell = gridSize / 3f
        val gap = cell * 0.07f
        val gx = left
        val gy = top + (areaH - gridSize) / 2f
        // Diamond geometry: characters at `reach` from the cell centre. With reach = 0.25 cell the
        // four glyphs of one petal are 0.35 cell apart and 0.5 cell from the next petal's glyphs,
        // so each group reads as a group (GuideLayoutTest checks the 1.3× ratio).
        // 0.34 cell = 34 px in the full placement (cell 100 px), the kana guide's character size.
        val charSize = cell * (if (emoji) 0.30f else 0.34f)
        val reach = cell * 0.25f
        for (petal in 0 until 8) {
            val ci = PETAL_TO_CELL[petal]
            val x = gx + (ci % 3) * cell
            val y = gy + (ci / 3) * cell
            val selected = petal == s.selectedPetal
            out += Primitive.Box(x + gap, y + gap, cell - 2 * gap, cell - 2 * gap, gap * 2, if (selected) BoxStyle.PETAL_SELECTED else BoxStyle.PETAL)
            val cx = x + cell / 2f
            val cy = y + cell / 2f
            val cells = petals[petal]
            for (pos in FacePosition.entries) {
                val (dx, dy) = diamond(pos)
                val t = cells.getOrNull(pos.petalIndex).orEmpty()
                if (t.isEmpty()) continue
                out += Primitive.Text(cx + dx * reach, cy + dy * reach, charSize, t, if (selected) TextStyle.SELECTED else TextStyle.DIM, petal)
            }
        }

        // Enlarged selected petal to the right of the grid: the flick cross.
        val bigLeft = gx + gridSize + areaW * 0.04f
        val bigSize = min(areaH, right - bigLeft)
        val bx = bigLeft
        val by = top + (areaH - bigSize) / 2f
        out += Primitive.Box(bx, by, bigSize, bigSize, bigSize * 0.12f, BoxStyle.BIG_PETAL)
        val bcx = bx + bigSize / 2f
        val bcy = by + bigSize / 2f
        val selected = s.selectedPetal
        val bigChar = bigSize * (if (emoji) 0.22f else 0.24f)
        val labelSize = max(9f * c.density, bigSize * 0.11f)
        val r1 = bigSize * 0.27f  // characters
        val r2 = bigSize * 0.43f  // direction / button labels, further out on the same axis
        val pending = s.pendingFlick?.let { positionOf(it) }
        for (pos in FacePosition.entries) {
            val (dx, dy) = diamond(pos)
            val label = if (s.fallback) s.faceLayout.buttonAt(pos).name else arrow(pos)
            // Direction labels only make sense once a petal is selected (the hint sits in the middle otherwise).
            if (selected != null) out += Primitive.Text(bcx + dx * r2, bcy + dy * r2, labelSize, label, if (pending == pos) TextStyle.ACCENT else TextStyle.LABEL)
            if (selected != null) {
                val t = petals[selected].getOrNull(pos.petalIndex).orEmpty()
                val accent = pending == null || pending == pos
                if (t.isNotEmpty()) out += Primitive.Text(bcx + dx * r1, bcy + dy * r1, if (pending == pos) bigChar * 1.15f else bigChar, t, if (accent) TextStyle.ACCENT else TextStyle.NORMAL)
            }
        }
        // Remaining width: the button roles, names plus one word each (left-aligned, clear of the cross).
        val hintLeft = bx + bigSize + areaW * 0.04f
        if (right - hintLeft > bigSize * 0.6f) {
            val hs = max(9f * c.density, areaH * 0.11f)
            val lines = s.legend.chunked(2).map { it.joinToString(" · ") }
            var y = top + hs * 0.8f
            for (line in lines) {
                out += Primitive.Text(hintLeft, y, fitSize(line, hs, right - hintLeft, 8f * c.density), line, TextStyle.HINT, align = Align.LEFT)
                y += hs * 1.4f
                if (y > bottom) break
            }
        }
    }
}
