package com.example.betterswipekeyboard.swipe

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class TrailTrimTest {

    private val keyRect = Rect(left = 100f, top = 100f, right = 160f, bottom = 160f)
    private val otherKeyRect = Rect(left = 200f, top = 100f, right = 260f, bottom = 160f)

    private fun point(x: Float, y: Float) = Vec2(x, y)

    @Test
    fun `prefix before the first letter contact trims`() {
        // Utility-row start: two approach points, then the finger enters a
        // letter key, then leaves it again (mid-trail excursion — kept).
        val points = listOf(
            point(130f, 20f), // utility row
            point(130f, 60f), // gap above the rows
            point(130f, 130f), // first letter-key contact
            point(180f, 130f), // gap between keys — mid-trail, untouched
        )
        assertEquals(2, firstLetterContactIndex(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `a drag that never touches a letter key has no contact`() {
        val points = listOf(point(10f, 20f), point(300f, 20f), point(600f, 40f))
        assertEquals(-1, firstLetterContactIndex(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `an all-letter trail starts at zero`() {
        val points = listOf(point(130f, 130f), point(230f, 130f))
        assertEquals(0, firstLetterContactIndex(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `empty trail has no contact`() {
        assertEquals(-1, firstLetterContactIndex(emptyList(), listOf(keyRect)))
    }

    @Test
    fun `no letter rects means no contact`() {
        assertEquals(-1, firstLetterContactIndex(listOf(point(130f, 130f)), emptyList()))
    }

    @Test
    fun `dead space drag crosses no letter keys`() {
        val points = listOf(point(10f, 20f), point(300f, 20f), point(600f, 40f))
        assertEquals(0, distinctLetterKeysCrossed(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `jitter inside one key crosses one letter key`() {
        val points = listOf(point(120f, 120f), point(140f, 140f), point(125f, 135f))
        assertEquals(1, distinctLetterKeysCrossed(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `crossing two keys counts two`() {
        val points = listOf(point(130f, 130f), point(180f, 130f), point(230f, 130f))
        assertEquals(2, distinctLetterKeysCrossed(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `a return visit counts distinct keys not visits`() {
        // e→w→e shape: two distinct keys, three visits.
        val points = listOf(point(130f, 130f), point(230f, 130f), point(130f, 130f))
        assertEquals(2, distinctLetterKeysCrossed(points, listOf(keyRect, otherKeyRect)))
    }

    @Test
    fun `counting is order independent`() {
        val rects = listOf(keyRect, otherKeyRect)
        val forward = listOf(point(130f, 130f), point(230f, 130f))
        val backward = listOf(point(230f, 130f), point(130f, 130f))
        assertEquals(
            distinctLetterKeysCrossed(forward, rects),
            distinctLetterKeysCrossed(backward, rects),
        )
    }

    @Test
    fun `approach prefix contributes nothing to the count`() {
        // Points before the first letter contact are in no letter rect, so
        // counting the full trail equals counting the trimmed trail.
        val rects = listOf(keyRect, otherKeyRect)
        val points = listOf(
            point(130f, 20f), // utility row
            point(130f, 60f), // gap above the rows
            point(130f, 130f), // first letter-key contact
            point(230f, 130f),
        )
        val trimmed = points.subList(firstLetterContactIndex(points, rects), points.size)
        assertEquals(
            distinctLetterKeysCrossed(trimmed, rects),
            distinctLetterKeysCrossed(points, rects),
        )
    }
}
