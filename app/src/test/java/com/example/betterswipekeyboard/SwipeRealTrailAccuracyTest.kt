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
 * ([MAX_COMMIT_SCORE]) exactly as KeyboardScreen does. Two sets:
 * `swipe_trails_philip.*` (first capture) and `swipe_trails2_philip.*`
 * (second capture, both phrases twice; intent `-` marks a genuine
 * mis-swipe — the user's typo, excluded from the counts).
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
                "#%-3d intent=%-10s top=%-11s (%6.2f) intentRank=%-3s committed=%-11s %s".format(
                    i,
                    intent,
                    top?.word ?: "-",
                    top?.score ?: Float.NaN,
                    if (intentRank < 0) "out" else "#${intentRank + 1}",
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
        /** Best achieved so far per set — raise on every win, never lower. */
        const val MIN_COMMITTED_CORRECT_SET1 = 11
        const val MIN_COMMITTED_CORRECT_SET2 = 25
    }
}
