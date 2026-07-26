package com.example.betterswipekeyboard

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
}
