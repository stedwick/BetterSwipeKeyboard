package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput

/**
 * Pure logic for space-bar cursor control (Gboard-style: drag horizontally
 * on the space bar to move the text cursor). No Android deps, so it is
 * fully unit-testable; the Compose gesture loop lives in
 * `ui/keyboard/SpacebarCursorDrag.kt`.
 */

/**
 * The space bar is a `Text(" ")` key in every layout (QwertyLayout,
 * SymbolsLayout) — detection is by output, not by weight or position.
 */
fun isSpaceBar(key: Key?): Boolean =
    (key?.output as? KeyOutput.Text)?.text == " "

/**
 * Net horizontal finger displacement → cursor steps (truncating, signed).
 * Net displacement, not path length: dragging back toward the start point
 * lowers the count again, so the gesture loop's delta emission
 * automatically moves the cursor back — matching Gboard, where the cursor
 * tracks finger displacement continuously.
 */
fun dragCursorSteps(displacementXPx: Float, stepPx: Float): Int =
    (displacementXPx / stepPx).toInt()
