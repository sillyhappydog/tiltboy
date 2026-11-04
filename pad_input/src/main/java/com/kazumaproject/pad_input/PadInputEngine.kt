package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode

/**
 * Controller input state machine. Deterministic and free of Android classes:
 * feed it button and stick events, get [PadAction]s back.
 *
 * One scheme for every text mode (amendment 6): "smartphone flick split across two hands".
 * Kana uses Sumire's tenkey table; 英字 and 数字 use the controller's own four-way tables
 * ([FourWayLayouts], amendment 14):
 *  - left stick selects the key on the 3x3 grid (neutral = the centre key);
 *  - a right-stick flick (push past [PadConfig.flickThreshold], come back under
 *    [PadConfig.flickReturnThreshold]) types the flick character. The key is latched when the
 *    right stick goes out; the direction follows the stick while it is out (amendment 17, see
 *    "Re-aim" below). In 英字 / 数字 the four tilts are the cell's four characters (← ↑ → ↓);
 *  - (v1–v6: TAP_CENTER on R3 typed the centre character; removed by amendment 18.)
 *  - Kana pentagon (amendment 15; the only kana right stick since amendment 18): in kana the
 *    right stick reads five 72° sectors ([KanaPentagon]): ↑ あ段, then clockwise → い段, ↘ う段,
 *    ↙ え段, ← お段. Latching: key and hold role when the stick goes out, direction re-aimed while
 *    out, typed when it comes back.
 *  - Pressing R3 tilts the stick a little, so no flick is armed while R3 is held, and after R3 up
 *    the next flick is armed only once the stick is back inside the return threshold.
 *  - Hold roles (amendments 7 and 10) change the character: 濁点 (L1), 半濁点 (L2), 小文字 (R1),
 *    大文字 in English; the first hold pressed wins; a transform that does not apply leaves the
 *    plain character. Transforms are the touch ゛゜小 key's own table. For a flick the role is
 *    latched when the right stick goes out, with the key: users bring the sticks and the shoulder
 *    back almost together, so L1 let go a few ms before the stick is home still gives が (JoyFlick).
 *  - The bar (amendment 10): with sticks it was L3 (removed by amendment 18: its symbols are on
 *    the R2 layer). It stays in the D-pad fallback (L1 held) and as the emoji wheel's page 2.
 *  - Face buttons (amendment 11): A = 決定 (CONFIRM), Y = 変換 (CONVERT_NEXT), B = 消す, X = 空白.
 *  - Modes (amendment 12): MODE_SWITCH cycles only the text modes enabled in [PadConfig.modes];
 *    with one mode a tap does nothing. If the host puts us in a disabled mode (the touch keyboard
 *    was switched), the next controller event moves to an enabled one first.
 *
 * Emoji wheel (EMOJI; since amendment 20 only in the D-pad fallback, see below): the 32 most recent emoji on 8 petals (the bar = next 32, on the
 * MODE_PREVIOUS button, L3); the right-stick flick picks the emoji at ← ↑ → ↓, the MODE_NEXT
 * button (R3) the petal's first one (amendment 18 keeps L3 / R3's v6 jobs there).
 * Until v7, EMOJI_JUMP (Start) went to the wheel from any mode and in the wheel opened Sumire's
 * full emoji keyboard (EMOJI_LIST: D-pad moves, L2/R2 switch the category tab, A / CONFIRM inserts,
 * B or Start goes back to the wheel), and MODE_SWITCH (Select tap) cycled the enabled text modes;
 * held ≥ [PadConfig.longPressMs] it hid the keyboard instead. Amendment 20 changed both (below).
 *
 * D-pad fallback (no stick events): the D-pad, held, replaces the left stick (two directions
 * at once = diagonal). A / X / Y / B / L2 = centre (英字 / 数字: space) / left / up / right / bottom character,
 * L1 held = the bar, Start = confirm, Select = backspace, L1+Select = hide, L1+R2 = space,
 * R1 = mode, R3 = emoji wheel / back. Fixed table, roles do not apply. Kana there keeps the v4
 * grid and its bar ([KanaGrid.FALLBACK_KANA]): without a left stick there is no や・わ layer.
 *
 * Amendment 16 (v5), kana with sticks (v5–v8; amendment 21 replaced the 記号 key and the layer, below):
 *  - The grid was ↖ か ↑ さ ↗ た / ← な ・あ → は / ↙ ま ↓ ら ↘ 記号 ([KanaGrid.MAIN]): the stick
 *    at rest is the あ key. The 記号 key typed 。 、 ！ ？ ー in slot order (amendment 17 and the
 *    user's decision on it).
 *  - No bar in かな: the BAR role (L3) does nothing there. 英字 / 数字 keep it.
 *  - The layer ([PadRole.YA_WA_LAYER], R2): while it is held the left stick types directly,
 *    like a right-stick flick: it arms past [PadConfig.flickThreshold], the 8-way sector it is
 *    aimed at picks the character (v5–v8 かな: ↖ や ↑ ゆ ↗ よ / ↙ わ ↓ を ↘ ん), re-aimed while out,
 *    and it types when the stick comes back under
 *    [PadConfig.flickReturnThreshold] (an empty slot types nothing). The layer is latched when the
 *    flick arms (v5–v8 also the 小文字 role, R1 + R2 = ゃ ゅ ょ ゎ).
 *    Pressed while the left stick is out (≥ the return threshold), nothing arms until the stick
 *    has come back. While the layer is held, right-stick flicks (and R3) are ignored; a right
 *    flick armed before completes normally.
 *
 * Amendment 17 (v6), re-aim by rotating, for every flick (right-stick flicks in かな pentagon and
 * cross, 英字, 数字 and 絵文字; the L3 bar's eight directions; the や/わ layer's left-stick flick):
 *  - arming is unchanged: the flick arms when the radius first reaches [PadConfig.flickThreshold];
 *    the key, the bar / layer flag and the hold role are latched then;
 *  - while armed, every sample with a radius ≥ [PadConfig.flickThreshold] sets the target to THAT
 *    sample's sector, so rotating the stick along the rim moves the target from slot to slot (the
 *    guide's pending highlight follows it);
 *  - samples between [PadConfig.flickReturnThreshold] and [PadConfig.flickThreshold] (the way
 *    back) leave the target alone;
 *  - under [PadConfig.flickReturnThreshold] the current target is typed (an empty slot: nothing).
 * This replaces the v1–v5 rule "the direction of the farthest sample".
 *
 * Amendment 18 (v7), one layout for every text mode:
 *  - L3 / R3 step the text mode ([PadRole.MODE_PREVIOUS] / [PadRole.MODE_NEXT]) through
 *    [PadConfig.textModes] with wrap-around (never 絵文字; Start goes there, Select still cycles).
 *    Guard against accidental clicks (a hard flick can push a stick in): the click switches only if
 *    that stick stays under [PadConfig.flickReturnThreshold] from press to release and no flick is
 *    armed on it; it fires on release; any other click is ignored and the note says why. In the
 *    emoji wheel L3 / R3 keep their v6 jobs (the next 32 emoji / the petal's first emoji).
 *  - What L3 and R3 did in the text modes is gone: no centre character (かな R3 あ段, 英字 / 数字
 *    R3 space) and no L3 bar. The bar's symbols moved to the R2 layer, which is now in every text
 *    mode ([KanaGrid.layerChars]: かな や/わ, 英字 / 数字 記号) with the や/わ mechanics.
 *  - The kana cross + click setting is gone: kana is always the pentagon.
 *  - An R3 press no longer cancels a flick that is already armed (the click is ignored instead
 *    and the flick types); no flick arms while R3 is held, as before.
 *  - Touch digits on the guide ([onTouchDigit]) type a digit the way 数字 mode types it.
 *
 * Amendment 20 (v8):
 *  - Select ([PadRole.TOUCH_KEYBOARD]): a tap sends [PadAction.TouchKeyboard] (the host switches
 *    between the guide and Sumire's touch keyboard for the mode); held ≥ [PadConfig.longPressMs] it
 *    hides the keyboard as before. Select no longer cycles the modes ([PadRole.MODE_SWITCH] is gone).
 *  - Start ([PadRole.EMOJI_JUMP]) opens Sumire's full emoji list directly (EMOJI_LIST). In the list
 *    the D-pad and the left stick move the selection (the stick steps once per tilt past
 *    [PadConfig.leftSelectThreshold], four ways, and repeats while held after
 *    [PadConfig.repeatDelayMs] every [PadConfig.repeatIntervalMs], the backspace timings; it must
 *    come home under [PadConfig.leftReleaseThreshold] before the next tilt), A inserts, L2 / R2 change
 *    the category, B or Start close the list and go back to the text mode the user came from; L3 /
 *    R3, the right stick, X and Y do nothing. A Select tap there closes the list and shows the touch
 *    keyboard; held, it closes the list and hides the keyboard.
 *  - With sticks no mode goes to the emoji wheel (EMOJI) any more; a wheel left over from the D-pad
 *    fallback is left for the text mode at the first stick event. The fallback keeps its table
 *    (R3 the wheel, Start the list from the wheel, R1 the modes).
 *
 * Amendment 21 (v9), かな:
 *  - The grid's ↘ is the や/わ key ([KanaGrid.YA_WA_KEY]); while the left stick selects it, the right
 *    stick reads six 60° sectors ([KanaHexagon]): ↑ や ↗ ゆ ↘ よ ↓ わ ↙ を ↖ ん. Arming, re-aim, the
 *    latch at arming, the return band and the pending highlight are the pentagon's. Only the 小文字
 *    role applies there (R1: ゃ ゅ ょ ゎ, を ん unchanged); L2 / L1 do nothing on it.
 *  - R2 ([PadRole.YA_WA_LAYER]) is the 記号 layer in かな too ([KanaGrid.KANA_SYMBOLS]: ↖ 〜 ↑ 。
 *    ↗ … / ← ー → 、 / ↙ ？ ↓ ・ ↘ ！), with the 英字 / 数字 mechanics; R1 does nothing on it. The 記号
 *    grid key, the や/わ layer and R1 + R2 small kana are gone.
 *
 * Amendment 22 (v9), 英字: while a button whose role [PadRole.isNumberHold] (L1 半濁点, R1 小文字 by
 * default) is held, the grid, the ring and the R2 layer are 数字's ([layoutMode]); L2 (大文字) does
 * nothing then; ABXY, L3 / R3, Select and Start stay 英字's. The layout is latched when a flick arms,
 * with the key and the hold role ([latchedLayout], [latchedLayerMode]), so letting L1 go before the
 * stick is home still types the digit. A digit goes out as [PadAction.InsertText], the way 数字 types
 * it (the IME appends it to the word being composed, as after R3 → 数字 mid-word). Works whether or
 * not 数字 is in [PadConfig.textModes]; 数字 and かな are unchanged.
 */
