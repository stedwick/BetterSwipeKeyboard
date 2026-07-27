package com.example.betterswipekeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailFadeTest {

    @Test
    fun `fade 1 reproduces the legacy bright-to-faint ramp`() {
        // First segment faintest, last segment brightest.
        assertEquals(0.15f + 0.75f * (1f / 10), trailSegmentAlpha(1, 10, 1f), 1e-6f)
        assertEquals(0.15f + 0.75f * (9f / 10), trailSegmentAlpha(9, 10, 1f), 1e-6f)
    }

    @Test
    fun `the ramp is monotonic so recent points are brighter`() {
        val alphas = (1 until 20).map { trailSegmentAlpha(it, 20, 1f) }
        assertEquals(alphas.sorted(), alphas)
    }

    @Test
    fun `fade scales the whole trail multiplicatively`() {
        val full = trailSegmentAlpha(5, 10, 1f)
        assertEquals(full / 2, trailSegmentAlpha(5, 10, 0.5f), 1e-6f)
        assertEquals(0f, trailSegmentAlpha(5, 10, 0f), 1e-6f)
    }

    @Test
    fun `out-of-range fade is clamped`() {
        assertEquals(0f, trailSegmentAlpha(5, 10, -0.5f), 1e-6f)
        assertEquals(
            trailSegmentAlpha(5, 10, 1f),
            trailSegmentAlpha(5, 10, 1.5f),
            1e-6f,
        )
    }
}
