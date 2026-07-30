package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards for the end-key surcharge ([END_KEY_SURCHARGE_WEIGHT] in
 * SwipeDecoder — the hello->help fix). The disease, measured on Philip's 13
 * captured hello trails (swipe_trails_word_hello.jsonl): on a drift lift-off
 * near O, the never-touched P (adjacent to O, 1.0kw center-to-center) gets a
 * first-basin match at 0.81-0.95kw whose charge the per-letter mean dilutes
 * to ~0.2 — and the frequency prior (help rank 163 vs hello rank 1905, a
 * constant +0.68 for help) then decides the word. The surcharge charges the
 * last letter's beyond-tunnel distance again, undiluted, so a word must END
 * on the trail.
 *
 * Each test pins one side of the lever:
 *
 *  - the drift-lift-off shape commits hello (pre-lever it committed help —
 *    measured help -1.693 vs hello -1.532 on this exact synthetic shape
 *    before the surcharge landed; the shape was tuned into the real-trail
 *    envelope: lift-off 0.22kw from O (real: 0.14-0.36), 1.18kw from P
 *    (real: 1.19-1.37), p's first-basin match 0.90kw (real: 0.81-0.95),
 *    salient [h,e,l,o] as on 8/13 real trails);
 *  - a genuine neighbor-end trail stays help: a real help swipe ENDS on P,
 *    pays no surcharge, and the lever must never punish it (no real help
 *    capture exists yet — this synthetic shape is the stand-in, see the
 *    investigation addendum's not-verified list).
 */
class SwipeEndKeySurchargeTest {

    private lateinit var decoder: SwipeDecoder

    /** 100px keys, rows offset like a real QWERTY keyboard. */
    private val keyCenters: Map<Char, Vec2> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Vec2(50f + i * 100f, 50f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Vec2(100f + i * 100f, 150f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Vec2(250f + i * 100f, 250f)) }
    }
    private val keyWidth = 100f

    @Before
    fun setUp() {
        decoder = SwipeDecoder(
            Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!),
        )
    }

    private fun centerOf(letter: Char) = keyCenters.getValue(letter)

    /**
     * Builds a trail through the given waypoints: points every ~10px, 16ms
     * apart, ending in the same decelerating lift-off creep as
     * SwipeDecoderTest.trailThrough (which see for why the points keep
     * creeping forward). Raw points, not letters — the drift waypoints
     * deliberately sit between keys.
     */
    private fun trailThroughPoints(vararg waypoints: Vec2): List<TimedPoint> {
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
        }
        val dir = waypoints.last() - waypoints[waypoints.size - 2]
        val len = waypoints.last().distanceTo(waypoints[waypoints.size - 2])
        val step = if (len > 1e-6f) Vec2(dir.x / len, dir.y / len) else Vec2(1f, 0f)
        var offset = 0f
        for ((advance, gap) in listOf(3f to 24L, 2f to 40L, 1f to 64L)) {
            offset += advance
            points += TimedPoint(
                Vec2(waypoints.last().x + step.x * offset, waypoints.last().y + step.y * offset),
                t,
            )
            t += gap
        }
        return points
    }

    private fun decodeCommitted(trail: List<TimedPoint>): String? {
        val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
        println(results.joinToString { "${it.word}=%.3f".format(it.score) })
        return results.firstOrNull()?.takeIf { it.score < MAX_COMMIT_SCORE }?.word
    }

    @Test
    fun `drift lift off toward the neighbor commits hello, not help`() {
        // h -> e -> l, then the real-trail endgame: a slight bow toward the
        // o/p midpoint mid-approach (Philip's trails pass right of O's
        // center on the way up) and a lift-off short-left of O. Without the
        // surcharge this committed help by 0.161 (frequency overruled the
        // diluted p-distance, exactly the captured disease); the surcharge
        // charges help's p (0.90kw basin) an extra 0.20 undiluted and hello
        // wins.
        val trail = trailThroughPoints(
            centerOf('h'), centerOf('e'), centerOf('l'), Vec2(872f, 95f), Vec2(838f, 68f),
        )
        assertEquals("hello", decodeCommitted(trail))
    }

    @Test
    fun `genuine neighbor end stays help`() {
        // A real help swipe ENDS on P: through the key centers with the
        // standard decelerating creep, p matches at ~0.0kw, the surcharge is
        // exactly zero, and help commits (pre-lever: help -2.279, margin
        // >1.0 over hell — the lever must not touch this shape).
        val trail = trailThroughPoints(
            centerOf('h'), centerOf('e'), centerOf('l'), centerOf('p'),
        )
        assertEquals("help", decodeCommitted(trail))
    }
}
