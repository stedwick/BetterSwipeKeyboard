package com.example.betterswipekeyboard.ui.keyboard

/**
 * Pure, unit-tested cell model for the swipe-alternates strip: the committed
 * word in the center ([isCenter], rendered green, tap = no-op) with the
 * score-ranked runner-ups flanking it. [isLiveLeader] marks the cell a LIVE
 * mid-swipe decode would commit if the finger lifted now (rendered underlined,
 * never green — green is reserved for the committed center); it is set only by
 * [liveOfferCells] and defaults to false everywhere else.
 */
data class StripCell(val word: String, val isCenter: Boolean, val isLiveLeader: Boolean = false)

/**
 * A LIVE mid-swipe decode's offers: [words] are the decoder's top candidates
 * ranked top-1 first (the `failedSwipeOffers` band-filtered list, so top-1 is
 * always `words.first()` whenever the list is non-empty) and
 * [leaderWouldCommit] is true exactly when top-1's score is below
 * MAX_COMMIT_SCORE — i.e. lifting the finger NOW would commit `words.first()`.
 * The flag is live-gesture-only: it never enters KeyboardState (a persisted
 * failed swipe's offers render without the leader mark, because after
 * finger-up nothing would auto-commit anymore and the mark would lie).
 */
data class LiveOffers(val words: List<String>, val leaderWouldCommit: Boolean)

/**
 * Strip width below which only TWO alternates show (one each side of the
 * center word); at or above it, FOUR show (two each side). 600dp is the
 * point where five cells (4 alts + center) stop crowding: every phone in
 * portrait (360-430dp) gets the skinny 3-cell strip, while foldables,
 * tablets and landscape phones get the wide 5-cell one. Gboard likewise
 * shows more suggestions as width grows. Tuning starting point — verify the
 * breakpoint on-device.
 */
private const val WIDE_STRIP_MIN_WIDTH_DP = 600f

/** Pure, unit-tested: how many runner-up words the strip shows at [widthDp]. */
fun alternateCountForWidth(widthDp: Float): Int =
    if (widthDp >= WIDE_STRIP_MIN_WIDTH_DP) 4 else 2

/**
 * Pure, unit-tested: the strip's cells in display order. Empty when no swipe
 * is armed ([committedWord] null) — the strip renders its placeholder then.
 * The alternates are score-ranked best-first; placement keeps the BEST
 * runner-up nearest the center, alternating sides by rank:
 * [a3, a1, CENTER, a2, a4] (wide) or [a1, CENTER, a2] (skinny) — so the two
 * most likely corrections sit a thumb-twitch from the committed word, and
 * the ranking reads outward from the green center on both sides.
 */
fun stripCells(
    committedWord: String?,
    alternates: List<String>,
    maxAlternates: Int,
): List<StripCell> {
    if (committedWord == null) return emptyList()
    val shown = alternates.take(maxAlternates)
    val left = shown.filterIndexed { index, _ -> index % 2 == 0 }.asReversed()
    val right = shown.filterIndexed { index, _ -> index % 2 == 1 }
    return left.map { StripCell(it, isCenter = false) } +
        StripCell(committedWord, isCenter = true) +
        right.map { StripCell(it, isCenter = false) }
}

/**
 * Cells for a FAILED swipe's offers: rank order, NO center cell — nothing was
 * committed, so a green center would lie. Used for both rendering and tap
 * hit-testing, same as [stripCells].
 */
fun failedOfferCells(offers: List<String>): List<StripCell> =
    offers.map { StripCell(it, isCenter = false) }

/**
 * Cells for a LIVE mid-swipe decode: plain cells in rank order (no center —
 * nothing is committed), with the first cell (top-1) marked [StripCell.isLiveLeader]
 * when it would commit on finger-up ([LiveOffers.leaderWouldCommit]). When the
 * flag is false the cells are plain near-miss offers — underlining a leader
 * that would NOT actually commit would lie.
 */
fun liveOfferCells(offers: LiveOffers): List<StripCell> =
    offers.words.mapIndexed { index, word ->
        StripCell(word, isCenter = false, isLiveLeader = index == 0 && offers.leaderWouldCommit)
    }
