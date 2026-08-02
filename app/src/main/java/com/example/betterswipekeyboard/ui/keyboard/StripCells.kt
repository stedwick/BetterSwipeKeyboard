package com.example.betterswipekeyboard.ui.keyboard

/**
 * Pure, unit-tested cell model for the swipe-alternates strip. ONE placement
 * rule serves all three strip states (live mid-swipe, persisted failed-swipe
 * offers, committed): the leader/center word in the MIDDLE with the
 * score-ranked runner-ups flanking it — so lifting the finger only recolors
 * the center, never rearranges the row.
 *
 * [isCenter]: the COMMITTED strip's center slot — rendered green and bold,
 * tap = no-op (it is the word already in the text field).
 * [isLiveLeader]: the LIVE strip's center slot when its word WOULD commit if
 * the finger lifted now — rendered light blue, never green (green is
 * reserved for the committed word). A live center that would not commit is
 * plain: same position, no color promise. The TAP-typing mirror borrows
 * this mark purely for the blue rendering (KeyboardScreen passes
 * leaderWouldCommit = true for the word mid-tap; nothing commits on
 * finger-up there — the blue cell is a mirror, not a leader).
 * [isPlaceholder]: a dropped band-mismatch flank (see [stripCells]) —
 * invisible, reserves its slot so the surviving words never move, and is
 * never tappable.
 */
data class StripCell(
    val word: String,
    val isCenter: Boolean,
    val isLiveLeader: Boolean = false,
    val isPlaceholder: Boolean = false,
)

/**
 * A LIVE mid-swipe decode's offers: [words] are the decoder's top candidates
 * ranked top-1 first (the `failedSwipeOffers` band-filtered list, so top-1 is
 * always `words.first()` whenever the list is non-empty — the strip's center
 * slot) and [leaderWouldCommit] is true exactly when top-1's score is below
 * MAX_COMMIT_SCORE — i.e. lifting the finger NOW would commit `words.first()`.
 * The flag is live-gesture-only: it never enters KeyboardState (a persisted
 * failed swipe's offers render without the leader mark, because after
 * finger-up nothing would auto-commit anymore and the mark would lie).
 * The tap-typing mirror (KeyboardScreen) wraps its single word in LiveOffers
 * too, borrowing the leader mark for the blue color only.
 */
data class LiveOffers(val words: List<String>, val leaderWouldCommit: Boolean)

/**
 * Strip width below which only TWO alternates flank the center word; at or
 * above it, FOUR show (two each side). 600dp is the point where five cells
 * (4 alts + center) stop crowding: every phone in portrait (360-430dp) gets
 * the skinny 3-cell strip, while foldables, tablets and landscape phones get
 * the wide 5-cell one. Gboard likewise shows more suggestions as width
 * grows. Tuning starting point — verify the breakpoint on-device.
 */
private const val WIDE_STRIP_MIN_WIDTH_DP = 600f

/** Pure, unit-tested: how many runner-up words the strip shows at [widthDp]. */
fun alternateCountForWidth(widthDp: Float): Int =
    if (widthDp >= WIDE_STRIP_MIN_WIDTH_DP) 4 else 2

/**
 * The ONE placement rule behind all three strip states: [center] in the
 * middle, [flanks] (score-ranked best-first) flanking it, best runner-up
 * nearest the center, alternating sides by rank: [a3, a1, CENTER, a2, a4]
 * (wide) or [a1, CENTER, a2] (skinny) — so the two most likely corrections
 * sit a thumb-twitch from the center word, and the ranking reads outward on
 * both sides. Ranks assign FIXED slots (rank 0 left-inner, rank 1
 * right-inner, rank 2 left-outer, rank 3 right-outer): a dropped flank stays
 * in [flanks] as an [StripCell.isPlaceholder] instead of being removed, so
 * the index parity — and with it every surviving word's side and slot — is
 * preserved.
 */
private fun centeredCells(
    center: StripCell,
    flanks: List<StripCell>,
    maxAlternates: Int,
): List<StripCell> {
    val shown = flanks.take(maxAlternates)
    val left = shown.filterIndexed { index, _ -> index % 2 == 0 }.asReversed()
    val right = shown.filterIndexed { index, _ -> index % 2 == 1 }
    return left + center + right
}

/**
 * The COMMITTED strip: [committedWord] as the green center (tap = no-op).
 * Empty when no swipe is armed ([committedWord] null) — the strip renders
 * its placeholder text then.
 *
 * [stripOffers] is the WIDE flank list — the near-miss-band runner-ups in
 * rank order, exactly what the live strip showed while swiping;
 * [alternates] is the NARROW survivor list — the runner-ups below
 * MAX_COMMIT_SCORE that may be offered post-commit. A runner-up between
 * MAX_COMMIT_SCORE and the near-miss band shows mid-swipe but must not be
 * offered post-commit (it is the same decoder junk the commit gate itself
 * rejects): it drops to an invisible [StripCell.isPlaceholder] — a gap, NOT
 * a re-lay-out — so every surviving word keeps the exact slot it had while
 * swiping and lifting the finger never rearranges the row.
 */
fun stripCells(
    committedWord: String?,
    stripOffers: List<String>,
    alternates: List<String>,
    maxAlternates: Int,
): List<StripCell> {
    if (committedWord == null) return emptyList()
    val flanks = stripOffers.map { word ->
        StripCell(word, isCenter = false, isPlaceholder = word !in alternates)
    }
    return centeredCells(StripCell(committedWord, isCenter = true), flanks, maxAlternates)
}

/**
 * The FAILED swipe's persisted offers: top-1 in the center slot, PLAIN (no
 * green, no blue — nothing was committed and nothing would auto-commit), the
 * remaining offers flanking it. Unlike a committed strip's green center, the
 * center slot here IS tappable (it commits through the OfferFailedSwipe
 * path). Same placement as the live strip, so finger-up never rearranges
 * the row. Used for both rendering and tap hit-testing, same as
 * [stripCells].
 */
fun failedOfferCells(offers: List<String>, maxAlternates: Int): List<StripCell> {
    if (offers.isEmpty()) return emptyList()
    return centeredCells(
        StripCell(offers.first(), isCenter = false),
        offers.drop(1).map { StripCell(it, isCenter = false) },
        maxAlternates,
    )
}

/**
 * The LIVE mid-swipe strip: top-1 in the center slot — [StripCell.isLiveLeader]
 * (light blue) exactly when it would commit on finger-up
 * ([LiveOffers.leaderWouldCommit]), plain otherwise (marking a leader that
 * would NOT actually commit would lie) — flanked by the remaining band
 * offers. Same placement as the committed strip, so lifting the finger only
 * recolors the center (blue to green), never rearranges the row.
 */
fun liveOfferCells(offers: LiveOffers, maxAlternates: Int): List<StripCell> {
    if (offers.words.isEmpty()) return emptyList()
    return centeredCells(
        StripCell(
            offers.words.first(),
            isCenter = false,
            isLiveLeader = offers.leaderWouldCommit,
        ),
        offers.words.drop(1).map { StripCell(it, isCenter = false) },
        maxAlternates,
    )
}
