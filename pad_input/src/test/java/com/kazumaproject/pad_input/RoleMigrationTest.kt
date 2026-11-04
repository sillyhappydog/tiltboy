package com.kazumaproject.pad_input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amendments 4, 7, 10 and 11: stored roles move to the new defaults only where the user never changed them.
 * Amendment 16: a map still equal to the v4 defaults moves to the v5 defaults as a whole; a customised one is kept.
 * Amendment 18 (roles version 6): a map still equal to the v5 / v6 defaults moves to the v7 defaults (L3 the
 * previous mode, R3 the next mode); a customised one is kept, except that a button set to a removed role (the
 * bar, the centre character) gets its v7 default; a v7 map is left alone.
 * Amendment 20 (roles version 7): Select's モード (MODE_SWITCH, stored or the v7 default) becomes the touch
 * keyboard (TOUCH_KEYBOARD); MODE_SWITCH on another button becomes MODE_NEXT; nothing else changes.
 */
class RoleMigrationTest {

    @Test
    fun current_defaults_and_version() {
        assertEquals(PadRole.MODE_NEXT, PadConfig.DEFAULT_ROLES[PadButton.R3])
        assertEquals(PadRole.MODE_PREVIOUS, PadConfig.DEFAULT_ROLES[PadButton.L3])
        assertEquals(PadRole.EMOJI_JUMP, PadConfig.DEFAULT_ROLES[PadButton.START])
        assertEquals(PadRole.HOLD_DAKUTEN, PadConfig.DEFAULT_ROLES[PadButton.L2])
        assertEquals(PadRole.HOLD_HANDAKUTEN, PadConfig.DEFAULT_ROLES[PadButton.L1])
        assertEquals(PadRole.HOLD_SMALL, PadConfig.DEFAULT_ROLES[PadButton.R1])
        assertEquals(PadRole.YA_WA_LAYER, PadConfig.DEFAULT_ROLES[PadButton.R2])
        assertEquals(PadRole.CONFIRM, PadConfig.DEFAULT_ROLES[PadButton.A])
        assertEquals(PadRole.CONVERT_NEXT, PadConfig.DEFAULT_ROLES[PadButton.Y])
        assertEquals(PadRole.TOUCH_KEYBOARD, PadConfig.DEFAULT_ROLES[PadButton.SELECT])
        assertEquals(PadRole.MODE_SWITCH, PadConfig.V7_DEFAULT_ROLES[PadButton.SELECT])
        assertEquals(7, PadConfig.ROLES_VERSION)
        assertEquals(setOf(PadButton.L1, PadButton.L2, PadButton.R2), PadConfig.V5_CHANGED)
        assertTrue("no removed role is a default", PadConfig.DEFAULT_ROLES.values.none { it.isRemoved })
        assertEquals(PadRole.BAR, PadConfig.V5_DEFAULT_ROLES[PadButton.L3])
        assertEquals(PadRole.TAP_CENTER, PadConfig.V5_DEFAULT_ROLES[PadButton.R3])
        assertEquals("v5 / v6 and v7 differ on L3 and R3 only", setOf(PadButton.L3, PadButton.R3),
            PadConfig.V7_DEFAULT_ROLES.keys.filter { PadConfig.V7_DEFAULT_ROLES[it] != PadConfig.V5_DEFAULT_ROLES[it] }.toSet())
        assertEquals("v7 and v8 differ on Select only", setOf(PadButton.SELECT),
            PadConfig.DEFAULT_ROLES.keys.filter { PadConfig.DEFAULT_ROLES[it] != PadConfig.V7_DEFAULT_ROLES[it] }.toSet())
    }

    @Test
    fun untouched_v1_defaults_move_to_the_new_defaults() {
        val stored = mapOf<PadButton, PadRole?>(
            PadButton.L1 to PadRole.BAR, // "SHIFT" in v1
            PadButton.L2 to PadRole.PREVIOUS_CANDIDATE,
            PadButton.R1 to PadRole.MODE_SWITCH,
            PadButton.SELECT to PadRole.HIDE_KEYBOARD,
            PadButton.R3 to PadRole.EMOJI_JUMP,
            PadButton.START to PadRole.NONE,
            PadButton.A to PadRole.TAP_CENTER,
            PadButton.Y to PadRole.CONFIRM,
            PadButton.L3 to PadRole.NONE,
        )
        assertEquals(
            mapOf(
                PadButton.L1 to PadRole.HOLD_HANDAKUTEN, PadButton.L2 to PadRole.HOLD_DAKUTEN,
                PadButton.R1 to PadRole.HOLD_SMALL, PadButton.SELECT to PadRole.TOUCH_KEYBOARD,
                PadButton.R3 to PadRole.MODE_NEXT, PadButton.START to PadRole.EMOJI_JUMP,
                PadButton.A to PadRole.CONFIRM, PadButton.Y to PadRole.CONVERT_NEXT, PadButton.L3 to PadRole.MODE_PREVIOUS,
                PadButton.R2 to PadRole.YA_WA_LAYER,
            ),
            PadConfig.migrateRoles(stored, fromVersion = 1),
        )
    }

