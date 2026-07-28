package com.example.betterswipekeyboard.ui.keyboard

import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.layout.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilityGestureTest {

    @Test
    fun `AI tap toggles proofread only when a proofreader is available`() {
        assertEquals(
            KeyboardAction.ToggleProofread,
            utilityTapAction(UtilityKeyId.AI, proofreaderAvailable = true, LayoutId.LETTERS),
        )
        assertNull(utilityTapAction(UtilityKeyId.AI, proofreaderAvailable = false, LayoutId.LETTERS))
    }

    @Test
    fun `emoji and clipboard taps switch to their panels`() {
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.EMOJI),
            utilityTapAction(UtilityKeyId.EMOJI, proofreaderAvailable = false, LayoutId.LETTERS),
        )
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.CLIPBOARD),
            utilityTapAction(UtilityKeyId.CLIPBOARD, proofreaderAvailable = false, LayoutId.LETTERS),
        )
    }

    @Test
    fun `numeric tap toggles the numpad both ways`() {
        // "123" from the key layouts, "ABC" back from the numpad.
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.NUMERIC),
            utilityTapAction(UtilityKeyId.NUMERIC, proofreaderAvailable = false, LayoutId.LETTERS),
        )
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.NUMERIC),
            utilityTapAction(UtilityKeyId.NUMERIC, proofreaderAvailable = false, LayoutId.SYMBOLS),
        )
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.LETTERS),
            utilityTapAction(UtilityKeyId.NUMERIC, proofreaderAvailable = false, LayoutId.NUMERIC),
        )
    }

    @Test
    fun `settings tap has no keyboard action`() {
        // Opening the app is the service's onSettingsClick callback, not a
        // KeyboardAction; the gesture loop calls it directly.
        assertNull(utilityTapAction(UtilityKeyId.SETTINGS, proofreaderAvailable = true, LayoutId.LETTERS))
        assertNull(utilityTapAction(UtilityKeyId.SETTINGS, proofreaderAvailable = false, LayoutId.LETTERS))
    }
}
