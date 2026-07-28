package com.example.betterswipekeyboard.ui.keyboard

import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.layout.LayoutId

/** The keys of the utility row above the letter rows. */
enum class UtilityKeyId { AI, EMOJI, CLIPBOARD, SETTINGS }

/**
 * Pure, unit-tested: the action for a utility-row tap when the row lives
 * inside the gesture surface (letters/symbols layouts), where keys are
 * purely visual and the container gesture loop dispatches taps
 * semantically. The AI key is disabled while no proofreader is available —
 * the same rule as clickable mode's `clickable(enabled = ...)`. SETTINGS
 * maps to null: opening the app is a service callback (onSettingsClick),
 * not a [KeyboardAction].
 */
fun utilityTapAction(id: UtilityKeyId, proofreaderAvailable: Boolean): KeyboardAction? = when (id) {
    UtilityKeyId.AI -> if (proofreaderAvailable) KeyboardAction.ToggleProofread else null
    UtilityKeyId.EMOJI -> KeyboardAction.SwitchLayout(LayoutId.EMOJI)
    UtilityKeyId.CLIPBOARD -> KeyboardAction.SwitchLayout(LayoutId.CLIPBOARD)
    UtilityKeyId.SETTINGS -> null
}
