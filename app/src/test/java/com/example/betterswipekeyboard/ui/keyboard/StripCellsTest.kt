package com.example.betterswipekeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class StripCellsTest {

    @Test
    fun `narrow widths get two alternates, wide widths get four`() {
        // Phones in portrait (360-430dp) and anything narrower: skinny mode.
        assertEquals(2, alternateCountForWidth(0f))
        assertEquals(2, alternateCountForWidth(360f))
        assertEquals(2, alternateCountForWidth(599f))
        // Foldables, tablets, landscape phones: wide mode.
        assertEquals(4, alternateCountForWidth(600f))
        assertEquals(4, alternateCountForWidth(840f))
    }

    @Test
    fun `no armed swipe yields no cells (placeholder renders)`() {
        assertEquals(emptyList<StripCell>(), stripCells(null, listOf("hell", "help"), 2))
        assertEquals(emptyList<StripCell>(), stripCells(null, emptyList(), 4))
    }

    @Test
    fun `center word shows even with zero surviving alternates`() {
        assertEquals(
            listOf(StripCell("hello", isCenter = true)),
            stripCells("hello", emptyList(), 2),
        )
    }

    @Test
    fun `skinny mode places the best runner-up left of center, second right`() {
        assertEquals(
            listOf(
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
                StripCell("help", isCenter = false),
            ),
            stripCells("hello", listOf("hell", "help", "held", "helm"), 2),
        )
    }

    @Test
    fun `wide mode flanks the center best-nearest, alternating sides by rank`() {
        assertEquals(
            listOf(
                StripCell("held", isCenter = false),
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
                StripCell("help", isCenter = false),
                StripCell("helm", isCenter = false),
            ),
            stripCells("hello", listOf("hell", "help", "held", "helm"), 4),
        )
    }

    @Test
    fun `fewer surviving alternates than the mode allows just shortens the strip`() {
        assertEquals(
            listOf(
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
            ),
            stripCells("hello", listOf("hell"), 4),
        )
        assertEquals(
            listOf(
                StripCell("held", isCenter = false),
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
                StripCell("help", isCenter = false),
            ),
            stripCells("hello", listOf("hell", "help", "held"), 4),
        )
    }

    @Test
    fun `failed-swipe offers render in rank order with no center cell`() {
        // Nothing was committed, so no cell is the green center — the best
        // rescue candidate is simply leftmost.
        assertEquals(
            listOf(
                StripCell("keyboard", isCenter = false),
                StripCell("keyword", isCenter = false),
                StripCell("key West", isCenter = false),
            ),
            failedOfferCells(listOf("keyboard", "keyword", "key West")),
        )
    }

    @Test
    fun `a single failed-swipe offer is one plain cell`() {
        assertEquals(
            listOf(StripCell("keyboard", isCenter = false)),
            failedOfferCells(listOf("keyboard")),
        )
    }
}
