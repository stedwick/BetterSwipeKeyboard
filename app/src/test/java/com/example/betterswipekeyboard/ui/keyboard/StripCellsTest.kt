package com.example.betterswipekeyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(
            emptyList<StripCell>(),
            stripCells(null, listOf("hell", "help"), listOf("hell", "help"), 2),
        )
        assertEquals(emptyList<StripCell>(), stripCells(null, emptyList(), emptyList(), 4))
    }

    @Test
    fun `center word shows even with zero surviving alternates`() {
        assertEquals(
            listOf(StripCell("hello", isCenter = true)),
            stripCells("hello", emptyList(), emptyList(), 2),
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
            stripCells("hello", listOf("hell", "help", "held", "helm"), listOf("hell", "help", "held", "helm"), 2),
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
            stripCells("hello", listOf("hell", "help", "held", "helm"), listOf("hell", "help", "held", "helm"), 4),
        )
    }

    @Test
    fun `fewer surviving alternates than the mode allows just shortens the strip`() {
        assertEquals(
            listOf(
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
            ),
            stripCells("hello", listOf("hell"), listOf("hell"), 4),
        )
        assertEquals(
            listOf(
                StripCell("held", isCenter = false),
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
                StripCell("help", isCenter = false),
            ),
            stripCells("hello", listOf("hell", "help", "held"), listOf("hell", "help", "held"), 4),
        )
    }

    @Test
    fun `a band-mismatch dropout becomes a placeholder, survivors keep their slots`() {
        // "help" scored between MAX_COMMIT_SCORE and the near-miss band: it
        // showed mid-swipe (it is in stripOffers) but must not be offered
        // post-commit (it is not in alternates). It drops to an invisible
        // placeholder IN ITS SLOT — "hell" stays left-inner, "held" stays
        // left-outer — instead of re-laying-out and shifting them.
        val cells = stripCells(
            "hello",
            stripOffers = listOf("hell", "help", "held"),
            alternates = listOf("hell", "held"),
            maxAlternates = 4,
        )
        assertEquals(
            listOf(
                StripCell("held", isCenter = false),
                StripCell("hell", isCenter = false),
                StripCell("hello", isCenter = true),
                StripCell("help", isCenter = false, isPlaceholder = true),
            ),
            cells,
        )
        // Same word order as the no-dropout layout — only the flags differ.
        assertEquals(
            listOf("held", "hell", "hello", "help"),
            cells.map { it.word },
        )
    }

    @Test
    fun `failed-swipe offers place top-1 in a plain, tappable center slot`() {
        // Nothing was committed: the center is NOT the green committed-word
        // cell (isCenter stays false so the tap dispatch commits it like any
        // other offer), and no cell is the live leader.
        assertEquals(
            listOf(
                StripCell("keyword", isCenter = false),
                StripCell("keyboard", isCenter = false),
                StripCell("key West", isCenter = false),
            ),
            failedOfferCells(listOf("keyboard", "keyword", "key West"), 4),
        )
    }

    @Test
    fun `a single failed-swipe offer is one plain center cell`() {
        assertEquals(
            listOf(StripCell("keyboard", isCenter = false)),
            failedOfferCells(listOf("keyboard"), 4),
        )
    }

    @Test
    fun `empty failed-swipe offers yield no cells`() {
        assertEquals(emptyList<StripCell>(), failedOfferCells(emptyList(), 4))
    }

    @Test
    fun `live strip marks the centered top-1 as leader when it would commit`() {
        assertEquals(
            listOf(
                StripCell("keyword", isCenter = false),
                StripCell("keyboard", isCenter = false, isLiveLeader = true),
                StripCell("key West", isCenter = false),
            ),
            liveOfferCells(
                LiveOffers(listOf("keyboard", "keyword", "key West"), leaderWouldCommit = true),
                4,
            ),
        )
    }

    @Test
    fun `live strip marks nothing when top-1 would not commit`() {
        // Top-1 at/above MAX_COMMIT_SCORE: lifting the finger now commits
        // nothing, so a blue leader mark would lie — plain cells, same slots.
        val cells = liveOfferCells(
            LiveOffers(listOf("keyboard", "keyword"), leaderWouldCommit = false),
            4,
        )
        assertEquals(
            listOf(
                StripCell("keyword", isCenter = false),
                StripCell("keyboard", isCenter = false),
            ),
            cells,
        )
        assertFalse(cells.any { it.isLiveLeader })
    }

    @Test
    fun `live, failed and committed strips share the exact same word order`() {
        // Philip's rule: the row must be identical while swiping and after
        // finger-up — only the center's color/flag changes, words never move.
        val ranked = listOf("keyboard", "keyword", "key West", "keyboards")
        val live = liveOfferCells(LiveOffers(ranked, leaderWouldCommit = true), 4)
        val failed = failedOfferCells(ranked, 4)
        val committed = stripCells(
            "keyboard",
            stripOffers = ranked.drop(1),
            alternates = ranked.drop(1),
            maxAlternates = 4,
        )
        assertEquals(listOf("keyboards", "keyword", "keyboard", "key West"), live.map { it.word })
        assertEquals(live.map { it.word }, failed.map { it.word })
        assertEquals(live.map { it.word }, committed.map { it.word })
        // And only the center slot's flags change between the three states.
        assertTrue(live[2].isLiveLeader && !live[2].isCenter)
        assertTrue(!failed[2].isLiveLeader && !failed[2].isCenter)
        assertTrue(committed[2].isCenter && !committed[2].isLiveLeader)
    }
}
