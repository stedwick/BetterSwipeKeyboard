package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDecodeThrottleTest {

    @Test
    fun `below the minimum trail points never decodes`() {
        assertFalse(
            shouldRunLiveDecode(
                nowMillis = 10_000,
                lastDecodeStartMillis = 0,
                trailPoints = LIVE_DECODE_MIN_TRAIL_POINTS - 1,
                pointsAtLastDecode = 0,
            ),
        )
    }

    @Test
    fun `first decode fires once the trail is long enough`() {
        // No previous decode (lastDecodeStartMillis / pointsAtLastDecode = 0):
        // the interval and new-points gates pass trivially.
        assertTrue(
            shouldRunLiveDecode(
                nowMillis = 10_000,
                lastDecodeStartMillis = 0,
                trailPoints = LIVE_DECODE_MIN_TRAIL_POINTS,
                pointsAtLastDecode = 0,
            ),
        )
    }

    @Test
    fun `too soon after the last decode does not fire, even with many new points`() {
        assertFalse(
            shouldRunLiveDecode(
                nowMillis = 10_000 + LIVE_DECODE_MIN_INTERVAL_MS - 1,
                lastDecodeStartMillis = 10_000,
                trailPoints = 100,
                pointsAtLastDecode = 10,
            ),
        )
    }

    @Test
    fun `enough time but too few new points does not fire (paused finger)`() {
        assertFalse(
            shouldRunLiveDecode(
                nowMillis = 10_000 + LIVE_DECODE_MIN_INTERVAL_MS,
                lastDecodeStartMillis = 10_000,
                trailPoints = 10 + LIVE_DECODE_MIN_NEW_POINTS - 1,
                pointsAtLastDecode = 10,
            ),
        )
    }

    @Test
    fun `all gates pass fires`() {
        assertTrue(
            shouldRunLiveDecode(
                nowMillis = 10_000 + LIVE_DECODE_MIN_INTERVAL_MS,
                lastDecodeStartMillis = 10_000,
                trailPoints = 10 + LIVE_DECODE_MIN_NEW_POINTS,
                pointsAtLastDecode = 10,
            ),
        )
    }
}