    @Test
    fun never_stored_counts_as_default() {
        val out = PadConfig.migrateRoles(emptyMap(), fromVersion = 1)
        assertEquals(PadRole.HOLD_HANDAKUTEN, out[PadButton.L1])
        assertEquals(PadRole.YA_WA_LAYER, out[PadButton.R2])
        assertEquals("an install that never stored a role ends on the v7 defaults", PadConfig.DEFAULT_ROLES, PadConfig.DEFAULT_ROLES.mapValues { out[it.key] ?: it.value })
        assertEquals(PadRole.EMOJI_JUMP, out[PadButton.START])
        assertEquals(PadRole.CONFIRM, out[PadButton.A])
        assertEquals(PadRole.MODE_PREVIOUS, out[PadButton.L3])
        assertEquals(PadRole.MODE_NEXT, out[PadButton.R3])
        for (from in 1..6) assertEquals("from $from", PadConfig.DEFAULT_ROLES, PadConfig.DEFAULT_ROLES.mapValues { PadConfig.migrateRoles(emptyMap(), from)[it.key] ?: it.value })
    }

    @Test
    fun v2_intermediate_defaults_move_too() {
        val stored = mapOf<PadButton, PadRole?>(
            PadButton.L1 to PadRole.BAR, PadButton.L2 to PadRole.PREVIOUS_CANDIDATE,
            PadButton.R1 to PadRole.MODE_SWITCH, PadButton.SELECT to PadRole.HIDE_KEYBOARD,
            PadButton.R3 to PadRole.TAP_CENTER, PadButton.START to PadRole.EMOJI_JUMP,
            PadButton.A to PadRole.TAP_CENTER, PadButton.Y to PadRole.CONFIRM,
        )
        val out = PadConfig.migrateRoles(stored, fromVersion = 2)
        assertEquals(setOf(PadButton.L1, PadButton.L2, PadButton.R1, PadButton.SELECT, PadButton.A, PadButton.Y, PadButton.L3, PadButton.R2, PadButton.R3), out.keys)
        assertEquals(PadRole.HOLD_SMALL, out[PadButton.R1])
        assertEquals(PadRole.HOLD_DAKUTEN, out[PadButton.L2])
        assertEquals(PadRole.MODE_NEXT, out[PadButton.R3])
    }

    @Test
    fun amendment_7_defaults_move_to_amendments_10_and_11() {
        // What apk/sumire-pad-v2.apk of amendments 6–9 stored: L2 HOLD_BOTTOM_ROW (now BAR), R1 小文字・゜, A あ段, Y 確定.
        val stored = mapOf<PadButton, PadRole?>(
            PadButton.L1 to PadRole.HOLD_DAKUTEN, PadButton.L2 to PadRole.byName("HOLD_BOTTOM_ROW"),
            PadButton.R1 to PadRole.HOLD_SMALL_OR_HANDAKUTEN, PadButton.L3 to PadRole.NONE,
            PadButton.A to PadRole.TAP_CENTER, PadButton.Y to PadRole.CONFIRM,
        )
        val out = PadConfig.migrateRoles(stored, fromVersion = 3)
        assertEquals(
            mapOf(
                PadButton.L2 to PadRole.HOLD_DAKUTEN, PadButton.R1 to PadRole.HOLD_SMALL, PadButton.A to PadRole.CONFIRM,
                PadButton.Y to PadRole.CONVERT_NEXT, PadButton.L3 to PadRole.MODE_PREVIOUS,
                PadButton.L1 to PadRole.HOLD_HANDAKUTEN, PadButton.R2 to PadRole.YA_WA_LAYER, PadButton.R3 to PadRole.MODE_NEXT,
                PadButton.SELECT to PadRole.TOUCH_KEYBOARD,
            ),
            out,
        )
    }

