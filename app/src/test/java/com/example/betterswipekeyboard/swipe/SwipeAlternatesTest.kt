package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `caps the strip at four alternates`() {
        val alts = swipeAlternates(
            results(
                "hello" to 0.1f,
                "hell" to 0.2f,
                "help" to 0.3f,
                "held" to 0.4f,
                "helm" to 0.5f,
            ),
        )
        assertEquals(listOf("hell", "help", "held", "helm"), alts)
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

    @Test
    fun `failed-swipe offers include the top candidate in score order`() {
        // Unlike swipeAlternates, top-1 stays: nothing was committed, so it
        // IS the primary rescue.
        val offers = failedSwipeOffers(
            results("keyboard" to 1.9f, "keyword" to 2.4f, "key West" to 2.8f),
            maxOffers = 4,
        )
        assertEquals(listOf("keyboard", "keyword", "key West"), offers)
    }

    @Test
    fun `candidates at or above the offer ceiling are never offered`() {
        val offers = failedSwipeOffers(
            results(
                "keyboard" to 1.9f,
                "keyword" to NEAR_MISS_OFFER_MAX_SCORE, // at the ceiling: rejected
                "key West" to 2.6f,
                "junk" to 4.1f,
            ),
            maxOffers = 4,
        )
        assertEquals(listOf("keyboard", "key West"), offers)
    }

    @Test
    fun `failed-swipe offers are capped at the strip's cell count`() {
        val offers = failedSwipeOffers(
            results(
                "one" to 1.9f,
                "two" to 2.0f,
                "three" to 2.1f,
                "four" to 2.2f,
            ),
            maxOffers = 2,
        )
        assertEquals(listOf("one", "two"), offers)
    }

    @Test
    fun `an empty offer band yields null so the placeholder shows`() {
        assertNull(failedSwipeOffers(emptyList(), maxOffers = 4))
        assertNull(failedSwipeOffers(results("junk" to 3.5f), maxOffers = 4))
    }
}