class PadInputEngine(
    config: PadConfig = PadConfig(),
    initialMode: InputMode = InputMode.ModeJapanese,
) {
    var config: PadConfig = config
        set(value) {
            field = value
            applyStickSource()
        }

    /** The engine's mode, including the emoji modes. */
    var padMode: PadMode = PadMode.from(initialMode)
        private set(value) {
            if (field != value) {
                field = value
                // A pending right-stick flick and an armed bar belong to the old mode; drop them.
                rightOut = false
                barOneShot = false
                repeating = null
                // So does a left-stick flick on the や・わ layer (amendment 16).
                layerOut = false
                layerBlocked = false
                // And the emoji list's left-stick step (amendment 20).
                listStickDir = null
                listStickBlocked = false
            }
        }

    /** Sumire's input mode underneath; for the emoji modes, the mode we came from. */
    private var sumireMode: InputMode = initialMode

    /**
     * Sumire input mode as the host sees it. Setting it (host sync from the touch keyboard)
     * leaves the emoji modes untouched unless the mode actually changed.
     */
    var mode: InputMode
        get() = padMode.inputMode ?: sumireMode
        set(value) {
            if (padMode.isEmoji && value == sumireMode) return
            sumireMode = value
            padMode = PadMode.from(value)
        }

    /** Host tells us whether a candidate list is on screen (D-pad then moves through candidates). */
    var candidatesShown: Boolean = false

    /** True once any stick motion has been seen on this device. */
    var stickMotionSeen: Boolean = false
        private set

    /** True when the engine is working without sticks (D-pad fallback). */
    var fallback: Boolean = false
        private set

    /** The host said the device declares joystick axes (AUTO starts in stick mode then). */
    private var declaredSticks = false
    private var pressesWithoutMotion = 0

    /** Emoji slots for the wheel: history first, then defaults. 64 entries (2 pages). */
    var emojiSlots: List<String> = EmojiWheel.slots(emptyList())
        private set

    fun setEmojiHistory(history: List<String>) {
        emojiSlots = EmojiWheel.slots(history)
    }

    /** Held buttons in press order (the first hold role pressed wins). */
    private val held = LinkedHashSet<PadButton>()

    /** The bar was tapped: the next character comes from it (amendment 10; the fallback's L1 bar, the emoji wheel's page 2). */
    private var barOneShot = false

    /** A character came from the bar during the current BAR press (release turns the bar off instead of arming it). */
    private var barTyped = false

    /**
     * The bar is held: in the D-pad fallback L1; with sticks only the emoji wheel has one since
     * amendment 18 (page 2 while the MODE_PREVIOUS button, L3, is held, as L3's v6 bar).
     */
    private val barHeld: Boolean
        get() = if (fallback) PadButton.L1 in held else padMode == PadMode.EMOJI && held.any { config.roleOf(it) == PadRole.MODE_PREVIOUS }

    /** The bar is active: fallback tenkey = A X Y B L2 type the bar; emoji wheel = page 2. */
    val barActive: Boolean
        get() = barHeld || barOneShot

    /** The character transform in force: the first held button with a transform role. */
    private val holdRole: PadRole?
        get() = if (fallback) null else held.firstOrNull { config.roleOf(it).isTransform }?.let { config.roleOf(it) }

    // ---- the R2 記号 layer (amendment 16 as や・わ; amendment 21: 記号 in every text mode)

    /** A YA_WA_LAYER button is held in a text mode with sticks: the left stick types the 記号 layer (amendment 18: 英字 / 数字 too). */
    val layerHeld: Boolean
        get() = !fallback && isTenkey() && held.any { config.roleOf(it) == PadRole.YA_WA_LAYER }

    /** The 小文字 role that applies on the や/わ key's ring (amendment 21): the first held button with HOLD_SMALL / HOLD_SMALL_OR_HANDAKUTEN. */
    private val smallRole: PadRole?
        get() = if (fallback) null else held.firstOrNull { config.roleOf(it).isSmall }?.let { config.roleOf(it) }

    /** Amendment 22: a [PadRole.isNumberHold] button is held in 英字 with sticks: 数字's grid, ring and layer. */
    val numberHeld: Boolean
        get() = !fallback && padMode == PadMode.ENGLISH && held.any { config.roleOf(it).isNumberHold }

    /** The mode whose tables the grid, the ring and the R2 layer use right now (amendment 22: 数字 while [numberHeld]). */
    private val layoutMode: PadMode get() = if (numberHeld) PadMode.NUMBER else padMode

    /** A left-stick flick on the layer is out (armed), waiting to come back. */
    private var layerOut = false
    private var layerSector: Sector9 = Sector9.UP
    private var layerAngle = 0f
    /** Amendment 22: the layout (英字, or 数字 under L1 / R1) latched when the layer flick armed. */
    private var latchedLayerMode: PadMode = PadMode.KANA
    /** The layer went on while the left stick was out: nothing arms until it has come back. */
    private var layerBlocked = false
    /** The key the right stick's ring shows (faint) while the layer is up: the one selected when it went on. */
    private var layerKey: Key = Key.KeyA
    private var lastLeftRadius = 0f

    /** The layer is showing: held, or a layer flick still out after the button was let go. */
    private val layerShown: Boolean get() = layerHeld || layerOut

    // Left stick state (with hysteresis).
    private var leftSector: Sector9 = Sector9.CENTER
    private var leftSelected = false

    // Right stick state (tenkey flick and emoji flick share it).
    private var rightOut = false
    private var rightDir: Dir4 = Dir4.UP
    private var latchedKey: Key = Key.KeyNA
    private var latchedPetal: Int = -1
    private var lastRightRadius = 0f

    /** The hold transform in force when the flick went out; used when it is typed (see the class notes). */
    private var latchedRole: PadRole? = null
    private var rightAngle = 0f

    // The kana pentagon (amendment 15): a flick that started on the pentagon reads 5 sectors.
    private var latchedPentagon = false
    // Amendment 21: a flick that started on the や/わ key reads the six-slot ring ([KanaHexagon]).
    private var latchedHexagon = false
    private var rightSlot = 0
    /** Amendment 22: the layout latched with the key when the flick armed (英字, or 数字 under L1 / R1). */
    private var latchedLayout: PadMode = PadMode.KANA

    // R3 tilt protection.
    private var r3Held = false
    private var rightBlockedUntilReturn = false

    // Amendment 18: L3 / R3 mode steps, with the accidental-click guard.
    private enum class StickSide { LEFT, RIGHT }
    /** A mode-step click waiting for its release; [veto] says why it will be ignored (null = it switches). */
    private class ModeClick(val forward: Boolean, val side: StickSide?, var veto: String?)
    private val modeClicks = LinkedHashMap<PadButton, ModeClick>()

    // Amendment 20: the left stick in the emoji list. The direction it holds (null = home) and
    // whether it was already out when the list opened (then it must come home first).
    private var listStickDir: Dir4? = null
    private var listStickBlocked = false

    // Analog trigger state.
    private val triggerPressed = mutableMapOf<PadButton, Boolean>()
    private val triggerHasKeyEvents = mutableSetOf<PadButton>()

    // Key repeat (backspace). `source` is the held button.
    private data class Repeat(val action: PadAction, var nextAt: Long, val source: PadButton?)
    private var repeating: Repeat? = null

    // MODE_SWITCH button: tap on release, hide when held long enough.
    private data class LongPress(val button: PadButton, val at: Long, var fired: Boolean = false)
    private var longPress: LongPress? = null

    init {
        applyStickSource()
    }

    private fun applyStickSource() {
        fallback = when (config.stickSource) {
            StickSource.STICKS -> false
            StickSource.DPAD_FALLBACK -> true
            StickSource.AUTO -> !stickMotionSeen
        }
    }

    /**
     * Host-level knowledge: whether the device declares joystick axes. Only matters in AUTO:
     * a device with sticks starts in stick mode (no 「十字キー」 before the first motion); it
     * falls back to the D-pad layout only if [FALLBACK_AFTER_PRESSES] buttons are pressed before
     * any stick motion arrives. A device without axes starts in the fallback.
     */
    fun setDeviceHasSticks(hasSticks: Boolean) {
        declaredSticks = hasSticks
        if (config.stickSource == StickSource.AUTO && !stickMotionSeen) fallback = !hasSticks
    }

    /**
     * v13: the host remembers controllers whose sticks have reached the keyboard before (across
     * process restarts). For those the "8 presses without stick motion" guess must never switch to
     * the D-pad layout: a user who opens a field and taps B to delete would otherwise start typing
     * letters with the face buttons.
     */
    fun markSticksKnownToWork() {
        if (stickMotionSeen) return
        stickMotionSeen = true
        if (config.stickSource == StickSource.AUTO) fallback = false
    }

    fun reset() {
        held.clear()
        leftSector = Sector9.CENTER
        leftSelected = false
        rightOut = false
        r3Held = false
        rightBlockedUntilReturn = false
        lastRightRadius = 0f
        barOneShot = false
        barTyped = false
        latchedRole = null
        latchedPentagon = false
        latchedHexagon = false
        layerOut = false
        layerBlocked = false
        lastLeftRadius = 0f
        repeating = null
        longPress = null
        modeClicks.clear()
        triggerPressed.clear()
        listStickDir = null
        listStickBlocked = false
    }

    // ------------------------------------------------------------------ guide

    val guide: PadGuideState
        get() {
            val sector = currentSector()
            val bar = barActive
            val labels = GuideLabels.build(config, padMode, fallback, bar, numberLayout = guideLayout() == PadMode.NUMBER && padMode == PadMode.ENGLISH)
            val barButtons = if (fallback) PadButton.L1.label
            // Amendment 18: with sticks only the emoji wheel has a bar (page 2), on the MODE_PREVIOUS button (L3).
            else if (!padMode.isEmoji) ""
            else GuideLabels.MAPPED.filter { config.roleOf(it) == PadRole.MODE_PREVIOUS }.joinToString("/") { it.label }
            val barLabel = if (barButtons.isEmpty()) "" else GuideLabels.barWord(padMode, config.words)
            return if (isTenkey()) {
                val keys = KanaGrid.keys(padMode, fallback)
                // Amendment 16: the R2 layer replaces the grid while it is up (amendment 21: 記号 in かな too).
                val layer = layerShown
                // Amendment 22: the tables in force (数字's while L1 / R1 is held in 英字; latched with a flick that is out).
                val layout = guideLayout()
                val numberLayout = layout != padMode
                val key = when {
                    layer -> layerKey
                    rightOut -> latchedKey
                    else -> keyFor(sector)
                }
                // Amendment 21: the や/わ key reads the six-slot ring (latched with a flick that is out).
                val hexagon = !bar && (if (rightOut) latchedHexagon else KanaGrid.isHexagon(key, padMode, fallback))
                // While a flick is out, the guide shows what it will type: the role latched with it.
                // The 記号 layer takes no transform (amendment 21), nor does 数字's layout under L1 / R1 (amendment 22).
                val role = when {
                    layer -> null
                    rightOut -> latchedRole
                    numberLayout -> null
                    else -> holdRole
                }
                // The や/わ key's ring takes only the 小文字 role (R1: ゃ ゅ ょ ゎ).
                val ringRole = when {
                    layer -> null
                    rightOut -> latchedRole
                    else -> smallRole
                }
                val plain = if (layer) KanaGrid.layerLabels(layout) else keys.map { KanaGrid.label(it, layout) }
                val transformed = plain.map { HoldTransforms.apply(role, it, layout) }
                // Amendment 18: 英字 / 数字 have no centre character with sticks (the fallback's A still types a space).
                val plainFlick = KanaGrid.flickChars(key, layout).let { if (!fallback && isFourWay()) it.copy(center = "") else it }
                // The faint ring under the layer shows the plain characters.
                val flick = if (layer) plainFlick else plainFlick.map { HoldTransforms.apply(role, it, layout) }
                val plainHex = if (hexagon) KanaGrid.hexagonChars() else emptyList()
                val hex = plainHex.map { HoldTransforms.apply(ringRole, it, PadMode.KANA) }
                val barChars = KanaGrid.bar(padMode).labels().mapValues { HoldTransforms.apply(role, it.value, padMode) }
                // Amendment 15: the pentagon while a pentagon flick is out, or (at rest) whenever kana reads it.
                val pentagon = !bar && !hexagon && (if (rightOut) latchedPentagon else usesPentagon())
                PadGuideState(
                    padMode = padMode, mode = mode, fallback = fallback, barActive = bar,
                    gridKeys = keys, gridLabels = transformed,
                    gridDim = when {
                        bar -> keys.map { true }
                        role == null -> keys.map { false }
                        else -> plain.indices.map { plain[it] == transformed[it] }
                    },
                    selectedKey = if (bar || layer) null else key, selectedSector = sector,
                    flickChars = if (bar) null else flick,
                    pendingFlick = if (rightOut && !latchedPentagon && !latchedHexagon) rightDir else null,
                    petals = null, selectedPetal = null,
                    faceLayout = config.faceLayout,
                    legend = GuideLabels.legend(labels),
                    labels = labels,
                    heldButtons = held.toSet(),
                    preview = when {
                        bar -> barChars[Sector9.CENTER].orEmpty()
                        layer -> if (layerOut) transformed[layerSector.index] else ""
                        else -> transformed[keys.indexOf(key).coerceAtLeast(0)]
                    },
                    holdRole = role,
                    barLabel = barLabel,
                    barButtons = barButtons,
                    barChars = barChars,
                    pentagon = pentagon,
                    pendingSlot = if (rightOut && (latchedPentagon || latchedHexagon)) rightSlot else null,
                    flickDim = when {
                        bar || layer -> emptyList()
                        hexagon -> if (ringRole == null) emptyList() else plainHex.indices.map { plainHex[it] == hex[it] }
                        role == null -> emptyList()
                        else -> plainFlick.slots.indices.map { plainFlick.slot(it) == flick.slot(it) }
                    },
                    layer = layer,
                    pendingLayer = if (layerOut) layerSector else null,
                    hexagon = hexagon,
                    hexagonChars = hex,
                    numberLayout = numberLayout,
                )
            } else {
                val petal = when {
                    padMode == PadMode.EMOJI_LIST -> null
                    rightOut && latchedPetal >= 0 -> latchedPetal
                    else -> sector.petal
                }
                PadGuideState(
                    padMode = padMode, mode = mode, fallback = fallback, barActive = bar,
                    gridKeys = null, selectedKey = null, selectedSector = sector, flickChars = null,
                    pendingFlick = if (rightOut && padMode == PadMode.EMOJI) rightDir else null,
                    petals = currentPetals(),
                    selectedPetal = petal,
                    faceLayout = config.faceLayout,
                    legend = GuideLabels.legend(labels),
                    labels = labels,
                    heldButtons = held.toSet(),
                    preview = when {
                        padMode == PadMode.EMOJI_LIST -> "list"
                        else -> petal?.let { currentPetals().getOrNull(it)?.joinToString("") }?.ifEmpty { null } ?: "?"
                    },
                    holdRole = null,
                    barLabel = barLabel,
                    barButtons = barButtons,
                )
            }
        }

    private fun currentSector(): Sector9 =
        if (fallback) StickGeometry.sectorFromDpad(
            up = PadButton.DPAD_UP in held, down = PadButton.DPAD_DOWN in held,
            left = PadButton.DPAD_LEFT in held, right = PadButton.DPAD_RIGHT in held,
        ) else leftSector

    /** The grid key at a position in the current mode (the D-pad fallback keeps the v4 kana grid). */
    private fun keyFor(sector: Sector9): Key = KanaGrid.keyFor(sector, padMode, fallback)

    /**
     * Amendment 22: the tables the guide shows: the ones latched with a flick that is out (the right
     * stick's, or the layer's on the left stick), else the live [layoutMode].
     */
    private fun guideLayout(): PadMode = when {
        !isTenkey() -> padMode
        layerOut -> latchedLayerMode
        rightOut && !layerShown -> latchedLayout
        else -> layoutMode
    }

    /** 8 petals × 4 emoji of the current page. */
    private fun currentPetals(): List<List<String>> =
        if (padMode.isEmoji) EmojiWheel.page(emojiSlots, if (barActive) 1 else 0) else emptyList()

    private fun isTenkey() = padMode == PadMode.KANA || padMode == PadMode.ENGLISH || padMode == PadMode.NUMBER
    /** 英字 / 数字: the controller's own four-way tables (amendment 14). */
    private fun isFourWay() = FourWayLayouts.applies(padMode)
    private fun isWheel() = padMode == PadMode.EMOJI

    /** Amendment 15: kana reads the right stick in five sectors (not in the D-pad fallback; the only kana right stick since amendment 18). */
    private fun usesPentagon() = padMode == PadMode.KANA && !fallback

    // ------------------------------------------------------------------ sticks

    /** Both sticks, normalised -1..1, y positive = down. */
    fun onSticks(lx: Float, ly: Float, rx: Float, ry: Float, timeMs: Long): PadDecision =
        withEnabledMode { sticks(lx, ly, rx, ry, timeMs) }

    /**
     * Amendment 12: the touch keyboard (or the host's mode sync) may have put us in a text mode
     * the user switched off. The first controller event then moves to an enabled mode, and the
     * event is handled there.
     */
    /**
     * v17: the keyboard opened straight into the guide (amendment 25) while the host is in a text
     * mode the user switched off (かな with only 英字 and 数字 checked, e.g. after the touch keyboard
     * or another app left Sumire in かな). Move to an enabled mode before the first frame; until v16
     * the guide showed the disabled mode until the first controller event ([withEnabledMode]).
     */
    fun enterEnabledMode(): PadDecision {
        if (padMode.isEmoji || padMode in config.textModes()) return PadDecision.NONE
        val fix = enterMode(returnMode())
        return PadDecision(fix.actions, fix.note + " (mode not enabled, keyboard opened)")
    }

    private inline fun withEnabledMode(handle: () -> PadDecision): PadDecision {
        val modes = config.textModes()
        // Amendment 20: the emoji wheel is the D-pad fallback's only; with sticks go back to the text mode.
        if (padMode == PadMode.EMOJI && !fallback) {
            val fix = enterMode(returnMode())
            val d = handle()
            return PadDecision(fix.actions + d.actions, listOf(fix.note + " (the emoji wheel is for the D-pad layout only)", d.note).filter { it.isNotEmpty() }.joinToString(", "))
        }
        if (padMode.isEmoji || padMode in modes) return handle()
        val fix = enterMode(returnMode())
        val d = handle()
        return PadDecision(fix.actions + d.actions, listOf(fix.note + " (mode not enabled)", d.note).filter { it.isNotEmpty() }.joinToString(", "))
    }

    private fun sticks(lx: Float, ly: Float, rx: Float, ry: Float, timeMs: Long): PadDecision {
        if (!stickMotionSeen) {
            stickMotionSeen = true
            if (config.stickSource == StickSource.AUTO) fallback = false
        }
        if (fallback) return PadDecision(emptyList(), "stick ignored (D-pad fallback)")
        // Amendment 20: the left stick moves the emoji list's selection; the right stick does nothing there.
        if (padMode == PadMode.EMOJI_LIST) return emojiListStick(lx, ly, timeMs)
        val notes = mutableListOf<String>()
        val actions = mutableListOf<PadAction>()
        // Amendment 20: a wheel left over from the D-pad fallback (the first stick motion ended it).
        if (padMode == PadMode.EMOJI) {
            val d = enterMode(returnMode())
            actions += d.actions
            notes += "${d.note} (the emoji wheel is for the D-pad layout only)"
        }

        // Left stick with hysteresis.
        val lr = StickGeometry.radius(lx, ly)
        lastLeftRadius = lr
        if (!leftSelected && lr >= config.leftSelectThreshold) leftSelected = true
        if (leftSelected && lr <= config.leftReleaseThreshold) leftSelected = false
        val newSector = if (leftSelected) StickGeometry.sector8(lx, ly) else Sector9.CENTER
        if (newSector != leftSector) {
            leftSector = newSector
            val note = when {
                isWheel() -> "petal ${describePetal(newSector)}"
                layerShown -> null // the left stick types the layer (below)
                else -> "key ${KanaGrid.label(keyFor(newSector), layoutMode)}"
            }
            if (note != null) notes += note
        }

        // Amendment 16: the layer (や・わ; amendment 18: 記号 in 英字 / 数字). While it is held the left stick flicks like the right one.
        layerStick(lx, ly, lr, actions, notes)

        val rr = StickGeometry.radius(rx, ry)
        lastRightRadius = rr
        // Amendment 18: a mode-step click is void once its stick leaves the centre.
        for ((button, click) in modeClicks) {
            if (click.veto != null) continue
            val r = when (click.side) { StickSide.LEFT -> lr; StickSide.RIGHT -> rr; null -> continue }
            if (r >= config.flickReturnThreshold) {
                click.veto = "the ${click.side?.name?.lowercase()} stick moved to ${"%.2f".format(r)} while $button was held"
                notes += "$button click void: ${click.veto}"
            }
        }

        // Right stick: flick = out past the threshold, back under the return threshold.
        run {
            if (rightBlockedUntilReturn && rr <= config.flickReturnThreshold) rightBlockedUntilReturn = false
            if (!rightOut && (r3Held || rightBlockedUntilReturn)) {
                // R3 is being pressed (whatever the stick reports is tilt noise), or it is still out
                // after R3 up: no new flick until it has come home. Amendment 18: a flick armed
                // before the click is not cancelled (the branches below finish it).
            } else if (!rightOut && rr >= config.flickThreshold && layerHeld) {
                // Amendment 16: right-stick flicks are ignored while the R2 layer is held; the
                // stick must come home before it can arm again (also after the layer is let go).
                rightBlockedUntilReturn = true
                notes += "right flick ignored while the symbols layer is held"
            } else if (!rightOut && rr >= config.flickThreshold) {
                rightOut = true
                rightDir = StickGeometry.sector4(rx, ry)
                rightAngle = StickGeometry.angleDeg(rx, ry)
                // Amendment 22: the layout (英字, or 数字 under L1 / R1) is latched with the key.
                latchedLayout = layoutMode
                if (isWheel()) {
                    latchedPentagon = false
                    latchedHexagon = false
                    latchedRole = holdRole
                    latchedPetal = leftSector.petal ?: -1
                    notes += if (latchedPetal < 0) "flick $rightDir: no petal selected" else "flick $rightDir on petal ${describePetal(leftSector)}"
                } else {
                    latchedKey = keyFor(leftSector)
                    // Amendment 21: the や/わ key reads six slots and takes only the 小文字 role.
                    latchedHexagon = KanaGrid.isHexagon(latchedKey, padMode, fallback)
                    latchedPentagon = usesPentagon() && !latchedHexagon
                    latchedRole = when {
                        latchedHexagon -> smallRole
                        latchedLayout != padMode -> null // 数字's layout: no 大文字 (amendment 22)
                        else -> holdRole
                    }
                    if (latchedHexagon) {
                        rightSlot = KanaHexagon.slotOf(rx, ry)
                        notes += "flick ${KanaHexagon.arrow(rightSlot)} on ${KanaGrid.YA_WA_LABEL} (six slots)" + (latchedRole?.let { " with ${it.name.lowercase()}" } ?: "")
                    } else if (latchedPentagon) {
                        rightSlot = KanaPentagon.slotOf(rx, ry)
                        notes += "flick ${KanaPentagon.arrow(rightSlot)} on ${KanaGrid.label(latchedKey, padMode)} (pentagon)"
                    } else {
                        notes += "flick $rightDir on ${KanaGrid.label(latchedKey, latchedLayout)}" + if (latchedLayout != padMode) " (number layout held)" else ""
                    }
                }
            } else if (rightOut && rr >= config.flickThreshold) {
                // Amendment 17: re-aim. Every sample out at the rim sets the target to its own
                // sector (no longer the farthest sample's), so rotating the stick moves the target.
                rightAngle = StickGeometry.angleDeg(rx, ry)
                if (latchedHexagon) {
                    val slot = KanaHexagon.slotOf(rx, ry)
                    if (slot != rightSlot) {
                        rightSlot = slot
                        notes += "flick re-aimed → ${KanaHexagon.arrow(slot)} (six slots)"
                    }
                } else if (latchedPentagon) {
                    val slot = KanaPentagon.slotOf(rx, ry)
                    if (slot != rightSlot) {
                        rightSlot = slot
                        notes += "flick re-aimed → ${KanaPentagon.arrow(slot)} (pentagon)"
                    }
                } else {
                    val d = StickGeometry.sector4(rx, ry)
                    if (d != rightDir) {
                        rightDir = d
                        notes += "flick re-aimed → $d"
                    }
                }
            } else if (rightOut && rr <= config.flickReturnThreshold) {
                rightOut = false
                if (isWheel()) {
                    if (latchedPetal >= 0) {
                        val d = wheelInsert(latchedPetal, rightDir.petalIndex, "right stick $rightDir")
                        actions += d.actions
                        notes += d.note
                    } else {
                        notes += "flick $rightDir ignored: no petal selected"
                    }
                } else if (latchedHexagon) {
                    val d = typeHexagon(rightSlot, latchedRole, via = "six slots ${KanaHexagon.arrow(rightSlot)} ${rightAngle.toInt()}°")
                    actions += d.actions
                    notes += d.note
                } else if (latchedPentagon) {
                    val d = typeKey(latchedKey, KanaPentagon.gesture(rightSlot), latchedRole,
                        via = "pentagon ${KanaPentagon.arrow(rightSlot)} ${rightAngle.toInt()}°")
                    actions += d.actions
                    notes += d.note
                } else {
                    val d = typeKey(latchedKey, rightDir.gesture, latchedRole, mode = latchedLayout)
                    actions += d.actions
                    notes += d.note
                }
            }
        }
        return PadDecision(actions, notes.joinToString(", "))
    }

    /**
     * Amendment 20: the left stick in the emoji list steps the selection like the D-pad. Past
     * [PadConfig.leftSelectThreshold] it moves one step in its four-way direction and starts a repeat
     * ([PadConfig.repeatDelayMs], then every [PadConfig.repeatIntervalMs]); a turn to another direction
     * past the threshold is a new step; under [PadConfig.leftReleaseThreshold] it is home and the
     * repeat stops. A stick that was out when the list opened does nothing until it has been home.
     */
    private fun emojiListStick(lx: Float, ly: Float, timeMs: Long): PadDecision {
        val r = StickGeometry.radius(lx, ly)
        lastLeftRadius = r
        if (listStickBlocked) {
            if (r > config.leftReleaseThreshold) return PadDecision.NONE
            listStickBlocked = false
            return PadDecision(emptyList(), "emoji list: left stick home, it moves the selection now")
        }
        val dir = when {
            r >= config.leftSelectThreshold -> StickGeometry.sector4(lx, ly)
            r <= config.leftReleaseThreshold -> null
            else -> listStickDir
        }
        if (dir == listStickDir) return PadDecision.NONE
        listStickDir = dir
        if (dir == null) {
            if (repeating?.source == null) repeating = null
            return PadDecision(emptyList(), "emoji list: left stick home")
        }
        val move = PadAction.EmojiKeyboard(listMove(dir))
        repeating = Repeat(move, timeMs + config.repeatDelayMs, null)
        return PadDecision.of(move, "emoji list ${dir.arrow} (left stick)")
    }

    private fun listMove(dir: Dir4): EmojiKeyboardCommand = when (dir) {
        Dir4.LEFT -> EmojiKeyboardCommand.MOVE_LEFT
        Dir4.UP -> EmojiKeyboardCommand.MOVE_UP
        Dir4.RIGHT -> EmojiKeyboardCommand.MOVE_RIGHT
        Dir4.DOWN -> EmojiKeyboardCommand.MOVE_DOWN
    }

    /**
     * Amendment 16, the R2 layer on the left stick (amendment 18: 記号 in 英字 / 数字; amendment 21:
     * 記号 in かな too): the same flick as the right stick's. Arms past [PadConfig.flickThreshold]
     * while the layer is held (and not blocked), re-aims in 8 sectors with every sample at or past
     * [PadConfig.flickThreshold] (amendment 17), types when the stick comes back under
     * [PadConfig.flickReturnThreshold] (an empty slot types nothing). The layer itself is latched when
     * it arms, so letting R2 go before the stick is home still types the layer character; so is the
     * layout (amendment 22: 数字's layer while L1 / R1 is held in 英字).
     */
    private fun layerStick(lx: Float, ly: Float, lr: Float, actions: MutableList<PadAction>, notes: MutableList<String>) {
        if (layerBlocked && lr <= config.flickReturnThreshold) {
            layerBlocked = false
            if (layerHeld) notes += "left stick home: the ${layerName()} layer is ready"
        }
        if (layerOut) {
            if (lr >= config.flickThreshold) {
                // Amendment 17: re-aim, as on the right stick.
                layerAngle = StickGeometry.angleDeg(lx, ly)
                val s8 = StickGeometry.sector8(lx, ly)
                if (s8 != layerSector) {
                    layerSector = s8
                    notes += "${layerName()} layer flick re-aimed → ${describeSector(s8)}"
                }
            } else if (lr <= config.flickReturnThreshold) {
                layerOut = false
                val d = typeLayer(layerSector, latchedLayerMode, "left stick ${describeSector(layerSector)} ${layerAngle.toInt()}°")
                actions += d.actions
                notes += d.note
            }
        } else if (layerHeld && !layerBlocked && lr >= config.flickThreshold) {
            layerOut = true
            layerAngle = StickGeometry.angleDeg(lx, ly)
            layerSector = StickGeometry.sector8(lx, ly)
            latchedLayerMode = layoutMode
            notes += "${layerName()} layer flick ${describeSector(layerSector)}" + if (latchedLayerMode != padMode) " (number layout held)" else ""
        }
    }

    /** The layer's name in the log: symbols in every text mode since amendment 21 (かな's was ya/wa). */
    private fun layerName(): String = "symbols"

    /**
     * Type one character of the R2 layer of [layout] (amendment 22: 数字's under L1 / R1 in 英字).
     * かな (amendment 21): the touch key's own (key, gesture) as [PadAction.KanaKey] when a touch key
     * types it (。 、 ？ ！ … the 、。？！ key, ー 〜 the わ key), else [PadAction.InsertText] (・); R1
     * does nothing there. 英字 / 数字 (amendment 18): [PadAction.InsertText], like their grid
     * characters (no upper case: L2 changes nothing here).
     */
    private fun typeLayer(sector: Sector9, layout: PadMode, via: String): PadDecision {
        val where = "${layerName()} layer ${describeSector(sector)} ($via)"
        val c = KanaGrid.layerChars(layout)[sector] ?: return PadDecision(emptyList(), "$where: no character")
        if (layout == PadMode.KANA) {
            KanaGrid.touchSlotFor(c)?.let { slot -> return PadDecision.of(PadAction.KanaKey(slot.key, slot.gesture, slot.char), "$where typed $c") }
        }
        return PadDecision.of(PadAction.InsertText(c.toString()), "$where typed $c" + if (layout != padMode) " (number layout)" else "")
    }

    /**
     * Amendment 21: type one slot of the や/わ key's six-slot ring: the touch や / わ key's own (key,
     * gesture) as [PadAction.KanaKey], or with the 小文字 role (R1) ゃ ゅ ょ ゎ as [PadAction.InsertText].
     */
    private fun typeHexagon(slot: Int, role: PadRole?, via: String): PadDecision {
        val s = KanaGrid.YA_WA_SLOTS[slot]
        val typed = HoldTransforms.apply(role, s.char, PadMode.KANA)
        val where = "$via: key ${KanaGrid.YA_WA_LABEL}"
        return if (typed != s.char) {
            PadDecision.of(PadAction.InsertText(typed.toString()), "$where, typed $typed (${role!!.name.lowercase()})")
        } else {
            PadDecision.of(PadAction.KanaKey(s.key, s.gesture, s.char), "$where, typed ${s.char}")
        }
    }

    /** The layer button down in a text mode: the layer is on; with the left stick still out, nothing arms until it is home. */
    private fun layerPress(button: PadButton): PadDecision {
        if (!isTenkey()) return PadDecision(emptyList(), "$button: no function in ${describeMode(padMode)} (the layer is for the text modes)")
        if (!layerOut) layerKey = keyFor(leftSector)
        if (lastLeftRadius >= config.flickReturnThreshold) {
            layerBlocked = true
            return PadDecision(emptyList(), "${layerName()} layer on; the left stick is still out: nothing until it comes back")
        }
        return PadDecision(emptyList(), "${layerName()} layer on (left stick: " + KanaGrid.layerChars(layoutMode).entries.joinToString(" ") { "${describeSector(it.key)} ${it.value}" } + ")")
    }

    /** Analog triggers 0..1. Ignored for a trigger that also sends key events. */
    fun onTriggers(l2: Float, r2: Float, timeMs: Long): PadDecision = withEnabledMode { triggers(l2, r2, timeMs) }

    private fun triggers(l2: Float, r2: Float, timeMs: Long): PadDecision {
        val actions = mutableListOf<PadAction>()
        val notes = mutableListOf<String>()
        for ((button, value) in listOf(PadButton.L2 to l2, PadButton.R2 to r2)) {
            if (button in triggerHasKeyEvents) continue
            val was = triggerPressed[button] ?: false
            val now = if (was) value > config.triggerThreshold * 0.6f else value >= config.triggerThreshold
            if (now != was) {
                triggerPressed[button] = now
                val d = onButton(button, now, timeMs, fromTriggerAxis = true)
                actions += d.actions
                if (d.note.isNotEmpty()) notes += "axis $button=${"%.2f".format(value)} → ${d.note}"
            }
        }
        return PadDecision(actions, notes.joinToString(", "))
    }

    // ------------------------------------------------------------------ touch digits (amendment 18)

    /**
     * A tap on one of the guide's touch digits (amendment 18: 1–9 and 0 in the left column of the
     * かな layout, in かな, 英字 and 数字). Types the digit exactly as 数字 mode types it from its grid
     * ([PadAction.InsertText], the flicked-character path the IME also uses for 数字), whatever text
     * mode is showing, so composing text is handled the same way. Holds, the mode and the sticks are
     * left alone. Nothing in the emoji modes (they draw no digits).
     */
    fun onTouchDigit(digit: Char): PadDecision {
        if (digit !in '0'..'9') return PadDecision(emptyList(), "touch: '$digit' is not a digit")
        if (!isTenkey()) return PadDecision(emptyList(), "touch digit $digit ignored in ${describeMode(padMode)}")
        val cell = FourWayLayouts.NUMBER.indexOfFirst { digit in it }
        val where = if (cell < 0) "" else " (the number grid's ${describeSector(Sector9.fromIndex(cell))} ${Dir4.entries[FourWayLayouts.NUMBER[cell].indexOf(digit)].arrow})"
        return PadDecision.of(PadAction.InsertText(digit.toString()), "touch digit $digit typed as in number mode$where, in ${describeMode(padMode)}")
    }

    // ------------------------------------------------------------------ buttons

    fun onButton(button: PadButton, pressed: Boolean, timeMs: Long): PadDecision =
        withEnabledMode { onButton(button, pressed, timeMs, fromTriggerAxis = false) }

    private fun onButton(button: PadButton, pressed: Boolean, timeMs: Long, fromTriggerAxis: Boolean): PadDecision {
        if (!fromTriggerAxis && (button == PadButton.L2 || button == PadButton.R2)) triggerHasKeyEvents += button
        if (pressed) {
            if (button in held) return PadDecision(emptyList(), "repeat ignored") // system auto-repeat
            held += button
            if (config.stickSource == StickSource.AUTO && declaredSticks && !stickMotionSeen && !fallback &&
                ++pressesWithoutMotion >= FALLBACK_AFTER_PRESSES) {
                fallback = true
                val d = if (padMode == PadMode.EMOJI_LIST) onPressEmojiList(button, timeMs) else onPressFallback(button, timeMs)
                return PadDecision(d.actions, "no stick motion after $FALLBACK_AFTER_PRESSES presses: D-pad layout; ${d.note}")
            }
        } else {
            if (button !in held) return PadDecision.NONE
            held -= button
        }
        if (button == PadButton.R3) r3Tilt(pressed)
        if (!pressed) return onRelease(button)
        if (padMode == PadMode.EMOJI_LIST) return onPressEmojiList(button, timeMs)
        return if (fallback) onPressFallback(button, timeMs) else onPress(button, timeMs)
    }

    /**
     * Clicking the right stick tilts it: no flick may start from that, and after the click the stick
     * must come home first. Amendment 18: a flick that is already armed when R3 goes down is not
     * cancelled any more (a hard flick pushed the stick in: the click is ignored, the flick types).
     */
    private fun r3Tilt(pressed: Boolean) {
        if (pressed) {
            r3Held = true
            rightBlockedUntilReturn = false
        } else {
            r3Held = false
            rightBlockedUntilReturn = !rightOut && lastRightRadius > config.flickReturnThreshold
        }
    }

    private fun onRelease(button: PadButton): PadDecision {
        if (repeating?.source == button) repeating = null
        // Amendment 18: a mode step fires on release (if its guard let it).
        modeClicks.remove(button)?.let { return modeStepRelease(button, it) }
        if (!fallback && config.roleOf(button) == PadRole.MODE_PREVIOUS && padMode == PadMode.EMOJI) return barRelease()
        if (!fallback && config.roleOf(button) == PadRole.YA_WA_LAYER && isTenkey() && !layerHeld) {
            layerBlocked = false
            return PadDecision(emptyList(), if (layerOut) "${layerName()} layer off (the flick that is out still types its character)" else "${layerName()} layer off")
        }
        val lp = longPress
        if (lp != null && lp.button == button) {
            longPress = null
            if (!lp.fired) return touchKeyboardTap()
            return PadDecision(emptyList(), "$button released after the long press")
        }
        return PadDecision.NONE
    }

    private fun onPress(button: PadButton, timeMs: Long): PadDecision {
        if (button.isDpad) return dpadAction(button)
        val role = config.roleOf(button)
        if (role == PadRole.TOUCH_KEYBOARD) {
            longPress = LongPress(button, timeMs + config.longPressMs)
            return PadDecision(emptyList(), "$button down (tap: touch keyboard / guide, hold: hide keyboard)")
        }
        return roleAction(role, button, timeMs)
    }

    private fun onPressFallback(button: PadButton, timeMs: Long): PadDecision {
        if (button.isDpad) {
            if (candidatesShown) return dpadAction(button)
            return PadDecision(emptyList(), "select ${describeSelection()}")
        }
        val sector = currentSector()
        val bar = barActive
        if (isWheel()) {
            Daisywheel.positionOf(button)?.let { pos ->
                val petal = sector.petal ?: return PadDecision(emptyList(), "no petal selected (hold D-pad)")
                return wheelInsert(petal, pos, button.name)
            }
            return when (button) {
                PadButton.START -> openEmojiList()
                PadButton.SELECT -> if (bar) PadDecision.of(PadAction.HideKeyboard, "hide") else backspace(timeMs, button)
                PadButton.L2 -> PadDecision.of(PadAction.Space, "space")
                PadButton.R2 -> PadDecision.of(PadAction.ConvertOrNextCandidate, "convert / next")
                PadButton.R1 -> cycleMode()
                PadButton.R3 -> emojiToggle()
                else -> PadDecision(emptyList(), "no function: $button")
            }
        }
        val gesture = when (button) {
            PadButton.A -> GestureType.Tap
            PadButton.X -> GestureType.FlickLeft
            PadButton.Y -> GestureType.FlickTop
            PadButton.B -> GestureType.FlickRight
            PadButton.L2 -> GestureType.FlickBottom
            else -> null
        }
        if (gesture != null) {
            if (bar) return typeBar(sectorOf(gesture), button.name)
            if (gesture == GestureType.Tap && isFourWay()) return PadDecision.of(PadAction.Space, "$button: space (${describeMode(padMode)} has no centre character)")
            return typeKey(keyFor(sector), gesture)
        }
        return when (button) {
            PadButton.START -> PadDecision.of(PadAction.Confirm, "confirm")
            PadButton.SELECT -> if (bar) PadDecision.of(PadAction.HideKeyboard, "hide") else backspace(timeMs, button)
            PadButton.R2 -> if (bar) PadDecision.of(PadAction.Space, "space") else PadDecision.of(PadAction.ConvertOrNextCandidate, "convert / next")
            PadButton.R1 -> cycleMode()
            PadButton.R3 -> emojiToggle()
            else -> PadDecision(emptyList(), "no function: $button")
        }
    }

    /** The bar direction a fallback gesture button stands for (A centre, X ←, Y ↑, B →, L2 ↓). */
    private fun sectorOf(g: GestureType): Sector9 = when (g) {
        GestureType.FlickLeft -> Sector9.LEFT
        GestureType.FlickTop -> Sector9.UP
        GestureType.FlickRight -> Sector9.RIGHT
        GestureType.FlickBottom -> Sector9.DOWN
        else -> Sector9.CENTER
    }

    /**
     * Full emoji keyboard: D-pad moves, L2/R2 tabs, A selects. With sticks (amendment 20): B or the
     * EMOJI_JUMP button (Start) closes it and goes back to the text mode the user came from; X, Y,
     * L3 and R3 do nothing; a TOUCH_KEYBOARD tap (Select) closes it and shows the touch keyboard,
     * held it closes it and hides the keyboard; HIDE_KEYBOARD closes it and hides the keyboard. The
     * D-pad fallback keeps its v7 table: B / Start / R3 back to the wheel, R1 closes it and goes on
     * to the next text mode, Select closes it and hides the keyboard.
     */
    private fun onPressEmojiList(button: PadButton, timeMs: Long): PadDecision {
        fun cmd(c: EmojiKeyboardCommand, note: String) = PadDecision.of(PadAction.EmojiKeyboard(c), note)
        when (button) {
            PadButton.DPAD_LEFT -> return cmd(EmojiKeyboardCommand.MOVE_LEFT, "emoji list ←")
            PadButton.DPAD_UP -> return cmd(EmojiKeyboardCommand.MOVE_UP, "emoji list ↑")
            PadButton.DPAD_RIGHT -> return cmd(EmojiKeyboardCommand.MOVE_RIGHT, "emoji list →")
            PadButton.DPAD_DOWN -> return cmd(EmojiKeyboardCommand.MOVE_DOWN, "emoji list ↓")
            PadButton.L2 -> return cmd(EmojiKeyboardCommand.PREVIOUS_TAB, "emoji list: previous tab")
            PadButton.R2 -> return cmd(EmojiKeyboardCommand.NEXT_TAB, "emoji list: next tab")
            PadButton.A -> return cmd(EmojiKeyboardCommand.SELECT, "emoji list: insert")
            else -> {}
        }
        if (fallback) {
            val role = fallbackListRole(button)
            return when {
                button == PadButton.B || role == PadRole.EMOJI_JUMP -> {
                    padMode = PadMode.EMOJI
                    cmd(EmojiKeyboardCommand.CLOSE, "emoji list: back to wheel")
                }
                role == PadRole.MODE_SWITCH -> closeListAndCycle()
                role == PadRole.HIDE_KEYBOARD -> {
                    padMode = PadMode.EMOJI
                    PadDecision(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE), PadAction.HideKeyboard), "emoji list: close, hide keyboard")
                }
                else -> PadDecision(emptyList(), "no function in emoji list: $button")
            }
        }
        if (button == PadButton.X || button == PadButton.Y || button == PadButton.L3 || button == PadButton.R3) {
            return PadDecision(emptyList(), "no function in emoji list: $button")
        }
        val role = config.roleOf(button)
        return when {
            button == PadButton.B || role == PadRole.EMOJI_JUMP -> closeEmojiList(button.label)
            role == PadRole.CONFIRM -> cmd(EmojiKeyboardCommand.SELECT, "emoji list: insert")
            role == PadRole.TOUCH_KEYBOARD -> {
                longPress = LongPress(button, timeMs + config.longPressMs)
                PadDecision(emptyList(), "$button down in the emoji list (tap: close it, touch keyboard; hold: hide keyboard)")
            }
            role == PadRole.HIDE_KEYBOARD -> {
                val c = closeEmojiList(button.label)
                PadDecision(c.actions + PadAction.HideKeyboard, "${c.note}, hide keyboard")
            }
            else -> PadDecision(emptyList(), "no function in emoji list: $button")
        }
    }

    /** Amendment 20: close the emoji list and go back to the text mode the user came from. */
    private fun closeEmojiList(via: String): PadDecision {
        val d = enterMode(returnMode())
        return PadDecision(listOf(PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE)) + d.actions, "emoji list closed ($via), ${d.note}")
    }

    private fun closeListAndCycle(): PadDecision {
        val close = PadAction.EmojiKeyboard(EmojiKeyboardCommand.CLOSE)
        val d = cycleMode()
        return PadDecision(listOf(close) + d.actions, "emoji list: close, ${d.note}")
    }

    /** The fixed fallback table expressed as roles, for the emoji list. */
    private fun fallbackListRole(button: PadButton): PadRole = when (button) {
        PadButton.START, PadButton.R3 -> PadRole.EMOJI_JUMP
        PadButton.R1 -> PadRole.MODE_SWITCH
        PadButton.SELECT -> PadRole.HIDE_KEYBOARD
        else -> PadRole.NONE
    }

    private fun roleAction(role: PadRole, button: PadButton, timeMs: Long): PadDecision = when (role) {
        // Removed in v7 (amendment 18) and v8 (amendment 20); [PadConfig.roleOf] never returns them.
        PadRole.TAP_CENTER, PadRole.BAR, PadRole.MODE_SWITCH -> PadDecision(emptyList(), "$button: removed role ${role.name}")
        PadRole.MODE_PREVIOUS, PadRole.MODE_NEXT -> modeStepPress(button, role)
        PadRole.CONFIRM -> PadDecision.of(PadAction.Confirm, "confirm")
        PadRole.BACKSPACE -> backspace(timeMs, button)
        PadRole.SPACE -> PadDecision.of(PadAction.Space, "space")
        PadRole.CONVERT_NEXT -> PadDecision.of(PadAction.ConvertOrNextCandidate, "convert / next candidate")
        PadRole.PREVIOUS_CANDIDATE -> PadDecision.of(PadAction.PreviousCandidate, "previous candidate")
        // Handled in [onPress] (tap / long press); here only for completeness.
        PadRole.TOUCH_KEYBOARD -> touchKeyboardTap()
        PadRole.HIDE_KEYBOARD -> PadDecision.of(PadAction.HideKeyboard, "hide keyboard")
        PadRole.EMOJI_JUMP -> emojiJump()
        PadRole.YA_WA_LAYER -> layerPress(button)
        PadRole.HOLD_DAKUTEN, PadRole.HOLD_HANDAKUTEN, PadRole.HOLD_SMALL, PadRole.HOLD_SMALL_OR_HANDAKUTEN,
        PadRole.HOLD_UPPERCASE ->
            PadDecision(emptyList(), "hold ${role.name.lowercase()}" + when {
                !HoldTransforms.applies(role, padMode) -> " (nothing in ${describeMode(padMode)})"
                // Amendment 22: L1 / R1 in 英字 = the number layout while held.
                role.isNumberHold && padMode == PadMode.ENGLISH && !fallback -> " (english: the number layout while held)"
                else -> ""
            })
        PadRole.NONE -> PadDecision(emptyList(), "no function: $button")
    }

    // ------------------------------------------------------------------ L3 / R3 mode steps (amendment 18)

    /**
     * A MODE_PREVIOUS / MODE_NEXT button down. Text modes: nothing happens yet; the click is
     * remembered with the stick it belongs to (L3 the left, R3 the right; another button has no
     * stick and no guard) and vetoed at once if that stick is out or has a flick armed. The emoji
     * wheel keeps the v6 jobs of L3 and R3: the next 32 emoji (the bar) and the petal's first emoji.
     */
    private fun modeStepPress(button: PadButton, role: PadRole): PadDecision {
        val forward = role == PadRole.MODE_NEXT
        if (padMode == PadMode.EMOJI) {
            if (!forward) return barPress()
            val petal = leftSector.petal ?: return PadDecision(emptyList(), "$button: no petal selected")
            return wheelInsert(petal, Daisywheel.POS_LEFT, button.name)
        }
        if (padMode == PadMode.EMOJI_LIST) return PadDecision(emptyList(), "no function in emoji list: $button")
        val side = when (button) {
            PadButton.L3 -> StickSide.LEFT
            PadButton.R3 -> StickSide.RIGHT
            else -> null
        }
        val veto = when (side) {
            StickSide.LEFT -> when {
                layerOut -> "a flick was armed on the left stick"
                lastLeftRadius >= config.flickReturnThreshold -> "the left stick was out (${"%.2f".format(lastLeftRadius)})"
                else -> null
            }
            StickSide.RIGHT -> when {
                rightOut -> "a flick was armed on the right stick"
                lastRightRadius >= config.flickReturnThreshold -> "the right stick was out (${"%.2f".format(lastRightRadius)})"
                else -> null
            }
            null -> null
        }
        modeClicks[button] = ModeClick(forward, side, veto)
        val what = if (forward) "next" else "previous"
        return PadDecision(emptyList(), if (veto == null) "$button down: $what mode on release" else "$button down: click will be ignored ($veto)")
    }

    /** A MODE_PREVIOUS / MODE_NEXT button up: step the text mode, unless the guard vetoed the click. */
    private fun modeStepRelease(button: PadButton, click: ModeClick): PadDecision {
        val what = if (click.forward) "next" else "previous"
        click.veto?.let { return PadDecision(emptyList(), "$button click ignored: $it") }
        if (padMode.isEmoji) return PadDecision(emptyList(), "$button click ignored: now in ${describeMode(padMode)}")
        val next = config.stepMode(padMode, click.forward)
            ?: return PadDecision(emptyList(), "$button: only ${describeMode(padMode)} is on, nothing to switch to")
        val d = enterMode(next)
        return PadDecision(d.actions, "$button ($what mode): ${d.note}")
    }

    // ------------------------------------------------------------------ the bar (amendment 10; the D-pad fallback and the emoji wheel)

    /** BAR button down: a second press while armed turns the bar off; otherwise the bar is active while held. */
    private fun barPress(): PadDecision {
        if (barOneShot) {
            barOneShot = false
            barTyped = true
            return PadDecision(emptyList(), "bar off")
        }
        barTyped = false
        return PadDecision(emptyList(), "bar on in ${describeMode(padMode)} (held; release without typing = next character only)")
    }

    /** BAR button up: nothing typed while held = armed for one character (a tap); else off. */
    private fun barRelease(): PadDecision {
        if (barTyped || padMode == PadMode.EMOJI_LIST) return PadDecision(emptyList(), "bar off")
        barOneShot = true
        return PadDecision(emptyList(), "bar armed: the next character comes from the bar")
    }

    /** A character came from the bar: a one-shot is used up, a held bar is marked as used. */
    private fun consumeBar() {
        barOneShot = false
        barTyped = true
    }

    /**
     * Type one bar character. The touch key's own (key, gesture) goes out as [PadAction.KanaKey]
     * when Sumire's table has it; a transformed (R1 + わ → ゎ) or table-less character as
     * [PadAction.InsertText]. The note carries the direction and the stick angle for the log.
     */
    private fun typeBar(sector: Sector9, via: String, role: PadRole? = holdRole): PadDecision {
        val slot = KanaGrid.bar(padMode).slots[sector]
        consumeBar()
        val where = "bar ${describeSector(sector)} ($via)"
        if (slot == null) return PadDecision(emptyList(), "$where: no character")
        val typed = HoldTransforms.apply(role, slot.char, padMode)
        return when {
            typed != slot.char -> PadDecision.of(PadAction.InsertText(typed.toString()), "$where typed $typed (${role!!.name.lowercase()})")
            slot.key != null && slot.gesture != null -> PadDecision.of(PadAction.KanaKey(slot.key, slot.gesture, slot.char), "$where typed ${slot.char}")
            else -> PadDecision.of(PadAction.InsertText(slot.char.toString()), "$where typed ${slot.char}")
        }
    }

    /**
     * Type one tenkey key with a gesture and the active hold transform. Kana goes through Sumire's
     * own table: a plain character as [PadAction.KanaKey], a transformed one as
     * [PadAction.InsertText] (the IME appends it as a flicked character). 英字 / 数字 characters
     * come from [FourWayLayouts], which has no touch key, so they always go out as
     * [PadAction.InsertText] (the same flicked-character path). [mode] is the layout the characters
     * come from (amendment 22: 数字 while L1 / R1 is held in 英字).
     */
    private fun typeKey(key: Key, gesture: GestureType, role: PadRole? = holdRole, via: String? = null, mode: PadMode = padMode): PadDecision {
        val prefix = via?.let { "$it: " } ?: ""
        val base = KanaGrid.charFor(key, gesture, mode)
            ?: return PadDecision(emptyList(), prefix + "no character for ${KanaGrid.label(key, mode)} ${describeGesture(gesture)} in ${describeMode(mode)}")
        val typed = HoldTransforms.apply(role, base, mode)
        val held = if (mode != padMode) " (${describeMode(mode)} layout held)" else ""
        return if (typed != base) {
            val action = PadAction.InsertText(typed.toString())
            PadDecision.of(action, "${prefix}key ${KanaGrid.label(key, mode)}, ${describeGesture(gesture)}, typed $typed (${role!!.name.lowercase()})")
        } else if (FourWayLayouts.applies(mode)) {
            PadDecision.of(PadAction.InsertText(typed.toString()), "${prefix}key ${KanaGrid.label(key, mode)}, ${describeGesture(gesture)}, typed $typed$held")
        } else {
            val action = PadAction.KanaKey(key, gesture, base)
            PadDecision.of(action, prefix + describe(action))
        }
    }

    private fun wheelInsert(petal: Int, pos: Int, via: String): PadDecision {
        val cells = currentPetals()
        val page2 = barActive
        val s = cells[petal][pos]
        if (page2) consumeBar()
        if (s.isEmpty()) return PadDecision(emptyList(), "petal $petal $via: empty slot")
        return PadDecision.of(PadAction.InsertEmoji(s), "petal $petal $via → emoji $s" + if (page2) " (page 2)" else "")
    }

    private fun openEmojiList(): PadDecision {
        padMode = PadMode.EMOJI_LIST
        // Amendment 20: a left stick still out (it was selecting a key) moves nothing until it is home.
        listStickBlocked = !fallback && lastLeftRadius > config.leftReleaseThreshold
        return PadDecision.of(PadAction.EmojiKeyboard(EmojiKeyboardCommand.OPEN), "open emoji list")
    }

    private fun backspace(timeMs: Long, source: PadButton): PadDecision {
        repeating = Repeat(PadAction.Backspace, timeMs + config.repeatDelayMs, source)
        return PadDecision.of(PadAction.Backspace, "backspace")
    }

    /**
     * MODE_SWITCH: the next enabled text mode (かな → 英字 → 数字 by default, amendment 12; emoji
     * stays on Start). From the emoji modes: back to the text mode. With one text mode a tap
     * does nothing.
     */
    private fun cycleMode(): PadDecision {
        val modes = config.textModes()
        if (padMode.isEmoji) return enterMode(returnMode())
        if (modes.size <= 1 && padMode in modes) return PadDecision(emptyList(), "mode: only ${describeMode(padMode)} is on, nothing to switch to")
        val i = modes.indexOf(padMode)
        return enterMode(modes[(i + 1) % modes.size])
    }

    /** The text mode to go back to: the one we came from if it is enabled, else the first enabled one. */
    private fun returnMode(): PadMode {
        val modes = config.textModes()
        val from = PadMode.from(sumireMode)
        return if (from in modes) from else modes.first()
    }

    /**
     * Amendment 20, a short press of the TOUCH_KEYBOARD button (Select): the touch keyboard ⇄ the
     * guide. From the emoji list: close it, back to the text mode, then the touch keyboard.
     */
    private fun touchKeyboardTap(): PadDecision {
        if (padMode == PadMode.EMOJI_LIST) {
            val c = closeEmojiList("Select")
            return PadDecision(c.actions + PadAction.TouchKeyboard, "${c.note}, touch keyboard")
        }
        return PadDecision.of(PadAction.TouchKeyboard, "touch keyboard / guide")
    }

    /**
     * EMOJI_JUMP (Start), amendment 20: open the full emoji list from a text mode; in the list, close
     * it and go back to the text mode. (The wheel stays in the D-pad fallback: R3, then Start.)
     */
    private fun emojiJump(): PadDecision = when (padMode) {
        PadMode.EMOJI_LIST -> closeEmojiList("Start")
        else -> openEmojiList()
    }

    /** Fallback R3 (v1 behaviour): emoji wheel, or back to the text mode from the emoji modes. */
    private fun emojiToggle(): PadDecision =
        if (padMode.isEmoji) enterMode(returnMode()) else enterMode(PadMode.EMOJI)

    private fun enterMode(next: PadMode): PadDecision {
        padMode = next
        val inputMode = next.inputMode
        return if (inputMode != null) {
            sumireMode = inputMode
            PadDecision.of(PadAction.SwitchMode(inputMode), "mode → ${describeMode(next)}")
        } else {
            PadDecision(emptyList(), "mode → ${describeMode(next)}")
        }
    }

    private fun dpadAction(button: PadButton): PadDecision {
        val dir = when (button) {
            PadButton.DPAD_UP -> Dir4.UP
            PadButton.DPAD_DOWN -> Dir4.DOWN
            PadButton.DPAD_LEFT -> Dir4.LEFT
            else -> Dir4.RIGHT
        }
        return PadDecision.of(PadAction.Dpad(dir), if (candidatesShown) "candidate $dir" else "cursor $dir")
    }

    // ------------------------------------------------------------------ repeat / long press

    /** Call periodically (about every [PadConfig.repeatIntervalMs]) while [isRepeating]. */
    fun pollRepeat(timeMs: Long): PadDecision {
        val lp = longPress
        if (lp != null && !lp.fired && timeMs >= lp.at) {
            lp.fired = true
            if (padMode == PadMode.EMOJI_LIST) {
                val c = closeEmojiList("${lp.button.label} held")
                return PadDecision(c.actions + PadAction.HideKeyboard, "${c.note}, hide keyboard")
            }
            return PadDecision.of(PadAction.HideKeyboard, "${lp.button.label} held: hide keyboard")
        }
        val rep = repeating ?: return PadDecision.NONE
        if (timeMs < rep.nextAt) return PadDecision.NONE
        rep.nextAt = timeMs + config.repeatIntervalMs
        return PadDecision.of(rep.action, "repeat ${describe(rep.action)}")
    }

    /** True while a repeat or a pending long press needs [pollRepeat] calls. */
    val isRepeating: Boolean get() = repeating != null || longPress?.fired == false

    // ------------------------------------------------------------------ text

    private fun describeSelection(): String {
        val s = currentSector()
        return when {
            isWheel() -> "petal ${describePetal(s)}"
            barActive -> "the bar"
            else -> "key ${KanaGrid.label(keyFor(s), padMode)}"
        }
    }

    private fun describePetal(s: Sector9): String {
        val p = s.petal ?: return "none"
        return "$p (${currentPetals().getOrNull(p)?.joinToString("") ?: ""})"
    }

    companion object {
        /** AUTO with declared sticks: presses without any stick motion before the D-pad layout takes over. */
        const val FALLBACK_AFTER_PRESSES = 8

        /** Flick direction → slot inside an emoji petal (← left, ↑ top, → right, ↓ bottom). */
        val Dir4.petalIndex: Int
            get() = when (this) {
                Dir4.LEFT -> Daisywheel.POS_LEFT
                Dir4.UP -> Daisywheel.POS_TOP
                Dir4.RIGHT -> Daisywheel.POS_RIGHT
                Dir4.DOWN -> Daisywheel.POS_BOTTOM
            }

        /** The arrow of a four-way direction, for the log. */
        val Dir4.arrow: String
            get() = when (this) {
                Dir4.LEFT -> "←"; Dir4.UP -> "↑"; Dir4.RIGHT -> "→"; Dir4.DOWN -> "↓"
            }

        fun describeSector(s: Sector9): String = when (s) {
            Sector9.UP_LEFT -> "↖"; Sector9.UP -> "↑"; Sector9.UP_RIGHT -> "↗"
            Sector9.LEFT -> "←"; Sector9.CENTER -> "centre"; Sector9.RIGHT -> "→"
            Sector9.DOWN_LEFT -> "↙"; Sector9.DOWN -> "↓"; Sector9.DOWN_RIGHT -> "↘"
        }

        fun describeMode(mode: InputMode): String = describeMode(PadMode.from(mode))

        /** Mode names in the diagnostics log (English, whatever the UI language). */
        fun describeMode(mode: PadMode): String = when (mode) {
            PadMode.KANA -> "kana"
            PadMode.ENGLISH -> "english"
            PadMode.NUMBER -> "number"
            PadMode.EMOJI -> "emoji"
            PadMode.EMOJI_LIST -> "emoji list"
        }

        fun describeGesture(g: GestureType): String = when (g) {
            GestureType.Tap -> "tap"
            GestureType.FlickLeft -> "flick ←"
            GestureType.FlickTop -> "flick ↑"
            GestureType.FlickRight -> "flick →"
            GestureType.FlickBottom -> "flick ↓"
            else -> g.toString()
        }

        fun describe(action: PadAction): String = when (action) {
            is PadAction.KanaKey -> {
                val typed = action.char?.let { "typed $it" } ?: "modifier key"
                "key ${KanaGrid.label(action.key)}, ${describeGesture(action.gesture)}, $typed"
            }
            is PadAction.InsertText -> "typed ${action.text}"
            is PadAction.InsertEmoji -> "emoji ${action.emoji}"
            is PadAction.EmojiKeyboard -> "emoji keyboard ${action.command}"
            PadAction.ConvertOrNextCandidate -> "convert / next candidate"
            PadAction.PreviousCandidate -> "previous candidate"
            PadAction.Confirm -> "confirm"
            PadAction.Backspace -> "backspace"
            PadAction.Space -> "space"
            is PadAction.SwitchMode -> "mode → ${describeMode(action.mode)}"
            is PadAction.Dpad -> "d-pad ${action.direction}"
            PadAction.HideKeyboard -> "hide keyboard"
            PadAction.TouchKeyboard -> "touch keyboard / guide"
        }
    }
}
