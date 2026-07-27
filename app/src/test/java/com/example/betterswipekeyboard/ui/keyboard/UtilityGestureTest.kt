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
            utilityTapAction(UtilityKeyId.AI, proofreaderAvailable = true),
        )
        assertNull(utilityTapAction(UtilityKeyId.AI, proofreaderAvailable = false))
    }

    @Test
    fun `emoji and clipboard taps switch to their panels`() {
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.EMOJI),
            utilityTapAction(UtilityKeyId.EMOJI, proofreaderAvailable = false),
        )
        assertEquals(
            KeyboardAction.SwitchLayout(LayoutId.CLIPBOARD),
            utilityTapAction(UtilityKeyId.CLIPBOARD, proofreaderAvailable = false),
        )
    }

    @Test
    fun `settings tap has no keyboard action`() {
        // Opening the app is the service's onSettingsClick callback, not a
        // KeyboardAction; the gesture loop calls it directly.
        assertNull(utilityTapAction(UtilityKeyId.SETTINGS, proofreaderAvailable = true))
        assertNull(utilityTapAction(UtilityKeyId.SETTINGS, proofreaderAvailable = false))
    }
}
