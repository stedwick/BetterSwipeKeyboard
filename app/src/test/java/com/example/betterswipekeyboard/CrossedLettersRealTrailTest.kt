package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import com.example.betterswipekeyboard.swipe.crossedLetters
import com.example.betterswipekeyboard.swipe.swipeLetters
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Crossed-letters ratchet: replays Philip's captured swipes (the same four
 * sets as [SwipeRealTrailAccuracyTest]) and checks, for every trail the
 * decoder commits CORRECTLY, that [crossedLetters] recovers the intended
 * word's letters as an in-order subsequence (consecutive duplicates of the
 * word collapsed first — a dwell is one visit to this helper, doubling is
 * the decoder's timed business). Misses are the approximation the prompt
 * warns about: letters absorbed by nearer keys and the e→r endpoint
 * disease.
 *
 * This is a RATCHET like the accuracy harness: the MIN_ constants are the
 * measured pass counts; bump them when the helper improves, never lower
 * them. Variant selection (nearest-key Voronoi vs radius gates) was
 * measured offline on all 157 records before landing — see the KDoc on
 * [crossedLetters].
 */
class CrossedLettersRealTrailTest {

    private lateinit var decoder: SwipeDecoder

    @Before
    fun setUp() {
        decoder = SwipeDecoder(
            Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!),
        )
    }

    @Test
    fun `first capture keeps its crossed-letters pass count`() {
        assertRate("swipe_trails_philip", MIN_PASS_SET1)
    }

    @Test
    fun `second capture keeps its crossed-letters pass count`() {
        assertRate("swipe_trails2_philip", MIN_PASS_SET2)
    }

    @Test
    fun `third capture keeps its crossed-letters pass count`() {
        assertRate("swipe_trails3_philip", MIN_PASS_SET3)
    }

    @Test
    fun `fourth capture keeps its crossed-letters pass count`() {
        assertRate("swipe_trails4_normal_philip", MIN_PASS_SET4)
    }

    private fun assertRate(resourceBase: String, minPass: Int) {
        val (pass, total) = replay(resourceBase)
        assertTrue(
            "ratchet: crossed-letters pass count dropped below $minPass/$total on $resourceBase",
            pass >= minPass,
        )
    }

    /** Returns (pass, total) over the set's committed-correct trails. */
    private fun replay(resourceBase: String): Pair<Int, Int> {
        val intents = javaClass.getResourceAsStream("/$resourceBase.intents.tsv")!!
            .bufferedReader().readLines()
            .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()
        val lines = javaClass.getResourceAsStream("/$resourceBase.jsonl")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }

        var pass = 0
        var total = 0
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
            if (intent == "-") return@forEachIndexed
            val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
            val committed = results.firstOrNull()?.takeIf { it.score < MAX_COMMIT_SCORE }
            if (committed?.word != intent) return@forEachIndexed

            total++
            val letters = crossedLetters(trail.map { it.position }, keyCenters)
            val expected = dedupConsecutive(swipeLetters(intent))
            if (isSubsequence(expected, letters)) {
                pass++
            } else {
                println("#%-3d intent=%-10s expected=%-10s crossed=%s".format(i, intent, expected, letters))
            }
        }
        println("CROSSED[$resourceBase] pass=$pass/$total")
        return pass to total
    }

    private companion object {
        /** Measured with nearest-key assignment on 2026-07-28 (see the
         * test report's standard output): set1 7/13, set2 28/32,
         * set3 30/34, set4 48/60 = 113/139 (81%) of committed-correct
         * trails. The misses concentrate on absorbed letters (`excellent`'s
         * l swallowed by j/k) and the e→r endpoint disease — the same
         * geometry the decoder struggles with. */
        const val MIN_PASS_SET1 = 7
        const val MIN_PASS_SET2 = 28
        const val MIN_PASS_SET3 = 30
        const val MIN_PASS_SET4 = 48

        fun dedupConsecutive(s: String): String = buildString {
            var last: Char? = null
            for (c in s) {
                if (c != last) {
                    append(c)
                    last = c
                }
            }
        }

        fun isSubsequence(needle: String, haystack: String): Boolean {
            var i = 0
            for (c in haystack) {
                if (i < needle.length && c == needle[i]) i++
            }
            return i == needle.length
        }
    }
}
