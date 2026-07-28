package com.example.betterswipekeyboard.ui.keyboard

import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongPressPopupTest {

    private fun textKey(text: String) = Key(label = text, output = KeyOutput.Text(text))

    @Test
    fun `period hosts the prose popup on letters and symbols`() {
        val expected = KeyPopup(PUNCTUATION_POPUP, hint = "!")
        assertEquals(expected, keyPopup(LayoutId.LETTERS, textKey(".")))
        assertEquals(expected, keyPopup(LayoutId.SYMBOLS, textKey(".")))
    }

    @Test
    fun `zero hosts the numeric popup only on the numpad`() {
        assertEquals(KeyPopup(NUMERIC_POPUP, hint = "#"), keyPopup(LayoutId.NUMERIC, textKey("0")))
        assertNull(keyPopup(LayoutId.LETTERS, textKey("0")))
    }

    @Test
    fun `no popup for other keys or layouts`() {
        assertNull(keyPopup(LayoutId.NUMERIC, textKey(".")))
        assertNull(keyPopup(LayoutId.NUMERIC, textKey("5")))
        assertNull(keyPopup(LayoutId.LETTERS, textKey("q")))
        assertNull(keyPopup(LayoutId.NUMERIC, Key(label = "⌫", output = KeyOutput.Backspace)))
    }

    @Test
    fun `numeric popup pins content and ordering`() {
        // Phone punctuation top row, datetime separators mid-row (datetime
        // fields auto-show the numpad), money + space bottom row.
        assertEquals(listOf("#", "*", "(", ")", "/", ":", ".", ",", " "), NUMERIC_POPUP)
        val rows = NUMERIC_POPUP.chunked(PUNCTUATION_POPUP_COLUMNS)
        assertEquals(3, rows.size)
        // Money/everyday characters on the bottom row, closest to the thumb;
        // space last (the numpad has no space bar).
        assertEquals(listOf(".", ",", " "), rows.last())
    }
}
