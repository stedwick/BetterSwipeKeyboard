package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * Same as [SwipeDecoderTest] but with trails that look like a real finger
 * drew them: dense points (~3px apart, ~8ms apart), ±3px jitter, and a
 * natural speed profile (slow at the start/end, faster in the middle).
 */
class SwipeDecoderRealisticTrailTest {

    private lateinit var decoder: SwipeDecoder

    private val keyCenters: Map<Char, Vec2> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Vec2(50f + i * 100f, 50f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Vec2(100f + i * 100f, 150f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Vec2(250f + i * 100f, 250f)) }
    }

    @Before
    fun setUp() {
        val words = javaClass.getResourceAsStream("/words_en.txt")!!
        decoder = SwipeDecoder(Dictionary.load(words))
    }

    private fun centerOf(letter: Char) = keyCenters.getValue(letter)

    private fun realisticTrail(vararg letters: Char): List<TimedPoint> {
        val random = Random(42)
        val waypoints = letters.map { centerOf(it) }
        val points = mutableListOf<TimedPoint>()
        var t = 0L
        fun add(p: Vec2, speedFactor: Float) {
            val jittered = Vec2(
                p.x + (random.nextFloat() - 0.5f) * 6f,
                p.y + (random.nextFloat() - 0.5f) * 6f,
            )
            points += TimedPoint(jittered, t)
            t += (3 / speedFactor).toLong().coerceAtLeast(2)
        }
        add(waypoints.first(), 0.3f)
        for (w in 1 until waypoints.size) {
            val from = waypoints[w - 1]
            val to = waypoints[w]
            val steps = maxOf(1, (from.distanceTo(to) / 3f).toInt())
            for (s in 1..steps) {
                // Slow near segment ends, fast in the middle. The 0.15 floor
                // makes turns genuinely linger (a real finger slows hard to
                // reverse direction) so deliberate-turn dwells sit above
                // DWELL_DOUBLE_MS under the faster 3 ms base gap — the base
                // came down 8 -> 3 ms when the mid-word dwell skip charge
                // (Addendum 10) started reading contiguous stays: at 8 ms a
                // mere slow crossing faked a >= MIDWORD_DWELL_MS stay, and
                // the synthetic trails failed every crossed-key guard.
                val phase = s.toFloat() / steps
                val speedFactor = 0.15f + 0.85f * sin(phase * Math.PI).toFloat()
                add(Vec2(from.x + (to.x - from.x) * s / steps, from.y + (to.y - from.y) * s / steps), speedFactor)
            }
        }
        // Real lift-offs decelerate into the final key (cf. the deliberate
        // stops of the trails6 pass-1 capture): the sin profile's ~26 ms end
        // gaps alone don't register as measured slowness over the 0.35kw
        // window, so the end region stayed evidence-free. Near-stationary
        // points with growing gaps fix that — but they keep CREEPING
        // forward: a fully stationary blob reaches into the final leg
        // through the salience window and merges the last turn's region
        // into the end region (it ate "hello"'s double-L dwell). ~128 ms
        // total, well under DWELL_DOUBLE_MS — the last letter never doubles.
        val dir = waypoints.last() - waypoints[waypoints.size - 2]
        val len = waypoints.last().distanceTo(waypoints[waypoints.size - 2])
        val step = if (len > 1e-6f) Vec2(dir.x / len, dir.y / len) else Vec2(1f, 0f)
        var offset = 0f
        for ((advance, gap) in listOf(3f to 24L, 2f to 40L, 1f to 64L)) {
            offset += advance
            val jittered = Vec2(
                waypoints.last().x + step.x * offset + (random.nextFloat() - 0.5f) * 6f,
                waypoints.last().y + step.y * offset + (random.nextFloat() - 0.5f) * 6f,
            )
            points += TimedPoint(jittered, t)
            t += gap
        }
        return points
    }

    @Test
    fun `realistic swipe trail decodes swipe`() {
        val results = decoder.decode(realisticTrail('s', 'w', 'p', 'e'), keyCenters, KEY_WIDTH)
        assertEquals("swipe", results.firstOrNull()?.word)
    }

    @Test
    fun `realistic trail decodes hello`() {
        val results = decoder.decode(realisticTrail('h', 'e', 'l', 'o'), keyCenters, KEY_WIDTH)
        assertEquals("hello", results.firstOrNull()?.word)
    }

    @Test
    fun `realistic trail decodes follow`() {
        val results = decoder.decode(realisticTrail('f', 'o', 'l', 'o', 'w'), keyCenters, KEY_WIDTH)
        assertEquals("follow", results.firstOrNull()?.word)
    }

    @Test
    fun `realistic trail with neighbor slip decodes jumps`() {
        val results = decoder.decode(realisticTrail('j', 'u', 'm', 'p', 'w'), keyCenters, KEY_WIDTH)
        assertEquals("jumps", results.firstOrNull()?.word)
    }

    @Test
    fun `realistic s w i p e trail decodes swipe, not swapped`() {
        // Real-world regression from the old unordered scorer: "swapped"
        // parked its letters anywhere (A next to S, double-P on one pass,
        // D under E) and beat "swipe" (-0.202 vs +0.067). Ordered
        // alignment + line conformance kill it: after S→W the trail heads
        // to I, the opposite direction of A, so "swapped"'s A matches far
        // off-trail and its S→W→A legs violate line conformance.
        // NOTE: this branch's word list has "swipe" (manual supplement)
        // but not "swapped" yet (main's rebuilt list has both) — the
        // score comparison activates once the dictionary branch merges.
        val results = decoder.decode(realisticTrail('s', 'w', 'i', 'p', 'e'), keyCenters, KEY_WIDTH)
        assertEquals("swipe", results.firstOrNull()?.word)
        val scores = results.associate { it.word to it.score }
        if ("swapped" in scores) {
            assertTrue(scores.getValue("swipe") < scores.getValue("swapped"))
        }
    }

    private companion object {
        const val KEY_WIDTH = 100f
    }
}
