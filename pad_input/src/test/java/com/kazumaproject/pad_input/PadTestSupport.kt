package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType

/**
 * The app's real string files, for the guide's words (amendment 12). JVM tests run from the
 * repository root (tools/test-engine.sh) or from pad_input/ (Gradle), so both are tried.
 */
object TestWords {
    private fun file(dir: String): java.io.File =
        listOf("app/src/main/res/$dir/pad_strings.xml", "../app/src/main/res/$dir/pad_strings.xml")
            .map { java.io.File(it) }.firstOrNull { it.isFile }
            ?: throw IllegalStateException("pad_strings.xml for $dir not found from ${java.io.File(".").absolutePath}")

    fun xml(dir: String): String = file(dir).readText()

    /** Japanese (values-ja), the language most tests assert in. */
    val ja: PadWords by lazy { PadWords.fromAndroidXml(xml("values-ja"), "ja") }

    /** English, the default (values). */
    val en: PadWords by lazy { PadWords.fromAndroidXml(xml("values"), "en") }
}

/**
 * Small helpers to drive the engine like a real controller session.
 *
 * Amendment 18: the kana right stick is always the pentagon (the cross + click setting is gone,
 * and R3 steps the text mode), so kana characters are typed with [kana] (a pentagon flick in the
 * slot of a touch gesture: tap = ↑ あ段, ← = → い段, ↑ = ↘ う段, → = ↙ え段, ↓ = ← お段).
 */
class PadDriver(val engine: PadInputEngine = PadInputEngine()) {
    init {
        // Labels in the tests are the Japanese resources unless a test chose a language.
        if (engine.config.words === PadWords.KEYS) engine.config = engine.config.copy(words = TestWords.ja)
        // Like PadController does on the first event: the device reports joystick axes.
        if (engine.config.stickSource == StickSource.AUTO) engine.setDeviceHasSticks(true)
    }

    var time = 0L
    val actions = mutableListOf<PadAction>()
    val notes = mutableListOf<String>()

    private var lx = 0f
    private var ly = 0f
    private var rx = 0f
    private var ry = 0f

    private fun record(d: PadDecision) {
        actions += d.actions
        if (d.note.isNotEmpty()) notes += d.note
    }

    fun tick(ms: Long = 16) { time += ms }

    fun sticks(lx: Float = this.lx, ly: Float = this.ly, rx: Float = this.rx, ry: Float = this.ry) {
        this.lx = lx; this.ly = ly; this.rx = rx; this.ry = ry
        tick()
        record(engine.onSticks(lx, ly, rx, ry, time))
    }

    fun left(x: Float, y: Float) = sticks(lx = x, ly = y)
    fun right(x: Float, y: Float) = sticks(rx = x, ry = y)
    fun leftRelease() = left(0f, 0f)

    fun press(b: PadButton) { tick(); record(engine.onButton(b, true, time)) }
    fun release(b: PadButton) { tick(); record(engine.onButton(b, false, time)) }
    fun tap(b: PadButton) { press(b); release(b) }

    /** The next text mode: R3 (MODE_NEXT) with the right stick at rest. Amendment 20: Select no longer cycles. */
    fun nextMode() = tap(PadButton.R3)

    /** Push the right stick fully in a direction and bring it back: one flick. */
    fun flick(dir: Dir4) {
        val (x, y) = unit(dir)
        right(x * 0.5f, y * 0.5f)
        right(x * 0.95f, y * 0.95f)
        right(x * 0.4f, y * 0.4f)
        right(0f, 0f)
    }

    /** Push the right stick toward an 8-way direction and bring it back: one flick (the bar reads diagonals). */
    fun flick8(sector: Sector9) {
        val (x, y) = unitSector(sector)
        right(x * 0.5f, y * 0.5f)
        right(x * 0.95f, y * 0.95f)
        right(x * 0.4f, y * 0.4f)
        right(0f, 0f)
    }

    /** Push the right stick toward [deg] (0° = right, 90° = up, counter-clockwise) and bring it back: one flick. */
    fun flickAt(deg: Double) {
        val (x, y) = unitAt(deg)
        right(x * 0.5f, y * 0.5f)
        right(x * 0.95f, y * 0.95f)
        right(x * 0.4f, y * 0.4f)
        right(0f, 0f)
    }

