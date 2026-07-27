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
}