    @Test
    fun user_changes_survive() {
        val stored = mapOf<PadButton, PadRole?>(
            PadButton.L1 to PadRole.CONFIRM,            // changed
            PadButton.L2 to PadRole.PREVIOUS_CANDIDATE, // v1 default
            PadButton.R1 to PadRole.SPACE,              // changed
            PadButton.SELECT to PadRole.HIDE_KEYBOARD,  // v1 default
            PadButton.R3 to PadRole.CONFIRM,            // changed
            PadButton.START to PadRole.NONE,            // v1 default
            PadButton.A to PadRole.SPACE,               // changed
            PadButton.Y to PadRole.CONFIRM,             // v1 default
            PadButton.B to PadRole.SPACE,               // changed, never a migrated button
        )
        val out = PadConfig.migrateRoles(stored, fromVersion = 1)
        assertEquals(
            mapOf(
                PadButton.L2 to PadRole.HOLD_HANDAKUTEN, PadButton.SELECT to PadRole.TOUCH_KEYBOARD, PadButton.START to PadRole.EMOJI_JUMP,
                PadButton.Y to PadRole.CONVERT_NEXT,
                // v4 gave L3 the bar, which v7 removed: L3 gets its v7 default.
                PadButton.L3 to PadRole.MODE_PREVIOUS,
            ),
            out,
        )
        assertTrue(PadButton.B !in out && PadButton.L1 !in out && PadButton.R1 !in out && PadButton.R3 !in out && PadButton.A !in out)
    }

    @Test
    fun untouched_v4_defaults_move_to_v7() {
        // What apk/tiltboy-v4.apk stores: the whole v4 map.
        val out = PadConfig.migrateRoles(PadConfig.V4_DEFAULT_ROLES, fromVersion = 4)
        assertEquals(mapOf(PadButton.L1 to PadRole.HOLD_HANDAKUTEN, PadButton.L2 to PadRole.HOLD_DAKUTEN, PadButton.R2 to PadRole.YA_WA_LAYER,
            PadButton.L3 to PadRole.MODE_PREVIOUS, PadButton.R3 to PadRole.MODE_NEXT, PadButton.SELECT to PadRole.TOUCH_KEYBOARD), out)
        assertEquals(PadConfig.DEFAULT_ROLES, PadConfig.V4_DEFAULT_ROLES + out)
        // Some buttons never stored (the defaults were never written): the same.
        assertEquals(out, PadConfig.migrateRoles(mapOf(PadButton.A to PadRole.CONFIRM), fromVersion = 4))
    }

    @Test
    fun a_customised_v4_map_keeps_everything_but_the_removed_roles() {
        // Any one change anywhere keeps every role, including L1 / L2 / R2; only L3 (the bar) and R3 (the
        // centre character) were removed in v7 and get their v7 defaults.
        for ((button, role) in listOf(PadButton.X to PadRole.CONFIRM, PadButton.R2 to PadRole.HOLD_SMALL, PadButton.L1 to PadRole.HOLD_HANDAKUTEN, PadButton.SELECT to PadRole.HIDE_KEYBOARD)) {
            val stored = PadConfig.V4_DEFAULT_ROLES + (button to role)
            val select = if (button == PadButton.SELECT) emptyMap() else mapOf(PadButton.SELECT to PadRole.TOUCH_KEYBOARD)
            assertEquals("$button → $role", mapOf(PadButton.L3 to PadRole.MODE_PREVIOUS, PadButton.R3 to PadRole.MODE_NEXT) + select, PadConfig.migrateRoles(stored, fromVersion = 4))
        }
        // A v3 install with a change of its own goes to v4 per button and stops there (bar the removed roles).
        val v3 = mapOf<PadButton, PadRole?>(PadButton.A to PadRole.TAP_CENTER, PadButton.B to PadRole.SPACE)
        val out = PadConfig.migrateRoles(v3, fromVersion = 3)
        assertEquals(PadRole.CONFIRM, out[PadButton.A])
        assertEquals("L2 to its v4 default", PadRole.HOLD_HANDAKUTEN, out[PadButton.L2])
        assertTrue("v4 roles for L1 / R2", PadButton.L1 !in out && PadButton.R2 !in out)
    }

    @Test
    fun the_v6_default_map_moves_to_v7() {
        // What apk/tiltboy-v5.apk and -v6.apk store (roles version 5): the whole v5 map.
        val out = PadConfig.migrateRoles(PadConfig.V5_DEFAULT_ROLES, fromVersion = 5)
        assertEquals(mapOf(PadButton.L3 to PadRole.MODE_PREVIOUS, PadButton.R3 to PadRole.MODE_NEXT, PadButton.SELECT to PadRole.TOUCH_KEYBOARD), out)
        assertEquals(PadConfig.DEFAULT_ROLES, PadConfig.V5_DEFAULT_ROLES + out)
        // Nothing stored at all (v5 / v6 never wrote the defaults): the same.
        assertEquals(out, PadConfig.migrateRoles(emptyMap(), fromVersion = 5))
    }

