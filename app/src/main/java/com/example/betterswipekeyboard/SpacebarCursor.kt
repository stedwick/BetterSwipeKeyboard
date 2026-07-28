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

// Velocity-sensitive space-bar scrubbing: the faster the finger moves, the
// less physical travel each cursor step costs, so a fast fling crosses many
// characters while a slow drag keeps today's precise feel. Three zones
// (tune on-device); velocity is the smoothed magnitude in dp/s, so the
// zones are direction-symmetric.
const val SPACEBAR_STEP_SLOW_DP = 14f // today's fixed feel
const val SPACEBAR_STEP_MID_DP = 8f
const val SPACEBAR_STEP_FAST_DP = 4f
const val SPACEBAR_SLOW_VELOCITY_DP_PER_SEC = 200f
const val SPACEBAR_FAST_VELOCITY_DP_PER_SEC = 800f

/**
 * Pure, unit-tested: horizontal travel per cursor step at a given
 * (smoothed, unsigned) scrub velocity in dp/s. Chosen over emitting
 * multi-char steps at speed: a variable step size keeps the
 * net-displacement accumulator and its reversal semantics intact, and
 * [rebaseCursorAnchor] makes zone changes continuous.
 */
fun spacebarStepSize(velocityDpPerSec: Float): Float = when {
    velocityDpPerSec < SPACEBAR_SLOW_VELOCITY_DP_PER_SEC -> SPACEBAR_STEP_SLOW_DP
    velocityDpPerSec < SPACEBAR_FAST_VELOCITY_DP_PER_SEC -> SPACEBAR_STEP_MID_DP
    else -> SPACEBAR_STEP_FAST_DP
}

/**
 * EMA smoothing factor for the scrub velocity (tune on-device): reacts
 * within ~2–3 pointer events, but a single jittery event (big dx over a
 * tiny dt) cannot flip the zone by itself.
 */
const val SPACEBAR_VELOCITY_EMA_ALPHA = 0.4f

/** Pure, unit-tested: exponential moving average of the scrub velocity. */
fun smoothVelocity(previous: Float, sample: Float, alpha: Float): Float =
    previous + alpha * (sample - previous)

/**
 * Pure, unit-tested: re-anchor a net-displacement step accumulator when
 * the step size changes mid-drag, keeping the already-emitted
 * [emittedSteps] continuous at [positionX]. Without this, a zone change
 * retroactively re-divides the whole drag from the original anchor and
 * the cursor jumps (the classic variable-rate accumulator bug).
 */
fun rebaseCursorAnchor(positionX: Float, emittedSteps: Int, stepPx: Float): Float =
    positionX - emittedSteps * stepPx
