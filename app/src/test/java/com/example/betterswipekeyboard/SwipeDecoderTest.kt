package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.ScoredWord
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SwipeDecoderTest {

    private lateinit var decoder: SwipeDecoder

    /** 100px keys, rows offset like a real QWERTY keyboard. */
    private val keyCenters: Map<Char, Vec2> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Vec2(50f + i * 100f, 50f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Vec2(100f + i * 100f, 150f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Vec2(250f + i * 100f, 250f)) }
    }

    @Before
    fun setUp() {
        val words = javaClass.getResourceAsStream("/words_en.txt")
            ?: error("words_en.txt missing from test resources")
        decoder = SwipeDecoder(Dictionary.load(words))
    }

    private fun centerOf(letter: Char) = keyCenters.getValue(letter)

    /**
     * Builds a trail through the given waypoints: points every ~10px, 16ms
     * apart. Waypoints listed in [dwellOn] get extra stationary points, making
     * them slow (salient) spots — the user "hesitating" on a key.
     */
    private fun trailThrough(vararg letters: Char, dwellOn: Set<Char> = emptySet()): List<TimedPoint> {
        val waypoints = letters.map { centerOf(it) }
        val points = mutableListOf<TimedPoint>()
        var t = 0L
        fun add(p: Vec2) {
            points += TimedPoint(p, t)
            t += 16
        }
        add(waypoints.first())
        for (w in 1 until waypoints.size) {
            val from = waypoints[w - 1]
            val to = waypoints[w]
            val steps = maxOf(1, (from.distanceTo(to) / 10f).toInt())
            for (s in 1..steps) {
                add(Vec2(from.x + (to.x - from.x) * s / steps, from.y + (to.y - from.y) * s / steps))
            }
            if (letters[w] in dwellOn) {
                repeat(25) { add(to) } // ~400ms standing still on the key
            }
        }
        return points
    }

    @Test
    fun `swipe decodes with crossed letter i`() {
        // Trail turns on s, w, p, e only; the i is crossed without stopping.
        val results = decoder.decode(trailThrough('s', 'w', 'p', 'e'), keyCenters, KEY_WIDTH)
        assertEquals("swipe", results.top())
    }

    @Test
    fun `follow decodes with single pass over l, helped by a dwell`() {
        val results = decoder.decode(
            trailThrough('f', 'o', 'l', 'o', 'w', dwellOn = setOf('l')),
            keyCenters,
            KEY_WIDTH,
        )
        assertEquals("follow", results.top())
        val scores = results.associate { it.word to it.score }
        if ("flow" in scores) {
            assertTrue(scores.getValue("follow") < scores.getValue("flow"))
        }
    }

    @Test
    fun `power decodes along the top row`() {
        val results = decoder.decode(trailThrough('p', 'o', 'w', 'e', 'r'), keyCenters, KEY_WIDTH)
        assertEquals("power", results.top())
    }

    @Test
    fun `straight line a to k decodes as ask`() {
        val results = decoder.decode(trailThrough('a', 'k'), keyCenters, KEY_WIDTH)
        assertEquals("ask", results.top())
    }

    @Test
    fun `candidates are pruned by trail start and end`() {
        val results = decoder.decode(trailThrough('z', 'm'), keyCenters, KEY_WIDTH)
        val nearZ = setOf('z', 'x', 's', 'd')
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.word.first() in nearZ })
    }

    @Test
    fun `short trail yields no candidates`() {
        val tiny = listOf(
            TimedPoint(centerOf('h'), 0),
            TimedPoint(centerOf('h'), 16),
        )
        assertTrue(decoder.decode(tiny, keyCenters, KEY_WIDTH).isEmpty())
    }

    @Test
    fun `neighbor slip at the end still decodes - jumpw becomes jumps`() {
        // The user swipes j-u-m-p-w but means "jumps" (w is next to s).
        val results = decoder.decode(trailThrough('j', 'u', 'm', 'p', 'w'), keyCenters, KEY_WIDTH)
        assertEquals("jumps", results.top())
    }

    private fun List<ScoredWord>.top(): String =
        firstOrNull()?.word ?: error("no candidates")

    private companion object {
        const val KEY_WIDTH = 100f
    }
}
