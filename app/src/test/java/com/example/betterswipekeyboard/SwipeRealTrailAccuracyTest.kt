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
 * Real-hand accuracy harness: replays Philip's captured swipes
 * (`swipe_trails_philip.jsonl`, recorded on a Galaxy Fold via
 * SwipeTrailCapture) with their confirmed intended words
 * (`swipe_trails_philip.intents.tsv`) through the decoder and applies the
 * commit rule ([MAX_COMMIT_SCORE]) exactly as KeyboardScreen does.
 *
 * This is a RATCHET: [MIN_COMMITTED_CORRECT] is the best committed-correct
 * count achieved so far; bump it every time tuning gains a trail, never
 * lower it. The per-trail table is printed for the tuning loop (see the
 * test report's standard output).
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
    fun `real-hand trails keep their committed-correct count`() {
        val intents = javaClass.getResourceAsStream("/swipe_trails_philip.intents.tsv")!!
            .bufferedReader().readLines()
            .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()
        val lines = javaClass.getResourceAsStream("/swipe_trails_philip.jsonl")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }

        var committedCorrect = 0
        var topCorrect = 0
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
            val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
            val intent = intents.getValue(i)
            val intentRank = results.indexOfFirst { it.word == intent }
            val top = results.firstOrNull()
            val committed = top?.takeIf { it.score < MAX_COMMIT_SCORE }
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
            "ACCURACY committed-correct=$committedCorrect/${lines.size} " +
                "top1-correct=$topCorrect/${lines.size} " +
                "(commit threshold $MAX_COMMIT_SCORE)",
        )
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT",
            committedCorrect >= MIN_COMMITTED_CORRECT,
        )
    }

    private companion object {
        /** Best achieved so far — raise on every tuning win, never lower. */
        const val MIN_COMMITTED_CORRECT = 11
    }
}