    @Test
    fun a_customised_v6_map_is_kept_except_the_removed_roles() {
        // A change elsewhere: L3 / R3 still hold the removed v6 roles, so they move; the change stays.
        val swapped = PadConfig.V5_DEFAULT_ROLES + mapOf(PadButton.L2 to PadRole.HOLD_HANDAKUTEN, PadButton.L1 to PadRole.HOLD_DAKUTEN)
        assertEquals(mapOf(PadButton.L3 to PadRole.MODE_PREVIOUS, PadButton.R3 to PadRole.MODE_NEXT, PadButton.SELECT to PadRole.TOUCH_KEYBOARD), PadConfig.migrateRoles(swapped, fromVersion = 5))
        // The user put the centre character on A and the bar on Select: they get A's and Select's v7 defaults.
        val removedElsewhere = PadConfig.V5_DEFAULT_ROLES + mapOf(PadButton.A to PadRole.TAP_CENTER, PadButton.SELECT to PadRole.BAR, PadButton.L3 to PadRole.HIDE_KEYBOARD)
        assertEquals(mapOf(PadButton.A to PadRole.CONFIRM, PadButton.SELECT to PadRole.TOUCH_KEYBOARD, PadButton.R3 to PadRole.MODE_NEXT),
            PadConfig.migrateRoles(removedElsewhere, fromVersion = 5))
        // A customised L3 / R3 (no removed role) is kept as it is.
        val custom = PadConfig.V5_DEFAULT_ROLES + mapOf(PadButton.L3 to PadRole.HIDE_KEYBOARD, PadButton.R3 to PadRole.SPACE)
        assertEquals(mapOf(PadButton.SELECT to PadRole.TOUCH_KEYBOARD), PadConfig.migrateRoles(custom, fromVersion = 5))
    }

    @Test
    fun v7_installs_change_select_only_and_v8_installs_are_left_alone() {
        // Amendment 20: a v7 map (stored or never written) gets Select = the touch keyboard, nothing else.
        assertEquals(mapOf(PadButton.SELECT to PadRole.TOUCH_KEYBOARD), PadConfig.migrateRoles(PadConfig.V7_DEFAULT_ROLES, fromVersion = 6))
        assertEquals(mapOf(PadButton.SELECT to PadRole.TOUCH_KEYBOARD), PadConfig.migrateRoles(emptyMap(), fromVersion = 6))
        // Select customised in v7: kept. モード on another button: the next mode.
        assertEquals(emptyMap<PadButton, PadRole>(), PadConfig.migrateRoles(PadConfig.V7_DEFAULT_ROLES + (PadButton.SELECT to PadRole.HIDE_KEYBOARD), fromVersion = 6))
        assertEquals(mapOf(PadButton.SELECT to PadRole.TOUCH_KEYBOARD, PadButton.R1 to PadRole.MODE_NEXT),
            PadConfig.migrateRoles(PadConfig.V7_DEFAULT_ROLES + (PadButton.R1 to PadRole.MODE_SWITCH), fromVersion = 6))
        assertEquals(emptyMap<PadButton, PadRole>(), PadConfig.migrateRoles(PadConfig.DEFAULT_ROLES, fromVersion = 7))
        assertEquals(PadRole.NONE, PadConfig(roles = PadConfig.DEFAULT_ROLES + (PadButton.SELECT to PadRole.MODE_SWITCH)).roleOf(PadButton.SELECT))
        // A removed role that is somehow still stored reads as no role at all.
        val cfg = PadConfig(roles = PadConfig.DEFAULT_ROLES + (PadButton.L3 to PadRole.BAR))
        assertEquals(PadRole.NONE, cfg.roleOf(PadButton.L3))
    }

    @Test
    fun stored_names_still_parse() {
        assertEquals(PadRole.BAR, PadRole.byName("SHIFT"))
        assertEquals(PadRole.BAR, PadRole.byName("HOLD_BOTTOM_ROW"))
        assertEquals(PadRole.TAP_CENTER, PadRole.byName("TAP_CENTER"))
        assertTrue(PadRole.BAR.isRemoved && PadRole.TAP_CENTER.isRemoved && PadRole.MODE_SWITCH.isRemoved)
        assertEquals(PadRole.MODE_SWITCH, PadRole.byName("MODE_SWITCH"))
        assertEquals(PadRole.TOUCH_KEYBOARD, PadRole.byName("TOUCH_KEYBOARD"))
        assertEquals(PadRole.HOLD_DAKUTEN, PadRole.byName("HOLD_DAKUTEN"))
        assertEquals(null, PadRole.byName("bogus"))
        assertEquals(PadRole.YA_WA_LAYER, PadRole.byName("YA_WA_LAYER"))
        assertEquals(PadRole.MODE_PREVIOUS, PadRole.byName("MODE_PREVIOUS"))
        assertEquals(PadRole.MODE_NEXT, PadRole.byName("MODE_NEXT"))
    }
}
