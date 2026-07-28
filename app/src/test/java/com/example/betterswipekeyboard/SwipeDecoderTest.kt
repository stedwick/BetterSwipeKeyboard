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

    /**
     * Straight d->o->g trail whose speed dips near F (Philip's "dog"
     * slowdown class): [slowGapMs] between the points within 35px of F,
     * 3 ms elsewhere — a slight slowdown over a crossed key, not a turn.
     * At 12 ms the dip totals ~50 ms near the region peak (below the dwell
     * gate); at 16 ms it totals ~65 ms (above). The geometry never changes,
     * only the timing, so any scoring difference is the gate's doing.
     */
    private fun dogTrailWithSpeedDip(slowGapMs: Long): List<TimedPoint> {
        val f = centerOf('f')
        val waypoints = listOf(centerOf('d'), centerOf('o'), centerOf('g'))
        val points = mutableListOf<TimedPoint>()
        var t = 0L
        fun add(p: Vec2) {
            val gap = if (p.distanceTo(f) < 35f) slowGapMs else 3L
            points += TimedPoint(p, t)
            t += gap
        }
        add(waypoints.first())
        for (w in 1 until waypoints.size) {
            val from = waypoints[w - 1]
            val to = waypoints[w]
            val steps = maxOf(1, (from.distanceTo(to) / 10f).toInt())
            for (s in 1..steps) {
                add(Vec2(from.x + (to.x - from.x) * s / steps, from.y + (to.y - from.y) * s / steps))
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

    @Test
    fun `two letter word up decodes on a short swipe`() {
        val results = decoder.decode(trailThrough('u', 'p'), keyCenters, KEY_WIDTH)
        assertEquals("up", results.top())
    }

    @Test
    fun `two letter word hi decodes on a short swipe`() {
        val results = decoder.decode(trailThrough('h', 'i'), keyCenters, KEY_WIDTH)
        assertEquals("hi", results.top())
    }

    @Test
    fun `my beats mummy on a straight m to y trail`() {
        // The straight M→Y diagonal passes within ~0.7 key-widths of U, but
        // "mummy" needs the trail to zigzag M→U→M→Y — ordered alignment and
        // line conformance must reject it even though every one of its
        // letters is near SOME trail point (the old unordered scorer's bug).
        val results = decoder.decode(trailThrough('m', 'y'), keyCenters, KEY_WIDTH)
        assertEquals("my", results.top())
    }

    @Test
    fun `mummy wins on a zigzag m u m y trail`() {
        val results = decoder.decode(trailThrough('m', 'u', 'm', 'y'), keyCenters, KEY_WIDTH)
        assertEquals("mummy", results.top())
    }

    @Test
    fun `am wins on a long straight a to m trail`() {
        // A→M spans ~7 key-widths: no trail-length gate may exclude the
        // obvious word when the trail is straight (both endpoint keys aim
        // true and the trail never leaves the A→M corridor).
        val results = decoder.decode(trailThrough('a', 'm'), keyCenters, KEY_WIDTH)
        assertEquals("am", results.top())
    }

    @Test
    fun `ak still loses to ask on the straight a to k trail`() {
        // Both words tunnel perfectly on this straight home-row trail, so
        // the tie is broken by frequency (ask rank 723 vs ak rank 4917)
        // plus the length bonus — the mechanism that replaced the old
        // two-letter trail-length gate.
        val results = decoder.decode(trailThrough('a', 'k'), keyCenters, KEY_WIDTH)
        assertEquals("ask", results.top())
        val scores = results.associate { it.word to it.score }
        if ("ak" in scores) {
            assertTrue(scores.getValue("ask") < scores.getValue("ak"))
        }
    }

    @Test
    fun `brief mid-trail slowdown does not mark the crossed key deliberate`() {
        // Philip's "dog must not become fog on a slight slowdown" class: a
        // d->o->g swipe whose speed dips near F for ~50 ms. The dwell gate
        // drops the slow region, so F never enters the salient keys and
        // "fog" stays out of the candidates entirely.
        val results = decoder.decode(dogTrailWithSpeedDip(slowGapMs = 12), keyCenters, KEY_WIDTH)
        assertEquals("dog", results.top())
        assertTrue("fog" !in results.map { it.word })
    }

    @Test
    fun `lingering mid-trail slowdown marks the crossed key deliberate`() {
        // Same trail with a ~65 ms linger near F: the region is admitted, F
        // becomes salient, "dog" pays the missed-salient charge it dodged
        // above, and "fog" enters the candidates (still loses on frequency).
        val brief = decoder.decode(dogTrailWithSpeedDip(slowGapMs = 12), keyCenters, KEY_WIDTH)
        val linger = decoder.decode(dogTrailWithSpeedDip(slowGapMs = 16), keyCenters, KEY_WIDTH, topN = 10)
        assertEquals("dog", linger.top())
        val briefDog = brief.first { it.word == "dog" }.score
        val lingerDog = linger.first { it.word == "dog" }.score
        assertTrue(lingerDog > briefDog + 0.2f)
        assertTrue(linger.any { it.word == "fog" })
    }

    @Test
    fun `word ignoring the trail opening pays the unexplained head charge`() {
        // Straight top-row O->T trail. "it" matches its i a full key-width
        // in, ignoring the o->i opening; "out" explains the whole trail.
        // The unexplained-head charge is what puts "it" clearly behind —
        // zero HEAD_ARC_WEIGHT and the gap shrinks from ~0.76 to ~0.26.
        val results = decoder.decode(trailThrough('o', 't'), keyCenters, KEY_WIDTH)
        assertEquals("out", results.top())
        val scores = results.associate { it.word to it.score }
        assertTrue(scores.getValue("it") - scores.getValue("out") > 0.5f)
    }

    private fun List<ScoredWord>.top(): String =
        firstOrNull()?.word ?: error("no candidates")

    private companion object {
        const val KEY_WIDTH = 100f
    }
}
