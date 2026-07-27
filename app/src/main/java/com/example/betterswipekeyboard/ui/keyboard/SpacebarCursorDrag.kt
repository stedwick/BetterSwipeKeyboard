package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.SPACEBAR_STEP_SLOW_DP
import com.example.betterswipekeyboard.SPACEBAR_VELOCITY_EMA_ALPHA
import com.example.betterswipekeyboard.dragCursorSteps
import com.example.betterswipekeyboard.rebaseCursorAnchor
import com.example.betterswipekeyboard.smoothVelocity
import com.example.betterswipekeyboard.spacebarStepSize
import kotlin.math.abs

/**
 * Tracks a drag that started on the space bar and emits
 * [KeyboardAction.MoveCursor] deltas as the finger crosses step thresholds.
 * Runs until the tracked pointer lifts. Kept out of KeyboardScreen.kt so
 * the shared gesture file stays a small additive edit.
 *
 * Only the x component counts: vertical movement off the space bar is
 * ignored (a mostly-vertical drag does nothing, as before this feature).
 *
 * Velocity-sensitive scrubbing: the travel per cursor step shrinks as the
 * smoothed finger velocity rises (see spacebarStepSize) — slow drags keep
 * the fixed 14.dp/char feel, fast flings cross many characters. When the
 * zone changes mid-drag the accumulator is RE-ANCHORED at the current
 * position (rebaseCursorAnchor) so already-emitted steps stay continuous;
 * MoveCursor deltas stay net-displacement-based at the current zone's
 * rate, and direction reversals still net out.
 */
suspend fun AwaitPointerEventScope.trackSpacebarDrag(
    pointerId: PointerId,
    down: Offset,
    downTimeMillis: Long,
    pxPerDp: Float,
    onAction: (KeyboardAction) -> Unit,
) {
    // A drag starts at zero velocity, hence in the slow zone.
    var anchorX = down.x
    var stepPx = SPACEBAR_STEP_SLOW_DP * pxPerDp
    var emitted = 0
    var velocityPxPerSec = 0f
    var lastX = down.x
    var lastTimeMillis = downTimeMillis
    while (true) {
        val change = awaitPointerEvent().changes
            .firstOrNull { it.id == pointerId } ?: break
        if (change.positionChange() != Offset.Zero) {
            val dtMillis = change.uptimeMillis - lastTimeMillis
            if (dtMillis > 0) {
                val instant = abs(change.position.x - lastX) / dtMillis * 1000f
                velocityPxPerSec =
                    smoothVelocity(velocityPxPerSec, instant, SPACEBAR_VELOCITY_EMA_ALPHA)
                val newStepPx = spacebarStepSize(velocityPxPerSec / pxPerDp) * pxPerDp
                if (newStepPx != stepPx) {
                    anchorX = rebaseCursorAnchor(change.position.x, emitted, newStepPx)
                    stepPx = newStepPx
                }
            }
            // Net displacement: crossing back over a threshold emits the
            // negative delta and moves the cursor back.
            val steps = dragCursorSteps(change.position.x - anchorX, stepPx)
            if (steps != emitted) {
                onAction(KeyboardAction.MoveCursor(steps - emitted))
                emitted = steps
            }
            lastX = change.position.x
            lastTimeMillis = change.uptimeMillis
            change.consume()
        }
        // Note: the release may arrive consumed (e.g. after move
        // consumption), so check `pressed`, not `changedToUp()`.
        if (!change.pressed) break
    }
}
