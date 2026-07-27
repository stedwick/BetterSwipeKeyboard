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
}
