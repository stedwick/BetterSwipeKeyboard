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
 * swiped, so it has no record), `swipe_trails5_normal2_philip.*` (the
 * ten-sentence corpus re-recorded at normal speed, one pass), and
 * `swipe_trails6_short_words_philip.*` (short-word paragraph — 19
 * swipeable words: am well and we go up the hill to ask if you will
 * fix it hello it is fun — swiped TWICE: pass 1 with a deliberate stop
 * on each last letter, pass 2 with natural drift lift-offs; #34/#35 are
 * probable echo swipes of 'it', not paragraph words, marked `-`).
 * The TSV's third column labels the pass for the reader; the pass split
 * is visible in the printed table by record index (pass 1 = #0-18,
 * pass 2 = #19-39).
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

    @Test
    fun `fifth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails5_normal2_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET5",
            correct >= MIN_COMMITTED_CORRECT_SET5,
        )
    }

    @Test
    fun `sixth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails6_short_words_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET6",
            correct >= MIN_COMMITTED_CORRECT_SET6,
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
         * and lazy #34 ('lay' coin-flip at the commit threshold).
         *
         * Lift-off evidence grading (isolated lift-off region emits no
         * salient key): lazy #34 fixed — the drift lift-off's free anchor
         * had been handing 'lay' its y. 34 -> 35, earned. The mother
         * misses shuffle impostors (#3 now silence via 'misinterpret',
         * #21 wrong-commits 'norbert') — same class, unchanged count. */
        const val MIN_COMMITTED_CORRECT_SET3 = 35

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

        /** Fifth capture (ten-sentence corpus RE-recorded at normal speed,
         * one pass, no retries; 'a' tapped in s8) baseline on main 7d48da9:
         * 58/65. Misses: dog #8 (doping 1.01, intent out of top-5 — the
         * lift-off drift class), minimum #13 (min 2.53 silence), his #14
         * (hours -0.89, intent out of top-5), past #33 (part by 0.11),
         * we #36 (were by 0.05), fix #40 (fox 0.05, intent out of top-5),
         * quick #52 (wick by 0.01). The set-4 mis-swipe candidates
         * (how/nine/nice/example/excellent) all pass in this re-recording,
         * confirming the diagnosis.
         *
         * Lift-off evidence grading: we #36 + quick #52 fixed (drift
         * lift-offs near r/k had handed were/wick a free salient),
         * had #60 FLIPPED to has (by 0.10) — the symmetric cost: that
         * lift-off also shows no deceleration, but the drift happened to
         * end 0.38kw from the RIGHT key (d), so the dropped anchor was
         * luck helping a thin margin, not evidence. Net 58 -> 59, earned. */
        const val MIN_COMMITTED_CORRECT_SET5 = 59

        /** Sixth capture (short-word paragraph, two passes) baseline on
         * feature/endpoint-evidence 103d75b: 34/38. Pass 1 (deliberate
         * stops on the last letter, #0-18): 18/19 — only and #2 misses
         * (amd -0.38, intent out of top-5). Pass 2 (natural drift
         * lift-offs, #19-39 minus the two '-' echo swipes): 16/19 —
         * we #22 (were by 0.03: drift ended 0.5kw from R and the
         * hardcoded lift-off anchor handed 'were' a free salient),
         * you #30 (yoy -0.56: drift ended 0.7kw from Y), hello #36
         * (help by 0.01). The 18/19-vs-16/19 deliberate-vs-drift split
         * is the captured contrast the lift-off grading targets: drift
         * lift-offs land near wrong keys, deliberate stops do not.
         * #34/#35 are excluded ('-'): timestamps + i->t endpoint
         * geometry say they are echo swipes of 'it', not paragraph
         * words (probable, not proven — flagged to Philip).
         *
         * Lift-off evidence grading: we #22 fixed (same r-anchor disease
         * as set5's we #36) — 34 -> 35, earned. Pass 1 unchanged (18/19):
         * deliberate stops decelerate, so their end regions are
         * non-isolated and keep their keys. Remaining: and #2, you #30
         * (yoy), hello #36 (help). */
        const val MIN_COMMITTED_CORRECT_SET6 = 35
    }
}
