package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationPopupTest {

    private val popupSize = Size(158f, 158f)
    private val container = Size(1000f, 400f)
    private val gap = 6f
    private val margin = 4f

    /** A 40px-wide key with vertical center at cx, top edge at y=300. */
    private fun anchor(cx: Float) = Rect(left = cx - 20f, top = 300f, right = cx + 20f, bottom = 352f)

    @Test
    fun `popup is horizontally centered over the anchor key`() {
        val topLeft = popupTopLeft(anchor(500f), popupSize, container, gap, margin)
        assertEquals(500f - popupSize.width / 2f, topLeft.x, 1e-3f)
    }

    @Test
    fun `popup bottom edge sits just above the anchor key`() {
        val topLeft = popupTopLeft(anchor(500f), popupSize, container, gap, margin)
        assertEquals(300f - popupSize.height - gap, topLeft.y, 1e-3f)
    }

    @Test
    fun `popup clamps to the left edge`() {
        val topLeft = popupTopLeft(anchor(10f), popupSize, container, gap, margin)
        assertEquals(margin, topLeft.x, 1e-3f)
    }

    @Test
    fun `popup clamps to the right edge`() {
        val topLeft = popupTopLeft(anchor(995f), popupSize, container, gap, margin)
        assertEquals(container.width - popupSize.width - margin, topLeft.x, 1e-3f)
    }

    @Test
    fun `grid hit-testing maps positions to indices`() {
        val bounds = Rect(0f, 0f, 150f, 150f) // 3x3 grid of 50px tiles
        assertEquals(0, popupIndexAt(Offset(25f, 25f), bounds)) // top-left
        assertEquals(8, popupIndexAt(Offset(125f, 125f), bounds)) // bottom-right
    }

    @Test
    fun `resting fingertip below the popup is outside the hit area`() {
        // The popup bottom edge sits 6dp above the period key's top edge, so
        // the resting fingertip at the key's vertical center is ~32dp below
        // the popup's bottom edge (~90px at Fold density) — beyond the 40px
        // bottom slack, so a no-drag release selects nothing and commits ".".
        val bounds = Rect(0f, 0f, 150f, 150f)
        assertEquals(-1, popupIndexAt(Offset(75f, 150f + 90f), bounds))
        // A deliberate drag into the bottom row still selects.
        assertEquals(7, popupIndexAt(Offset(75f, 125f), bounds))
    }

    @Test
    fun `most common punctuation sits on the bottom row`() {
        // Pin the full order: any merge that resurrects the old top-row
        // "! ? ," arrangement must fail loudly here.
        assertEquals(
            listOf("\"", ";", ":", "-", "'", ".", ",", "!", "?"),
            PUNCTUATION_POPUP,
        )
        val rows = PUNCTUATION_POPUP.chunked(PUNCTUATION_POPUP_COLUMNS)
        // "!" bottom-center (straight above the finger), "?" thumb-side corner.
        assertEquals(listOf(",", "!", "?"), rows.last())
    }
}
