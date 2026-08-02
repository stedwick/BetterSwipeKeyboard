package com.example.betterswipekeyboard.swipe

/**
 * Bounded top-N selection for [SwipeDecoder.decode] (perf A1): keeps the N
 * lowest-scored candidates in ONE pass over the candidate stream, replacing
 * collect-all + stable sort + take(N) — which allocated one [ScoredWord]
 * per candidate plus the sort's boxed-comparator garbage (~15-30% of decode
 * CPU at candidate scale) when only N results are ever read.
 *
 * The semantics reproduce `sortedBy { it.score }.take(N)` EXACTLY. A stable
 * ascending sort orders the candidates by (score, insertion sequence), so
 * [offer] inserts a candidate before the first slot whose score is STRICTLY
 * greater and displaces from the end: a candidate tying a kept slot goes
 * AFTER it, and a candidate tying the Nth-best score never enters. Culled
 * words (non-finite scores) are filtered by the caller, exactly as the old
 * `isFinite()` filter did before collection.
 *
 * Decode-local like the rest of the scratch state: one instance per decode,
 * never shared — decode() can run concurrently with itself on one
 * SwipeDecoder instance.
 */
internal class TopN(n: Int) {

    private val words = arrayOfNulls<String>(n)
    private val scores = FloatArray(n)

    /** Kept candidates, ≤ N (fewer when the stream was short). */
    var size = 0
        private set

    fun offer(word: String, score: Float) {
        // First kept slot STRICTLY worse than the candidate; ties skip past
        // existing equals (stable-sort insertion order).
        var i = 0
        while (i < size && scores[i] <= score) i++
        if (i == words.size) return // full, and the candidate loses or ties the cut
        val end = minOf(size + 1, words.size)
        for (j in end - 1 downTo i + 1) {
            scores[j] = scores[j - 1]
            words[j] = words[j - 1]
        }
        scores[i] = score
        words[i] = word
        if (size < words.size) size++
    }

    /** The kept candidates, best first — the order sortedBy+take produced. */
    fun results(): List<ScoredWord> =
        (0 until size).map { ScoredWord(words[it]!!, scores[it]) }
}
