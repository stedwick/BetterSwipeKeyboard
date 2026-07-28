package com.example.betterswipekeyboard.ime

import android.text.InputType
import com.example.betterswipekeyboard.layout.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericFieldTest {

    @Test
    fun `number, phone and datetime fields are numeric`() {
        assertTrue(isNumericInputType(InputType.TYPE_CLASS_NUMBER))
        assertTrue(isNumericInputType(InputType.TYPE_CLASS_PHONE))
        assertTrue(isNumericInputType(InputType.TYPE_CLASS_DATETIME))
    }

    @Test
    fun `text fields are not numeric, even with password variations`() {
        assertFalse(isNumericInputType(InputType.TYPE_CLASS_TEXT))
        assertFalse(
            isNumericInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        )
        assertFalse(
            isNumericInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ),
        )
    }

    @Test
    fun `variation and flag bits do not change the class`() {
        assertTrue(
            isNumericInputType(
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED,
            ),
        )
        assertTrue(
            isNumericInputType(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ),
        )
        assertTrue(
            isNumericInputType(
                InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE,
            ),
        )
        assertTrue(
            isNumericInputType(
                InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME,
            ),
        )
    }

    @Test
    fun `numeric field start shows the numpad from any layout`() {
        LayoutId.entries.forEach { current ->
            assertEquals(
                LayoutId.NUMERIC,
                fieldStartLayout(InputType.TYPE_CLASS_NUMBER, current),
            )
        }
    }

    @Test
    fun `non-numeric field start undoes only the numpad`() {
        assertEquals(LayoutId.LETTERS, fieldStartLayout(InputType.TYPE_CLASS_TEXT, LayoutId.NUMERIC))
        // Other layouts keep their existing cross-field persistence.
        assertNull(fieldStartLayout(InputType.TYPE_CLASS_TEXT, LayoutId.LETTERS))
        assertNull(fieldStartLayout(InputType.TYPE_CLASS_TEXT, LayoutId.SYMBOLS))
        assertNull(fieldStartLayout(InputType.TYPE_CLASS_TEXT, LayoutId.EMOJI))
        assertNull(fieldStartLayout(InputType.TYPE_CLASS_TEXT, LayoutId.CLIPBOARD))
    }
}
