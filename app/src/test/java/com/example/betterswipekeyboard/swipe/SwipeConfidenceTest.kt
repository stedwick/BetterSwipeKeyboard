package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeConfidenceTest {

    private fun results(vararg scored: Pair<String, Float>) =
        scored.map { (word, score) -> ScoredWord(word, score) }

    @Test
    fun `no candidates at all is failed`() {
        assertEquals(SwipeConfidence.FAILED, swipeConfidence(emptyList()))
    }

    @Test
    fun `top score at or above the commit cutoff is failed`() {
        assertEquals(
            SwipeConfidence.FAILED,
            swipeConfidence(results("junk" to MAX_COMMIT_SCORE)),
        )
        assertEquals(
            SwipeConfidence.FAILED,
            swipeConfidence(results("junk" to 3.3f, "worse" to 4.0f)),
        )
    }

    @Test
    fun `committed with no runner-up is confident`() {
        assertEquals(SwipeConfidence.CONFIDENT, swipeConfidence(results("hello" to 0.5f)))
    }

    @Test
    fun `committed with a comfortable margin is confident`() {
        assertEquals(
            SwipeConfidence.CONFIDENT,
            swipeConfidence(results("hello" to -1.0f, "hell" to 1.0f)),
        )
        // Boundary: exactly LOW_CONFIDENCE_MARGIN is NOT low confidence.
        assertEquals(
            SwipeConfidence.CONFIDENT,
            swipeConfidence(results("hello" to 0.0f, "hell" to LOW_CONFIDENCE_MARGIN)),
        )
    }

    @Test
    fun `committed with a close runner-up is low confidence`() {
        assertEquals(
            SwipeConfidence.LOW,
            swipeConfidence(results("fix" to 0.07f, "fox" to 0.15f)),
        )
    }

    @Test
    fun `a margin between the old and new cutoffs is now low confidence`() {
        // Pins the raised threshold: 0.20 was CONFIDENT at the old 0.15
        // cutoff, and must flash yellow at 0.25 (see the calibration table
        // on LOW_CONFIDENCE_MARGIN).
        assertEquals(
            SwipeConfidence.LOW,
            swipeConfidence(results("hello" to 0.0f, "hell" to 0.20f)),
        )
    }

    @Test
    fun `a close margin below the commit cutoff is low even with a great score`() {
        // The margin, not the absolute score, decides (calibration: wrong
        // commits score anywhere from -1.63 to 1.65).
        assertEquals(
            SwipeConfidence.LOW,
            swipeConfidence(results("lay" to -0.08f, "lazy" to -0.07f)),
        )
    }
}
