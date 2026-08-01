package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards for the start-key surcharge ([START_KEY_SURCHARGE_WEIGHT] in
 * SwipeDecoder — the go->to fix). The disease, measured on Philip's 24
 * captured 'to go to' trails (swipe_trails7_to_go_to_philip.jsonl): on a
 * swipe starting ON G and sweeping up-right to O, the never-touched T
 * (0.73-1.16kw away at closest approach) gets a first-basin match at trail
 * index 0 whose charge the per-letter mean halves — the head-arc term sees
 * arc 0 and cannot charge it — and the frequency prior (to rank 2 vs go
 * rank 96, a constant +1.06 for to) then decides the word (6 of 10 go
 * attempts committed 'to'). The surcharge charges the first letter's
 * beyond-tunnel distance again, undiluted, so a word must START on the
 * trail.
 *
 * Each test pins one side of the lever:
 *
 *  - the drift-start shape commits go (pre-lever it committed to — measured
 *    to -2.293 vs go -2.161 on this exact synthetic shape with
 *    START_KEY_SURCHARGE_WEIGHT = 0.0; the shape was tuned into the
 *    real-trail envelope: touch-down 0.27kw from G (real: 0.05-0.31kw),
 *    t's first-basin match 0.85kw (real: 0.73-1.16kw), lift-off 0.11kw
 *    from O (real: 0.04-0.31kw), salient [g,o] as on 8/10 real go trails);
 *  - a genuine to swipe stays to: a real 'to' swipe STARTS on T, pays no
 *    surcharge, and the lever must never punish it (14 real to trails in
 *    the capture, all commit 'to' pre- and post-lever — this synthetic
 *    shape is the clean stand-in).
 */
class SwipeStartKeySurchargeTest {

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
    fun `drift start toward the neighbor commits go, not to`() {
        // Touch-down just off G toward T's row (0.27kw — the real trails'
        // envelope), sweep up-right with a slight bow, lift off just short
        // of O. Without the surcharge this committed to by 0.132 (frequency
        // overruled the diluted t-distance, exactly the captured disease);
        // the surcharge charges to's t (0.85kw basin) an extra
        // (0.85-0.5)*0.7 = 0.24 undiluted and go wins by 0.113.
        val trail = trailThroughPoints(
            Vec2(490f, 125f), Vec2(690f, 92f), Vec2(840f, 54f),
        )
        assertEquals("go", decodeCommitted(trail))
    }

    @Test
    fun `genuine to stays to`() {
        // A real to swipe STARTS on T: through the key centers with the
        // standard decelerating creep, t matches at ~0.0kw, the surcharge is
        // exactly zero, and to commits (measured to -3.379 vs too -2.262,
        // margin 1.12 — the lever must not touch this shape).
        val trail = trailThroughPoints(
            centerOf('t'), centerOf('o'),
        )
        assertEquals("to", decodeCommitted(trail))
    }
}
