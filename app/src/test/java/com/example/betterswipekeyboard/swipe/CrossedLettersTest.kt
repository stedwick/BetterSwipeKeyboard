package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossedLettersTest {

    /** QWERTY-ish grid on a unit key width. */
    private val centers: Map<Char, Vec2> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Vec2(i + 0.5f, 0.5f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Vec2(i + 1.0f, 1.5f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Vec2(i + 1.5f, 2.5f)) }
    }

    private fun lettersOf(vararg points: Vec2): String =
        crossedLetters(points.toList(), centers)

    @Test
    fun `straight trail through key centers yields those keys in order`() {
        assertEquals(
            "dog",
            lettersOf(Vec2(3.0f, 1.5f), Vec2(8.5f, 0.5f), Vec2(5.0f, 1.5f)),
        )
    }

    @Test
    fun `order follows the trail, not the keyboard`() {
        assertEquals(
            "god",
            lettersOf(Vec2(5.0f, 1.5f), Vec2(8.5f, 0.5f), Vec2(3.0f, 1.5f)),
        )
    }

    @Test
    fun `a dwell collapses to one letter`() {
        val dwell = List(5) { Vec2(3.0f, 1.5f) }
        assertEquals(
            "dog",
            lettersOf(*dwell.toTypedArray(), Vec2(8.5f, 0.5f), Vec2(5.0f, 1.5f)),
        )
    }

    @Test
    fun `non-consecutive revisits survive (zigzag)`() {
        // m -> u -> m, the mummy reversal: only STRICTLY consecutive
        // repeats collapse.
        assertEquals(
            "mum",
            lettersOf(Vec2(7.5f, 2.5f), Vec2(6.5f, 0.5f), Vec2(7.5f, 2.5f)),
        )
    }

    @Test
    fun `dead space near a key stays inside its visit`() {
        // (3.0, 1.2) is off the d key but still nearest to it.
        assertEquals("d", lettersOf(Vec2(3.0f, 1.5f), Vec2(3.0f, 1.2f), Vec2(3.0f, 1.5f)))
    }

    @Test
    fun `far points resolve to the nearest key instead of being dropped`() {
        // (3.0, 10.0) is far below the keyboard; nearest key is x — no
        // point is ever dropped, excursions show up as letters.
        assertEquals("dxd", lettersOf(Vec2(3.0f, 1.5f), Vec2(3.0f, 10.0f), Vec2(3.0f, 1.5f)))
    }

    @Test
    fun `a point between two keys goes to the nearer one`() {
        // (3.6, 1.5): 0.6 from d, 0.4 from f.
        assertEquals("df", lettersOf(Vec2(3.0f, 1.5f), Vec2(3.6f, 1.5f)))
    }

    @Test
    fun `empty trail yields empty letters`() {
        assertEquals("", lettersOf())
    }
}
