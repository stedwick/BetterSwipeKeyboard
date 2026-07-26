package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.dragCursorSteps

/**
 * Tracks a drag that started on the space bar and emits
 * [KeyboardAction.MoveCursor] deltas as the finger crosses step thresholds.
 * Runs until the tracked pointer lifts. Kept out of KeyboardScreen.kt so
 * the shared gesture file stays a small additive edit.
 *
 * Only the x component counts: vertical movement off the space bar is
 * ignored (a mostly-vertical drag does nothing, as before this feature).
 * [stepPx] is the horizontal travel per cursor step.
 */
suspend fun AwaitPointerEventScope.trackSpacebarDrag(
    pointerId: PointerId,
    down: Offset,
    stepPx: Float,
    onAction: (KeyboardAction) -> Unit,
) {
    var emitted = 0
    while (true) {
        val change = awaitPointerEvent().changes
            .firstOrNull { it.id == pointerId } ?: break
        if (change.positionChange() != Offset.Zero) {
            // Net displacement: crossing back over a threshold emits the
            // negative delta and moves the cursor back.
            val steps = dragCursorSteps(change.position.x - down.x, stepPx)
            if (steps != emitted) {
                onAction(KeyboardAction.MoveCursor(steps - emitted))
                emitted = steps
            }
            change.consume()
        }
        // Note: the release may arrive consumed (e.g. after move
        // consumption), so check `pressed`, not `changedToUp()`.
        if (!change.pressed) break
    }
}
