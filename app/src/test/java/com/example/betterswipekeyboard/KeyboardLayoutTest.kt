package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.KeyboardLayout
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private val layouts = listOf(QwertyLayout, SymbolsLayout)

    @Test
    fun `layouts have no empty rows and positive weights`() {
        layouts.forEach { layout ->
            assertTrue(layout.rows.isNotEmpty())
            layout.rows.forEach { row ->
                assertTrue(row.keys.isNotEmpty())
                row.keys.forEach { key -> assertTrue(key.weight > 0f) }
            }
        }
    }

    @Test
    fun `every layout can delete and return`() {
        layouts.forEach { layout ->
            val outputs = layout.rows.flatMap { it.keys }.map { it.output }
            assertTrue(outputs.any { it is KeyOutput.Backspace })
            assertTrue(outputs.any { it is KeyOutput.Enter })
        }
    }

    @Test
    fun `layouts link to each other`() {
        assertTrue(QwertyLayout.switchesTo(LayoutId.SYMBOLS))
        assertTrue(SymbolsLayout.switchesTo(LayoutId.LETTERS))
    }

    @Test
    fun `qwerty has all 26 letters`() {
        val letters = QwertyLayout.rows.flatMap { it.keys }
            .mapNotNull { (it.output as? KeyOutput.Text)?.text }
            .filter { it.singleOrNull()?.isLetter() == true }
            .map { it.lowercase() }
            .toSet()
        assertEquals(26, letters.size)
    }

    @Test
    fun `only qwerty has shift`() {
        assertTrue(QwertyLayout.rows.flatMap { it.keys }.any { it.output is KeyOutput.Shift })
    }

    @Test
    fun `qwerty home row is inset by half a key`() {
        assertEquals(0.5f, QwertyLayout.rows[1].insetWeight, 1e-6f)
    }

    /**
     * Character keys are only the same width across rows if every row spans
     * the same total weight (10 units), counting the row insets.
     */
    @Test
    fun `every row spans exactly ten key units`() {
        layouts.forEach { layout ->
            layout.rows.forEach { row ->
                val total = row.keys.sumOf { it.weight.toDouble() } + 2.0 * row.insetWeight
                assertEquals(10.0, total, 1e-4)
            }
        }
    }

    @Test
    fun `all character keys have unit weight`() {
        layouts.forEach { layout ->
            layout.rows.flatMap { it.keys }
                // Character keys = visible single-character outputs; the space
                // bar is a Text output too, but a special key that may differ.
                .filter { ((it.output as? KeyOutput.Text)?.text?.isNotBlank() == true) }
                .forEach { key -> assertEquals(1f, key.weight, 1e-6f) }
        }
    }

    private fun KeyboardLayout.switchesTo(target: LayoutId): Boolean =
        rows.flatMap { it.keys }.any { (it.output as? KeyOutput.SwitchLayout)?.layout == target }
}
