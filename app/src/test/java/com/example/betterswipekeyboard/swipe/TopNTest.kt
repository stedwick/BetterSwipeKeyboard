package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tie/boundary semantics of [TopN] — the bounded selection must reproduce
 * `sortedBy { it.score }.take(n)` (stable ascending sort) exactly, or
 * decode results change. The randomized equivalence test is the broad
 * check; the hand-written cases pin the boundary rules a sampling might
 * miss.
 */
class TopNTest {

    private fun track(n: Int, scores: List<Float>): List<ScoredWord> {
        val top = TopN(n)
        scores.forEachIndexed { i, s -> top.offer("w$i", s) }
        return top.results()
    }

    /** The selection TopN must reproduce: stable ascending sort, take n. */
    private fun reference(n: Int, scores: List<Float>): List<ScoredWord> =
        scores.mapIndexed { i, s -> ScoredWord("w$i", s) }
            .sortedBy { it.score }
            .take(n)

    @Test
    fun `empty stream yields empty results`() {
        assertEquals(emptyList<ScoredWord>(), track(5, emptyList()))
    }

    @Test
    fun `fewer candidates than slots keeps all, best first`() {
        assertEquals(
            listOf(ScoredWord("w2", 1f), ScoredWord("w0", 3f), ScoredWord("w1", 4f)),
            track(5, listOf(3f, 4f, 1f)),
        )
    }

    @Test
    fun `tie with the cut-off score never enters`() {
        // Slots full with distinct scores 1..5; a sixth candidate tying the
        // 5th-best score loses the stable-sort tie (it came later) and must
        // not displace the kept one.
        val results = track(5, listOf(1f, 2f, 3f, 4f, 5f, 5f, 5f))
        assertEquals(
            (0 until 5).map { ScoredWord("w$it", (it + 1).toFloat()) },
            results,
        )
    }

    @Test
    fun `ties inside the top N keep stream order`() {
        // Equal scores rank by insertion sequence: w0(2) before w2(2).
        assertEquals(
            listOf(ScoredWord("w1", 1f), ScoredWord("w0", 2f), ScoredWord("w2", 2f)),
            track(5, listOf(2f, 1f, 2f)),
        )
    }

    @Test
    fun `identical entries are distinct stream elements`() {
        // The same (word, score) twice occupies two slots, in order — the
        // stable sort keeps both too.
        assertEquals(
            listOf(ScoredWord("dup", 1f), ScoredWord("dup", 1f), ScoredWord("w0", 2f)),
            TopN(5).apply {
                offer("w0", 2f)
                offer("dup", 1f)
                offer("dup", 1f)
            }.results(),
        )
    }

    @Test
    fun `better late candidate displaces from the end`() {
        // w2 and w4 tie at 2.0: the earlier w2 keeps the slot (stable order).
        assertEquals(
            listOf(ScoredWord("w5", -1f), ScoredWord("w1", 1f), ScoredWord("w2", 2f)),
            track(3, listOf(3f, 1f, 2f, 4f, 2f, -1f)),
        )
    }

    @Test
    fun `single slot keeps the best and earliest of ties`() {
        assertEquals(listOf(ScoredWord("w1", 1f)), track(1, listOf(3f, 1f, 1f, 2f)))
    }

    @Test
    fun `matches stable sort plus take on randomized tie-heavy streams`() {
        // Scores drawn from a tiny integer set so exact ties are frequent,
        // including ties straddling the cut-off; n varies across 1, the
        // production 5, and a larger-than-stream case. Seeded: deterministic.
        val rng = java.util.Random(20260802)
        for (n in listOf(1, 3, 5, 10)) {
            repeat(200) {
                val stream = List(rng.nextInt(60)) { rng.nextInt(8).toFloat() }
                assertEquals(reference(n, stream), track(n, stream))
            }
        }
    }
}
