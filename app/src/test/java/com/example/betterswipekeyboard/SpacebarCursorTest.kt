package com.example.betterswipekeyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacebarCursorTest {

    private val stepPx = 42f // e.g. 14.dp at 3x density

    @Test
    fun `zero displacement is zero steps`() {
        assertEquals(0, dragCursorSteps(0f, stepPx))
    }

    @Test
    fun `displacement below one step is a dead zone`() {
        assertEquals(0, dragCursorSteps(stepPx - 1f, stepPx))
        assertEquals(0, dragCursorSteps(-(stepPx - 1f), stepPx))
    }

    @Test
    fun `exact multiples give exact steps`() {
        assertEquals(1, dragCursorSteps(stepPx, stepPx))
        assertEquals(3, dragCursorSteps(3 * stepPx, stepPx))
        assertEquals(-2, dragCursorSteps(-2 * stepPx, stepPx))
    }

    @Test
    fun `fractional displacement truncates toward zero`() {
        assertEquals(2, dragCursorSteps(2.9f * stepPx, stepPx))
        assertEquals(-2, dragCursorSteps(-2.9f * stepPx, stepPx))
    }

    @Test
    fun `dragging back over a threshold lowers the count`() {
        // Net displacement: this is what makes the gesture loop emit the
        // negative delta that moves the cursor back.
        val forward = dragCursorSteps(3.5f * stepPx, stepPx)
        val draggedBack = dragCursorSteps(1.5f * stepPx, stepPx)
        assertEquals(3, forward)
        assertEquals(1, draggedBack)
    }

    @Test
    fun `space bars of both layouts are detected`() {
        val qwertySpace = QwertyLayout.rows.last().single {
            (it.output as? KeyOutput.Text)?.text == " "
        }
        val symbolsSpace = SymbolsLayout.rows.last().single {
            (it.output as? KeyOutput.Text)?.text == " "
        }
        assertTrue(isSpaceBar(qwertySpace))
        assertTrue(isSpaceBar(symbolsSpace))
    }

    @Test
    fun `other keys are not space bars`() {
        assertFalse(isSpaceBar(Key(label = "a", output = KeyOutput.Text("a"))))
        assertFalse(isSpaceBar(Key(label = ".", output = KeyOutput.Text("."))))
        assertFalse(isSpaceBar(Key(label = "⌫", output = KeyOutput.Backspace)))
        assertFalse(isSpaceBar(Key(label = "⇧", output = KeyOutput.Shift)))
        assertFalse(isSpaceBar(null))
    }

    // A 300x40 px space bar at (10, 100).
    private val bar = Rect(10f, 100f, 310f, 140f)

    @Test
    fun `hit rect keeps the sides and bottom of the visual rect`() {
        val hit = spacebarHitRect(bar, 12f)
        assertEquals(bar.left, hit.left, 0.001f)
        assertEquals(bar.right, hit.right, 0.001f)
        assertEquals(bar.bottom, hit.bottom, 0.001f)
    }

    @Test
    fun `hit rect excludes the top slack strip`() {
        val hit = spacebarHitRect(bar, 12f)
        assertFalse(hit.contains(Offset(160f, 105f))) // 5 px into the strip
        assertTrue(hit.contains(Offset(160f, 113f))) // just below the inset
    }

    @Test
    fun `an inset taller than the key accepts nothing`() {
        val hit = spacebarHitRect(bar, 1000f)
        assertFalse(hit.contains(Offset(160f, 120f)))
    }

    @Test
    fun `step size zones keep slow drags at the legacy feel`() {
        assertEquals(SPACEBAR_STEP_SLOW_DP, spacebarStepSize(0f), 1e-6f)
        assertEquals(SPACEBAR_STEP_SLOW_DP, spacebarStepSize(199.9f), 1e-6f)
        assertEquals(SPACEBAR_STEP_SLOW_DP, spacebarStepSize(14f), 1e-6f)
    }

    @Test
    fun `step size zone boundaries are sharp and ordered`() {
        assertEquals(SPACEBAR_STEP_MID_DP, spacebarStepSize(200f), 1e-6f)
        assertEquals(SPACEBAR_STEP_MID_DP, spacebarStepSize(799.9f), 1e-6f)
        assertEquals(SPACEBAR_STEP_FAST_DP, spacebarStepSize(800f), 1e-6f)
        assertEquals(SPACEBAR_STEP_FAST_DP, spacebarStepSize(5000f), 1e-6f)
        assertTrue(SPACEBAR_STEP_SLOW_DP > SPACEBAR_STEP_MID_DP)
        assertTrue(SPACEBAR_STEP_MID_DP > SPACEBAR_STEP_FAST_DP)
    }

    @Test
    fun `velocity smoothing converges and damps jitter`() {
        // Constant 1000 px/s samples pull the EMA to ~1000.
        var v = 0f
        repeat(10) { v = smoothVelocity(v, 1000f, SPACEBAR_VELOCITY_EMA_ALPHA) }
        assertEquals(1000f, v, 10f)
        // One jitter spike from rest moves the average by only alpha.
        assertEquals(
            400f,
            smoothVelocity(0f, 1000f, SPACEBAR_VELOCITY_EMA_ALPHA),
            1e-6f,
        )
    }

    @Test
    fun `rebasing the anchor keeps emitted steps continuous`() {
        // 20 steps emitted at 14 px/step; at x = 280 the zone switches to
        // 8 px/step — the step count at the switch point must not move.
        val anchor = rebaseCursorAnchor(positionX = 280f, emittedSteps = 20, stepPx = 8f)
        assertEquals(20, dragCursorSteps(280f - anchor, 8f))
    }

    @Test
    fun `after a zone change only remaining displacement uses the new rate`() {
        // Classic bug: re-dividing the whole drag at the new rate. Drag
        // 280 px at 14 px/step = 20 steps; switch to 8 px/step and continue
        // 80 px further = exactly 10 more steps, not 45 total.
        val emitted = dragCursorSteps(280f, 14f)
        val anchor = rebaseCursorAnchor(280f, emitted, 8f)
        assertEquals(emitted + 10, dragCursorSteps(360f - anchor, 8f))
        // And direction reversal at the new rate still nets out.
        assertEquals(emitted, dragCursorSteps(280f - anchor, 8f))
        assertEquals(emitted - 5, dragCursorSteps(240f - anchor, 8f))
    }
}
