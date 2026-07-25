package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.junit.Assert.assertEquals
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
            t += (8 / speedFactor).toLong().coerceAtLeast(4)
        }
        add(waypoints.first(), 0.3f)
        for (w in 1 until waypoints.size) {
            val from = waypoints[w - 1]
            val to = waypoints[w]
            val steps = maxOf(1, (from.distanceTo(to) / 3f).toInt())
            for (s in 1..steps) {
                // Slow near segment ends, fast in the middle.
                val phase = s.toFloat() / steps
                val speedFactor = 0.3f + 0.7f * sin(phase * Math.PI).toFloat()
                add(Vec2(from.x + (to.x - from.x) * s / steps, from.y + (to.y - from.y) * s / steps), speedFactor)
            }
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

    private companion object {
        const val KEY_WIDTH = 100f
    }
}
