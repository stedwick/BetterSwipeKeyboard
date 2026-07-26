package com.example.betterswipekeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyWidthsTest {

    @Test
    fun `full row divides the container into ten equal keys`() {
        // 10 keys + 9 gaps + side padding fill the container exactly.
        assertEquals(96.4f, unitKeyWidthPx(1006f, 3f, 4f), 1e-3f)
    }

    @Test
    fun `unmeasured container yields a non-positive width`() {
        assertTrue(unitKeyWidthPx(0f, 3f, 4f) <= 0f)
    }
}
