package com.example.betterswipekeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Guards for the last-letter lift-off-basin re-match
 * ([REBASIN_RADIUS_KEYS] in SwipeDecoder — overshoot-and-return, the
 * 'keyboard' fix). The synthetic geometry mirrors the 7 silent captures in
 * swipe_trails_word_keyboard.jsonl: the finger overshoots the last key and
 * returns to it, which first-basin matching cannot see. Each test pins one
 * gate of the re-match (see the re-match comment in SwipeDecoder.score):
 *
 *  - a genuine overshoot-and-return commits the intended word, clear of
 *    its plural: the longer word matches the overshoot key as a MIDDLE
 *    letter at the first pass and parks its unmatched final letter at the
 *    trail-end clamp, while the intended word pays first-basin distance +
 *    tail arc. The re-match moves the last letter's charge to the lift-off
 *    basin and zeroes the tail — the measured margin over the plural more
 *    than doubles (see the first test for numbers);
 *  - a lift-off drift near a foreign key does not summon it (the same
 *    deliberate-vs-drift line as the lift-off salience grading; the drift
 *    endpoint's final basin sits beyond REBASIN_RADIUS_KEYS);
 *  - a wild excursion still hits the 1.75kw line-conformance cull —
 *    re-anchoring the last letter must never resurrect a cull-worthy
 *    trail (the cull is evaluated on the re-matched legs).
 *
 * The commit-side trail keeps its worst off-segment excursion under ~1.0
 * key-widths, leaving headroom under the cull (the 14 captured keyboard
 * trails measured at most 1.45kw). The overshoot goes off the SIDE of the
 * r->d leg rather than past d: a collinear overshoot's return leg opposes
 * the r->d direction and pays full backtrack (the same term that dominates
 * the two real residual trails #0/#7), masking the re-match's win.
 */
class SwipeRebasinTest {

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
     * creeping forward). Raw points, not letters — the overshoot waypoints
     * of these tests deliberately sit between keys.
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

    /** k->e->y->b->o->a->r straight, then the given excursion and end point. */
    private fun keyboardTrail(excursion: List<Vec2>, end: Vec2): List<TimedPoint> {
        val letters = listOf('k', 'e', 'y', 'b', 'o', 'a', 'r').map { centerOf(it) }
        return trailThroughPoints(*(letters + excursion + end).toTypedArray())
    }

    @Test
    fun `overshoot and return on the last letter commits keyboard`() {
        // Off the r->d line right after r, out sideways (0.94kw off the
        // segment — the basin closes above the pass minimum), and back onto
        // d (0.04kw, the lift-off basin). The excursion goes off the SIDE of
        // the r->d leg rather than past d: a collinear overshoot's return
        // leg opposes the r->d direction and pays full backtrack, masking
        // the re-match's win (the same term that dominates the two real
        // residual trails #0/#7).
        //
        // The margin assertion is what pins the re-match on this shape:
        // stock first-basin matching locks d at 1.05kw mid-trail and
        // charges 0.78 tail arc (keyboard 0.061 vs keyboards 0.661, margin
        // 0.60); the re-match moves d to the lift-off basin (0.04kw) and
        // zeroes the tail (keyboard -0.781 vs keyboards 0.661, margin
        // 1.44). Measured with the investigation's instrumented decoder;
        // without the re-match the margin collapses back to ~0.6. The
        // plural contest is the live pressure here: "keyboards" matches d
        // as a MIDDLE letter at the first pass and parks s at the
        // trail-end clamp, so it gains nothing from the re-match.
        val trail = keyboardTrail(
            excursion = listOf(Vec2(323f, 104f), Vec2(413f, 143f), Vec2(417f, 150f), Vec2(413f, 157f)),
            end = Vec2(305f, 152f),
        )
        val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
        println(results.joinToString { "${it.word}=%.3f".format(it.score) })
        val keyboard = results.first { it.word == "keyboard" }
        val keyboards = results.first { it.word == "keyboards" }
        assertEquals("keyboard", results.first().word)
        assertTrue(
            "re-match pulls keyboard clear of its plural (margin " +
                "${keyboards.score - keyboard.score}, stock measured 0.60)",
            keyboards.score - keyboard.score > 1.0f,
        )
    }

    @Test
    fun `lift off drift near a foreign key does not summon it`() {
        // A straight t->h->e whose lift-off drifts down-left, ending 1.27kw
        // from r. The impostor "ther" can only match its r in the end
        // region (ordered matching puts the mid-trail r-pass before e's
        // match): the final basin sits beyond REBASIN_RADIUS_KEYS, so the
        // drift endpoint's nearest key cannot claim the word.
        val trail = trailThroughPoints(
            centerOf('t'), centerOf('h'), centerOf('e'), Vec2(230f, 90f),
        )
        assertEquals("the", decodeCommitted(trail))
    }

    @Test
    fun `wild excursion past the last key still culls`() {
        // Same overshoot-and-return shape, but the excursion reaches 1.95kw
        // off the r->d segment — past the 1.75kw line-conformance cull. The
        // re-match re-anchors d, but legCosts still evaluates the excursion
        // points on the re-matched leg: keyboard is rejected outright.
        val trail = keyboardTrail(
            excursion = listOf(Vec2(360f, 320f), Vec2(365f, 328f), Vec2(358f, 336f)),
            end = Vec2(310f, 160f),
        )
        assertNotEquals("keyboard", decodeCommitted(trail))
    }
}
