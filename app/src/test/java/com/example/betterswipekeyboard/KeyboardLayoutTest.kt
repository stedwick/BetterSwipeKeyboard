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
                assertTrue(row.isNotEmpty())
                row.forEach { key -> assertTrue(key.weight > 0f) }
            }
        }
    }

    @Test
    fun `every layout can delete and return`() {
        layouts.forEach { layout ->
            val outputs = layout.rows.flatten().map { it.output }
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
        val letters = QwertyLayout.rows.flatten()
            .mapNotNull { (it.output as? KeyOutput.Text)?.text }
            .filter { it.singleOrNull()?.isLetter() == true }
            .map { it.lowercase() }
            .toSet()
        assertEquals(26, letters.size)
    }

    @Test
    fun `only qwerty has shift`() {
        assertTrue(QwertyLayout.rows.flatten().any { it.output is KeyOutput.Shift })
    }

    private fun KeyboardLayout.switchesTo(target: LayoutId): Boolean =
        rows.flatten().any { (it.output as? KeyOutput.SwitchLayout)?.layout == target }
}
