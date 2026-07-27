package com.example.betterswipekeyboard

import androidx.compose.ui.geometry.Rect
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
 * The space bar's touch-acceptance rect: its visual rect with the top edge
 * inset by [topInsetPx]. A word-swipe starting a few px above the space bar
 * (thumb overshoot aiming at the bottom letter row) must become a letter
 * swipe, not a space-bar cursor drag — so space-bar taps and drags are
 * hit-tested against this shrunken rect, while the visual key and the
 * stored decoder geometry keep the true bounds. Clamped to an empty rect
 * (top == bottom, contains nothing) when the inset exceeds the key height.
 */
fun spacebarHitRect(visualRect: Rect, topInsetPx: Float): Rect =
    Rect(
        visualRect.left,
        (visualRect.top + topInsetPx).coerceAtMost(visualRect.bottom),
        visualRect.right,
        visualRect.bottom,
    )

/**
 * Net horizontal finger displacement → cursor steps (truncating, signed).
 * Net displacement, not path length: dragging back toward the start point
 * lowers the count again, so the gesture loop's delta emission
 * automatically moves the cursor back — matching Gboard, where the cursor
 * tracks finger displacement continuously.
 */
fun dragCursorSteps(displacementXPx: Float, stepPx: Float): Int =
    (displacementXPx / stepPx).toInt()
