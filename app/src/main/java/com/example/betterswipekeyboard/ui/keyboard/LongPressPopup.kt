package com.example.betterswipekeyboard.ui.keyboard

import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId

/**
 * Popup content for the numpad's 0 long-press: phone/DMTF punctuation, the
 * datetime separators (datetime fields auto-show the numpad, so `:` and `/`
 * must be reachable), the money characters, and space — the numpad has no
 * space bar, so grouped numbers ("4111 1111") stay possible without leaving
 * the layout. 9 items keep the clean 3x3; ordered top (least common) to
 * bottom (closest to the thumb), like [PUNCTUATION_POPUP]:
 * rows [# * (] [) / :] [. , ␣].
 */
internal val NUMERIC_POPUP = listOf("#", "*", "(", ")", "/", ":", ".", ",", " ")

/** A long-press popup: the committed choices and the host key's corner hint. */
data class KeyPopup(val choices: List<String>, val hint: String)

/**
 * The long-press popup behind a key, if any. The period key hosts the prose
 * popup on the letters/symbols layouts; the 0 key hosts the numeric popup on
 * the numpad (dial-pad convention: phone keypads put '+' behind 0 long-press).
 * Single host per layout keeps the pattern trivial.
 */
fun keyPopup(layout: LayoutId, key: Key): KeyPopup? {
    val text = (key.output as? KeyOutput.Text)?.text
    return when {
        layout != LayoutId.NUMERIC && text == "." ->
            KeyPopup(PUNCTUATION_POPUP, hint = "!")
        layout == LayoutId.NUMERIC && text == "0" ->
            KeyPopup(NUMERIC_POPUP, hint = "#")
        else -> null
    }
}
