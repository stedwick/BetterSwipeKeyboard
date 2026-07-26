package com.example.betterswipekeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomInsetsTest {

    @Test
    fun `no strip means no clearance`() {
        assertEquals(0, bottomClearancePx(0, 0, 0))
    }

    @Test
    fun `gesture nav strip is used when it is the only candidate`() {
        assertEquals(48, bottomClearancePx(48, 0, 42))
    }

    @Test
    fun `three-button nav bar is covered by tappable element`() {
        assertEquals(126, bottomClearancePx(48, 126, 0))
    }

    @Test
    fun `largest candidate wins`() {
        assertEquals(80, bottomClearancePx(20, 80, 48))
    }
}
