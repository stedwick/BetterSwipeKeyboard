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
 * calibration table in [LOW_CONFIDENCE_MARGIN]'s KDoc): 11/20 wrong
 * commits flagged, 14/234 correct commits flagged (6.0%). Raise the
 * wrong-flagged floor only when decoder tuning earns it; the
 * correct-flagged ceiling may only move down. If the decoder's scores
 * shift (retuning), re-measure the whole table before touching the
 * constant — don't just relax these numbers.
 */
class SwipeConfidenceCalibrationTest {

    private data class Committed(val correct: Boolean, val margin: Float)

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

        /** Measured at 0.25 on the six sets: 11/20 wrong commits flagged. */
        const val MIN_WRONG_FLAGGED = 11

        /** Measured at 0.25 on the six sets: 14/234 correct commits (6.0%). */
        const val MAX_CORRECT_FLAGGED = 14
    }
}
