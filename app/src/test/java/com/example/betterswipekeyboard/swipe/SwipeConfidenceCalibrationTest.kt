package com.example.betterswipekeyboard.swipe

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Calibration ratchet for [LOW_CONFIDENCE_MARGIN]: replays the six
 * captured real-hand trail sets (same fixtures as
 * `SwipeRealTrailAccuracyTest`, intents as ground truth) and counts how
 * many wrong commits and how many correct commits the yellow flash flags.
 *
 * Floors/ceiling pin the measured trade at the 0.25 cutoff (see the
 * calibration table in [LOW_CONFIDENCE_MARGIN]'s KDoc): 9/17 wrong
 * commits flagged, 15/237 correct commits flagged (6.3%). Raise the
 * wrong-flagged floor only when decoder tuning earns it; the
 * correct-flagged ceiling may only move down. If the decoder's scores
 * shift (retuning), re-measure the whole table before touching the
 * constant — don't just relax these numbers.
 *
 * Recalibration history: the last-letter lift-off re-match
 * ([REBASIN_RADIUS_KEYS]) moved the ratchets 11/20 -> 9/17 and
 * 14/234 -> 15/237. Both moves are denominator changes, not flag-rate
 * changes: four formerly wrong commits now commit CORRECTLY (dog/his/
 * fix/and — fixed swipes, not lost flags) and one formerly correct
 * commit flipped wrong (lazy#34, signed off), so the wrong pool shrank
 * 20 -> 17; the correct ceiling rose by one of the newly-correct
 * commits, which has a genuinely close runner-up (a close race the
 * yellow flash exists for). Flag rates: 53% vs 55% of wrong, 6.3% vs
 * 6.0% of correct — unchanged.
 *
 * The end-key surcharge ([END_KEY_SURCHARGE_WEIGHT], the hello->help
 * fix) moved the ratchets 9/17 -> 8/16 and 15/237 -> 14/237. Again a
 * denominator change, not a flag-rate change: the surcharge pushed the
 * signed-off lazy->last wrong commit (set2#35, pre-lever score 1.647)
 * past MAX_COMMIT_SCORE into silence, and that commit (margin 0.13)
 * was one of the 9 flagged — the floor drops only because it no longer
 * exists. The correct ceiling IMPROVED 15 -> 14 (5.9%) and ceilings
 * only move down. Flag rates: 50% vs 53% of wrong, 5.9% vs 6.3% of
 * correct. The constant stays 0.25 (see its KDoc: 0.30's one extra
 * flag is a one-commit margin artifact, not a knee shift).
 *
 * The start-key surcharge ([START_KEY_SURCHARGE_WEIGHT], the go->to
 * fix): 253 committed (235 correct, 18 wrong), measured 8/18 wrong and
 * 9/235 correct (3.8%) flagged at 0.25. The two signed-off q/w aim
 * slips (set4#54, set5#52) flipped correct -> wrong (quick->wick) —
 * denominator changes: the correct pool loses two flagged commits
 * (margins 0.24/0.03 pre-lever), the wrong pool gains two (margins
 * 0.02 flagged, 0.53 not flagged). The wrong floor stays 8: set4#32's
 * flagged 'notice' wrong commit had its margin widened 0.068 -> 0.473
 * (its runner-up pays the surcharge) while set4#54's new 'wick' is
 * flagged at 0.02. The correct ceiling IMPROVES 14 -> 9: four flagged
 * correct commits' margins widened past 0.25 (set1#6 us, set1#12
 * jumps, set3#6 us, set5#31 mice — their runner-ups pay the start
 * surcharge), the two quick flips removed their two flagged commits,
 * and one correct commit became flagged (set6#39 fun 0.406 -> 0.238 —
 * its own 0.74kw start miss narrows its margin against 'gun').
 * Ceilings only move down. The constant stays 0.25 (0.30 buys one
 * flag, a single 0.27-margin commit — same one-commit artifact as
 * the end-surcharge table).
 */
class SwipeConfidenceCalibrationTest {

    private data class Committed(val correct: Boolean, val margin: Float, val score: Float)

    @Test
    fun `yellow flash flags at least the calibrated share of wrong commits`() {
        val flagged = committedSwipes().count { !it.correct && it.margin < LOW_CONFIDENCE_MARGIN }
        assertTrue(
            "ratchet: wrong commits flagged dropped below $MIN_WRONG_FLAGGED (got $flagged)",
            flagged >= MIN_WRONG_FLAGGED,
        )
    }

    @Test
    fun `yellow flash cries wolf on at most the calibrated share of correct commits`() {
        val flagged = committedSwipes().count { it.correct && it.margin < LOW_CONFIDENCE_MARGIN }
        assertTrue(
            "ceiling: correct commits flagged rose above $MAX_CORRECT_FLAGGED (got $flagged)",
            flagged <= MAX_CORRECT_FLAGGED,
        )
    }

    /**
     * Prints the calibration table behind [LOW_CONFIDENCE_MARGIN]'s KDoc:
     * wrong/correct commits flagged at each candidate cutoff. Not an
     * assertion — the documented recalibration workflow ("if the decoder's
     * scores shift, re-measure the whole table before touching the
     * constant") reads this table from the test report's standard output.
     */
    @Test
    fun `print the margin calibration table`() {
        val committed = committedSwipes()
        val wrong = committed.filter { !it.correct }
        val correct = committed.filter { it.correct }
        println("committed=${committed.size} (${correct.size} correct, ${wrong.size} wrong)")
        println("margin < M    wrong flagged   correct flagged")
        listOf(0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f, 0.40f, 0.45f).forEach { m ->
            val wf = wrong.count { it.margin < m }
            val cf = correct.count { it.margin < m }
            println(
                "%.2f          %d/%d            %d/%d  (%.1f%%)".format(
                    m, wf, wrong.size, cf, correct.size, 100f * cf / correct.size,
                ),
            )
        }
        println(
            "wrong-commit margins: " + wrong.map { "%.2f".format(it.margin) }.sorted().toString() +
                "  scores: " + wrong.map { "%.2f".format(it.score) }.sorted().toString(),
        )
    }

    /** Every committed swipe across the six fixture sets: correct? and
     * its top2-top1 margin (infinity when there is no runner-up). */
    private fun committedSwipes(): List<Committed> {
        val decoder = SwipeDecoder(
            Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!),
        )
        return SETS.flatMap { base ->
            val intents = javaClass.getResourceAsStream("/$base.intents.tsv")!!
                .bufferedReader().readLines()
                .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()
            javaClass.getResourceAsStream("/$base.jsonl")!!
                .bufferedReader().readLines().filter { it.isNotBlank() }
                .mapIndexedNotNull { i, line ->
                    val intent = intents.getValue(i)
                    if (intent == "-") return@mapIndexedNotNull null
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
                    val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
                    val top = results.firstOrNull()
                        ?.takeIf { it.score < MAX_COMMIT_SCORE }
                        ?: return@mapIndexedNotNull null
                    Committed(
                        correct = top.word == intent,
                        margin = results.getOrNull(1)?.score?.minus(top.score)
                            ?: Float.POSITIVE_INFINITY,
                        score = top.score,
                    )
                }
        }
    }

    private companion object {
        val SETS = listOf(
            "swipe_trails_philip",
            "swipe_trails2_philip",
            "swipe_trails3_philip",
            "swipe_trails4_normal_philip",
            "swipe_trails5_normal2_philip",
            "swipe_trails6_short_words_philip",
        )

        /** Measured at 0.25 on the six sets: 8/18 wrong commits flagged
         * (was 8/16 — the start-key surcharge's two signed-off quick->wick
         * flips grew the pool to 18; the flagged count is unchanged:
         * set4#32's 'notice' margin widened 0.068 -> 0.473 and lost its
         * flag, set4#54's new 'wick' is flagged at 0.02). */
        const val MIN_WRONG_FLAGGED = 8

        /** Measured at 0.25 on the six sets: 9/235 correct commits (3.8%)
         * (was 14/237 = 5.9% — the start-key surcharge widened four
         * flagged margins past 0.25 and removed the two flagged quicks;
         * set6#39 fun became flagged. Ceilings only move down). */
        const val MAX_CORRECT_FLAGGED = 9
    }
}
