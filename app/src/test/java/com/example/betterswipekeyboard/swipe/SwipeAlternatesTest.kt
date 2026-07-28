package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeAlternatesTest {

    private fun results(vararg pairs: Pair<String, Float>) =
        pairs.map { (word, score) -> ScoredWord(word, score) }

    @Test
    fun `drops the committed top word and keeps the runners-up in order`() {
        val alts = swipeAlternates(
            results("hello" to 0.5f, "hell" to 0.9f, "help" to 1.2f),
        )
        assertEquals(listOf("hell", "help"), alts)
    }

    @Test
    fun `caps the strip at three alternates`() {
        val alts = swipeAlternates(
            results(
                "hello" to 0.1f,
                "hell" to 0.2f,
                "help" to 0.3f,
                "held" to 0.4f,
                "helm" to 0.5f,
            ),
        )
        assertEquals(listOf("hell", "help", "held"), alts)
    }

    @Test
    fun `runners-up at or above the commit cutoff are never offered`() {
        val alts = swipeAlternates(
            results(
                "hello" to 0.5f,
                "junk" to MAX_COMMIT_SCORE, // at the cutoff: rejected
                "help" to 1.2f,
                "worse" to 2.5f, // above the cutoff: rejected
            ),
        )
        assertEquals(listOf("help"), alts)
    }

    @Test
    fun `empty and single-candidate results yield an empty strip`() {
        assertEquals(emptyList<String>(), swipeAlternates(emptyList()))
        assertEquals(emptyList<String>(), swipeAlternates(results("hello" to 0.5f)))
    }
}
