package com.example.betterswipekeyboard.ui.keyboard

/**
 * Pure, unit-tested: the alpha of one swipe-trail segment. Recent points
 * are bright, older points fade out (the 0.15..0.90 ramp — segment [index]
 * of [count] total). [fade] scales the whole trail for the failed-swipe
 * flash's fade-out (1 = fully visible, 0 = gone); multiplicative, so the
 * flash keeps the ramp shape instead of flattening into a uniform ghost.
 */
fun trailSegmentAlpha(index: Int, count: Int, fade: Float): Float =
    (0.15f + 0.75f * (index.toFloat() / count)) * fade.coerceIn(0f, 1f)
