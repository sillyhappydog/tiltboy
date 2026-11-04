package com.kazumaproject.pad_input

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Face-button arrangements (Nintendo vs Xbox) and the RG477V descriptor. Since amendment 4
 * the arrangement matters only in the D-pad fallback (characters by button); with sticks the
 * right-stick flick picks the character, so no calibration is needed.
 */
class FaceLayoutTest {

    private fun driver(layout: FaceLayout): PadDriver =
        PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.STICKS, faceLayout = layout)))

    @Test
    fun presets() {
        assertEquals(PadButton.X, FaceLayout.NINTENDO.top)
        assertEquals(PadButton.Y, FaceLayout.NINTENDO.left)
        assertEquals(PadButton.A, FaceLayout.NINTENDO.right)
        assertEquals(PadButton.B, FaceLayout.NINTENDO.bottom)
        assertEquals(PadButton.Y, FaceLayout.XBOX.top)
        assertEquals(PadButton.X, FaceLayout.XBOX.left)
        assertEquals(PadButton.B, FaceLayout.XBOX.right)
        assertEquals(PadButton.A, FaceLayout.XBOX.bottom)
        assertEquals(FacePosition.TOP, FaceLayout.NINTENDO.positionOf(PadButton.X))
        assertNull(FaceLayout.NINTENDO.positionOf(PadButton.L1))
        assertEquals("nintendo", FaceLayout.NINTENDO.name)
        assertEquals("custom", FaceLayout(PadButton.A, PadButton.B, PadButton.X, PadButton.Y).name)
    }

    @Test
    fun with_sticks_the_arrangement_does_not_matter() {
        // The flick picks the character on both layouts; the face buttons keep their roles.
        for (layout in listOf(FaceLayout.XBOX, FaceLayout.NINTENDO)) {
            val d = driver(layout)
            d.left(0.1f, 0f); d.nextMode(); d.clear()
            d.holdSector(Sector9.UP)
            d.tap(PadButton.X); d.flick(Dir4.LEFT); d.flick(Dir4.UP)
            // Amendment 14: the ↑ cell is efgh; X is space (amendment 18: R3 steps the mode).
            assertEquals("$layout", listOf(PadAction.Space, PadAction.InsertText("e"), PadAction.InsertText("f")), d.actions)
            d.clear()
            d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.A)
            assertEquals("$layout", listOf(PadAction.Space, PadAction.ConvertOrNextCandidate, PadAction.Backspace, PadAction.Confirm), d.actions)
        }
    }

    @Test
    fun emoji_wheel_in_the_fallback_is_by_printed_name_too() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK, faceLayout = FaceLayout.NINTENDO)))
        d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸"))
        d.tap(PadButton.R3)
        d.press(PadButton.DPAD_UP)
        d.tap(PadButton.X); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.A)
        d.release(PadButton.DPAD_UP)
        assertEquals(listOf("🐱", "🍣", "🎮", "🌸"), d.actions.filterIsInstance<PadAction.InsertEmoji>().map { it.emoji })
    }

    @Test
    fun kana_roles_stay_by_printed_label_on_both_layouts() {
        for (layout in listOf(FaceLayout.NINTENDO, FaceLayout.XBOX)) {
            val d = driver(layout)
            d.kanaTap(); d.tap(PadButton.A); d.tap(PadButton.Y); d.tap(PadButton.B); d.tap(PadButton.X)
            assertEquals("$layout", listOf("あ", null, null, null, null), d.actions.map { (it as? PadAction.KanaKey)?.char?.toString() })
            assertEquals("$layout", listOf(PadAction.Confirm, PadAction.ConvertOrNextCandidate, PadAction.Backspace, PadAction.Space), d.actions.drop(1))
        }
    }

    @Test
    fun layouts_from_stored_names() {
        assertEquals(FaceLayout.NINTENDO, FaceLayout.fromNames("X", "Y", "A", "B"))
        assertEquals(FaceLayout.XBOX, FaceLayout.fromNames("Y", "X", "B", "A"))
        val swapped = FaceLayout.fromNames("X", "Y", "B", "A")!!
        assertEquals("custom", swapped.name)
        assertEquals(FacePosition.RIGHT, swapped.positionOf(PadButton.B))
        assertNull("missing", FaceLayout.fromNames("X", "Y", "A", null))
        assertNull("duplicate", FaceLayout.fromNames("X", "X", "A", "B"))
        assertNull("not a face button", FaceLayout.fromNames("L1", "Y", "A", "B"))
        assertEquals(FaceLayout.NINTENDO, FaceLayout.byName("nintendo"))
        assertNull(FaceLayout.byName("auto"))
    }

    @Test
    fun rg477v_descriptor_means_sticks_analog_triggers_and_nintendo_layout() {
        assertTrue(DeviceProfile.hasSticks(DeviceProfile.RG477V_AXES))
        assertFalse(DeviceProfile.rightStickUsesRx(DeviceProfile.RG477V_AXES))
        assertTrue(DeviceProfile.hasAnalogTriggers(DeviceProfile.RG477V_AXES))
        assertEquals(FaceLayout.NINTENDO, DeviceProfile.defaultFaceLayout(DeviceProfile.RG477V_DEVICE_NAME))
        assertEquals(FaceLayout.NINTENDO, DeviceProfile.defaultFaceLayout("Some Pad", "Anbernic"))
        assertEquals(FaceLayout.XBOX, DeviceProfile.defaultFaceLayout("Xbox Wireless Controller"))
        assertEquals(FaceLayout.XBOX, DeviceProfile.defaultFaceLayout(null))
        // AUTO picks the stick layout for this device as soon as the descriptor is known.
        val e = PadInputEngine()
        e.setDeviceHasSticks(DeviceProfile.hasSticks(DeviceProfile.RG477V_AXES))
        assertFalse(e.fallback)
        // A D-pad-only device keeps the fallback.
        val e2 = PadInputEngine()
        e2.setDeviceHasSticks(DeviceProfile.hasSticks(setOf(DeviceProfile.AXIS_HAT_X, DeviceProfile.AXIS_HAT_Y)))
        assertTrue(e2.fallback)
    }

    @Test
    fun vendor_id_picks_the_nintendo_arrangement() {
        assertEquals(FaceLayout.NINTENDO, DeviceProfile.defaultFaceLayout("whatever", null, DeviceProfile.ANBERNIC_VENDOR_ID))
        assertEquals(FaceLayout.XBOX, DeviceProfile.defaultFaceLayout("whatever", null, 0x045e))
    }

    /** v14: the AYN Thor's own controller is Nintendo (measured 2026-11-04); an Xbox pad plugged into it is not. */
    @Test
    fun ayn_thor_builtin_controller_is_nintendo() {
        assertEquals(FaceLayout.NINTENDO, DeviceProfile.defaultFaceLayout("Odin Controller", "AYN", DeviceProfile.AYN_ODIN_VENDOR_ID, "AYN Thor"))
        assertEquals("other AYN models are unverified", FaceLayout.XBOX, DeviceProfile.defaultFaceLayout("Odin Controller", "AYN", DeviceProfile.AYN_ODIN_VENDOR_ID, "Odin2"))
        assertEquals("an external pad on the Thor", FaceLayout.XBOX, DeviceProfile.defaultFaceLayout("Xbox Wireless Controller", "AYN", 0x045e, "AYN Thor"))
    }

    @Test
    fun fallback_guide_labels_show_the_printed_buttons_at_their_positions() {
        val d = PadDriver(PadInputEngine(PadConfig(stickSource = StickSource.DPAD_FALLBACK, faceLayout = FaceLayout.NINTENDO)))
        d.engine.setEmojiHistory(listOf("🐱", "🍣", "🎮", "🌸", "🔥", "👍"))
        d.tap(PadButton.R3) // fallback: emoji wheel (the only wheel left)
        d.press(PadButton.DPAD_RIGHT)
        val g = d.engine.guide
        assertEquals(FaceLayout.NINTENDO, g.faceLayout)
        assertEquals(PadMode.EMOJI, g.padMode)
        assertTrue(g.fallback)
        val r = GuideLayout.layout(g, GuideLayout.GuideCanvas(912f, 250f, 2f))
        val labels = r.texts.filter { it.style == GuideLayout.TextStyle.LABEL && it.text in setOf("A", "B", "X", "Y") }
        val top = labels.minByOrNull { it.y }!!
        val left = labels.minByOrNull { it.x }!!
        assertEquals("X", top.text)
        assertEquals("Y", left.text)
    }
}