    /**
     * Amendment 18: type the kana the touch key types with [gesture] on the pentagon (tap = slot 0
     * ↑ … flick ↓ = slot 4 ←), with the left stick wherever it is.
     */
    fun kana(gesture: GestureType) = flickAt(KanaPentagon.CENTRES_DEG[KanaPentagon.slotOf(gesture)])

    /** The kana of a slot, by the touch gesture: tap (あ段). */
    fun kanaTap() = kana(GestureType.Tap)

    /**
     * Amendment 21: one flick on the や/わ key's six-slot ring (0 = ↑ や, 1 = ↗ ゆ, 2 = ↘ よ, 3 = ↓ わ,
     * 4 = ↙ を, 5 = ↖ ん): the left stick on ↘ (the や/わ key), the right stick out at the slot's centre and back.
     */
    fun yaWa(slot: Int) { holdSector(Sector9.DOWN_RIGHT); flickAt(KanaHexagon.CENTRES_DEG[slot]) }

    /** Amendment 18: R2 (the layer) held, one left-stick flick toward [sector], R2 released. */
    fun layer(sector: Sector9) { press(PadButton.R2); flickLeft8(sector); release(PadButton.R2) }

    /**
     * Amendment 16: flick the LEFT stick toward an 8-way direction and bring it back (the や・わ
     * layer while R2 is held). The right stick stays where it is.
     */
    fun flickLeft8(sector: Sector9) {
        val (x, y) = unitSector(sector)
        left(x * 0.5f, y * 0.5f)
        left(x * 0.95f, y * 0.95f)
        left(x * 0.4f, y * 0.4f)
        left(0f, 0f)
    }

    /** Push the left stick out toward an 8-way direction without bringing it back. */
    fun leftOut(sector: Sector9, r: Float = 0.95f) {
        val (x, y) = unitSector(sector)
        left(x * 0.5f, y * 0.5f)
        left(x * r, y * r)
    }

    /** Hold the left stick toward a grid position. */
    fun holdSector(sector: Sector9) {
        val (x, y) = unitSector(sector)
        left(x, y)
    }

    fun poll() { record(engine.pollRepeat(time)) }

    fun typed(): String = actions.mapNotNull {
        when (it) {
            is PadAction.KanaKey -> it.char?.toString()
            is PadAction.InsertText -> it.text
            else -> null
        }
    }.joinToString("")

    fun kanaKeys(): List<PadAction.KanaKey> = actions.filterIsInstance<PadAction.KanaKey>()

    fun clear() { actions.clear(); notes.clear() }

    companion object {
        fun unit(dir: Dir4): Pair<Float, Float> = when (dir) {
            Dir4.LEFT -> -1f to 0f
            Dir4.RIGHT -> 1f to 0f
            Dir4.UP -> 0f to -1f
            Dir4.DOWN -> 0f to 1f
        }

        /** Stick coordinates for a sector (diagonals at ~0.7/0.7 so the radius is ~1). */
        fun unitSector(sector: Sector9): Pair<Float, Float> {
            if (sector == Sector9.CENTER) return 0f to 0f
            val h = (sector.col - 1).toFloat()
            val v = (sector.row - 1).toFloat()
            val diag = h != 0f && v != 0f
            val s = if (diag) 0.707f else 1f
            return h * s to v * s
        }

        /** Stick coordinates (x right, y down) of a unit push toward [deg] (0° = right, counter-clockwise). */
        fun unitAt(deg: Double): Pair<Float, Float> {
            val rad = Math.toRadians(deg)
            return kotlin.math.cos(rad).toFloat() to -kotlin.math.sin(rad).toFloat()
        }

        val GESTURES = listOf(GestureType.Tap, GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom)

        fun gestureToDir(g: GestureType): Dir4? = when (g) {
            GestureType.FlickLeft -> Dir4.LEFT
            GestureType.FlickTop -> Dir4.UP
            GestureType.FlickRight -> Dir4.RIGHT
            GestureType.FlickBottom -> Dir4.DOWN
            else -> null
        }

        val ALL_KEYS: List<Key> = KanaGrid.MAIN
    }
}
