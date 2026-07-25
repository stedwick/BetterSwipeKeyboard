package com.example.betterswipekeyboard.layout

/**
 * The keyboard is defined as pure data: which layout is active, and which keys
 * sit in which row. A future swipe/glide decoder needs exactly this — key
 * identity and geometry — to map a touch trail onto words, so layouts live
 * here instead of being baked into the UI.
 */

enum class LayoutId { LETTERS, SYMBOLS }

/** What a key produces when tapped. Labels are a UI concern; output is semantics. */
sealed interface KeyOutput {
    data class Text(val text: String) : KeyOutput
    data object Backspace : KeyOutput
    data object Enter : KeyOutput
    data object Shift : KeyOutput
    data class SwitchLayout(val layout: LayoutId) : KeyOutput
}

data class Key(
    val label: String,
    val output: KeyOutput,
    /** Width relative to a standard key in the same row. */
    val weight: Float = 1f,
)

data class KeyboardLayout(
    val id: LayoutId,
    val rows: List<List<Key>>,
)
