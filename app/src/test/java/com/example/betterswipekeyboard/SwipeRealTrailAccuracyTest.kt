package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real-hand accuracy harness: replays Philip's captured swipes (recorded
 * on a Galaxy Fold via SwipeTrailCapture) with their confirmed intended
 * words through the decoder and applies the commit rule
 * ([MAX_COMMIT_SCORE]) exactly as KeyboardScreen does. Four sets:
 * `swipe_trails_philip.*` (first capture), `swipe_trails2_philip.*`
 * (second capture, both phrases twice; intent `-` marks a genuine
 * mis-swipe — the user's typo, excluded from the counts),
 * `swipe_trails3_philip.*` (third capture: sentence 1 with 'bought',
 * sentence 1 with 'sold', the pangram twice, then a lone 'mother'
 * retry — recorded on the combined build with the frequency prior
 * at 3.0 and the filtered word list), and
 * `swipe_trails4_normal_philip.*` (fourth capture: the ten-sentence
 * TDD corpus at normal speed, one pass — 65 word slots plus two
 * genuine retries in sentence 4, 'excellent' #23/#24 and 'example'
 * #25/#26, both scored; sentence 8's one-letter 'a' was tapped, not
 * swiped, so it has no record).
 *
 * This is a RATCHET: the MIN_COMMITTED_CORRECT constants are the best
 * committed-correct counts achieved so far per set; bump them every time
 * tuning gains a trail, never lower them. The per-trail table is printed
 * for the tuning loop (see the test report's standard output).
 */
class SwipeRealTrailAccuracyTest {

    private lateinit var decoder: SwipeDecoder

    @Before
    fun setUp() {
        decoder = SwipeDecoder(
            Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!),
        )
    }

    @Test
    fun `first capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET1",
            correct >= MIN_COMMITTED_CORRECT_SET1,
        )
    }

    @Test
    fun `second capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails2_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET2",
            correct >= MIN_COMMITTED_CORRECT_SET2,
        )
    }

    @Test
    fun `third capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails3_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET3",
            correct >= MIN_COMMITTED_CORRECT_SET3,
        )
    }

    @Test
    fun `fourth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails4_normal_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET4",
            correct >= MIN_COMMITTED_CORRECT_SET4,
        )
    }

    /** Replays one capture set, prints the per-trail table, returns the
     * committed-correct count. */
    private fun replay(resourceBase: String): Int {
        val intents = javaClass.getResourceAsStream("/$resourceBase.intents.tsv")!!
            .bufferedReader().readLines()
            .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()
        val lines = javaClass.getResourceAsStream("/$resourceBase.jsonl")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }

        var committedCorrect = 0
        var topCorrect = 0
        var scored = 0
        lines.forEachIndexed { i, line ->
            val rec = JSONObject(line)
            val keyWidth = rec.getDouble("keyWidth").toFloat()
            val keysObj = rec.getJSONObject("keys")
            val keyCenters = keysObj.keys().asSequence().associate { k ->
                val xy = keysObj.getJSONArray(k)
                k.single() to Vec2(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            }
            val trailArr = rec.getJSONArray("trail")
            val trail = (0 until trailArr.length()).map { j ->
                val p = trailArr.getJSONArray(j)
                TimedPoint(
                    Vec2(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()),
                    p.getLong(2),
                )
            }
            val intent = intents.getValue(i)
            val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
            val intentRank = results.indexOfFirst { it.word == intent }
            val intentScore = results.getOrNull(intentRank)?.score
            val top = results.firstOrNull()
            val committed = top?.takeIf { it.score < MAX_COMMIT_SCORE }
            if (intent == "-") {
                println(
                    "#%-3d MIS-SWIPE (user typo, not scored) top=%-11s (%6.2f)".format(
                        i, top?.word ?: "-", top?.score ?: Float.NaN,
                    ),
                )
                return@forEachIndexed
            }
            scored++
            if (top?.word == intent) topCorrect++
            if (committed?.word == intent) committedCorrect++
            println(
                "#%-3d intent=%-10s top=%-11s (%6.2f) intentRank=%-3s intentScore=%6s committed=%-11s %s".format(
                    i,
                    intent,
                    top?.word ?: "-",
                    top?.score ?: Float.NaN,
                    if (intentRank < 0) "out" else "#${intentRank + 1}",
                    intentScore?.let { "%.2f".format(it) } ?: "-",
                    committed?.word ?: "(none)",
                    if (committed?.word == intent) "OK" else "MISS",
                ),
            )
        }
        println(
            "ACCURACY[$resourceBase] committed-correct=$committedCorrect/$scored " +
                "top1-correct=$topCorrect/$scored " +
                "(commit threshold $MAX_COMMIT_SCORE)",
        )
        return committedCorrect
    }

    private companion object {
        /**
         * Best achieved so far per set — raise on every win, never lower.
         *
         * History (set 1): the wordfreq dictionary swap (56k words,
         * replacing google-10000) regressed 6 common-word trails
         * (very->vey, quick->wick, brown->brien, jumps->humps,
         * over->iver, lazy->krazy), dropping set 1 from 11 to 5. The
         * wordlist junk filter (feature/wordlist-filter: rare proper
         * names + nonce respellings dropped by word class) removed vey,
         * brien, iver and krazy, recovering "very" and "lazy" -> 7/17.
         * The remaining misses are real-word or surviving-word
         * competitors (wick, humps, overt, britten, dix, doh) — decoder
         * scoring territory, for the frequency-tuning branch.
         *
         * Combined state (frequency weight 3.0 + filtered list, measured
         * in qa/acc-combined): set 1 = 12/17, set 2 = 32/36 — the
         * frequency prior and the filter compound (set 1 beats either
         * branch alone: 11 freq-only, 7 filter-only). Set-1 floor raised
         * 11 -> 12, earned.
         *
         * feature/endpoint-b3g (endpoint evidence grading: endpoint
         * re-anchoring, mid-trail dwell gate, no salience multiplier at
         * endpoint match indices, unexplained-head charge): set 1 #12
         * "jumps" fixed (the head charge sinks "humps", whose H basin
         * sits past the touch-down). Set-1 floor raised 12 -> 13, earned.
         * Set 2 unchanged at 32 — the endpoint classes this package
         * targets were already held by the frequency prior there.
         */
        const val MIN_COMMITTED_CORRECT_SET1 = 13
        const val MIN_COMMITTED_CORRECT_SET2 = 32

        /** Third capture's baseline at the B3 decoder: 34/37. The three
         * misses are mother x2 (#3 silence, #21 wrong-commits 'not') —
         * the known line-conformance-cull victims: deleting the 1.75kw
         * hard cull was MEASURED and rejected (it resurrected
         * ict/mortimer/lay/pad/liszt across all three sets and mother
         * still lost to 'mothed'/'moths' junk — the cull is not
         * mother's real blocker, the saturating conformance mean is) —
         * and lazy #34 ('lay' coin-flip at the commit threshold). */
        const val MIN_COMMITTED_CORRECT_SET3 = 34

        /** Fourth capture (ten-sentence TDD corpus, normal speed) baseline
         * at the B3 decoder: 60/67. The seven misses, by class:
         * excellent #23 + example #25 (wild-trail SILENCE — intent absent
         * from top-300, whole candidate field scores >3.3; both are
         * first attempts that Philip immediately retried successfully);
         * nine #31 + nice #32 (intent absent from top-300 while long
         * words bounce/notice partition the trail and win — the
         * mother-cull mechanism, NOT the predicted frequency tie);
         * past #35 (loses 'part' by 0.06); the #48 (loses 'that' by
         * 0.53); how #56 (loses 'hire' by 1.01 — a CONTROL-sentence
         * breach at baseline, fix is TDD territory). */
        const val MIN_COMMITTED_CORRECT_SET4 = 60
    }
}
