package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.LayoutId

/**
 * Every user intent — from a tap today or a swipe decoder tomorrow — becomes a
 * semantic [KeyboardAction]. The state layer reduces actions into a new
 * [KeyboardState] plus an optional [KeyboardEffect] for the text field, so
 * InputConnection handling exists in exactly one place regardless of how the
 * action was produced.
 */
sealed interface KeyboardAction {
    data class InsertText(val text: String) : KeyboardAction
    data object Backspace : KeyboardAction
    data object Enter : KeyboardAction

    /** Tap on shift: off → one-shot caps → off. */
    data object Shift : KeyboardAction

    /** Long-press on shift: caps lock. */
    data object CapsLock : KeyboardAction

    data class SwitchLayout(val layout: LayoutId) : KeyboardAction

    /**
     * Reserved for future swipe typing: a whole word decoded from a glide
     * trail, committed as a single unit (followed by any trailing separator
     * the decoder decides on).
     */
    data class CommitWord(val word: String) : KeyboardAction

    /** Tap the sparkly key: toggles auto-proofreading on/off (like Shift). */
    data object ToggleProofread : KeyboardAction
}

/** Side effects to apply to the current InputConnection. */
sealed interface KeyboardEffect {
    data class CommitText(val text: String) : KeyboardEffect
    data object DeleteBackward : KeyboardEffect
    data object PerformEnter : KeyboardEffect
}
