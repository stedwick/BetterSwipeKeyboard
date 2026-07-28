package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom words (user-added via the setup screen) must be swipable, and
 * adding them must not distort decoding of common built-in words.
 *
 * "kijimi" is deliberately absent from words_en.txt, and no common English
 * word follows the k→i→j→i→m→i key path, so the trail has no built-in
 * competitor. (This used to be "kimi", but the wordfreq-based dictionary
 * now contains it at rank ~22k — a built-in word would make the first test
 * vacuous.)
 */
class SwipeDecoderCustomWordsTest {

    private val keyCenters: Map<Char, Vec2> = buildMap {
        "qwertyuiop".forEachIndexed { i, c -> put(c, Vec2(50f + i * 100f, 50f)) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, Vec2(100f + i * 100f, 150f)) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, Vec2(250f + i * 100f, 250f)) }
    }

    private fun decoderWith(vararg customWords: String): SwipeDecoder {
        val words = javaClass.getResourceAsStream("/words_en.txt")!!
        return SwipeDecoder(Dictionary.load(words).withCustomWords(customWords.toList()))
    }

    private fun centerOf(letter: Char) = keyCenters.getValue(letter)

    /** Same dense, jittery, speed-profiled trail as [SwipeDecoderRealisticTrailTest]. */
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
            t += (8 / speedFactor).toLong().coerceAtLeast(4)
        }
        add(waypoints.first(), 0.3f)
        for (w in 1 until waypoints.size) {
            val from = waypoints[w - 1]
            val to = waypoints[w]
            val steps = maxOf(1, (from.distanceTo(to) / 3f).toInt())
            for (s in 1..steps) {
                // Slow near segment ends, fast in the middle (0.25 floor —
                // see SwipeDecoderRealisticTrailTest for why turns must
                // genuinely linger).
                val phase = s.toFloat() / steps
                val speedFactor = 0.25f + 0.75f * sin(phase * Math.PI).toFloat()
                add(Vec2(from.x + (to.x - from.x) * s / steps, from.y + (to.y - from.y) * s / steps), speedFactor)
            }
        }
        // Same decelerating lift-off tail as SwipeDecoderRealisticTrailTest
        // (which see for why the points keep creeping forward): the sin
        // profile's end gaps alone don't register as measured slowness, so
        // the end region stayed evidence-free. ~128 ms total, well under
        // DWELL_DOUBLE_MS — the last letter never doubles.
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
    fun `custom word not in the built-in dictionary decodes first`() {
        val results = decoderWith("kijimi")
            .decode(realisticTrail('k', 'i', 'j', 'i', 'm', 'i'), keyCenters, KEY_WIDTH)
        assertEquals("kijimi", results.firstOrNull()?.word)
    }

    @Test
    fun `common words still decode first with custom words present`() {
        val decoder = decoderWith("kimi", "zxqw")
        assertEquals(
            "hello",
            decoder.decode(realisticTrail('h', 'e', 'l', 'o'), keyCenters, KEY_WIDTH)
                .firstOrNull()?.word,
        )
        assertEquals(
            "swipe",
            decoder.decode(realisticTrail('s', 'w', 'p', 'e'), keyCenters, KEY_WIDTH)
                .firstOrNull()?.word,
        )
    }

    private companion object {
        const val KEY_WIDTH = 100f
    }
}
