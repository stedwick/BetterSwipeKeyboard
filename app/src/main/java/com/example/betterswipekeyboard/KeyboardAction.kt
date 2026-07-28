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

    /** A key with no behavior of its own (placeholder; the mic now uses [ToggleVoice]). */
    data object Noop : KeyboardAction

    /** Tap the microphone key: start/stop voice dictation (decided by the service). */
    data object ToggleVoice : KeyboardAction

    /**
     * Reserved for future swipe typing: a whole word decoded from a glide
     * trail, committed as a single unit (followed by any trailing separator
     * the decoder decides on).
     */
    data class CommitWord(val word: String) : KeyboardAction

    /** Tap the sparkly key: toggles auto-proofreading on/off (like Shift). */
    data object ToggleProofread : KeyboardAction

    /**
     * Finger down on the key area. No state change; the service uses it to
     * suspend the auto-proofread timer for the duration of the gesture, so
     * a proofread can never start mid-swipe or mid-long-press.
     */
    data object GestureStarted : KeyboardAction

    /** Finger lifted (or the gesture otherwise ended). */
    data object GestureEnded : KeyboardAction

    /** Tap a clipboard-history entry: paste it verbatim and return to letters. */
    data class PasteClip(val text: String) : KeyboardAction

    /** Long-press a clipboard-history entry: delete it. */
    data class DeleteClip(val text: String) : KeyboardAction

    /**
     * Space-bar drag cursor control: move the cursor by [steps] characters
     * relative to its current position (negative = backward). Emitted as
     * deltas while the finger crosses step thresholds.
     */
    data class MoveCursor(val steps: Int) : KeyboardAction
}

/** Side effects to apply to the current InputConnection. */
sealed interface KeyboardEffect {
    data class CommitText(val text: String) : KeyboardEffect

    /**
     * A pasted clipboard entry. Distinct from [CommitText] because a paste
     * must land exactly as copied: verbatim (no leading-space rules) and
     * without triggering auto-proofreading, which could rewrite it.
     */
    data class PasteText(val text: String) : KeyboardEffect

    /**
     * A swiped word. Distinct from [CommitText] because the service adds a
     * leading space when the field doesn't already end in whitespace.
     */
    data class CommitWord(val word: String) : KeyboardEffect
    data object DeleteBackward : KeyboardEffect

    /**
     * Delete the word a swipe just committed, including the leading space
     * the swipe commit auto-inserted, so the cursor returns to the
     * pre-swipe state. Emitted instead of [DeleteBackward] for the first
     * backspace after a swipe commit (see
     * [KeyboardState.lastCommitWasSwipe]); distinct because the editor
     * reads the word length from the text field instead of deleting one
     * grapheme cluster.
     */
    data object DeleteWordBackward : KeyboardEffect
    data object PerformEnter : KeyboardEffect

    /**
     * Move the text cursor by [steps] characters (negative = backward).
     * No text changes, so the service must not reschedule auto-proofread.
     */
    data class MoveCursor(val steps: Int) : KeyboardEffect
}
